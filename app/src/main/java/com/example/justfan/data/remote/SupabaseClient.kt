package com.example.justfan.data.remote

import android.util.Log
import com.example.justfan.BuildConfig
import com.example.justfan.data.model.CollectionEntity
import com.example.justfan.data.model.CommentEntity
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

object SupabaseClient {

    private const val TAG = "SupabaseClient"

    private val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    private val apiKey: String
        get() = BuildConfig.SUPABASE_KEY

    private fun openConnection(endpoint: String, method: String): HttpURLConnection {
        val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl/rest/v1/$endpoint"
        val connection = URL(fullUrl).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("apikey", apiKey)
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Prefer", "return=representation")
        return connection
    }

    suspend fun fetchPosts(limit: Int = 1000): Result<List<PostEntity>> = withContext(Dispatchers.IO) {
        try {
            val allPosts = mutableListOf<PostEntity>()
            var offset = 0
            val batchSize = 1000
            var hasMore = true

            while (hasMore) {
                val endpoint = "posts?select=*&order=created_at.desc&limit=$batchSize&offset=$offset"
                val conn = openConnection(endpoint, "GET")
                val code = conn.responseCode

                if (code in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()
                    conn.disconnect()

                    val jsonArray = JSONArray(response)
                    if (jsonArray.length() == 0) {
                        hasMore = false
                        break
                    }

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optString("id", UUID.randomUUID().toString())
                        val title = obj.optString("title", "Untitled Gallery")
                        val description = obj.optString("description", "")
                        val imageUrl = obj.optString("image_url", "")
                        val linkUrl = obj.optString("link_url", "")
                        val directLinkUrl = if (obj.has("direct_link_url") && !obj.isNull("direct_link_url")) obj.optString("direct_link_url") else null
                        val premiumLinkUrl = if (obj.has("premium_link_url") && !obj.isNull("premium_link_url")) obj.optString("premium_link_url") else null

                        val contentImages = mutableListOf<String>()
                        if (obj.has("content_images") && !obj.isNull("content_images")) {
                            val arr = obj.optJSONArray("content_images")
                            if (arr != null) {
                                for (j in 0 until arr.length()) {
                                    contentImages.add(arr.optString(j))
                                }
                            }
                        }

                        val tags = mutableListOf<String>()
                        if (obj.has("tags") && !obj.isNull("tags")) {
                            val arr = obj.optJSONArray("tags")
                            if (arr != null) {
                                for (j in 0 until arr.length()) {
                                    tags.add(arr.optString(j))
                                }
                            }
                        }

                        val isFree = obj.optBoolean("is_free", false)
                        val isNsfw = obj.optBoolean("is_nsfw", false)
                        val section = obj.optString("section", "home")
                        val createdAtStr = obj.optString("created_at", "")
                        val createdAtMillis = parseIsoToMillis(createdAtStr)

                        allPosts.add(
                            PostEntity(
                                id = id,
                                title = title,
                                description = description,
                                imageUrl = imageUrl,
                                contentImages = contentImages,
                                linkUrl = linkUrl,
                                premiumLinkUrl = premiumLinkUrl,
                                directLinkUrl = directLinkUrl,
                                tags = tags,
                                author = "Creator",
                                isFree = isFree,
                                isNsfw = isNsfw,
                                section = section,
                                clicksCount = 0,
                                likesCount = 0,
                                createdAt = createdAtMillis
                            )
                        )
                    }

                    if (jsonArray.length() < batchSize) {
                        hasMore = false
                    } else {
                        offset += jsonArray.length()
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    conn.disconnect()
                    if (allPosts.isNotEmpty()) {
                        // Return what we fetched so far rather than failing completely
                        break
                    }
                    return@withContext Result.failure(Exception("Supabase fetchPosts failed: $err"))
                }
            }

            Result.success(allPosts)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPosts error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchRequests(): Result<List<RequestEntity>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "RequestfromApp?select=*&order=created_at.desc&limit=1000"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val list = mutableListOf<RequestEntity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val rawName = if (obj.has("name") && !obj.isNull("name")) obj.optString("name", "").trim() else ""
                    val rawMsg = if (obj.has("message") && !obj.isNull("message")) obj.optString("message", "").trim() else ""
                    val resolvedName = when {
                        rawName.isNotBlank() && rawName != "null" -> rawName
                        rawMsg.isNotBlank() && rawMsg != "null" -> rawMsg.take(50)
                        else -> "Community Request #${id.take(8)}"
                    }

                    val email = if (obj.has("email") && !obj.isNull("email")) obj.optString("email", "").trim() else ""
                    val telegram = if (obj.has("telegram_username") && !obj.isNull("telegram_username")) obj.optString("telegram_username", "").trim() else null
                    val imageUrl = if (obj.has("image_url") && !obj.isNull("image_url")) obj.optString("image_url").trim().ifBlank { null } else null
                    val status = obj.optString("status", "pending")
                    val createdAtStr = obj.optString("created_at", "")

                    list.add(
                        RequestEntity(
                            id = id,
                            name = resolvedName,
                            email = email,
                            telegramUsername = telegram,
                            message = rawMsg,
                            imageUrl = imageUrl,
                            status = status,
                            downloadLink = null,
                            createdAt = parseIsoToMillis(createdAtStr)
                        )
                    )
                }
                Result.success(list)
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                conn.disconnect()
                Result.failure(Exception("Supabase fetchRequests failed: $err"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRequests error", e)
            Result.failure(e)
        }
    }

    data class AuthSession(
        val accessToken: String,
        val refreshToken: String? = null,
        val userId: String,
        val email: String
    )

    suspend fun signInWithEmailPassword(email: String, password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$baseUrl/auth/v1/token?grant_type=password"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: "HTTP $code"
            }
            conn.disconnect()

            if (code in 200..299) {
                val json = JSONObject(responseBody)
                val token = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", null)
                val userObj = json.getJSONObject("user")
                val uid = userObj.getString("id")
                val uEmail = userObj.optString("email", email)
                Result.success(AuthSession(accessToken = token, refreshToken = refreshToken, userId = uid, email = uEmail))
            } else {
                val errMessage = try {
                    val j = JSONObject(responseBody)
                    j.optString("msg", j.optString("error_description", responseBody))
                } catch (_: Exception) {
                    responseBody
                }
                Result.failure(Exception(errMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshSession(refreshToken: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$baseUrl/auth/v1/token?grant_type=refresh_token"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("refresh_token", refreshToken)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: "HTTP $code"
            }
            conn.disconnect()

            if (code in 200..299) {
                val json = JSONObject(responseBody)
                val token = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                val userObj = json.optJSONObject("user")
                val uid = userObj?.optString("id") ?: ""
                val uEmail = userObj?.optString("email") ?: ""
                Result.success(AuthSession(accessToken = token, refreshToken = newRefreshToken, userId = uid, email = uEmail))
            } else {
                Result.failure(Exception("Refresh failed ($code): $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRequestQuota(userId: String, deviceId: String, accessToken: String?): Result<com.example.justfan.data.model.RequestQuota> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$baseUrl/rest/v1/rpc/get_request_quota"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                val authHeader = if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey"
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("p_user_id", userId)
                put("p_device_id", deviceId)
            }

            val writer = OutputStreamWriter(conn.outputStream, Charsets.UTF_8)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: "HTTP $code"
            }
            conn.disconnect()

            Log.d("QuotaDebug", "HTTP $code $responseBody")

            if (code in 200..299) {
                val json = JSONObject(responseBody)
                val quota = com.example.justfan.data.model.RequestQuota(
                    unlimited = json.optBoolean("unlimited", false),
                    limit = json.optInt("limit", 3),
                    used = json.optInt("used", 0),
                    remaining = json.optInt("remaining", 3),
                    resetsAt = if (json.has("resets_at") && !json.isNull("resets_at")) json.optString("resets_at") else null
                )
                Result.success(quota)
            } else {
                Result.failure(Exception("Failed to fetch quota ($code): $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRequestQuota error", e)
            Result.failure(e)
        }
    }

    suspend fun submitRequest(
        name: String,
        email: String,
        telegram: String? = null,
        message: String,
        imageUrl: String?,
        userId: String,
        deviceId: String,
        accessToken: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$baseUrl/rest/v1/RequestfromApp"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Prefer", "return=representation")
            }

            val payload = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("name", name)
                put("email", email)
                if (!telegram.isNullOrBlank()) {
                    put("telegram_username", telegram)
                }
                put("message", message)
                if (!imageUrl.isNullOrBlank()) {
                    put("image_url", imageUrl)
                }
                put("status", "pending")
                put("user_id", userId)
                put("device_id", deviceId)
            }

            val writer = OutputStreamWriter(conn.outputStream, Charsets.UTF_8)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: "HTTP $code"
            }
            conn.disconnect()

            Log.d("QuotaDebug", "HTTP $code $responseBody")

            if (code in 200..299) {
                Result.success(true)
            } else {
                val errMessage = when {
                    responseBody.contains("DEVICE_REQUIRED", ignoreCase = true) -> "DEVICE_REQUIRED"
                    responseBody.contains("SIGN_IN_REQUIRED", ignoreCase = true) || code == 401 -> "SIGN_IN_REQUIRED"
                    responseBody.contains("LIMIT_REACHED", ignoreCase = true) -> "LIMIT_REACHED"
                    responseBody.contains("USER_MISMATCH", ignoreCase = true) -> "USER_MISMATCH"
                    else -> {
                        try {
                            val j = JSONObject(responseBody)
                            j.optString("message", responseBody)
                        } catch (_: Exception) {
                            responseBody
                        }
                    }
                }
                Result.failure(Exception(errMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "submitRequest error", e)
            Result.failure(e)
        }
    }

    suspend fun updateRequestStatusInSupabase(id: String, status: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "RequestfromApp?id=eq.$id"
            val conn = openConnection(endpoint, "PATCH")
            conn.doOutput = true
            val payload = JSONObject().apply {
                put("status", status)
            }
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "updateRequestStatusInSupabase error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRequestFromSupabase(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "RequestfromApp?id=eq.$id"
            val conn = openConnection(endpoint, "DELETE")
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "deleteRequestFromSupabase error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchCollections(): Result<List<CollectionEntity>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "collections?select=*&order=created_at.desc"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val list = mutableListOf<CollectionEntity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        CollectionEntity(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "Collection"),
                            createdAt = parseIsoToMillis(obj.optString("created_at", ""))
                        )
                    )
                }
                Result.success(list)
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCollection(name: String): Result<CollectionEntity> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "collections"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("name", name)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONArray(response)
                val obj = arr.getJSONObject(0)
                Result.success(
                    CollectionEntity(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", name),
                        createdAt = parseIsoToMillis(obj.optString("created_at", ""))
                    )
                )
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProfile(email: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "profiles?username=eq.$email&limit=1"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val arr = JSONArray(response)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val profile = UserProfile(
                        id = obj.optString("id", "user_1"),
                        username = obj.optString("username", email),
                        email = email,
                        tier = if (email.equals("reytherapper12@gmail.com", ignoreCase = true)) "Legendary" else "Free",
                        isAdmin = email.equals("reytherapper12@gmail.com", ignoreCase = true),
                        isSignedIn = true
                    )
                    Result.success(profile)
                } else {
                    Result.success(null)
                }
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProfiles(limit: Int = 500): Result<List<com.example.justfan.data.model.UserEntity>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "profiles?select=*&order=created_at.desc&limit=$limit"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val users = mutableListOf<com.example.justfan.data.model.UserEntity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val rawUsername = if (obj.has("username") && !obj.isNull("username")) obj.optString("username") else null
                    val email = rawUsername ?: "${id.take(8)}@user.justfan"
                    val username = if (email.contains("@")) email.substringBefore("@") else email
                    val isAdmin = email.equals("reytherapper12@gmail.com", ignoreCase = true) || id == "fe335770-80a9-4125-9c15-d47f385579fb"
                    val createdAtStr = obj.optString("created_at", "")

