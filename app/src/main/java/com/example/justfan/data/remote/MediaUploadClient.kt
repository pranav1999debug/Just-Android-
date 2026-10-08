package com.example.justfan.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.justfan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class BufferChannel(
    val id: String,
    val name: String,
    val displayName: String,
    val service: String,
    val avatar: String? = null,
    val characterLimit: Int = 2000
)

data class GeneratedCaption(
    val description: String,
    val hashtags: List<String>
)

data class MediaGroupResult(
    val uploadRequestId: String,
    val host: String,
    val mediaType: String,
    val fileCount: Int,
    val urls: List<String>,
    val status: String,
    val bufferPostId: String? = null,
    val error: String? = null
)

data class MediaUploadResponse(
    val success: Boolean,
    val groups: List<MediaGroupResult>,
    val error: String? = null
)

object MediaUploadClient {
    private const val TAG = "MediaUploadClient"
    private const val DEBUG_TAG = "UploadDebug"
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val SUPABASE_KEY = BuildConfig.SUPABASE_KEY
    private val BASE_FUNCTIONS_URL = "$SUPABASE_URL/functions/v1"
    private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024 // 50MB

    /**
     * Lists connected Buffer channels using the list-buffer-channels Edge Function.
     */
    suspend fun listBufferChannels(): Result<List<BufferChannel>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_FUNCTIONS_URL/list-buffer-channels")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                connectTimeout = 20000
                readTimeout = 25000
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write("{}") }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                val err = conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: "HTTP $code"
                conn.disconnect()
                return@withContext Result.failure(Exception("Failed to fetch Buffer channels ($code): $err"))
            }
            conn.disconnect()

            val json = JSONObject(responseText)
            val channelsArray = json.optJSONArray("channels") ?: JSONArray()
            val list = mutableListOf<BufferChannel>()

            for (i in 0 until channelsArray.length()) {
                val ch = channelsArray.getJSONObject(i)
                list.add(
                    BufferChannel(
                        id = ch.optString("id", ""),
                        name = ch.optString("name", "Buffer Channel"),
                        displayName = ch.optString("displayName", ch.optString("name", "Channel")),
                        service = ch.optString("service", "social"),
                        avatar = if (ch.has("avatar") && !ch.isNull("avatar")) ch.optString("avatar") else null,
                        characterLimit = ch.optInt("characterLimit", 2000)
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "listBufferChannels error", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a description and hashtags given a title using Gemini AI with fallback.
     */
    suspend fun generateCaption(title: String): Result<GeneratedCaption> = withContext(Dispatchers.IO) {
        try {
            val genResult = GeminiAiHelper.generateDescriptionAndHashtags(title).getOrThrow()
            Result.success(GeneratedCaption(description = genResult.description, hashtags = genResult.hashtags))
        } catch (e: Exception) {
            Log.e(TAG, "generateCaption error", e)
            Result.failure(e)
        }
    }

    /**
     * Convenience method to upload a single media file and return its URL directly.
     */
    suspend fun uploadSingleMedia(
        context: Context,
        uri: Uri,
        title: String? = null
    ): Result<String> {
        val result = uploadMedia(
            context = context,
            uris = listOf(uri),
            title = title
        )
        return if (result.isSuccess) {
            val response = result.getOrNull()
            val url = response?.groups?.flatMap { it.urls }?.firstOrNull()
            if (!url.isNullOrBlank()) {
                Result.success(url)
            } else {
                val err = response?.error ?: response?.groups?.mapNotNull { it.error }?.firstOrNull() ?: "No URL returned from upload"
                Result.failure(Exception(err))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Upload failed"))
        }
    }

    /**
     * Direct media upload with automatic host routing:
     * - Images -> Imgchest
     * - Videos -> Catbox
     * Holds URLs in memory, saves to `upload_requests` via Supabase REST API,
     * and optionally triggers `post-to-buffer` Edge Function if requested.
     */
    suspend fun uploadMedia(
        context: Context,
        uris: List<Uri>,
        title: String? = null,
        description: String? = null,
        hashtags: String? = null,
        channelId: String? = null,
        mode: String = "addToQueue",
        shareToSocial: Boolean = false,
        userId: String? = null
    ): Result<MediaUploadResponse> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No media selected for upload"))
        }

        // Check video size limit: max 50MB
        for (uri in uris) {
            val mime = getMimeType(context, uri)
            if (mime.startsWith("video/")) {
                val size = getFileSize(context, uri)
                if (size > MAX_VIDEO_BYTES) {
                    Log.e(DEBUG_TAG, "Video exceeds 50MB ($size bytes)")
                    return@withContext Result.failure(Exception("Video too large, max 50MB"))
                }
            }
        }

        val groups = mutableListOf<MediaGroupResult>()
        val allUploadedUrls = mutableListOf<String>()
        val effectiveTitle = title?.ifBlank { "JUSTFAN Media" } ?: "JUSTFAN Media"

        // Separate images and videos
        val imageUris = mutableListOf<Uri>()
        val videoUris = mutableListOf<Uri>()

        for (uri in uris) {
            val mime = getMimeType(context, uri)
            if (mime.startsWith("video/")) {
                videoUris.add(uri)
            } else {
                imageUris.add(uri)
            }
        }

        var primaryRequestId = ""

        // 1. Process Images via Imgchest
        if (imageUris.isNotEmpty()) {
            val host = "imgchest"
            val mediaType = "image"
            val reqId = createUploadRequestRecord(
                host = host,
                mediaType = mediaType,
                fileCount = imageUris.size,
                title = effectiveTitle,
                userId = userId
            )
            if (reqId.isNotBlank() && primaryRequestId.isBlank()) {
                primaryRequestId = reqId
            }

            val imgResult = ImgchestUploader.uploadImagesDirect(
                context = context,
                uris = imageUris,
                title = effectiveTitle
            )

            if (imgResult.isSuccess) {
                val urls = imgResult.getOrNull() ?: emptyList()
                allUploadedUrls.addAll(urls)
                updateUploadRequestRecord(
                    id = reqId,
                    status = "done",
                    imageUrls = urls,
                    error = null
                )
                groups.add(
                    MediaGroupResult(
                        uploadRequestId = reqId,
                        host = host,
                        mediaType = mediaType,
                        fileCount = imageUris.size,
                        urls = urls,
                        status = "done"
                    )
                )
            } else {
                val err = imgResult.exceptionOrNull()?.message ?: "Imgchest upload failed"
                updateUploadRequestRecord(
                    id = reqId,
                    status = "failed",
                    imageUrls = null,
                    error = err
                )
                groups.add(
                    MediaGroupResult(
                        uploadRequestId = reqId,
                        host = host,
                        mediaType = mediaType,
                        fileCount = imageUris.size,
                        urls = emptyList(),
                        status = "failed",
                        error = err
                    )
                )
            }
        }

        // 2. Process Videos via Catbox
        if (videoUris.isNotEmpty()) {
            val host = "catbox"
            val mediaType = "video"
            val reqId = createUploadRequestRecord(
                host = host,
                mediaType = mediaType,
                fileCount = videoUris.size,
                title = effectiveTitle,
                userId = userId
            )
            if (reqId.isNotBlank() && primaryRequestId.isBlank()) {
                primaryRequestId = reqId
            }

            val videoUrls = mutableListOf<String>()
            var videoError: String? = null

            for (vUri in videoUris) {
                val vRes = uploadVideoToCatbox(context, vUri)
                if (vRes.isSuccess) {
                    val url = vRes.getOrNull().orEmpty()
                    if (url.isNotBlank()) {
                        videoUrls.add(url)
                    }
                } else {
                    videoError = vRes.exceptionOrNull()?.message ?: "Catbox video upload failed"
                    break
                }
            }

            if (videoError == null && videoUrls.isNotEmpty()) {
                allUploadedUrls.addAll(videoUrls)
                updateUploadRequestRecord(
                    id = reqId,
                    status = "done",
                    imageUrls = videoUrls,
                    error = null
                )
                groups.add(
                    MediaGroupResult(
                        uploadRequestId = reqId,
                        host = host,
                        mediaType = mediaType,
                        fileCount = videoUris.size,
                        urls = videoUrls,
                        status = "done"
                    )
                )
            } else {
                val err = videoError ?: "Catbox video upload failed"
                updateUploadRequestRecord(
                    id = reqId,
                    status = "failed",
                    imageUrls = null,
                    error = err
                )
                groups.add(
                    MediaGroupResult(
                        uploadRequestId = reqId,
                        host = host,
                        mediaType = mediaType,
                        fileCount = videoUris.size,
                        urls = emptyList(),
                        status = "failed",
                        error = err
                    )
                )
            }
        }

        // If no URLs were successfully uploaded, return failure with the real error
        if (allUploadedUrls.isEmpty()) {
            val firstErr = groups.mapNotNull { it.error }.firstOrNull() ?: "Upload failed"
            return@withContext Result.failure(Exception(firstErr))
        }

        // 3. Social posting via Buffer Edge Function if requested
        var bufferPostId: String? = null
        if (shareToSocial && !channelId.isNullOrBlank()) {
            try {
                val isAnyVideo = videoUris.isNotEmpty()
                val postResult = callPostToBufferFunction(
                    channelId = channelId,
                    title = effectiveTitle,
                    description = description.orEmpty(),
                    hashtags = hashtags.orEmpty(),
                    mediaUrls = allUploadedUrls,
                    mediaType = if (isAnyVideo) "video" else "image",
                    mode = mode,
                    uploadRequestId = primaryRequestId.ifBlank { null }
                )
                bufferPostId = postResult.optString("id", null)
            } catch (e: Exception) {
                Log.e(TAG, "Buffer post failed: ${e.message}")
            }
        }

        Result.success(
            MediaUploadResponse(
                success = true,
                groups = groups.map { if (bufferPostId != null) it.copy(bufferPostId = bufferPostId) else it }
            )
        )
    }

    /**
     * Uploads a video file directly to Catbox.
     * Retries once on 401 or 403 by refreshing catbox_userhash from SecretsStore.
     */
    private suspend fun uploadVideoToCatbox(
        context: Context,
        uri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var userhash = SecretsStore.get("catbox_userhash")
            var attempts = 0
            while (attempts < 2) {
                attempts++
                val res = runCatching {
                    uploadVideoToCatboxInternal(context, uri, userhash)
                }

                if (res.isSuccess) {
                    return@withContext res
                }

                val ex = res.exceptionOrNull()
                if (ex is HostAuthException && attempts < 2) {
                    Log.w(TAG, "Catbox returned 401/403, clearing secret cache and retrying...")
                    SecretsStore.clearCache()
                    userhash = SecretsStore.get("catbox_userhash", forceReload = true)
                    continue
                }
                return@withContext res
            }
            Result.failure(Exception("Catbox upload failed after retry"))
        } catch (e: Exception) {
            Log.e(TAG, "uploadVideoToCatbox failed", e)
            Result.failure(e)
        }
    }

    /**
     * Internal streaming upload to Catbox using 8KB chunked streaming mode.
     * Never loads the whole video into memory.
     */
    private fun uploadVideoToCatboxInternal(
        context: Context,
        uri: Uri,
        userhash: String
    ): String {
        val boundary = "==CatboxBoundary_${System.currentTimeMillis()}=="
        val url = URL("https://catbox.moe/user/api.php")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 60000
            readTimeout = 180000
            setChunkedStreamingMode(8192)
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        val lineEnd = "\r\n"
        val twoHyphens = "--"
        val outputStream = DataOutputStream(conn.outputStream)

        // reqtype field
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"reqtype\"$lineEnd$lineEnd")
        outputStream.writeBytes("fileupload")
        outputStream.writeBytes(lineEnd)

        // userhash field
        if (userhash.isNotBlank()) {
            outputStream.writeBytes(twoHyphens + boundary + lineEnd)
            outputStream.writeBytes("Content-Disposition: form-data; name=\"userhash\"$lineEnd$lineEnd")
            outputStream.write(userhash.toByteArray(Charsets.UTF_8))
            outputStream.writeBytes(lineEnd)
        }

        // fileToUpload field
        val fileName = getFileName(context, uri) ?: "video_${System.currentTimeMillis()}.mp4"
        val mimeType = getMimeType(context, uri)

        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"$fileName\"$lineEnd")
        outputStream.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
        }
        outputStream.writeBytes(lineEnd)

        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
        outputStream.flush()
        outputStream.close()

        val responseCode = conn.responseCode
        val responseBody = if (responseCode in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } else {
            conn.errorStream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: "HTTP $responseCode"
        }
        conn.disconnect()

        Log.d(DEBUG_TAG, "HTTP $responseCode $responseBody")

        if (responseCode == 401 || responseCode == 403) {
            throw HostAuthException("Catbox auth failed (HTTP $responseCode): $responseBody")
        }

        val cleanUrl = responseBody.trim()
        if (responseCode in 200..299 && cleanUrl.startsWith("http")) {
            return cleanUrl
        } else {
            throw Exception("Catbox upload failed (HTTP $responseCode): $cleanUrl")
        }
    }

    /**
     * Creates an upload_requests row in Supabase via REST API.
     */
    private fun createUploadRequestRecord(
        host: String,
        mediaType: String,
        fileCount: Int,
        title: String?,
        userId: String?
    ): String {
        return try {
            val endpoint = "$SUPABASE_URL/rest/v1/upload_requests"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                setRequestProperty("Prefer", "return=representation")
            }

            val json = JSONObject().apply {
                put("status", "uploading")
                put("host", host)
                put("media_type", mediaType)
                put("file_count", fileCount)
                if (!title.isNullOrBlank()) put("title", title)
                if (!userId.isNullOrBlank()) put("user_id", userId)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: "HTTP $code"
            }
            conn.disconnect()

            Log.d(DEBUG_TAG, "HTTP $code $responseBody")

            if (code in 200..299) {
                val arr = JSONArray(responseBody)
                if (arr.length() > 0) {
                    arr.getJSONObject(0).optString("id", "")
                } else ""
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create upload_requests record: ${e.message}")
            ""
        }
    }

    /**
     * Updates an upload_requests row in Supabase via REST API.
     */
    private fun updateUploadRequestRecord(
        id: String,
        status: String,
        imageUrls: List<String>?,
        error: String?
    ) {
        if (id.isBlank()) return
        try {
            val endpoint = "$SUPABASE_URL/rest/v1/upload_requests?id=eq.$id"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            }

            val json = JSONObject().apply {
                put("status", status)
                if (imageUrls != null) {
                    val arr = JSONArray()
                    for (u in imageUrls) arr.put(u)
                    put("image_urls", arr)
                }
                if (!error.isNullOrBlank()) {
                    put("error", error)
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: "HTTP $code"
            }
            conn.disconnect()

            Log.d(DEBUG_TAG, "HTTP $code $responseBody")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update upload_requests record: ${e.message}")
        }
    }

    /**
     * Calls post-to-buffer Edge Function. Does NOT read buffer_api_key in the app.
     */
    private fun callPostToBufferFunction(
        channelId: String,
        title: String,
        description: String,
        hashtags: String,
        mediaUrls: List<String>,
        mediaType: String,
        mode: String,
        uploadRequestId: String?
    ): JSONObject {
        val endpoint = "$BASE_FUNCTIONS_URL/post-to-buffer"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 25000
            readTimeout = 30000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        }

        val json = JSONObject().apply {
            put("channel_id", channelId)
            put("title", title)
            put("description", description)
            put("hashtags", hashtags)
            val urlsArr = JSONArray()
            for (u in mediaUrls) urlsArr.put(u)
            put("media_urls", urlsArr)
            put("media_type", mediaType)
            put("mode", mode)
            if (!uploadRequestId.isNullOrBlank()) {
                put("upload_request_id", uploadRequestId)
            }
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }

        val code = conn.responseCode
        val responseBody = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } else {
            conn.errorStream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: "HTTP $code"
        }
        conn.disconnect()

        Log.d(DEBUG_TAG, "HTTP $code $responseBody")

        return if (code in 200..299) {
            JSONObject(responseBody)
        } else {
            throw Exception("Buffer post failed ($code): $responseBody")
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getLong(sizeIndex)
                }
            }
        }
        return 0L
    }

    /**
     * Determines MIME type, falling back to image/jpeg for images and video/mp4 for videos.
     * Never returns application/octet-stream.
     */
    fun getMimeType(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)?.lowercase()?.trim()
        if (!type.isNullOrBlank() && type != "application/octet-stream") {
            return type
        }

        val name = getFileName(context, uri)?.lowercase() ?: uri.toString().lowercase()
        val ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name).lowercase().ifBlank {
            name.substringAfterLast('.', "")
        }

        if (ext in listOf("mp4", "mov", "webm", "mkv", "m4v", "avi", "ts")) {
            return "video/mp4"
        }
        if (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "avif")) {
            return "image/jpeg"
        }

        if (name.contains("video") || ext.contains("mp4") || ext.contains("mov") || ext.contains("webm")) {
            return "video/mp4"
        }

        return "image/jpeg"
    }
}
