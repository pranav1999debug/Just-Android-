package com.example.justfan.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HostAuthException(message: String) : Exception(message)

/**
 * Direct Imgchest uploader:
 * - POST https://api.imgchest.com/v1/post
 * - Header Authorization: Bearer <imgchest_api_key>
 * - Multipart fields: title, images[] (max 20 per request; for more use POST /v1/post/{id}/add)
 * - Read URLs from data.images[].link
 * - If 401 or 403, clear SecretsStore cache, reload once, and retry once.
 * - Streams file data in 8KB chunks.
 * - Logs "UploadDebug: HTTP <status> <body>" for every call.
 */
object ImgchestUploader {
    private const val TAG = "ImgchestUploader"
    private const val DEBUG_TAG = "UploadDebug"
    private const val BASE_URL = "https://api.imgchest.com/v1"

    /**
     * Uploads images directly to Imgchest in batches of up to 20.
     * Retries once with reloaded secret on 401 or 403.
     */
    suspend fun uploadImagesDirect(
        context: Context,
        uris: List<Uri>,
        title: String = "JUSTFAN Media"
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No images to upload"))
        }

        try {
            var apiKey = SecretsStore.get("imgchest_api_key")
            var attempts = 0
            while (attempts < 2) {
                attempts++
                val uploadAttempt = runCatching {
                    executeBatchUpload(context, uris, title, apiKey)
                }

                if (uploadAttempt.isSuccess) {
                    return@withContext uploadAttempt
                }

                val ex = uploadAttempt.exceptionOrNull()
                if (ex is HostAuthException && attempts < 2) {
                    Log.w(TAG, "Imgchest returned 401/403, clearing secret cache and retrying...")
                    SecretsStore.clearCache()
                    apiKey = SecretsStore.get("imgchest_api_key", forceReload = true)
                    continue
                }
                return@withContext uploadAttempt
            }
            Result.failure(Exception("Imgchest upload failed after auth retry"))
        } catch (e: Exception) {
            Log.e(TAG, "uploadImagesDirect failed", e)
            Result.failure(e)
        }
    }

    /**
     * Single image upload directly to Imgchest.
     */
    suspend fun uploadImageDirect(
        context: Context,
        imageUri: Uri,
        title: String = "JUSTFAN Media"
    ): Result<String> {
        val result = uploadImagesDirect(context, listOf(imageUri), title)
        return if (result.isSuccess) {
            val url = result.getOrNull()?.firstOrNull()
            if (!url.isNullOrBlank()) {
                Result.success(url)
            } else {
                Result.failure(Exception("No URL returned from Imgchest"))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Imgchest upload failed"))
        }
    }

    /**
     * Unified wrapper so any caller of ImgchestUploader.uploadImage
     * routes through the unified MediaUploadClient path.
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        title: String = "JUSTFAN Media"
    ): Result<String> {
        return MediaUploadClient.uploadSingleMedia(context, imageUri, title)
    }

    private fun executeBatchUpload(
        context: Context,
        uris: List<Uri>,
        title: String,
        apiKey: String
    ): List<String> {
        val chunks = uris.chunked(20)
        val firstChunk = chunks.first()
        val allUrls = mutableListOf<String>()

        // 1. First batch: POST /v1/post
        val (postId, firstUrls) = postFirstBatch(context, firstChunk, title, apiKey)
        allUrls.addAll(firstUrls)

        // 2. Remaining batches: POST /v1/post/{id}/add
        for (i in 1 until chunks.size) {
            val chunk = chunks[i]
            val additionalUrls = postAdditionalBatch(context, chunk, postId, apiKey)
            allUrls.addAll(additionalUrls)
        }

        return allUrls
    }

    private fun postFirstBatch(
        context: Context,
        uris: List<Uri>,
        title: String,
        apiKey: String
    ): Pair<String, List<String>> {
        val boundary = "==ImgchestBoundary_${System.currentTimeMillis()}=="
        val url = URL("$BASE_URL/post")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 60000
            readTimeout = 120000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }

        val lineEnd = "\r\n"
        val twoHyphens = "--"
        val outputStream = DataOutputStream(conn.outputStream)

        // title field
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"title\"$lineEnd$lineEnd")
        outputStream.write(title.toByteArray(Charsets.UTF_8))
        outputStream.writeBytes(lineEnd)

        // images[] fields
        for (uri in uris) {
            val fileName = getFileName(context, uri) ?: "image_${System.currentTimeMillis()}.jpg"
            val mime = getMimeType(context, uri)

            outputStream.writeBytes(twoHyphens + boundary + lineEnd)
            outputStream.writeBytes("Content-Disposition: form-data; name=\"images[]\"; filename=\"$fileName\"$lineEnd")
            outputStream.writeBytes("Content-Type: $mime$lineEnd$lineEnd")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            outputStream.writeBytes(lineEnd)
        }

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
            throw HostAuthException("Imgchest auth error (HTTP $responseCode): $responseBody")
        }

        if (responseCode !in 200..299) {
            val errMessage = parseErrorMessage(responseBody)
            throw Exception("Imgchest upload failed (HTTP $responseCode): $errMessage")
        }

        val json = JSONObject(responseBody)
        val data = json.getJSONObject("data")
        val postId = data.optString("id", "")
        val images = data.optJSONArray("images") ?: JSONArray()
        val links = mutableListOf<String>()
        for (i in 0 until images.length()) {
            val item = images.getJSONObject(i)
            val link = item.optString("link", "")
            if (link.isNotBlank()) links.add(link)
        }

        return Pair(postId, links)
    }

    private fun postAdditionalBatch(
        context: Context,
        uris: List<Uri>,
        postId: String,
        apiKey: String
    ): List<String> {
        val boundary = "==ImgchestBoundary_${System.currentTimeMillis()}=="
        val url = URL("$BASE_URL/post/$postId/add")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 60000
            readTimeout = 120000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }

        val lineEnd = "\r\n"
        val twoHyphens = "--"
        val outputStream = DataOutputStream(conn.outputStream)

        for (uri in uris) {
            val fileName = getFileName(context, uri) ?: "image_${System.currentTimeMillis()}.jpg"
            val mime = getMimeType(context, uri)

            outputStream.writeBytes(twoHyphens + boundary + lineEnd)
            outputStream.writeBytes("Content-Disposition: form-data; name=\"images[]\"; filename=\"$fileName\"$lineEnd")
            outputStream.writeBytes("Content-Type: $mime$lineEnd$lineEnd")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            outputStream.writeBytes(lineEnd)
        }

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
            throw HostAuthException("Imgchest auth error (HTTP $responseCode): $responseBody")
        }

        if (responseCode !in 200..299) {
            val errMessage = parseErrorMessage(responseBody)
            throw Exception("Imgchest add images failed (HTTP $responseCode): $errMessage")
        }

        val json = JSONObject(responseBody)
        val data = json.optJSONObject("data") ?: json
        val images = data.optJSONArray("images") ?: JSONArray()
        val links = mutableListOf<String>()
        for (i in 0 until images.length()) {
            val item = images.getJSONObject(i)
            val link = item.optString("link", "")
            if (link.isNotBlank()) links.add(link)
        }

        return links
    }

    private fun parseErrorMessage(body: String): String {
        return try {
            val j = JSONObject(body)
            j.optString("error", "").ifBlank {
                j.optString("message", body)
            }
        } catch (_: Exception) {
            body
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

    private fun getMimeType(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)?.lowercase()?.trim()
        if (!type.isNullOrBlank() && type != "application/octet-stream") {
            return type
        }

        val name = getFileName(context, uri)?.lowercase() ?: uri.toString().lowercase()
        val ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name).lowercase().ifBlank {
            name.substringAfterLast('.', "")
        }

        if (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "avif")) {
            return "image/jpeg"
        }
        if (ext in listOf("mp4", "mov", "webm", "mkv", "m4v", "avi", "ts")) {
            return "video/mp4"
        }
        return "image/jpeg"
    }
}