                    users.add(
                        com.example.justfan.data.model.UserEntity(
                            id = id,
                            username = username,
                            email = email,
                            tier = if (isAdmin) "Legendary" else "Free",
                            requestsCount = 0,
                            isAdmin = isAdmin,
                            status = "Active",
                            joinedAt = parseIsoToMillis(createdAtStr)
                        )
                    )
                }
                Result.success(users)
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchProfiles error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchPostComments(): Result<List<CommentEntity>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "post_comments?select=*&order=created_at.asc"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val comments = mutableListOf<CommentEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val postId = obj.optString("post_id", "")
                    val userId = obj.optString("user_id", "")
                    val content = obj.optString("content", "")
                    val createdAtStr = obj.optString("created_at", "")
                    val createdAt = parseIsoToMillis(createdAtStr)

                    if (postId.isNotBlank() && content.isNotBlank()) {
                        comments.add(
                            CommentEntity(
                                id = id,
                                postId = postId,
                                authorName = userId, // Repo will resolve to profile username
                                content = content,
                                createdAt = createdAt
                            )
                        )
                    }
                }
                Result.success(comments)
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPostComments error", e)
            Result.failure(e)
        }
    }

    suspend fun insertPostComment(postId: String, userId: String?, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "post_comments"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("post_id", postId)
                if (!userId.isNullOrBlank() && userId.length > 10) {
                    put("user_id", userId)
                } else {
                    put("user_id", "fe335770-80a9-4125-9c15-d47f385579fb")
                }
                put("content", content)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPostClicksSummary(): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        try {
            // First attempt: fetch from aggregate view/table post_click_counts
            val endpoint = "post_click_counts?select=post_id,count&limit=1500"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val summary = mutableMapOf<String, Int>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val postId = obj.optString("post_id", "")
                    val count = obj.optInt("count", 0)
                    if (postId.isNotBlank() && count > 0) {
                        summary[postId] = count
                    }
                }
                Log.d(TAG, "fetchPostClicksSummary: fetched ${summary.size} posts from post_click_counts")
                Result.success(summary)
            } else {
                conn.disconnect()
                // Fallback: fetch from post_clicks table if post_click_counts is unavailable
                val fallbackConn = openConnection("post_clicks?select=post_id&limit=10000", "GET")
                val fallbackCode = fallbackConn.responseCode
                if (fallbackCode in 200..299) {
                    val response = fallbackConn.inputStream.bufferedReader().readText()
                    fallbackConn.disconnect()
                    val jsonArray = JSONArray(response)
                    val summary = mutableMapOf<String, Int>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val postId = obj.optString("post_id", "")
                        if (postId.isNotBlank()) {
                            summary[postId] = (summary[postId] ?: 0) + 1
                        }
                    }
                    Result.success(summary)
                } else {
                    val err = fallbackConn.errorStream?.bufferedReader()?.readText() ?: "HTTP $fallbackCode"
                    fallbackConn.disconnect()
                    Result.failure(Exception("fetchPostClicksSummary failed ($fallbackCode): $err"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPostClicksSummary error", e)
            Result.failure(e)
        }
    }

    suspend fun recordPostClick(postId: String, userId: String? = null, clickType: String = "download"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Validate UUID for post_id
            val isUuid = postId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
            if (!isUuid) {
                Log.w(TAG, "recordPostClick skipped: postId is not a UUID: $postId")
                return@withContext Result.failure(IllegalArgumentException("postId is not a UUID"))
            }

            val endpoint = "post_clicks"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("post_id", postId)
                // PostgreSQL post_clicks.user_id requires uuid type; only pass if valid UUID
                if (!userId.isNullOrBlank() && userId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))) {
                    put("user_id", userId)
                }
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream?.bufferedReader()?.readText() ?: ""
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            }
            conn.disconnect()
            Log.d(TAG, "recordPostClick to Supabase ($code): $responseBody")
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "recordPostClick error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchPostSharesSummary(): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "post_shares?select=post_id,count"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val summary = mutableMapOf<String, Int>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val postId = obj.optString("post_id", "")
                    val count = obj.optInt("count", 1)
                    if (postId.isNotBlank()) {
                        summary[postId] = (summary[postId] ?: 0) + (if (count > 0) count else 1)
                    }
                }
                Result.success(summary)
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordPostShare(postId: String, userId: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "post_shares"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("post_id", postId)
                if (!userId.isNullOrBlank() && userId.length > 10) {
                    put("user_id", userId)
                }
                put("count", 1)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFavoritesSummary(): Result<Pair<Map<String, Int>, Set<String>>> = withContext(Dispatchers.IO) {
        try {
            val likesCountMap = mutableMapOf<String, Int>()
            val userFavPostIds = mutableSetOf<String>()

            // 1. Fetch from post_like_counts view/table if available
            try {
                val likeCountsConn = openConnection("post_like_counts?select=post_id,count&limit=1500", "GET")
                val likeCountsCode = likeCountsConn.responseCode
                if (likeCountsCode in 200..299) {
                    val response = likeCountsConn.inputStream.bufferedReader().readText()
                    likeCountsConn.disconnect()
                    val jsonArray = JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val postId = obj.optString("post_id", "")
                        val count = obj.optInt("count", 0)
                        if (postId.isNotBlank() && count > 0) {
                            likesCountMap[postId] = count
                        }
                    }
                } else {
                    likeCountsConn.disconnect()
                }
            } catch (_: Exception) {}

            val endpoint = "favorites?select=post_id,user_id"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val postId = obj.optString("post_id", "")
                    val userId = obj.optString("user_id", "")
                    if (postId.isNotBlank()) {
                        likesCountMap[postId] = maxOf(likesCountMap[postId] ?: 0, (likesCountMap[postId] ?: 0) + 1)
                        if (userId.isNotBlank()) {
                            userFavPostIds.add(postId)
                        }
                    }
                }
                Result.success(Pair(likesCountMap, userFavPostIds))
            } else {
                conn.disconnect()
                Result.success(Pair(likesCountMap, userFavPostIds))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(postId: String, userId: String?, isAdd: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (isAdd) {
                val endpoint = "favorites"
                val conn = openConnection(endpoint, "POST")
                conn.doOutput = true
                val payload = JSONObject().apply {
                    put("post_id", postId)
                    if (!userId.isNullOrBlank() && userId.length > 10) {
                        put("user_id", userId)
                    } else {
                        put("user_id", "fe335770-80a9-4125-9c15-d47f385579fb")
                    }
                }
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(payload.toString())
                writer.flush()
                writer.close()
                val code = conn.responseCode
                conn.disconnect()
                Result.success(code in 200..299)
            } else {
                val targetUser = if (!userId.isNullOrBlank() && userId.length > 10) userId else "fe335770-80a9-4125-9c15-d47f385579fb"
                val endpoint = "favorites?post_id=eq.$postId&user_id=eq.$targetUser"
                val conn = openConnection(endpoint, "DELETE")
                val code = conn.responseCode
                conn.disconnect()
                Result.success(code in 200..299)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertPost(post: PostEntity, accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "posts"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val payload = JSONObject().apply {
                put("id", post.id)
                put("title", post.title)
                put("description", post.description)
                put("image_url", post.imageUrl)
                put("link_url", post.linkUrl)
                if (!post.premiumLinkUrl.isNullOrBlank()) {
                    put("premium_link_url", post.premiumLinkUrl)
                }
                if (!post.directLinkUrl.isNullOrBlank()) {
                    put("direct_link_url", post.directLinkUrl)
                }
                put("content_images", JSONArray(post.contentImages))
                put("tags", JSONArray(post.tags))
                put("is_free", post.isFree)
                put("is_nsfw", post.isNsfw)
                put("section", post.section)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            conn.disconnect()
            if (code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception("POST_CREATE_FAILED ($code): $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "insertPost error", e)
            Result.failure(e)
        }
    }

    suspend fun updatePost(post: PostEntity, accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "posts?id=eq.${post.id}"
            val conn = openConnection(endpoint, "PATCH")
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val payload = JSONObject().apply {
                put("title", post.title)
                put("description", post.description)
                put("image_url", post.imageUrl)
                put("link_url", post.linkUrl)
                if (!post.premiumLinkUrl.isNullOrBlank()) {
                    put("premium_link_url", post.premiumLinkUrl)
                }
                if (!post.directLinkUrl.isNullOrBlank()) {
                    put("direct_link_url", post.directLinkUrl)
                }
                put("content_images", JSONArray(post.contentImages))
                put("tags", JSONArray(post.tags))
                put("is_free", post.isFree)
                put("is_nsfw", post.isNsfw)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            conn.disconnect()
            if (code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception("POST_UPDATE_FAILED ($code): $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updatePost error", e)
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "posts?id=eq.$postId"
            val conn = openConnection(endpoint, "DELETE")
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "deletePost error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCollection(collectionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "collections?id=eq.$collectionId"
            val conn = openConnection(endpoint, "DELETE")
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "deleteCollection error", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserTier(userId: String, tier: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "profiles?id=eq.$userId"
            val conn = openConnection(endpoint, "PATCH")
            conn.doOutput = true
            val payload = JSONObject().apply {
                put("tier", tier)
            }
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserTier error", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserStatus(userId: String, status: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "profiles?id=eq.$userId"
            val conn = openConnection(endpoint, "PATCH")
            conn.doOutput = true
            val payload = JSONObject().apply {
                put("status", status)
            }
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserStatus error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUser(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "profiles?id=eq.$userId"
            val conn = openConnection(endpoint, "DELETE")
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "deleteUser error", e)
            Result.failure(e)
        }
    }

    suspend fun fulfillRequestInSupabase(id: String, downloadLink: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "RequestfromApp?id=eq.$id"
            val conn = openConnection(endpoint, "PATCH")
            conn.doOutput = true
            val payload = JSONObject().apply {
                put("status", "delivered")
                put("download_link", downloadLink)
            }
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "fulfillRequestInSupabase error", e)
            Result.failure(e)
        }
    }

    suspend fun rejectRequestInSupabase(id: String, reason: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "RequestfromApp?id=eq.$id"
            val conn = openConnection(endpoint, "PATCH")
            conn.doOutput = true
            val payload = JSONObject().apply {
                put("status", "rejected")
                put("rejection_reason", reason)
            }
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "rejectRequestInSupabase error", e)
            Result.failure(e)
        }
    }

    private fun parseIsoToMillis(isoString: String): Long {
        if (isoString.isBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
