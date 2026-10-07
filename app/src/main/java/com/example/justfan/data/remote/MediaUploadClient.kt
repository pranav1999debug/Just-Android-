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
import java.io.*
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
    private val BASE_FUNCTIONS_URL = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1"
    private val SUPABASE_KEY = BuildConfig.SUPABASE_KEY

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

            // Empty body or post
            OutputStreamWriter(conn.outputStream).use { it.write("{}") }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
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
     * Generates a description and hashtags given a title using Groq via the generate-caption Edge Function.
     */
    suspend fun generateCaption(title: String): Result<GeneratedCaption> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_FUNCTIONS_URL/generate-caption")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                connectTimeout = 25000
                readTimeout = 30000
                doOutput = true
            }

            val payload = JSONObject().apply {
                put("title", title)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                conn.disconnect()
                return@withContext Result.failure(Exception("Caption generation failed ($code): $err"))
            }
            conn.disconnect()

            val json = JSONObject(responseText)
            val desc = json.optString("description", "")
            val tagsArray = json.optJSONArray("hashtags") ?: JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.getString(i))
            }

            Result.success(GeneratedCaption(description = desc, hashtags = tags))
        } catch (e: Exception) {
            Log.e(TAG, "generateCaption error", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads media files with automatic host routing via upload-media Edge Function.
     * Optionally posts to Buffer if social posting is requested.
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
        try {
            if (uris.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No media selected for upload"))
            }

            val boundary = "==JustFanUploadBoundary_${System.currentTimeMillis()}=="
            val url = URL("$BASE_FUNCTIONS_URL/upload-media")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                connectTimeout = 60000
                readTimeout = 120000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("apikey", SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                setRequestProperty("Accept", "application/json")
            }

            val lineEnd = "\r\n"
            val twoHyphens = "--"
            val outputStream = DataOutputStream(conn.outputStream)

            // Add form fields
            fun addFormField(name: String, value: String) {
                outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                outputStream.writeBytes("Content-Disposition: form-data; name=\"$name\"$lineEnd$lineEnd")
                outputStream.write(value.toByteArray(Charsets.UTF_8))
                outputStream.writeBytes(lineEnd)
            }

            if (!title.isNullOrBlank()) addFormField("title", title)
            if (!description.isNullOrBlank()) addFormField("description", description)
            if (!hashtags.isNullOrBlank()) addFormField("hashtags", hashtags)
            if (!channelId.isNullOrBlank()) addFormField("channel_id", channelId)
            addFormField("mode", mode)
            addFormField("share", if (shareToSocial) "true" else "false")
            if (!userId.isNullOrBlank()) addFormField("user_id", userId)

            // Add file parts
            for (uri in uris) {
                val fileName = getFileName(context, uri) ?: "media_${System.currentTimeMillis()}"
                val mimeType = getMimeType(context, uri) ?: "application/octet-stream"

                outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                outputStream.writeBytes("Content-Disposition: form-data; name=\"files[]\"; filename=\"$fileName\"$lineEnd")
                outputStream.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                outputStream.writeBytes(lineEnd)
            }

            // End boundary
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            outputStream.flush()
            outputStream.close()

            val responseCode = conn.responseCode
            val responseText = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            }
            conn.disconnect()

            Log.d(TAG, "uploadMedia response ($responseCode): $responseText")

            if (responseCode in 200..299) {
                val json = JSONObject(responseText)
                val resultsArray = json.optJSONArray("results") ?: JSONArray()
                val groups = mutableListOf<MediaGroupResult>()

                for (i in 0 until resultsArray.length()) {
                    val r = resultsArray.getJSONObject(i)
                    val urlsArray = r.optJSONArray("urls") ?: JSONArray()
                    val urlsList = mutableListOf<String>()
                    for (j in 0 until urlsArray.length()) {
                        urlsList.add(urlsArray.getString(j))
                    }

                    groups.add(
                        MediaGroupResult(
                            uploadRequestId = r.optString("upload_request_id", ""),
                            host = r.optString("host", ""),
                            mediaType = r.optString("media_type", "image"),
                            fileCount = r.optInt("file_count", urlsList.size),
                            urls = urlsList,
                            status = r.optString("status", "done"),
                            bufferPostId = if (r.has("buffer_post_id") && !r.isNull("buffer_post_id")) r.optString("buffer_post_id") else null,
                            error = if (r.has("error") && !r.isNull("error")) r.optString("error") else null
                        )
                    )
                }

                Result.success(
                    MediaUploadResponse(
                        success = json.optBoolean("success", true),
                        groups = groups
                    )
                )
            } else {
                Result.failure(Exception("Upload failed ($responseCode): $responseText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadMedia error", e)
            Result.failure(e)
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

    private fun getMimeType(context: Context, uri: Uri): String? {
        val type = context.contentResolver.getType(uri)
        if (!type.isNullOrBlank()) return type

        val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        if (!extension.isNullOrBlank()) {
            return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        }
        return "image/jpeg"
    }
}
