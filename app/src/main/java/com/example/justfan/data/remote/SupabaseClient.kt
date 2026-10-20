package com.example.justfan.data.remote

import android.util.Log
import com.example.justfan.BuildConfig
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RemoteUserPreferences
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.RequestQuota
import com.example.justfan.data.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

object SupabaseClient {
    private const val TAG = "SupabaseClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    val apiKey: String
        get() = BuildConfig.SUPABASE_KEY

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

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

    private fun parseIsoToMillis(isoStr: String): Long {
        if (isoStr.isBlank() || isoStr == "null") return System.currentTimeMillis()
        return try {
            Instant.parse(isoStr).toEpochMilli()
        } catch (_: Exception) {
            try {
                DateTimeFormatter.ISO_DATE_TIME.parse(isoStr, Instant::from).toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    suspend fun executePatch(endpoint: String, jsonPayload: String, accessToken: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl/rest/v1/$endpoint"
            val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

            fun doPatch(authHeader: String): Pair<Int, String> {
                val request = Request.Builder()
                    .url(fullUrl)
                    .patch(body)
                    .header("apikey", apiKey)
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Prefer", "return=representation")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    return Pair(response.code, response.body?.string().orEmpty())
                }
            }

            val initialHeader = if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey"
            var (code, responseBody) = doPatch(initialHeader)

            if ((code == 401 || code == 403) && initialHeader != "Bearer $apiKey") {
                Log.w(TAG, "executePatch: Token rejected ($code: $responseBody), falling back to apiKey")
                val fallback = doPatch("Bearer $apiKey")
                code = fallback.first
                responseBody = fallback.second
            }

            if (code in 200..299) {
                Result.success(responseBody)
            } else {
                Result.failure(Exception("HTTP $code: $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "executePatch error on $endpoint", e)
            Result.failure(e)
        }
    }

    suspend fun executePost(endpoint: String, jsonPayload: String, accessToken: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl/rest/v1/$endpoint"
            val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

            fun doPost(authHeader: String): Pair<Int, String> {
                val request = Request.Builder()
                    .url(fullUrl)
                    .post(body)
                    .header("apikey", apiKey)
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Prefer", "return=representation")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    return Pair(response.code, response.body?.string().orEmpty())
                }
            }

            val initialHeader = if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey"
            var (code, responseBody) = doPost(initialHeader)

            if ((code == 401 || code == 403) && initialHeader != "Bearer $apiKey") {
                Log.w(TAG, "executePost: Token rejected ($code: $responseBody), falling back to apiKey")
                val fallback = doPost("Bearer $apiKey")
                code = fallback.first
                responseBody = fallback.second
            }

            if (code in 200..299) {
                Result.success(responseBody)
            } else {
                Result.failure(Exception("HTTP $code: $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "executePost error on $endpoint", e)
            Result.failure(e)
        }
    }

    suspend fun executeDelete(endpoint: String, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl/rest/v1/$endpoint"
            val request = Request.Builder()
                .url(fullUrl)
                .delete()
                .header("apikey", apiKey)
                .header("Authorization", if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeDelete error on $endpoint", e)
            Result.failure(e)
        }
    }

    private fun fetchPostClickCounts(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        try {
            val conn = openConnection("post_click_counts?select=post_id,count&order=count.desc&limit=1000", "GET")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pid = obj.optString("post_id", "")
                    val count = obj.optInt("count", obj.optInt("clicks_count", 0))
                    if (pid.isNotBlank()) {
                        map[pid] = count
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPostClickCounts warning", e)
        }
        return map
    }

    suspend fun fetchPosts(limit: Int = 1000): Result<List<PostEntity>> = withContext(Dispatchers.IO) {
        try {
            val clickCountsMap = fetchPostClickCounts()
            val endpoint = "posts?select=*&order=created_at.desc&limit=$limit"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val jsonArray = JSONArray(response)
                val list = mutableListOf<PostEntity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val title = obj.optString("title", "Untitled Post")
                    val desc = obj.optString("description", "")
                    val img = obj.optString("image_url", "")
                    val link = obj.optString("link_url", "")
                    val direct = if (obj.has("direct_link_url") && !obj.isNull("direct_link_url")) obj.optString("direct_link_url") else null
                    val prem = if (obj.has("premium_link_url") && !obj.isNull("premium_link_url")) obj.optString("premium_link_url") else null
                    val isFree = obj.optBoolean("is_free", false)
                    val isNsfw = obj.optBoolean("is_nsfw", false)
                    val clicks = clickCountsMap[id] ?: obj.optInt("clicks_count", 0)
                    val likes = obj.optInt("likes_count", 0)
                    val section = obj.optString("section", "home")
                    val author = obj.optString("author", "JustFan Creator")
                    val createdAtStr = obj.optString("created_at", "")

                    val tagsList = mutableListOf<String>()
                    val tagsArr = obj.optJSONArray("tags")
                    if (tagsArr != null) {
                        for (j in 0 until tagsArr.length()) {
                            tagsList.add(tagsArr.getString(j))
                        }
                    }

                    val contentImagesList = mutableListOf<String>()
                    val contentImgsArr = obj.optJSONArray("content_images")
                    if (contentImgsArr != null) {
                        for (j in 0 until contentImgsArr.length()) {
                            val url = contentImgsArr.optString(j, "").trim()
                            if (url.isNotEmpty()) contentImagesList.add(url)
                        }
                    }

                    list.add(
                        PostEntity(
                            id = id,
                            title = title,
                            description = desc,
                            imageUrl = img,
                            linkUrl = link,
                            directLinkUrl = direct,
                            premiumLinkUrl = prem,
                            contentImages = contentImagesList,
                            tags = tagsList,
                            section = section,
                            author = author,
                            isNsfw = isNsfw,
                            isFree = isFree,
                            clicksCount = clicks,
                            likesCount = likes,
                            createdAt = parseIsoToMillis(createdAtStr)
                        )
                    )
                }
                Result.success(list)
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPosts error", e)
            Result.failure(e)
        }
    }

    suspend fun insertPost(post: PostEntity, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("id", post.id)
            put("title", post.title)
            put("description", post.description)
            put("image_url", post.imageUrl)
            put("link_url", post.linkUrl)
            if (!post.directLinkUrl.isNullOrBlank()) put("direct_link_url", post.directLinkUrl)
            if (!post.premiumLinkUrl.isNullOrBlank()) put("premium_link_url", post.premiumLinkUrl)
            if (post.contentImages.isNotEmpty()) put("content_images", JSONArray(post.contentImages))
            put("is_free", post.isFree)
            put("is_nsfw", post.isNsfw)
            put("section", post.section)
            put("tags", JSONArray(post.tags))
        }
        val res = executePost("posts", payload.toString(), accessToken)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("INSERT_FAILED"))
    }

    suspend fun updatePost(post: PostEntity, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("title", post.title)
            put("description", post.description)
            put("image_url", post.imageUrl)
            put("link_url", post.linkUrl)
            if (!post.directLinkUrl.isNullOrBlank()) put("direct_link_url", post.directLinkUrl)
            if (!post.premiumLinkUrl.isNullOrBlank()) put("premium_link_url", post.premiumLinkUrl)
            if (post.contentImages.isNotEmpty()) put("content_images", JSONArray(post.contentImages))
            put("is_free", post.isFree)
            put("is_nsfw", post.isNsfw)
            put("section", post.section)
            put("tags", JSONArray(post.tags))
        }
        val res = executePatch("posts?id=eq.${post.id}", payload.toString(), accessToken)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("UPDATE_FAILED"))
    }

    suspend fun deletePost(postId: String, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        executeDelete("posts?id=eq.$postId", accessToken)
    }

    suspend fun recordPostClick(postId: String, userId: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 1. Insert click event into post_clicks table
            val clickPayload = JSONObject().apply {
                put("post_id", postId)
                if (!userId.isNullOrBlank() && userId != "guest_user" && userId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                    put("user_id", userId)
                }
            }
            val clickRes = executePost("post_clicks", clickPayload.toString())

            // 2. Also ensure post_click_counts table is updated directly if trigger didn't increment
            try {
                val checkConn = openConnection("post_click_counts?select=post_id,count&post_id=eq.$postId&limit=1", "GET")
                if (checkConn.responseCode in 200..299) {
                    val body = checkConn.inputStream.bufferedReader().readText()
                    checkConn.disconnect()
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val currentCount = arr.getJSONObject(0).optInt("count", 0)
                        // If post_clicks failed or didn't auto-increment via trigger, patch count + 1
                        if (clickRes.isFailure) {
                            val patchObj = JSONObject().apply { put("count", currentCount + 1) }
                            executePatch("post_click_counts?post_id=eq.$postId", patchObj.toString())
                        }
                    } else if (clickRes.isFailure) {
                        val postObj = JSONObject().apply {
                            put("post_id", postId)
                            put("count", 1)
                        }
                        executePost("post_click_counts", postObj.toString())
                    }
                } else {
                    checkConn.disconnect()
                }
            } catch (_: Exception) {}

            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "recordPostClick notice", e)
            Result.success(false)
        }
    }

    suspend fun fetchRequests(): Result<List<RequestEntity>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "RequestfromApp?select=*&order=created_at.desc"
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
                    val userId = if (obj.has("user_id") && !obj.isNull("user_id")) obj.optString("user_id") else null
                    val name = if (obj.has("name") && !obj.isNull("name")) obj.optString("name") else null
                    val email = obj.optString("email", "")
                    val telegram = if (obj.has("telegram_username") && !obj.isNull("telegram_username")) obj.optString("telegram_username") else null
                    val msg = obj.optString("message", "")
                    val img = if (obj.has("image_url") && !obj.isNull("image_url")) obj.optString("image_url") else null
                    val status = obj.optString("status", "pending")
                    val dl = if (obj.has("download_link") && !obj.isNull("download_link")) obj.optString("download_link") else null
                    val rej = if (obj.has("rejection_reason") && !obj.isNull("rejection_reason")) obj.optString("rejection_reason") else null
                    val createdAtStr = obj.optString("created_at", "")

                    list.add(
                        RequestEntity(
                            id = id,
                            userId = userId,
                            name = name,
                            email = email,
                            telegramUsername = telegram,
                            message = msg,
                            imageUrl = img,
                            status = status,
                            downloadLink = dl,
                            rejectionReason = rej,
                            createdAt = parseIsoToMillis(createdAtStr)
                        )
                    )
                }
                Result.success(list)
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRequests error", e)
            Result.failure(e)
        }
    }

    suspend fun createRequest(
        name: String,
        email: String,
        telegram: String?,
        message: String,
        imageUrl: String?,
        userId: String?,
        deviceId: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            if (!userId.isNullOrBlank() && userId != "guest_user") put("user_id", userId)
            put("name", name)
            put("email", email)
            if (!telegram.isNullOrBlank()) put("telegram_username", telegram)
            put("message", message)
            if (!imageUrl.isNullOrBlank()) put("image_url", imageUrl)
            put("status", "pending")
            if (!deviceId.isNullOrBlank()) put("device_id", deviceId)
        }
        val res = executePost("RequestfromApp", payload.toString())
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("CREATE_REQUEST_FAILED"))
    }

    suspend fun updateRequestStatusInSupabase(id: String, status: String, link: String?, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("status", status)
            if (!link.isNullOrBlank()) put("download_link", link)
        }
        val res = executePatch("RequestfromApp?id=eq.$id", payload.toString(), accessToken)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("UPDATE_FAILED"))
    }

    suspend fun rejectRequestInSupabase(id: String, reason: String, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("status", "rejected")
            put("rejection_reason", reason)
        }
        val res = executePatch("RequestfromApp?id=eq.$id", payload.toString(), accessToken)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("REJECT_FAILED"))
    }

    suspend fun deleteRequestFromSupabase(id: String, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        executeDelete("RequestfromApp?id=eq.$id", accessToken)
    }

    suspend fun fetchSubscriptions(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "subscriptions?select=user_id,plan,expires_at"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode
            if (code in 200..299) {
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val array = JSONArray(text)
                val map = mutableMapOf<String, String>()
                val nowMillis = System.currentTimeMillis()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val uid = item.optString("user_id")
                    val plan = item.optString("plan", "Free")
                    val exp = item.optString("expires_at", "")
                    var isExpired = false
                    if (exp.isNotBlank() && exp != "null") {
                        try {
                            if (parseIsoToMillis(exp) < nowMillis) isExpired = true
                        } catch (_: Exception) {}
                    }
                    if (uid.isNotBlank() && !isExpired && plan.isNotBlank()) {
                        map[uid] = plan
                    }
                }
                Result.success(map)
            } else {
                conn.disconnect()
                Result.success(emptyMap())
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchSubscriptions warning", e)
            Result.success(emptyMap())
        }
    }

    data class SubscriptionDetails(
        val tier: String,
        val expiresAtMillis: Long
    )

    suspend fun fetchUserSubscriptionDetails(
        userId: String,
        email: String? = null,
        accessToken: String? = null
    ): Result<SubscriptionDetails?> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email?.trim()?.lowercase().orEmpty()
            if (evaluateIsAdmin(cleanEmail) || userId == "a2d7bbad-c31f-4679-bf51-2d2684bb3216") {
                return@withContext Result.success(SubscriptionDetails("Legendary", Long.MAX_VALUE))
            }

            val resolvedUuid = if (userId.contains("@")) {
                resolveUserUuid(userId) ?: userId
            } else if (userId.isBlank() && cleanEmail.isNotBlank()) {
                resolveUserUuid(cleanEmail) ?: userId
            } else {
                userId
            }

            val nowMillis = System.currentTimeMillis()
            val oneMonthMillis = 31L * 24L * 60L * 60L * 1000L

            // 1. Check `subscriptions` table first
            if (resolvedUuid.isNotBlank()) {
                val subEndpoint = "subscriptions?user_id=eq.$resolvedUuid&order=created_at.desc&limit=1"
                val subReq = Request.Builder()
                    .url("$baseUrl/rest/v1/$subEndpoint")
                    .get()
                    .header("apikey", apiKey)
                    .header("Authorization", if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey")
                    .build()

                okHttpClient.newCall(subReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val array = JSONArray(response.body?.string().orEmpty())
                        if (array.length() > 0) {
                            val obj = array.getJSONObject(0)
                            val rawPlan = obj.optString("plan", "Free")
                            val expStr = obj.optString("expires_at", "")
                            val createdStr = obj.optString("created_at", "")

                            if (rawPlan.equals("legendary", ignoreCase = true)) {
                                return@withContext Result.success(SubscriptionDetails("Legendary", Long.MAX_VALUE))
                            } else if (rawPlan.equals("pro", ignoreCase = true)) {
                                val expMillis = when {
                                    expStr.isNotBlank() && expStr != "null" -> parseIsoToMillis(expStr)
                                    createdStr.isNotBlank() && createdStr != "null" -> parseIsoToMillis(createdStr) + oneMonthMillis
                                    else -> nowMillis + oneMonthMillis
                                }
                                if (expMillis < nowMillis) {
                                    // Automatically downgrade expired 1-month Pro subscription back to Free in database
                                    Log.i(TAG, "Pro subscription expired for $resolvedUuid; auto-downgrading to Free in Supabase")
                                    updateUserTier(resolvedUuid, "Free", accessToken, cleanEmail)
                                    return@withContext Result.success(SubscriptionDetails("Free", 0L))
                                } else {
                                    return@withContext Result.success(SubscriptionDetails("Pro", expMillis))
                                }
                            } else {
                                return@withContext Result.success(SubscriptionDetails("Free", 0L))
                            }
                        }
                    }
                }
            }

            // 2. Also check `users` and `profiles` tables as fallback for tier identification
            val userCheckEndpoints = mutableListOf<String>()
            if (resolvedUuid.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                userCheckEndpoints.add("users?id=eq.$resolvedUuid&limit=1")
                userCheckEndpoints.add("profiles?id=eq.$resolvedUuid&limit=1")
            }
            if (cleanEmail.isNotBlank()) {
                userCheckEndpoints.add("users?email=eq.$cleanEmail&limit=1")
                userCheckEndpoints.add("profiles?username=eq.$cleanEmail&limit=1")
            }

            for (ep in userCheckEndpoints) {
                try {
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/$ep")
                        .get()
                        .header("apikey", apiKey)
                        .header("Authorization", if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey")
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val arr = JSONArray(resp.body?.string().orEmpty())
                            if (arr.length() > 0) {
                                val obj = arr.getJSONObject(0)
                                val dbTier = obj.optString("tier", obj.optString("plan", "")).trim()
                                val expStr = obj.optString("expires_at", obj.optString("pro_expires_at", ""))
                                if (dbTier.equals("legendary", ignoreCase = true)) {
                                    return@withContext Result.success(SubscriptionDetails("Legendary", Long.MAX_VALUE))
                                } else if (dbTier.equals("pro", ignoreCase = true)) {
                                    val expMillis = if (expStr.isNotBlank() && expStr != "null") {
                                        parseIsoToMillis(expStr)
                                    } else {
                                        nowMillis + oneMonthMillis
                                    }
                                    if (expMillis < nowMillis) {
                                        updateUserTier(resolvedUuid, "Free", accessToken, cleanEmail)
                                        return@withContext Result.success(SubscriptionDetails("Free", 0L))
                                    }
                                    return@withContext Result.success(SubscriptionDetails("Pro", expMillis))
                                } else if (dbTier.equals("free", ignoreCase = true)) {
                                    return@withContext Result.success(SubscriptionDetails("Free", 0L))
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            Result.success(null)
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserSubscriptionDetails warning", e)
            Result.failure(e)
        }
    }

    suspend fun fetchUserSubscription(userId: String, accessToken: String? = null, email: String? = null): Result<String?> = withContext(Dispatchers.IO) {
        val detailsRes = fetchUserSubscriptionDetails(userId, email, accessToken)
        if (detailsRes.isSuccess) {
            Result.success(detailsRes.getOrNull()?.tier)
        } else {
            Result.failure(detailsRes.exceptionOrNull() ?: Exception("SUBSCRIPTION_CHECK_FAILED"))
        }
    }

    suspend fun resolveUserUuid(userIdentifier: String): String? = withContext(Dispatchers.IO) {
        if (!userIdentifier.contains("@")) return@withContext userIdentifier
        try {
            val clean = userIdentifier.trim().lowercase()
            val url = "$baseUrl/rest/v1/profiles?username=eq.$clean&limit=1"
            val req = Request.Builder()
                .url(url)
                .get()
                .header("apikey", apiKey)
                .header("Authorization", "Bearer $apiKey")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    if (arr.length() > 0) {
                        return@withContext arr.getJSONObject(0).optString("id")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveUserUuid warning", e)
        }
        null
    }

    suspend fun updateUserTier(
        userIdOrEmail: String,
        tier: String,
        accessToken: String? = null,
        emailHint: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val normalizedTier = when {
                tier.equals("legendary", ignoreCase = true) -> "Legendary"
                tier.equals("pro", ignoreCase = true) -> "Pro"
                else -> "Free"
            }

            val resolvedUuid = if (userIdOrEmail.contains("@")) {
                resolveUserUuid(userIdOrEmail) ?: UUID.nameUUIDFromBytes("justfan:${userIdOrEmail.trim().lowercase()}".toByteArray()).toString()
            } else {
                userIdOrEmail
            }

            val now = Instant.now()
            val expiresAtStr = when (normalizedTier.lowercase()) {
                "pro" -> now.plus(31, java.time.temporal.ChronoUnit.DAYS).toString()
                "legendary" -> Instant.parse("2099-12-31T23:59:59Z").toString()
                else -> null
            }

            val isUuid = resolvedUuid.matches(Regex("^[0-9a-fA-F-]{36}$"))
            if (isUuid) {
                val checkUrl = "$baseUrl/rest/v1/subscriptions?user_id=eq.$resolvedUuid&limit=1"
                val checkReq = Request.Builder()
                    .url(checkUrl)
                    .get()
                    .header("apikey", apiKey)
                    .header("Authorization", if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey")
                    .build()

                var exists = false
                try {
                    okHttpClient.newCall(checkReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            exists = JSONArray(resp.body?.string().orEmpty()).length() > 0
                        }
                    }
                } catch (_: Exception) {}

                val payload = JSONObject().apply {
                    put("user_id", resolvedUuid)
                    put("plan", normalizedTier)
                    if (expiresAtStr != null) {
                        put("expires_at", expiresAtStr)
                    } else {
                        put("expires_at", JSONObject.NULL)
                    }
                }

                val subResult = if (exists) {
                    executePatch("subscriptions?user_id=eq.$resolvedUuid", payload.toString(), accessToken)
                } else {
                    payload.put("id", UUID.randomUUID().toString())
                    payload.put("created_at", now.toString())
                    executePost("subscriptions", payload.toString(), accessToken)
                }

                if (subResult.isSuccess) {
                    Log.i(TAG, "updateUserTier succeeded in subscriptions table for $resolvedUuid -> $normalizedTier")
                }

                // Also sync tier to `users` and `profiles` tables if columns exist
                try {
                    val userTierPayload = JSONObject().apply {
                        put("tier", normalizedTier)
                        if (expiresAtStr != null) put("expires_at", expiresAtStr) else put("expires_at", JSONObject.NULL)
                    }
                    executePatch("users?id=eq.$resolvedUuid", userTierPayload.toString(), accessToken)
                } catch (_: Exception) {}

                try {
                    val profileTierPayload = JSONObject().apply {
                        put("tier", normalizedTier)
                    }
                    executePatch("profiles?id=eq.$resolvedUuid", profileTierPayload.toString(), accessToken)
                } catch (_: Exception) {}
            }

            val cleanEmail = (emailHint ?: if (userIdOrEmail.contains("@")) userIdOrEmail else "").trim().lowercase()
            if (cleanEmail.isNotBlank()) {
                try {
                    val emailTierPayload = JSONObject().apply {
                        put("tier", normalizedTier)
                    }
                    executePatch("users?email=eq.$cleanEmail", emailTierPayload.toString(), accessToken)
                } catch (_: Exception) {}
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserTier error", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserStatus(userId: String, status: String, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resolvedUuid = if (userId.contains("@")) resolveUserUuid(userId) ?: userId else userId
            val payload = JSONObject().apply {
                put("status", status)
                put("updated_at", Instant.now().toString())
            }
            val res = executePatch("profiles?id=eq.$resolvedUuid", payload.toString(), accessToken)
            if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("UPDATE_STATUS_FAILED"))
        } catch (e: Exception) {
            Log.e(TAG, "updateUserStatus error", e)
            Result.failure(e)
        }
    }

    suspend fun setUserBlocked(userId: String, isBlocked: Boolean, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val status = if (isBlocked) "Suspended" else "Active"
        updateUserStatus(userId, status, accessToken)
    }

    suspend fun fetchUserPreferences(userId: String, accessToken: String? = null): Result<RemoteUserPreferences?> = withContext(Dispatchers.IO) {
        try {
            val resolvedUuid = if (userId.contains("@")) resolveUserUuid(userId) ?: userId else userId
            val endpoint = "user_preferences?user_id=eq.$resolvedUuid&limit=1"
            val fullUrl = "$baseUrl/rest/v1/$endpoint"
            val request = Request.Builder()
                .url(fullUrl)
                .get()
                .header("apikey", apiKey)
                .header("Authorization", if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val array = JSONArray(response.body?.string().orEmpty())
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val theme = if (obj.has("theme") && !obj.isNull("theme")) obj.optString("theme") else null
                        val themeVariant = if (obj.has("theme_variant") && !obj.isNull("theme_variant")) obj.optString("theme_variant") else null
                        val wallpaper = if (obj.has("wallpaper_url") && !obj.isNull("wallpaper_url")) obj.optString("wallpaper_url") else null
                        val overlay = if (obj.has("wallpaper_overlay") && !obj.isNull("wallpaper_overlay")) obj.optDouble("wallpaper_overlay", 0.65).toFloat() else null
                        return@withContext Result.success(
                            RemoteUserPreferences(
                                theme = theme,
                                theme_variant = themeVariant,
                                wallpaper_url = wallpaper,
                                wallpaper_overlay = overlay
                            )
                        )
                    }
                    Result.success(null)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserPreferences warning", e)
            Result.failure(e)
        }
    }

    suspend fun saveUserPreferences(
        userId: String,
        theme: String,
        themeVariant: String? = null,
        wallpaperUrl: String? = null,
        wallpaperOverlay: Float? = null,
        accessToken: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resolvedUuid = if (userId.contains("@")) resolveUserUuid(userId) ?: userId else userId
            val isUuid = resolvedUuid.matches(Regex("^[0-9a-fA-F-]{36}$"))
            if (!isUuid) return@withContext Result.success(true)

            val checkUrl = "$baseUrl/rest/v1/user_preferences?user_id=eq.$resolvedUuid&limit=1"
            val checkReq = Request.Builder()
                .url(checkUrl)
                .get()
                .header("apikey", apiKey)
                .header("Authorization", if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey")
                .build()

            var exists = false
            try {
                okHttpClient.newCall(checkReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        exists = JSONArray(resp.body?.string().orEmpty()).length() > 0
                    }
                }
            } catch (_: Exception) {}

            val now = Instant.now().toString()
            val payload = JSONObject().apply {
                put("user_id", resolvedUuid)
                put("theme", theme)
                if (!themeVariant.isNullOrBlank()) {
                    put("theme_variant", themeVariant)
                }
                if (!wallpaperUrl.isNullOrBlank()) {
                    put("wallpaper_url", wallpaperUrl)
                } else {
                    put("wallpaper_url", JSONObject.NULL)
                }
                if (wallpaperOverlay != null) {
                    put("wallpaper_overlay", wallpaperOverlay.toDouble())
                }
                put("updated_at", now)
            }

            val result = if (exists) {
                executePatch("user_preferences?user_id=eq.$resolvedUuid", payload.toString(), accessToken)
            } else {
                payload.put("id", UUID.randomUUID().toString())
                payload.put("created_at", now)
                executePost("user_preferences", payload.toString(), accessToken)
            }

            if (result.isSuccess) {
                Log.i(TAG, "saveUserPreferences succeeded for $resolvedUuid: theme=$theme, variant=$themeVariant, wallpaper=$wallpaperUrl")
                Result.success(true)
            } else {
                Log.w(TAG, "saveUserPreferences warning: ${result.exceptionOrNull()?.message}")
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveUserPreferences error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchProfiles(limit: Int = 500): Result<List<UserEntity>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "profiles?select=*&order=created_at.desc&limit=$limit"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val subscriptionsMap = fetchSubscriptions().getOrDefault(emptyMap())
                val jsonArray = JSONArray(response)
                val users = mutableListOf<UserEntity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val rawUsername = if (obj.has("username") && !obj.isNull("username")) obj.optString("username") else null
                    val email = rawUsername ?: "${id.take(8)}@user.justfan"
                    val username = if (email.contains("@")) email.substringBefore("@") else email
                    val isAdmin = email.equals("reytherapper12@gmail.com", ignoreCase = true) || id == "a2d7bbad-c31f-4679-bf51-2d2684bb3216" || id == "fe335770-80a9-4125-9c15-d47f385579fb"
                    val createdAtStr = obj.optString("created_at", "")

                    val subTier = subscriptionsMap[id] ?: subscriptionsMap[email]
                    val finalTier = subTier ?: if (isAdmin) "Legendary" else "Free"

                    users.add(
                        UserEntity(
                            id = id,
                            username = username,
                            email = email,
                            tier = finalTier,
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

    suspend fun fetchRequestQuota(
        userId: String,
        deviceId: String,
        email: String? = null,
        accessToken: String? = null
    ): Result<RequestQuota> = withContext(Dispatchers.IO) {
        // 1. Check user's tier in `subscriptions` and `users` tables first
        try {
            val subDetails = fetchUserSubscriptionDetails(userId, email, accessToken).getOrNull()
            if (subDetails != null) {
                val activeTier = subDetails.tier
                if (activeTier.equals("legendary", ignoreCase = true) || activeTier.equals("pro", ignoreCase = true)) {
                    return@withContext Result.success(
                        RequestQuota(
                            unlimited = true,
                            limit = 9999,
                            used = 0,
                            remaining = 9999,
                            resetsAt = if (activeTier.equals("pro", ignoreCase = true) && subDetails.expiresAtMillis > 0L) {
                                Instant.ofEpochMilli(subDetails.expiresAtMillis).toString()
                            } else {
                                null
                            }
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. For Free tier users: enforce 3 requests per month (last 31 days rolling cycle)
        var monthlyDeviceCount = 0
        val monthAgoMillis = System.currentTimeMillis() - (31L * 24L * 60L * 60L * 1000L)
        try {
            val filters = mutableListOf<String>()
            if (deviceId.isNotBlank()) filters.add("device_id.eq.$deviceId")
            if (userId.isNotBlank() && userId != "guest_user" && userId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                filters.add("user_id.eq.$userId")
            }
            if (!email.isNullOrBlank()) {
                filters.add("email.eq.${email.trim().lowercase()}")
            }
            if (filters.isNotEmpty()) {
                val orClause = filters.joinToString(",")
                val conn = openConnection("RequestfromApp?select=id,device_id,email,created_at&or=($orClause)", "GET")
                if (conn.responseCode in 200..299) {
                    val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                    for (i in 0 until arr.length()) {
                        val row = arr.getJSONObject(i)
                        val createdStr = row.optString("created_at", "")
                        val createdMillis = if (createdStr.isNotBlank() && createdStr != "null") {
                            parseIsoToMillis(createdStr)
                        } else {
                            System.currentTimeMillis()
                        }
                        if (createdMillis >= monthAgoMillis) {
                            monthlyDeviceCount++
                        }
                    }
                }
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRequestQuota direct count notice", e)
        }

        try {
            val endpoint = "$baseUrl/rest/v1/rpc/get_request_quota"
            val payload = JSONObject().apply {
                put("p_user_id", userId)
                put("p_device_id", deviceId)
            }
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", apiKey)
                .header("Authorization", if (!accessToken.isNullOrBlank()) "Bearer $accessToken" else "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    if (json.optBoolean("unlimited", false)) {
                        return@withContext Result.success(
                            RequestQuota(unlimited = true, limit = 9999, used = 0, remaining = 9999)
                        )
                    }
                    val rpcUsed = json.optInt("used", 0)
                    val limit = 3
                    val effectiveUsed = maxOf(rpcUsed, monthlyDeviceCount)
                    val remaining = (limit - effectiveUsed).coerceAtLeast(0)
                    Result.success(
                        RequestQuota(
                            unlimited = false,
                            limit = limit,
                            used = effectiveUsed,
                            remaining = remaining,
                            resetsAt = json.optString("resets_at", null)
                        )
                    )
                } else {
                    val remaining = (3 - monthlyDeviceCount).coerceAtLeast(0)
                    Result.success(RequestQuota(unlimited = false, limit = 3, used = monthlyDeviceCount, remaining = remaining))
                }
            }
        } catch (e: Exception) {
            val remaining = (3 - monthlyDeviceCount).coerceAtLeast(0)
            Result.success(RequestQuota(unlimited = false, limit = 3, used = monthlyDeviceCount, remaining = remaining))
        }
    }

    suspend fun syncDeviceBiometricToSupabase(
        userId: String,
        email: String,
        deviceId: String,
        manufacturer: String,
        model: String,
        osVersion: String,
        biometricEnabled: Boolean,
        accessToken: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 1. Update auth user_metadata if user has a valid accessToken
            if (!accessToken.isNullOrBlank() && accessToken != apiKey) {
                try {
                    val userPayload = JSONObject().apply {
                        put("data", JSONObject().apply {
                            put("device_id", deviceId)
                            put("device_manufacturer", manufacturer)
                            put("device_model", model)
                            put("os_version", osVersion)
                            put("biometric_enabled", biometricEnabled)
                            put("biometric_verified_at", Instant.now().toString())
                        })
                    }
                    val req = Request.Builder()
                        .url("$baseUrl/auth/v1/user")
                        .put(userPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .header("apikey", apiKey)
                        .header("Authorization", "Bearer $accessToken")
                        .header("Content-Type", "application/json")
                        .build()
                    okHttpClient.newCall(req).execute().close()
                } catch (_: Exception) {}
            }

            // 2. Also update any existing RequestfromApp rows for this email/userId that lack a hardware device_id
            if (email.isNotBlank()) {
                val patchPayload = JSONObject().apply {
                    put("device_id", deviceId)
                }
                executePatch("RequestfromApp?email=eq.${email.trim().lowercase()}", patchPayload.toString(), accessToken)
            }

            Log.i(TAG, "Synced biometric & device details ($manufacturer $model / $deviceId) for $email to Supabase")
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "syncDeviceBiometricToSupabase warning", e)
            Result.success(false)
        }
    }

    data class AuthSession(
        val accessToken: String,
        val refreshToken: String? = null,
        val userId: String,
        val email: String,
        val isAdmin: Boolean = email.trim().equals("reytherapper12@gmail.com", ignoreCase = true)
    )

    fun evaluateIsAdmin(email: String?): Boolean {
        return email?.trim().equals("reytherapper12@gmail.com", ignoreCase = true)
    }

    fun extractUserFromJwt(accessToken: String): Triple<String?, String?, String?> {
        return try {
            val parts = accessToken.split(".")
            if (parts.size >= 2) {
                val payloadJson = String(
                    android.util.Base64.decode(
                        parts[1],
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                    )
                )
                val obj = JSONObject(payloadJson)
                val uid = obj.optString("sub", "").ifBlank { null }
                val email = obj.optString("email", "").ifBlank { null }
                val meta = obj.optJSONObject("user_metadata")
                val name = meta?.optString("full_name", meta.optString("name", ""))?.ifBlank { null }
                Triple(uid, email, name)
            } else {
                Triple(null, null, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractUserFromJwt failed", e)
            Triple(null, null, null)
        }
    }

    suspend fun handleGoogleAuthSession(
        email: String,
        accessToken: String? = null,
        refreshToken: String? = null,
        userId: String? = null
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        val jwtInfo = if (!accessToken.isNullOrBlank()) extractUserFromJwt(accessToken) else Triple(null, null, null)
        val cleanEmail = (jwtInfo.second ?: email).trim().lowercase()
        val isAdmin = evaluateIsAdmin(cleanEmail)
        val uid = jwtInfo.first
            ?: userId
            ?: resolveUserUuid(cleanEmail)
            ?: if (isAdmin) "a2d7bbad-c31f-4679-bf51-2d2684bb3216" else UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()
        val session = AuthSession(
            accessToken = accessToken ?: apiKey,
            refreshToken = refreshToken,
            userId = uid,
            email = cleanEmail,
            isAdmin = isAdmin
        )
        Log.i(TAG, "Google Auth Session: $cleanEmail -> isAdmin=$isAdmin")
        Result.success(session)
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$baseUrl/auth/v1/token?grant_type=password"
            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
            }
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", apiKey)
                .header("Content-Type", "application/json")
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = JSONObject(body)
                    val token = json.getString("access_token")
                    val refresh = json.optString("refresh_token", null)
                    val userObj = json.getJSONObject("user")
                    val uid = userObj.getString("id")
                    val em = userObj.optString("email", email.trim().lowercase())
                    val isAdmin = evaluateIsAdmin(em)
                    Result.success(AuthSession(accessToken = token, refreshToken = refresh, userId = uid, email = em, isAdmin = isAdmin))
                } else {
                    Result.failure(Exception("AUTH_FAILED: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmailPassword(email: String, password: String, username: String = ""): Result<AuthSession?> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            val endpoint = "$baseUrl/auth/v1/signup"
            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", password)
                if (username.isNotBlank()) {
                    put("data", JSONObject().apply { put("username", username) })
                }
            }
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", apiKey)
                .header("Content-Type", "application/json")
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = JSONObject(body)
                    if (json.has("access_token") && !json.isNull("access_token")) {
                        val token = json.getString("access_token")
                        val refresh = json.optString("refresh_token", null)
                        val userObj = json.optJSONObject("user")
                        val uid = userObj?.optString("id") ?: UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()
                        Result.success(AuthSession(accessToken = token, refreshToken = refresh, userId = uid, email = cleanEmail, isAdmin = evaluateIsAdmin(cleanEmail)))
                    } else {
                        Result.success(null)
                    }
                } else {
                    Result.failure(Exception("SIGNUP_FAILED: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(refreshToken: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$baseUrl/auth/v1/token?grant_type=refresh_token"
            val payload = JSONObject().apply {
                put("refresh_token", refreshToken)
            }
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", apiKey)
                .header("Content-Type", "application/json")
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = JSONObject(body)
                    val token = json.getString("access_token")
                    val refresh = json.optString("refresh_token", refreshToken)
                    val userObj = json.optJSONObject("user")
                    val uid = userObj?.optString("id") ?: ""
                    val em = userObj?.optString("email") ?: ""
                    Result.success(AuthSession(accessToken = token, refreshToken = refresh, userId = uid, email = em, isAdmin = evaluateIsAdmin(em)))
                } else {
                    Result.failure(Exception("REFRESH_FAILED: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
