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

    suspend fun fetchPosts(limit: Int = 1500, offset: Int = 0): Result<List<PostEntity>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "posts?select=*&order=created_at.desc&limit=$limit&offset=$offset"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val posts = mutableListOf<PostEntity>()

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

                    posts.add(
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

                Result.success(posts)
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                conn.disconnect()
                Result.failure(Exception("Supabase fetchPosts failed: $err"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPosts error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchRequests(): Result<List<RequestEntity>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "requests?select=*&order=created_at.desc"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val list = mutableListOf<RequestEntity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        RequestEntity(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "Creator Request"),
                            email = obj.optString("email", ""),
                            telegramUsername = null,
                            message = obj.optString("message", ""),
                            imageUrl = if (obj.has("image_url") && !obj.isNull("image_url")) obj.optString("image_url") else null,
                            status = obj.optString("status", "pending"),
                            downloadLink = null,
                            createdAt = parseIsoToMillis(obj.optString("created_at", ""))
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

    suspend fun submitRequest(
        name: String,
        email: String,
        message: String,
        imageUrl: String?,
        userId: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "requests"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("message", message)
                if (!imageUrl.isNullOrBlank()) {
                    put("image_url", imageUrl)
                }
                put("status", "pending")
                val uid = if (!userId.isNullOrBlank() && userId.length > 10) userId else "fe335770-80a9-4125-9c15-d47f385579fb"
                put("user_id", uid)
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
            Log.d(TAG, "submitRequest to Supabase ($code): $responseBody")
            if (code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception("Supabase insert request ($code): $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "submitRequest error", e)
            Result.failure(e)
        }
    }

    suspend fun updateRequestStatusInSupabase(id: String, status: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "requests?id=eq.$id"
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
            val endpoint = "requests?id=eq.$id"
            val conn = openConnection(endpoint, "DELETE")
            val code = conn.responseCode
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "deleteRequestFromSupabase error", e)
            Result.failure(e)
        }
    }

    suspend fun recordPostClick(postId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "post_clicks"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("post_id", postId)
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
                        requestsCount = 0,
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
            val endpoint = "post_clicks?select=post_id,count"
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

    suspend fun recordPostClick(postId: String, userId: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "post_clicks"
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
            val endpoint = "favorites?select=post_id,user_id"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode

            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val jsonArray = JSONArray(response)
                val likesCountMap = mutableMapOf<String, Int>()
                val userFavPostIds = mutableSetOf<String>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val postId = obj.optString("post_id", "")
                    val userId = obj.optString("user_id", "")
                    if (postId.isNotBlank()) {
                        likesCountMap[postId] = (likesCountMap[postId] ?: 0) + 1
                        if (userId.isNotBlank()) {
                            userFavPostIds.add(postId)
                        }
                    }
                }
                Result.success(Pair(likesCountMap, userFavPostIds))
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
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

    suspend fun insertPost(post: PostEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "posts"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true

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
            conn.disconnect()
            Result.success(code in 200..299)
        } catch (e: Exception) {
            Log.e(TAG, "insertPost error", e)
            Result.failure(e)
        }
    }

    suspend fun updatePost(post: PostEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "posts?id=eq.${post.id}"
            val conn = openConnection(endpoint, "PATCH")
            conn.doOutput = true

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
            conn.disconnect()
            Result.success(code in 200..299)
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
            val endpoint = "requests?id=eq.$id"
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
            val endpoint = "requests?id=eq.$id"
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
