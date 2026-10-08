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

object ImgchestUploader {

    private const val TAG = "ImgchestUploader"
    private const val PREFS_NAME = "justfan_imgchest_prefs"
    private const val KEY_IMGCHEST_TOKEN = "imgchest_api_token"
    const val DEFAULT_API_TOKEN = ""

    fun getApiToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_IMGCHEST_TOKEN, "")?.trim().orEmpty()
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
                mimeType.contains("mp4") -> "mp4"
                mimeType.contains("video") -> "mp4"
                else -> "jpg"
            }
            val fileName = "upload_${System.currentTimeMillis()}.$extension"

            val token = if (!userToken.isNullOrBlank()) userToken.trim() else getApiToken(context)

            // 1. If valid Imgchest API token is available, attempt Imgchest first
            if (token.isNotBlank()) {
                val imgchestResult = uploadToImgchest(token, fileName, mimeType, imageBytes, title)
                if (imgchestResult.isSuccess) {
                    return@withContext imgchestResult
                }
            }

            // 2. Primary free anonymous upload: Litterbox (Catbox network)
            val litterboxResult = uploadToLitterbox(fileName, mimeType, imageBytes)
            if (litterboxResult.isSuccess) {
                Log.d(TAG, "Uploaded successfully via Litterbox: ${litterboxResult.getOrNull()}")
                return@withContext litterboxResult
            }

            // 3. Secondary free upload: Uguu
            val uguuResult = uploadToUguu(fileName, mimeType, imageBytes)
            if (uguuResult.isSuccess) {
                Log.d(TAG, "Uploaded successfully via Uguu: ${uguuResult.getOrNull()}")
                return@withContext uguuResult
            }

            // 4. Tertiary fallback: Tmpfiles
            val tmpResult = uploadToTmpfiles(fileName, mimeType, imageBytes)
            if (tmpResult.isSuccess) {
                return@withContext tmpResult
            }

            Result.failure(Exception("Upload failed on all image hosts. Please check your network connection."))
        } catch (e: Exception) {
            Log.e(TAG, "uploadImage error", e)
            Result.failure(e)
        }
    }

    private fun uploadToImgchest(
        token: String,
        fileName: String,
        mimeType: String,
        imageBytes: ByteArray,
        title: String?
    ): Result<String> {
        return try {
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
            conn.connectTimeout = 20000
            conn.readTimeout = 25000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "JUSTFAN-Android/1.0")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")

            val outputStream = DataOutputStream(conn.outputStream)
            if (!title.isNullOrBlank()) {
                outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                outputStream.writeBytes("Content-Disposition: form-data; name=\"title\"$lineEnd$lineEnd")
                outputStream.write(title.toByteArray(Charsets.UTF_8))
                outputStream.writeBytes(lineEnd)
            }
            outputStream.writeBytes(twoHyphens + boundary + lineEnd)
            outputStream.writeBytes("Content-Disposition: form-data; name=\"images[]\"; filename=\"$fileName\"$lineEnd")
            outputStream.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")
            outputStream.write(imageBytes)
            outputStream.writeBytes(lineEnd)
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            outputStream.flush()
            outputStream.close()

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(responseText)
                if (json.has("data")) {
                    val data = json.getJSONObject("data")
                    if (data.has("images") && !data.isNull("images")) {
                        val imagesArr = data.getJSONArray("images")
                        if (imagesArr.length() > 0) {
                            val firstImg = imagesArr.getJSONObject(0)
                            val link = firstImg.optString("link", "")
                            if (link.isNotBlank()) return Result.success(link)
                        }
                    }
                    val postUrl = data.optString("url", "")
                    if (postUrl.isNotBlank()) return Result.success(postUrl)
                }
            }
            conn.disconnect()
            Result.failure(Exception("Imgchest HTTP $responseCode"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uploadToLitterbox(fileName: String, mimeType: String, imageBytes: ByteArray): Result<String> {
        return try {
            val boundary = "==CatboxLitterBoundary${System.currentTimeMillis()}=="
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val url = URL("https://litterbox.catbox.moe/resources/internals/api.php")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doInput = true
            conn.doOutput = true
            conn.connectTimeout = 25000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

            val dos = DataOutputStream(conn.outputStream)
            // reqtype
            dos.writeBytes(twoHyphens + boundary + lineEnd)
            dos.writeBytes("Content-Disposition: form-data; name=\"reqtype\"$lineEnd$lineEnd")
            dos.writeBytes("fileupload$lineEnd")

            // time (72h retention)
            dos.writeBytes(twoHyphens + boundary + lineEnd)
            dos.writeBytes("Content-Disposition: form-data; name=\"time\"$lineEnd$lineEnd")
            dos.writeBytes("72h$lineEnd")

            // fileToUpload
            dos.writeBytes(twoHyphens + boundary + lineEnd)
            dos.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"$fileName\"$lineEnd")
            dos.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")
            dos.write(imageBytes)
            dos.writeBytes(lineEnd)

            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            dos.flush()
            dos.close()

            val code = conn.responseCode
            if (code in 200..299) {
                val link = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText().trim() }
                conn.disconnect()
                if (link.startsWith("http://") || link.startsWith("https://")) {
                    return Result.success(link)
                }
            }
            conn.disconnect()
            Result.failure(Exception("Litterbox HTTP $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uploadToUguu(fileName: String, mimeType: String, imageBytes: ByteArray): Result<String> {
        return try {
            val boundary = "==UguuBoundary${System.currentTimeMillis()}=="
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val url = URL("https://uguu.se/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doInput = true
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 25000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            val dos = DataOutputStream(conn.outputStream)
            dos.writeBytes(twoHyphens + boundary + lineEnd)
            dos.writeBytes("Content-Disposition: form-data; name=\"files[]\"; filename=\"$fileName\"$lineEnd")
            dos.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")
            dos.write(imageBytes)
            dos.writeBytes(lineEnd)
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            dos.flush()
            dos.close()

            val code = conn.responseCode
            if (code in 200..299) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(resp)
                if (json.optBoolean("success", false) && json.has("files")) {
                    val arr = json.getJSONArray("files")
                    if (arr.length() > 0) {
                        val fileObj = arr.getJSONObject(0)
                        val urlStr = fileObj.optString("url", "")
                        if (urlStr.isNotBlank()) return Result.success(urlStr)
                    }
                }
            }
            conn.disconnect()
            Result.failure(Exception("Uguu HTTP $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uploadToTmpfiles(fileName: String, mimeType: String, imageBytes: ByteArray): Result<String> {
        return try {
            val boundary = "----TmpfilesBoundary${System.currentTimeMillis()}"
            val lineEnd = "\r\n"
            val twoHyphens = "--"
            val url = URL("https://tmpfiles.org/api/v1/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doInput = true
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 25000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            val dos = DataOutputStream(conn.outputStream)
            dos.writeBytes(twoHyphens + boundary + lineEnd)
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd")
            dos.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")
            dos.write(imageBytes)
            dos.writeBytes(lineEnd)
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            dos.flush()
            dos.close()

            val code = conn.responseCode
            if (code in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                conn.disconnect()
                val resObj = JSONObject(text)
                val direct = resObj.getJSONObject("data").getString("url").replace("tmpfiles.org/", "tmpfiles.org/dl/")
                return Result.success(direct)
            }
            conn.disconnect()
            Result.failure(Exception("Tmpfiles HTTP $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
