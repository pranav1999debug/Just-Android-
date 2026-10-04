package com.example.justfan.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ImgchestUploader {

    private const val TAG = "ImgchestUploader"
    private const val PREFS_NAME = "justfan_imgchest_prefs"
    private const val KEY_IMGCHEST_TOKEN = "imgchest_api_token"

    // Default or user-configured Imgchest token
    fun getApiToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_IMGCHEST_TOKEN, null)?.ifBlank { null }
    }

    fun setApiToken(context: Context, token: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IMGCHEST_TOKEN, token?.trim()).apply()
    }

    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        title: String? = null,
        userToken: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Cannot open image from gallery"))

            val imageBytes = inputStream.use { it.readBytes() }
            if (imageBytes.isEmpty()) {
                return@withContext Result.failure(Exception("Selected image is empty"))
            }

            val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
            val extension = when {
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                else -> "jpg"
            }
            val fileName = "upload_${System.currentTimeMillis()}.$extension"

            val token = userToken?.ifBlank { null } ?: getApiToken(context)

            val boundary = "==ImgchestUploadBoundary${System.currentTimeMillis()}=="
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val endpoint = "https://api.imgchest.com/v1/post"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doInput = true
            conn.doOutput = true
            conn.useCaches = false
            conn.connectTimeout = 30000
            conn.readTimeout = 40000
            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "JUSTFAN-Android/1.0")
            conn.setRequestProperty("Accept", "application/json")

            if (!token.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }

            val outputStream = DataOutputStream(conn.outputStream)

            // Part: title (if provided)
            if (!title.isNullOrBlank()) {
                outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                outputStream.writeBytes("Content-Disposition: form-data; name=\"title\"$lineEnd$lineEnd")
                outputStream.write(title.toByteArray(Charsets.UTF_8))
                outputStream.writeBytes(lineEnd)
            }

            // Part: images[]
            outputStream.writeBytes(twoHyphens + boundary + lineEnd)
            outputStream.writeBytes("Content-Disposition: form-data; name=\"images[]\"; filename=\"$fileName\"$lineEnd")
            outputStream.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")
            outputStream.write(imageBytes)
            outputStream.writeBytes(lineEnd)

            // End boundary
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            outputStream.flush()
            outputStream.close()

            val responseCode = conn.responseCode
            val responseText = if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: "HTTP $responseCode"
            }
            conn.disconnect()

            Log.d(TAG, "Imgchest API response ($responseCode): $responseText")

            if (responseCode in 200..299) {
                val json = JSONObject(responseText)
                if (json.has("data")) {
                    val data = json.getJSONObject("data")
                    // If images array is present
                    if (data.has("images") && !data.isNull("images")) {
                        val imagesArr = data.getJSONArray("images")
                        if (imagesArr.length() > 0) {
                            val firstImg = imagesArr.getJSONObject(0)
                            val link = firstImg.optString("link", "")
                            if (link.isNotBlank()) {
                                return@withContext Result.success(link)
                            }
                        }
                    }
                    // Or post url
                    val postUrl = data.optString("url", "")
                    if (postUrl.isNotBlank()) {
                        return@withContext Result.success(postUrl)
                    }
                }
                Result.failure(Exception("Imgchest response missing direct image link: $responseText"))
            } else if (responseCode == 401 || responseCode == 403 || responseText.contains("login", ignoreCase = true)) {
                // If API requires a personal token and one wasn't supplied or was invalid
                Result.failure(
                    Exception(
                        "Imgchest requires an API key. Please configure your Imgchest API token in Request options or settings."
                    )
                )
            } else {
                Result.failure(Exception("Imgchest upload failed ($responseCode): $responseText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadImage error", e)
            Result.failure(e)
        }
    }
}
