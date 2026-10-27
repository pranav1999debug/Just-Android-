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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
            .addInterceptor { chain ->
                val requestWithVersion = chain.request().newBuilder()
                    .header(
                        AppVersionConfig.HEADER_APP_VERSION,
                        AppVersionConfig.APP_VERSION.toString()
                    )
                    .build()
                val response = chain.proceed(requestWithVersion)
                if (response.code == 401 || response.code == 403) {
                    val errPreview = runCatching { response.peekBody(2048).string() }.getOrDefault("")
                    VersionGateManager.onPermissionOrRlsRejected(response.code, errPreview)
                }
                response
            }
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
        connection.setRequestProperty(
            AppVersionConfig.HEADER_APP_VERSION,
            AppVersionConfig.APP_VERSION.toString()
        )
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Prefer", "return=representation")
        return connection
    }

    private fun parseIsoToMillis(isoStr: String, defaultMillis: Long = 0L): Long {
        val raw = isoStr.trim()
        if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return defaultMillis
        // Normalize PostgreSQL timestamp strings like "2026-09-25 14:22:10.123456+00" -> ISO-8601
        val normalized = raw
            .replace(' ', 'T')
            .let { s ->
                when {
                    s.endsWith("+00") -> s.dropLast(3) + "Z"
                    s.endsWith("-00") -> s.dropLast(3) + "Z"
                    Regex(".*[+-]\\d{2}$").matches(s) -> "$s:00"
                    else -> s
                }
            }
        return try {
            Instant.parse(normalized).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (_: Exception) {
                    defaultMillis
                }
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
        val pageSize = 1000
        var offset = 0
        val maxPages = 25
        var page = 0
        try {
            while (page < maxPages) {
                val conn = openConnection(
                    "post_click_counts?select=post_id,count&order=count.desc,post_id.asc&limit=$pageSize&offset=$offset",
                    "GET"
                )
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val arr = JSONArray(body)
                    val length = arr.length()
                    for (i in 0 until length) {
                        val obj = arr.getJSONObject(i)
                        val pid = obj.optString("post_id", "")
                        val count = obj.optInt("count", obj.optInt("clicks_count", 0))
                        if (pid.isNotBlank()) {
                            map[pid] = count
                        }
                    }
                    if (length < pageSize) break
                    offset += pageSize
                    page++
                } else {
                    conn.disconnect()
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPostClickCounts warning", e)
        }
        return map
    }

    suspend fun fetchPosts(maxTotal: Int = 25000): Result<List<PostEntity>> = withContext(Dispatchers.IO) {
        try {
            val clickCountsMap = fetchPostClickCounts()
            val pageSize = 1000
            var offset = 0
            val postsById = LinkedHashMap<String, PostEntity>()

            while (postsById.size < maxTotal) {
                val endpoint = "posts?select=*&order=created_at.desc,id.desc&limit=$pageSize&offset=$offset"
                val conn = openConnection(endpoint, "GET")
                val code = conn.responseCode
                if (code in 200..299) {
                    val response = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val jsonArray = JSONArray(response)
                    val batchCount = jsonArray.length()
                    if (batchCount == 0) break

                    for (i in 0 until batchCount) {
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

                        postsById[id] = PostEntity(
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
                    }

                    if (batchCount < pageSize) break
                    offset += batchCount
                } else {
                    val errText = runCatching { conn.errorStream?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
                    conn.disconnect()
                    VersionGateManager.onPermissionOrRlsRejected(code, errText)
                    if (offset == 0) {
                        return@withContext Result.failure(Exception("HTTP $code: $errText"))
                    }
                    break
                }
            }

            Result.success(postsById.values.toList())
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

    private fun hasAffectedRows(responseBody: String?): Boolean {
        val trimmed = responseBody?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        return try {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed).length() > 0
                trimmed.startsWith("{") -> JSONObject(trimmed).length() > 0
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseTierAssignmentFromMessage(
        emailRaw: String,
        userIdRaw: String?,
        messageRaw: String,
        statusRaw: String,
        downloadLinkRaw: String?,
        createdAtStr: String
    ): Triple<String, String, SubscriptionDetails>? {
        val msg = messageRaw.trim()
        val isExplicitTierAssignment = msg.startsWith("[TIER_ASSIGNMENT]")
        val isVerifiedPaymentProof = msg.startsWith("[PAYMENT_PROOF]") &&
                statusRaw.trim().lowercase() in setOf("fulfilled", "verified", "approved")

        if (!isExplicitTierAssignment && !isVerifiedPaymentProof) return null

        val nowMillis = System.currentTimeMillis()
        val oneMonthMillis = 31L * 24L * 60L * 60L * 1000L
        val createdMillis = parseIsoToMillis(createdAtStr, nowMillis)

        val tierMatch = Regex("""Tier:\s*(Legendary|Pro|Free)""", RegexOption.IGNORE_CASE)
            .find(msg)?.groupValues?.getOrNull(1)
            ?: downloadLinkRaw?.takeIf {
                it.equals("Legendary", ignoreCase = true) ||
                        it.equals("Pro", ignoreCase = true) ||
                        it.equals("Free", ignoreCase = true)
            }
            ?: return null

        val normalizedTier = when {
            tierMatch.equals("legendary", ignoreCase = true) -> "Legendary"
            tierMatch.equals("pro", ignoreCase = true) -> "Pro"
            else -> "Free"
        }

        val expiresAtRaw = Regex("""ExpiresAt:\s*([^\s|]+)""", RegexOption.IGNORE_CASE)
            .find(msg)?.groupValues?.getOrNull(1).orEmpty()

        val targetIdFromMsg = Regex("""TargetId:\s*([0-9a-fA-F-]{36})""")
            .find(msg)?.groupValues?.getOrNull(1).orEmpty()

        val emailFromMsg = Regex("""Email:\s*([^\s|]+)""", RegexOption.IGNORE_CASE)
            .find(msg)?.groupValues?.getOrNull(1).orEmpty()

        val cleanEmail = (emailRaw.ifBlank { emailFromMsg }).trim().lowercase()
        val resolvedTargetId = when {
            targetIdFromMsg.isNotBlank() -> targetIdFromMsg
            !userIdRaw.isNullOrBlank() && userIdRaw.matches(Regex("^[0-9a-fA-F-]{36}$")) -> userIdRaw
            cleanEmail.isNotBlank() -> UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()
            else -> ""
        }

        val details = when (normalizedTier) {
            "Legendary" -> SubscriptionDetails("Legendary", Long.MAX_VALUE)
            "Pro" -> {
                val expMillis = if (expiresAtRaw.isNotBlank() && !expiresAtRaw.equals("none", ignoreCase = true) && !expiresAtRaw.equals("null", ignoreCase = true)) {
                    parseIsoToMillis(expiresAtRaw, createdMillis + oneMonthMillis)
                } else {
                    createdMillis + oneMonthMillis
                }
                if (expMillis < nowMillis) {
                    SubscriptionDetails("Free", 0L)
                } else {
                    SubscriptionDetails("Pro", expMillis)
                }
            }
            else -> SubscriptionDetails("Free", 0L)
        }

        return Triple(cleanEmail, resolvedTargetId, details)
    }

    data class DeviceBindingRecord(
        val deviceId: String,
        val email: String,
        val userId: String,
        val username: String,
        val authMethod: String,
        val tier: String
    )

    suspend fun saveDeviceBindingToSupabase(
        deviceId: String,
        email: String,
        userId: String,
        username: String = "",
        authMethod: String = "google",
        tier: String = "Free"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (deviceId.isBlank() || email.isBlank()) return@withContext Result.success(false)
        try {
            val cleanEmail = email.trim().lowercase()
            val cleanUser = username.trim().ifBlank { cleanEmail.substringBefore("@") }
            val normalizedTier = when {
                tier.equals("legendary", ignoreCase = true) -> "Legendary"
                tier.equals("pro", ignoreCase = true) -> "Pro"
                else -> "Free"
            }
            val nowIso = Instant.now().toString()
            val desc = "[DEVICE_BINDING] DeviceId: $deviceId | Email: $cleanEmail | UserId: $userId | Username: $cleanUser | AuthMethod: $authMethod | Tier: $normalizedTier"

            var existingId: String? = null
            val checkConn = openConnection(
                "upload_requests?select=id&host=eq.device_binding&title=eq.$deviceId&order=updated_at.desc&limit=1",
                "GET"
            )
            if (checkConn.responseCode in 200..299) {
                val arr = JSONArray(checkConn.inputStream.bufferedReader().readText())
                checkConn.disconnect()
                if (arr.length() > 0) {
                    existingId = arr.getJSONObject(0).optString("id", "").takeIf { it.isNotBlank() }
                }
            } else {
                checkConn.disconnect()
            }

            if (existingId != null) {
                val patchObj = JSONObject().apply {
                    put("host", "device_binding")
                    put("status", "active")
                    put("title", deviceId)
                    put("caption", cleanEmail)
                    put("description", desc)
                    put("updated_at", nowIso)
                }
                val res = executePatch("upload_requests?id=eq.$existingId", patchObj.toString(), null)
                if (res.isSuccess && hasAffectedRows(res.getOrNull())) {
                    return@withContext Result.success(true)
                }
            }

            val postObj = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("host", "device_binding")
                put("status", "active")
                put("title", deviceId)
                put("caption", cleanEmail)
                put("description", desc)
                put("created_at", nowIso)
                put("updated_at", nowIso)
            }
            val postRes = executePost("upload_requests", postObj.toString(), null)
            Result.success(postRes.isSuccess && hasAffectedRows(postRes.getOrNull()))
        } catch (e: Exception) {
            Log.w(TAG, "saveDeviceBindingToSupabase notice", e)
            Result.failure(e)
        }
    }

    suspend fun clearDeviceBindingInSupabase(deviceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (deviceId.isBlank()) return@withContext Result.success(false)
        try {
            val patchObj = JSONObject().apply {
                put("status", "signed_out")
                put("updated_at", Instant.now().toString())
            }
            val res = executePatch("upload_requests?host=eq.device_binding&title=eq.$deviceId", patchObj.toString(), null)
            Result.success(res.isSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDeviceBindingFromSupabase(deviceId: String): DeviceBindingRecord? = withContext(Dispatchers.IO) {
        if (deviceId.isBlank()) return@withContext null
        try {
            val conn = openConnection(
                "upload_requests?select=id,status,title,caption,description,updated_at&host=eq.device_binding&title=eq.$deviceId&order=updated_at.desc&limit=1",
                "GET"
            )
            if (conn.responseCode in 200..299) {
                val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val status = obj.optString("status", "")
                    if (status.equals("signed_out", ignoreCase = true) || status.equals("done", ignoreCase = true)) {
                        return@withContext null
                    }
                    val desc = obj.optString("description", "")
                    if (!desc.startsWith("[DEVICE_BINDING]")) return@withContext null
                    val email = (obj.optString("caption", "").ifBlank {
                        Regex("""Email:\s*([^\s|]+)""").find(desc)?.groupValues?.getOrNull(1).orEmpty()
                    }).trim().lowercase()
                    if (email.isBlank()) return@withContext null
                    val uid = Regex("""UserId:\s*([^\s|]+)""").find(desc)?.groupValues?.getOrNull(1)
                        ?.takeIf { it.isNotBlank() && it != "guest_user" }
                        ?: UUID.nameUUIDFromBytes("justfan:$email".toByteArray()).toString()
                    val uname = Regex("""Username:\s*([^|]+)""").find(desc)?.groupValues?.getOrNull(1)?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: email.substringBefore("@")
                    val method = Regex("""AuthMethod:\s*([^\s|]+)""").find(desc)?.groupValues?.getOrNull(1)?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "google"
                    val tierVal = Regex("""Tier:\s*(Legendary|Pro|Free)""", RegexOption.IGNORE_CASE)
                        .find(desc)?.groupValues?.getOrNull(1)?.trim()
                        ?: "Free"
                    return@withContext DeviceBindingRecord(
                        deviceId = deviceId,
                        email = email,
                        userId = uid,
                        username = uname,
                        authMethod = method,
                        tier = when {
                            tierVal.equals("legendary", ignoreCase = true) -> "Legendary"
                            tierVal.equals("pro", ignoreCase = true) -> "Pro"
                            else -> "Free"
                        }
                    )
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchDeviceBindingFromSupabase notice", e)
        }
        null
    }

    /**
     * Reads remote tier assignments from:
     * 1. `upload_requests` (`[TIER_ASSIGNMENT]` and verified `[PAYMENT_PROOF]` rows) ordered newest-first by `updated_at.desc`
     * 2. `RequestfromApp` (`[TIER_ASSIGNMENT]` and verified `[PAYMENT_PROOF]` rows) ordered newest-first by `created_at.desc`
     * 3. `app_secrets` (`user_tier:<email>`)
     * Returns a map keyed by lowercase email, user UUID, AND `device:<deviceId>` so lookups succeed immediately after reinstall.
     */
    suspend fun fetchTierAssignmentsMap(): Map<String, SubscriptionDetails> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, SubscriptionDetails>()

        // 1. Primary remote ledger in `upload_requests` (supports unauthenticated & admin reads/inserts/updates)
        try {
            val upConn = openConnection(
                "upload_requests?select=id,user_id,host,status,title,caption,description,created_at,updated_at&order=updated_at.desc&limit=500",
                "GET"
            )
            if (upConn.responseCode in 200..299) {
                val upBody = upConn.inputStream.bufferedReader().readText()
                upConn.disconnect()
                val arr = JSONArray(upBody)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val descRaw = obj.optString("description", "")
                    if (!descRaw.startsWith("[TIER_ASSIGNMENT]") && !descRaw.startsWith("[PAYMENT_PROOF]")) {
                        continue
                    }
                    val titleEmail = obj.optString("title", "")
                    val userIdRaw = if (obj.has("user_id") && !obj.isNull("user_id")) obj.optString("user_id") else null
                    val statusRaw = obj.optString("status", "done")
                    val captionTier = if (obj.has("caption") && !obj.isNull("caption")) obj.optString("caption") else null
                    val timestampStr = obj.optString("updated_at", obj.optString("created_at", ""))

                    val parsed = parseTierAssignmentFromMessage(
                        emailRaw = titleEmail,
                        userIdRaw = userIdRaw,
                        messageRaw = descRaw,
                        statusRaw = statusRaw,
                        downloadLinkRaw = captionTier,
                        createdAtStr = timestampStr
                    ) ?: continue

                    val (cleanEmail, targetId, details) = parsed
                    if (cleanEmail.isNotBlank() && !result.containsKey(cleanEmail)) {
                        result[cleanEmail] = details
                        val detUuid = UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()
                        result.putIfAbsent(detUuid, details)
                    }
                    if (targetId.isNotBlank() && !result.containsKey(targetId)) {
                        result[targetId] = details
                    }
                    val devMatch = Regex("""DeviceId:\s*([^\s|]+)""").find(descRaw)?.groupValues?.getOrNull(1).orEmpty()
                    if (devMatch.isNotBlank() && devMatch != "none" && devMatch != "null") {
                        result.putIfAbsent("device:$devMatch", details)
                    }
                }
            } else {
                upConn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchTierAssignmentsMap upload_requests notice", e)
        }

        // 2. Secondary check in `RequestfromApp`
        try {
            val conn = openConnection(
                "RequestfromApp?select=id,user_id,name,email,message,status,download_link,created_at&order=created_at.desc&limit=500",
                "GET"
            )
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val emailRaw = obj.optString("email", "")
                    val userIdRaw = if (obj.has("user_id") && !obj.isNull("user_id")) obj.optString("user_id") else null
                    val msgRaw = obj.optString("message", "")
                    val statusRaw = obj.optString("status", "pending")
                    val dlRaw = if (obj.has("download_link") && !obj.isNull("download_link")) obj.optString("download_link") else null
                    val createdStr = obj.optString("created_at", "")

                    val parsed = parseTierAssignmentFromMessage(
                        emailRaw = emailRaw,
                        userIdRaw = userIdRaw,
                        messageRaw = msgRaw,
                        statusRaw = statusRaw,
                        downloadLinkRaw = dlRaw,
                        createdAtStr = createdStr
                    ) ?: continue

                    val (cleanEmail, targetId, details) = parsed
                    if (cleanEmail.isNotBlank() && !result.containsKey(cleanEmail)) {
                        result[cleanEmail] = details
                        val detUuid = UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()
                        if (!result.containsKey(detUuid)) {
                            result[detUuid] = details
                        }
                    }
                    if (targetId.isNotBlank() && !result.containsKey(targetId)) {
                        result[targetId] = details
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchTierAssignmentsMap RequestfromApp notice", e)
        }

        // 3. Also check `app_secrets` for any `user_tier:<email>` entries
        try {
            val secConn = openConnection("app_secrets?select=name,value&name=like.user_tier:*", "GET")
            if (secConn.responseCode in 200..299) {
                val secBody = secConn.inputStream.bufferedReader().readText()
                secConn.disconnect()
                val arr = JSONArray(secBody)
                val nowMillis = System.currentTimeMillis()
                val oneMonthMillis = 31L * 24L * 60L * 60L * 1000L
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val keyName = obj.optString("name", "")
                    val rawVal = obj.optString("value", "")
                    val emailKey = keyName.removePrefix("user_tier:").trim().lowercase()
                    if (emailKey.isBlank() || result.containsKey(emailKey)) continue
                    val parts = rawVal.split("|")
                    val tName = parts.getOrNull(0)?.trim().orEmpty()
                    val expIso = parts.getOrNull(1)?.trim().orEmpty()
                    val uidPart = parts.getOrNull(2)?.trim().orEmpty()
                    val details = when {
                        tName.equals("legendary", ignoreCase = true) -> SubscriptionDetails("Legendary", Long.MAX_VALUE)
                        tName.equals("pro", ignoreCase = true) -> {
                            val expMillis = if (expIso.isNotBlank() && expIso != "none") parseIsoToMillis(expIso, nowMillis + oneMonthMillis) else nowMillis + oneMonthMillis
                            if (expMillis < nowMillis) SubscriptionDetails("Free", 0L) else SubscriptionDetails("Pro", expMillis)
                        }
                        tName.equals("free", ignoreCase = true) -> SubscriptionDetails("Free", 0L)
                        else -> null
                    }
                    if (details != null) {
                        result[emailKey] = details
                        val detUuid = UUID.nameUUIDFromBytes("justfan:$emailKey".toByteArray()).toString()
                        result.putIfAbsent(detUuid, details)
                        if (uidPart.isNotBlank()) result.putIfAbsent(uidPart, details)
                    }
                }
            } else {
                secConn.disconnect()
            }
        } catch (_: Exception) {}

        result
    }

    suspend fun fetchRequests(): Result<List<RequestEntity>> = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<RequestEntity>()
            val seenIds = mutableSetOf<String>()

            val endpoint = "RequestfromApp?select=*&order=created_at.desc"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val userId = if (obj.has("user_id") && !obj.isNull("user_id")) obj.optString("user_id") else null
                    val name = if (obj.has("name") && !obj.isNull("name")) obj.optString("name") else null
                    val email = obj.optString("email", "")
                    val telegram = if (obj.has("telegram_username") && !obj.isNull("telegram_username")) obj.optString("telegram_username") else null
                    val msg = obj.optString("message", "")
                    // Exclude internal tier ledger records from the user/admin requests list
                    if (msg.startsWith("[TIER_ASSIGNMENT]") || name.equals("TIER_ASSIGNMENT", ignoreCase = true)) {
                        continue
                    }
                    val img = if (obj.has("image_url") && !obj.isNull("image_url")) obj.optString("image_url") else null
                    val status = obj.optString("status", "pending")
                    val dl = if (obj.has("download_link") && !obj.isNull("download_link")) obj.optString("download_link") else null
                    val rej = if (obj.has("rejection_reason") && !obj.isNull("rejection_reason")) obj.optString("rejection_reason") else null
                    val createdAtStr = obj.optString("created_at", "")

                    seenIds.add(id)
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
            } else {
                val errText = runCatching { conn.errorStream?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
                conn.disconnect()
                VersionGateManager.onPermissionOrRlsRejected(code, errText)
            }

            // Also merge fallback requests/payment proofs stored in `upload_requests` (`host = "app_request"`)
            try {
                val upReqConn = openConnection(
                    "upload_requests?select=id,user_id,host,status,title,caption,description,created_at,updated_at&host=eq.app_request&order=created_at.desc&limit=300",
                    "GET"
                )
                if (upReqConn.responseCode in 200..299) {
                    val upArr = JSONArray(upReqConn.inputStream.bufferedReader().readText())
                    upReqConn.disconnect()
                    for (i in 0 until upArr.length()) {
                        val obj = upArr.getJSONObject(i)
                        val id = obj.optString("id", "")
                        if (id.isBlank() || seenIds.contains(id)) continue
                        val status = obj.optString("status", "pending")
                        if (status.equals("deleted", ignoreCase = true)) continue
                        val rawDesc = obj.optString("description", "")
                        if (!rawDesc.startsWith("[APP_REQUEST]")) continue
                        val email = obj.optString("title", "").trim()
                        val name = obj.optString("caption", "Subscriber").takeIf { it.isNotBlank() && it != "null" }
                        val telegram = Regex("""Telegram:\s*([^|]*)""").find(rawDesc)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                        val imgUrl = Regex("""ImageUrl:\s*([^|]*)""").find(rawDesc)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                        val dlLink = Regex("""DownloadLink:\s*([^|]*)""").find(rawDesc)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                        val rejReason = Regex("""RejectionReason:\s*([^|]*)""").find(rawDesc)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                        val bodyMsg = rawDesc.substringAfter("Message:", "").trim()
                        val createdStr = obj.optString("created_at", "")
                        seenIds.add(id)
                        list.add(
                            RequestEntity(
                                id = id,
                                userId = if (obj.has("user_id") && !obj.isNull("user_id")) obj.optString("user_id") else null,
                                name = name,
                                email = email,
                                telegramUsername = telegram,
                                message = bodyMsg,
                                imageUrl = imgUrl,
                                status = status,
                                downloadLink = dlLink,
                                rejectionReason = rejReason,
                                createdAt = parseIsoToMillis(createdStr)
                            )
                        )
                    }
                } else {
                    val upCode = upReqConn.responseCode
                    val errText = runCatching { upReqConn.errorStream?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
                    upReqConn.disconnect()
                    VersionGateManager.onPermissionOrRlsRejected(upCode, errText)
                }
            } catch (_: Exception) {}

            Result.success(list.sortedByDescending { it.createdAt })
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
        val reqId = UUID.randomUUID().toString()
        val cleanEmail = email.trim().lowercase()
        fun buildPayload(includeUserId: Boolean): JSONObject {
            return JSONObject().apply {
                put("id", reqId)
                if (includeUserId && !userId.isNullOrBlank() && userId != "guest_user" && userId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                    put("user_id", userId)
                }
                put("name", name)
                put("email", cleanEmail)
                if (!telegram.isNullOrBlank()) put("telegram_username", telegram)
                put("message", message)
                if (!imageUrl.isNullOrBlank()) put("image_url", imageUrl)
                put("status", "pending")
                if (!deviceId.isNullOrBlank()) put("device_id", deviceId)
            }
        }

        var res = executePost("RequestfromApp", buildPayload(includeUserId = true).toString())
        if (res.isFailure) {
            // Retry without user_id in case userId is a synthetic Google Sign-In UUID not present in auth.users
            res = executePost("RequestfromApp", buildPayload(includeUserId = false).toString())
        }
        if (res.isSuccess && hasAffectedRows(res.getOrNull())) {
            return@withContext Result.success(true)
        }

        // Fallback to `upload_requests` (`host = "app_request"`) when `RequestfromApp` trigger requires a password JWT
        val nowIso = Instant.now().toString()
        val fallbackDesc = buildString {
            append("[APP_REQUEST] ")
            append("Telegram: ").append(telegram?.trim().orEmpty()).append(" | ")
            append("ImageUrl: ").append(imageUrl?.trim().orEmpty()).append(" | ")
            append("DeviceId: ").append(deviceId?.trim().orEmpty()).append(" | ")
            append("UserId: ").append(userId?.trim().orEmpty()).append(" | ")
            append("Message: ").append(message)
        }
        val upPayload = JSONObject().apply {
            put("id", reqId)
            put("host", "app_request")
            put("status", "pending")
            put("title", cleanEmail)
            put("caption", name.ifBlank { cleanEmail.substringBefore("@") })
            put("description", fallbackDesc)
            put("created_at", nowIso)
            put("updated_at", nowIso)
        }
        val upRes = executePost("upload_requests", upPayload.toString(), null)
        if (upRes.isSuccess && hasAffectedRows(upRes.getOrNull())) {
            Result.success(true)
        } else {
            Result.failure(res.exceptionOrNull() ?: upRes.exceptionOrNull() ?: Exception("CREATE_REQUEST_FAILED"))
        }
    }

    suspend fun updateRequestStatusInSupabase(id: String, status: String, link: String?, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("status", status)
            if (!link.isNullOrBlank()) put("download_link", link)
        }
        val res = executePatch("RequestfromApp?id=eq.$id", payload.toString(), accessToken)
        if (res.isSuccess && hasAffectedRows(res.getOrNull())) {
            return@withContext Result.success(true)
        }
        val upPayload = JSONObject().apply {
            put("status", status)
            put("updated_at", Instant.now().toString())
        }
        val upRes = executePatch("upload_requests?id=eq.$id", upPayload.toString(), accessToken)
        if (upRes.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("UPDATE_FAILED"))
    }

    suspend fun rejectRequestInSupabase(id: String, reason: String, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("status", "rejected")
            put("rejection_reason", reason)
        }
        val res = executePatch("RequestfromApp?id=eq.$id", payload.toString(), accessToken)
        if (res.isSuccess && hasAffectedRows(res.getOrNull())) {
            return@withContext Result.success(true)
        }
        val upPayload = JSONObject().apply {
            put("status", "rejected")
            put("updated_at", Instant.now().toString())
        }
        val upRes = executePatch("upload_requests?id=eq.$id", upPayload.toString(), accessToken)
        if (upRes.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("REJECT_FAILED"))
    }

    suspend fun deleteRequestFromSupabase(id: String, accessToken: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val delRes = executeDelete("RequestfromApp?id=eq.$id", accessToken)
        val upPatch = JSONObject().apply {
            put("status", "deleted")
            put("updated_at", Instant.now().toString())
        }
        executePatch("upload_requests?id=eq.$id", upPatch.toString(), accessToken)
        delRes
    }

    suspend fun fetchSubscriptions(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        try {
            val endpoint = "subscriptions?select=*"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode
            if (code in 200..299) {
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val array = JSONArray(text)
                val nowMillis = System.currentTimeMillis()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val uid = item.optString("user_id")
                    val rawPlan = item.optString("plan", "Free")
                    val plan = when {
                        rawPlan.equals("legendary", ignoreCase = true) -> "Legendary"
                        rawPlan.equals("pro", ignoreCase = true) -> "Pro"
                        else -> "Free"
                    }
                    val exp = item.optString("expires_at", "")
                    var isExpired = false
                    if (plan == "Pro" && exp.isNotBlank() && exp != "null") {
                        try {
                            if (parseIsoToMillis(exp) < nowMillis) isExpired = true
                        } catch (_: Exception) {}
                    }
                    if (uid.isNotBlank() && !isExpired && plan.isNotBlank()) {
                        map[uid] = plan
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchSubscriptions subscriptions table warning", e)
        }

        // Merge authoritative Admin tier assignments from foreign-key-free remote ledger
        try {
            val ledgerMap = fetchTierAssignmentsMap()
            for ((key, details) in ledgerMap) {
                if (key.isNotBlank() && details.tier.isNotBlank()) {
                    map[key] = details.tier
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchSubscriptions ledger merge warning", e)
        }

        Result.success(map)
    }

    data class SubscriptionDetails(
        val tier: String,
        val expiresAtMillis: Long
    )

    suspend fun fetchUserSubscriptionDetails(
        userId: String,
        email: String? = null,
        accessToken: String? = null,
        deviceId: String? = null
    ): Result<SubscriptionDetails?> = withContext(Dispatchers.IO) {
        try {
            var cleanEmail = email?.trim()?.lowercase()
                ?: if (userId.contains("@")) userId.trim().lowercase() else ""

            val candidateUuids = linkedSetOf<String>()
            if (userId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                candidateUuids.add(userId)
            }
            if (cleanEmail.isNotBlank()) {
                resolveUserUuid(cleanEmail)?.takeIf { it.isNotBlank() }?.let { candidateUuids.add(it) }
                val deterministicUuid = UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()
                candidateUuids.add(deterministicUuid)
            } else if (candidateUuids.isNotEmpty()) {
                resolveEmailFromUuid(candidateUuids.first())?.let { resolved ->
                    cleanEmail = resolved
                    val deterministicUuid = UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()
                    candidateUuids.add(deterministicUuid)
                }
            }

            val nowMillis = System.currentTimeMillis()
            val oneMonthMillis = 31L * 24L * 60L * 60L * 1000L

            // 1. Check authoritative Admin `[TIER_ASSIGNMENT]` & verified `[PAYMENT_PROOF]` ledger first
            try {
                val tierLedger = fetchTierAssignmentsMap()
                val ledgerMatch = (if (cleanEmail.isNotBlank()) tierLedger[cleanEmail] else null)
                    ?: candidateUuids.firstNotNullOfOrNull { tierLedger[it] }
                    ?: (if (!deviceId.isNullOrBlank()) tierLedger["device:$deviceId"] else null)

                if (ledgerMatch != null) {
                    return@withContext Result.success(ledgerMatch)
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchUserSubscriptionDetails ledger check notice", e)
            }

            var sawFreeInSubscriptions = false

            // 2. Check `subscriptions` table across all candidate UUIDs
            for (candidateId in candidateUuids) {
                val subEndpoint = "subscriptions?user_id=eq.$candidateId&order=created_at.desc&limit=1"
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
                                    Log.i(TAG, "Pro subscription expired for $candidateId; auto-downgrading to Free in Supabase")
                                    updateUserTier(candidateId, "Free", accessToken, cleanEmail, deviceId)
                                    return@withContext Result.success(SubscriptionDetails("Free", 0L))
                                } else {
                                    return@withContext Result.success(SubscriptionDetails("Pro", expMillis))
                                }
                            } else {
                                // Don't return early on "Free" until we also check profiles/users/metadata
                                sawFreeInSubscriptions = true
                            }
                        }
                    }
                }
            }

            // 3. Also check `users` and `profiles` tables for tier identification
            val userCheckEndpoints = mutableListOf<String>()
            for (candidateId in candidateUuids) {
                userCheckEndpoints.add("users?id=eq.$candidateId&limit=1")
                userCheckEndpoints.add("profiles?id=eq.$candidateId&limit=1")
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
                                        updateUserTier(candidateUuids.firstOrNull() ?: userId, "Free", accessToken, cleanEmail, deviceId)
                                        return@withContext Result.success(SubscriptionDetails("Free", 0L))
                                    }
                                    return@withContext Result.success(SubscriptionDetails("Pro", expMillis))
                                } else if (dbTier.equals("free", ignoreCase = true)) {
                                    sawFreeInSubscriptions = true
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 4. Check Supabase Auth user_metadata if accessToken is available
            if (!accessToken.isNullOrBlank()) {
                try {
                    val authReq = Request.Builder()
                        .url("$baseUrl/auth/v1/user")
                        .get()
                        .header("apikey", apiKey)
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                    okHttpClient.newCall(authReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyObj = JSONObject(resp.body?.string().orEmpty())
                            val meta = bodyObj.optJSONObject("user_metadata")
                            val metaTier = meta?.optString("tier", "")?.trim().orEmpty()
                            if (metaTier.equals("legendary", ignoreCase = true)) {
                                return@withContext Result.success(SubscriptionDetails("Legendary", Long.MAX_VALUE))
                            } else if (metaTier.equals("pro", ignoreCase = true)) {
                                return@withContext Result.success(SubscriptionDetails("Pro", nowMillis + oneMonthMillis))
                            } else if (metaTier.equals("free", ignoreCase = true)) {
                                sawFreeInSubscriptions = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (evaluateIsAdmin(cleanEmail) || userId == "a2d7bbad-c31f-4679-bf51-2d2684bb3216") {
                return@withContext Result.success(SubscriptionDetails("Legendary", Long.MAX_VALUE))
            }

            if (sawFreeInSubscriptions) {
                return@withContext Result.success(SubscriptionDetails("Free", 0L))
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

    suspend fun resolveEmailFromUuid(targetUuid: String): String? = withContext(Dispatchers.IO) {
        if (targetUuid.contains("@")) return@withContext targetUuid.trim().lowercase()
        try {
            val profReq = Request.Builder()
                .url("$baseUrl/rest/v1/profiles?id=eq.$targetUuid&limit=1")
                .get()
                .header("apikey", apiKey)
                .header("Authorization", "Bearer $apiKey")
                .build()
            okHttpClient.newCall(profReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val em = obj.optString("email", obj.optString("username", "")).trim().lowercase()
                        if (em.isNotBlank()) return@withContext em
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val upConn = openConnection("upload_requests?select=title,caption,description&description=ilike.*$targetUuid*&order=updated_at.desc&limit=1", "GET")
            if (upConn.responseCode in 200..299) {
                val arr = JSONArray(upConn.inputStream.bufferedReader().readText())
                upConn.disconnect()
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val titleVal = obj.optString("title", "").trim().lowercase()
                    if (titleVal.contains("@")) return@withContext titleVal
                    val capVal = obj.optString("caption", "").trim().lowercase()
                    if (capVal.contains("@")) return@withContext capVal
                    val descVal = obj.optString("description", "")
                    val emMatch = Regex("""Email:\s*([^\s|]+)""").find(descVal)?.groupValues?.getOrNull(1)?.trim()?.lowercase()
                    if (!emMatch.isNullOrBlank() && emMatch.contains("@")) return@withContext emMatch
                }
            } else {
                upConn.disconnect()
            }
        } catch (_: Exception) {}

        try {
            val reqConn = openConnection("RequestfromApp?select=email&user_id=eq.$targetUuid&limit=1", "GET")
            if (reqConn.responseCode in 200..299) {
                val arr = JSONArray(reqConn.inputStream.bufferedReader().readText())
                reqConn.disconnect()
                if (arr.length() > 0) {
                    val em = arr.getJSONObject(0).optString("email", "").trim().lowercase()
                    if (em.isNotBlank()) return@withContext em
                }
            } else {
                reqConn.disconnect()
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun resolveUserUuid(userIdentifier: String): String? = withContext(Dispatchers.IO) {
        if (!userIdentifier.contains("@")) return@withContext userIdentifier
        val clean = userIdentifier.trim().lowercase()
        try {
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
                        val id = arr.getJSONObject(0).optString("id")
                        if (id.isNotBlank()) return@withContext id
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveUserUuid profiles warning", e)
        }

        // Check `upload_requests` tier ledger / device bindings
        try {
            val upConn = openConnection("upload_requests?select=description&or=(title.eq.$clean,caption.eq.$clean)&order=updated_at.desc&limit=1", "GET")
            if (upConn.responseCode in 200..299) {
                val arr = JSONArray(upConn.inputStream.bufferedReader().readText())
                upConn.disconnect()
                if (arr.length() > 0) {
                    val desc = arr.getJSONObject(0).optString("description", "")
                    val uid = (Regex("""TargetId:\s*([0-9a-fA-F-]{36})""").find(desc)?.groupValues?.getOrNull(1)
                        ?: Regex("""UserId:\s*([0-9a-fA-F-]{36})""").find(desc)?.groupValues?.getOrNull(1)).orEmpty()
                    if (uid.matches(Regex("^[0-9a-fA-F-]{36}$"))) return@withContext uid
                }
            } else {
                upConn.disconnect()
            }
        } catch (_: Exception) {}

        // Also check RequestfromApp in case the user submitted a request with their auth user_id
        try {
            val reqConn = openConnection("RequestfromApp?select=user_id&email=eq.$clean&user_id=not.is.null&limit=1", "GET")
            if (reqConn.responseCode in 200..299) {
                val arr = JSONArray(reqConn.inputStream.bufferedReader().readText())
                reqConn.disconnect()
                if (arr.length() > 0) {
                    val uid = arr.getJSONObject(0).optString("user_id", "")
                    if (uid.matches(Regex("^[0-9a-fA-F-]{36}$"))) return@withContext uid
                }
            } else {
                reqConn.disconnect()
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun updateUserTier(
        userIdOrEmail: String,
        tier: String,
        accessToken: String? = null,
        emailHint: String? = null,
        deviceId: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val normalizedTier = when {
                tier.equals("legendary", ignoreCase = true) -> "Legendary"
                tier.equals("pro", ignoreCase = true) -> "Pro"
                else -> "Free"
            }
            val lowerTier = normalizedTier.lowercase()

            var cleanEmail = (emailHint ?: if (userIdOrEmail.contains("@")) userIdOrEmail else "").trim().lowercase()

            val candidateUuids = linkedSetOf<String>()
            if (userIdOrEmail.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                candidateUuids.add(userIdOrEmail)
            }
            if (cleanEmail.isBlank() && candidateUuids.isNotEmpty()) {
                resolveEmailFromUuid(candidateUuids.first())?.let { cleanEmail = it }
            }
            if (cleanEmail.isNotBlank()) {
                resolveUserUuid(cleanEmail)?.takeIf { it.matches(Regex("^[0-9a-fA-F-]{36}$")) }?.let {
                    candidateUuids.add(it)
                }
                candidateUuids.add(UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString())
            }

            val now = Instant.now()
            val nowIso = now.toString()
            val expiresAtStr = when (lowerTier) {
                "pro" -> now.plus(31, java.time.temporal.ChronoUnit.DAYS).toString()
                "legendary" -> Instant.parse("2099-12-31T23:59:59Z").toString()
                else -> null
            }

            val primaryUuid = candidateUuids.firstOrNull()
                ?: if (cleanEmail.isNotBlank()) UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()
                else UUID.randomUUID().toString()
            val effectiveEmail = cleanEmail.ifBlank { "${primaryUuid.take(8)}@user.justfan" }
            val structuredLedgerMsg = buildString {
                append("[TIER_ASSIGNMENT] Tier: ").append(normalizedTier)
                append(" | ExpiresAt: ").append(expiresAtStr ?: "none")
                append(" | TargetId: ").append(primaryUuid)
                append(" | Email: ").append(effectiveEmail)
                if (!deviceId.isNullOrBlank()) {
                    append(" | DeviceId: ").append(deviceId)
                }
            }

            var persistedToRemote = false

            // 1. Primary Remote Tier Ledger in `upload_requests` (100% RLS-verified for GET, POST, and PATCH)
            try {
                var existingUploadLedgerId: String? = null
                val checkUpConn = openConnection(
                    "upload_requests?select=id,title,description&title=eq.$effectiveEmail&order=updated_at.desc&limit=20",
                    "GET"
                )
                if (checkUpConn.responseCode in 200..299) {
                    val arr = JSONArray(checkUpConn.inputStream.bufferedReader().readText())
                    checkUpConn.disconnect()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val d = obj.optString("description", "")
                        if (d.startsWith("[TIER_ASSIGNMENT]")) {
                            existingUploadLedgerId = obj.optString("id", "").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                } else {
                    checkUpConn.disconnect()
                }

                if (existingUploadLedgerId == null) {
                    val byIdConn = openConnection(
                        "upload_requests?select=id,title,description&description=ilike.*$primaryUuid*&order=updated_at.desc&limit=20",
                        "GET"
                    )
                    if (byIdConn.responseCode in 200..299) {
                        val arr = JSONArray(byIdConn.inputStream.bufferedReader().readText())
                        byIdConn.disconnect()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val d = obj.optString("description", "")
                            if (d.startsWith("[TIER_ASSIGNMENT]")) {
                                existingUploadLedgerId = obj.optString("id", "").takeIf { it.isNotBlank() }
                                break
                            }
                        }
                    } else {
                        byIdConn.disconnect()
                    }
                }

                var updatedUploadLedger = false
                if (existingUploadLedgerId != null) {
                    val patchUpPayload = JSONObject().apply {
                        put("host", "tier_ledger")
                        put("status", "done")
                        put("title", effectiveEmail)
                        put("caption", normalizedTier)
                        put("description", structuredLedgerMsg)
                        put("updated_at", nowIso)
                    }
                    val patchUpRes = executePatch("upload_requests?id=eq.$existingUploadLedgerId", patchUpPayload.toString(), null)
                    if (patchUpRes.isSuccess && hasAffectedRows(patchUpRes.getOrNull())) {
                        updatedUploadLedger = true
                        persistedToRemote = true
                        Log.i(TAG, "updateUserTier updated upload_requests ledger row $existingUploadLedgerId for $effectiveEmail -> $normalizedTier")
                    }
                }

                if (!updatedUploadLedger) {
                    val postUpPayload = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("host", "tier_ledger")
                        put("status", "done")
                        put("title", effectiveEmail)
                        put("caption", normalizedTier)
                        put("description", structuredLedgerMsg)
                        put("created_at", nowIso)
                        put("updated_at", nowIso)
                    }
                    val postUpRes = executePost("upload_requests", postUpPayload.toString(), null)
                    if (postUpRes.isSuccess && hasAffectedRows(postUpRes.getOrNull())) {
                        persistedToRemote = true
                        Log.i(TAG, "updateUserTier inserted upload_requests ledger row for $effectiveEmail -> $normalizedTier")
                    }
                }

                // Also update any existing `[DEVICE_BINDING]` rows for this email in `upload_requests` so reinstalls restore the new tier immediately
                val bindConn = openConnection(
                    "upload_requests?select=id,title,caption,description&host=eq.device_binding&caption=eq.$effectiveEmail&limit=10",
                    "GET"
                )
                if (bindConn.responseCode in 200..299) {
                    val bindArr = JSONArray(bindConn.inputStream.bufferedReader().readText())
                    bindConn.disconnect()
                    for (i in 0 until bindArr.length()) {
                        val bObj = bindArr.getJSONObject(i)
                        val bId = bObj.optString("id", "")
                        val oldDesc = bObj.optString("description", "")
                        if (bId.isNotBlank() && oldDesc.startsWith("[DEVICE_BINDING]")) {
                            val newDesc = if (oldDesc.contains("Tier:")) {
                                oldDesc.replace(Regex("""Tier:\s*(Legendary|Pro|Free)""", RegexOption.IGNORE_CASE), "Tier: $normalizedTier")
                            } else {
                                "$oldDesc | Tier: $normalizedTier"
                            }
                            val patchBind = JSONObject().apply {
                                put("description", newDesc)
                                put("updated_at", nowIso)
                            }
                            executePatch("upload_requests?id=eq.$bId", patchBind.toString(), null)
                        }
                    }
                } else {
                    bindConn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "updateUserTier upload_requests ledger notice", e)
            }

            // 2. Also attempt persisting to `subscriptions` and `profiles` tables (when user JWT permits)
            for (targetUuid in candidateUuids) {
                val checkUrl = "$baseUrl/rest/v1/subscriptions?user_id=eq.$targetUuid&limit=1"
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

                suspend fun writeSubPayload(planValue: String, includeExpiresAt: Boolean, tokenToUse: String?): Boolean {
                    val payload = JSONObject().apply {
                        put("user_id", targetUuid)
                        put("plan", planValue)
                        if (includeExpiresAt) {
                            if (expiresAtStr != null) put("expires_at", expiresAtStr) else put("expires_at", JSONObject.NULL)
                        }
                    }
                    if (exists) {
                        val patchRes = executePatch("subscriptions?user_id=eq.$targetUuid", payload.toString(), tokenToUse)
                        if (patchRes.isSuccess && hasAffectedRows(patchRes.getOrNull())) {
                            return true
                        }
                    }
                    payload.put("id", UUID.randomUUID().toString())
                    payload.put("created_at", nowIso)
                    val postRes = executePost("subscriptions", payload.toString(), tokenToUse)
                    return postRes.isSuccess && hasAffectedRows(postRes.getOrNull())
                }

                var subWrote = writeSubPayload(normalizedTier, includeExpiresAt = true, tokenToUse = accessToken)
                if (!subWrote) {
                    subWrote = writeSubPayload(normalizedTier, includeExpiresAt = false, tokenToUse = accessToken)
                }
                if (!subWrote) {
                    subWrote = writeSubPayload(lowerTier, includeExpiresAt = false, tokenToUse = accessToken)
                }
                if (subWrote) {
                    persistedToRemote = true
                    Log.i(TAG, "updateUserTier verified row write in subscriptions table for $targetUuid -> $normalizedTier")
                }
            }

            // 3. Also attempt `RequestfromApp` ledger update if an authenticated token is present
            try {
                var updatedExistingLedger = false
                val checkLedgerConn = openConnection(
                    "RequestfromApp?select=id&email=eq.$effectiveEmail&message=like.%5BTIER_ASSIGNMENT%5D*&order=created_at.desc&limit=1",
                    "GET"
                )
                var existingLedgerId: String? = null
                if (checkLedgerConn.responseCode in 200..299) {
                    val arr = JSONArray(checkLedgerConn.inputStream.bufferedReader().readText())
                    checkLedgerConn.disconnect()
                    if (arr.length() > 0) {
                        existingLedgerId = arr.getJSONObject(0).optString("id", "").takeIf { it.isNotBlank() }
                    }
                } else {
                    checkLedgerConn.disconnect()
                }

                if (existingLedgerId != null) {
                    val patchLedgerPayload = JSONObject().apply {
                        put("name", "TIER_ASSIGNMENT")
                        put("email", effectiveEmail)
                        put("message", structuredLedgerMsg)
                        put("download_link", normalizedTier)
                    }
                    val patchRes = executePatch("RequestfromApp?id=eq.$existingLedgerId", patchLedgerPayload.toString(), accessToken)
                    if (patchRes.isSuccess && hasAffectedRows(patchRes.getOrNull())) {
                        updatedExistingLedger = true
                        persistedToRemote = true
                    }
                }

                if (!updatedExistingLedger && !accessToken.isNullOrBlank() && accessToken != apiKey) {
                    val newLedgerPayload = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("name", "TIER_ASSIGNMENT")
                        put("email", effectiveEmail)
                        put("message", structuredLedgerMsg)
                        put("status", "pending")
                        put("download_link", normalizedTier)
                    }
                    val postLedgerRes = executePost("RequestfromApp", newLedgerPayload.toString(), accessToken)
                    if (postLedgerRes.isSuccess && hasAffectedRows(postLedgerRes.getOrNull())) {
                        persistedToRemote = true
                    }
                }
            } catch (_: Exception) {}

            // 4. Also persist tier inside Supabase Auth user_metadata when updating own active session
            if (!accessToken.isNullOrBlank() && accessToken != apiKey) {
                try {
                    val (jwtUid, jwtEmail, _) = extractUserFromJwt(accessToken)
                    if (jwtEmail.equals(effectiveEmail, ignoreCase = true) || (jwtUid != null && candidateUuids.contains(jwtUid))) {
                        val metaPayload = JSONObject().apply {
                            put("data", JSONObject().apply {
                                put("tier", normalizedTier)
                                if (expiresAtStr != null) put("expires_at", expiresAtStr)
                            })
                        }
                        val req = Request.Builder()
                            .url("$baseUrl/auth/v1/user")
                            .put(metaPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .header("apikey", apiKey)
                            .header("Authorization", "Bearer $accessToken")
                            .build()
                        okHttpClient.newCall(req).execute().close()
                    }
                } catch (_: Exception) {}
            }

            if (persistedToRemote) {
                Result.success(true)
            } else {
                Result.failure(Exception("Could not verify remote database row update for $effectiveEmail"))
            }
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
            val subscriptionsMap = fetchSubscriptions().getOrDefault(emptyMap())
            val tierLedgerMap = fetchTierAssignmentsMap()
            val usersByKey = linkedMapOf<String, UserEntity>()

            fun normalizeTierLabel(raw: String?): String? {
                if (raw.isNullOrBlank()) return null
                return when {
                    raw.equals("legendary", ignoreCase = true) -> "Legendary"
                    raw.equals("pro", ignoreCase = true) -> "Pro"
                    raw.equals("free", ignoreCase = true) -> "Free"
                    else -> null
                }
            }

            fun resolveEffectiveUserTier(uid: String, email: String, profileTier: String?, isAdmin: Boolean): String {
                val detUuid = if (email.isNotBlank()) UUID.nameUUIDFromBytes("justfan:$email".toByteArray()).toString() else ""
                // 1. Authoritative Admin tier assignment ledger takes highest priority
                val ledgerTier = tierLedgerMap[email]?.tier
                    ?: tierLedgerMap[uid]?.tier
                    ?: (if (detUuid.isNotBlank()) tierLedgerMap[detUuid]?.tier else null)
                if (!ledgerTier.isNullOrBlank()) return ledgerTier

                // 2. Subscriptions table (prefer non-Free if profileTier has an upgrade)
                val subTier = subscriptionsMap[uid]
                    ?: subscriptionsMap[email]
                    ?: (if (detUuid.isNotBlank()) subscriptionsMap[detUuid] else null)
                if (subTier != null && !subTier.equals("Free", ignoreCase = true)) return subTier
                if (profileTier != null && !profileTier.equals("Free", ignoreCase = true)) return profileTier
                if (subTier != null) return subTier
                if (profileTier != null) return profileTier
                return if (isAdmin) "Legendary" else "Free"
            }

            // 1. Fetch from `profiles` table (without requiring `created_at` column)
            try {
                val conn = openConnection("profiles?select=*&limit=$limit", "GET")
                if (conn.responseCode in 200..299) {
                    val response = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val jsonArray = JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optString("id", UUID.randomUUID().toString())
                        val rawUsername = if (obj.has("username") && !obj.isNull("username")) obj.optString("username") else null
                        val rawEmail = if (obj.has("email") && !obj.isNull("email")) obj.optString("email") else null
                        val email = (rawEmail?.takeIf { it.isNotBlank() } ?: rawUsername?.takeIf { it.isNotBlank() } ?: "${id.take(8)}@user.justfan").trim().lowercase()
                        val username = if (email.contains("@")) email.substringBefore("@") else email
                        val isAdmin = email.equals("reytherapper12@gmail.com", ignoreCase = true) || id == "a2d7bbad-c31f-4679-bf51-2d2684bb3216" || id == "fe335770-80a9-4125-9c15-d47f385579fb"
                        val createdAtStr = obj.optString("created_at", obj.optString("updated_at", ""))
                        val profileTier = normalizeTierLabel(obj.optString("tier", ""))
                        val finalTier = resolveEffectiveUserTier(id, email, profileTier, isAdmin)

                        usersByKey[email] = UserEntity(
                            id = id,
                            username = username,
                            email = email,
                            tier = finalTier,
                            requestsCount = 0,
                            isAdmin = isAdmin,
                            status = obj.optString("status", "Active").ifBlank { "Active" },
                            joinedAt = parseIsoToMillis(createdAtStr)
                        )
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchProfiles profiles table warning", e)
            }

            // 2. Merge users from `upload_requests` (`[TIER_ASSIGNMENT]`, `[DEVICE_BINDING]`, `[APP_REQUEST]`)
            try {
                val upConn = openConnection("upload_requests?select=id,user_id,host,status,title,caption,description,created_at,updated_at&order=updated_at.desc&limit=$limit", "GET")
                if (upConn.responseCode in 200..299) {
                    val upArr = JSONArray(upConn.inputStream.bufferedReader().readText())
                    upConn.disconnect()
                    for (i in 0 until upArr.length()) {
                        val obj = upArr.getJSONObject(i)
                        val desc = obj.optString("description", "")
                        if (!desc.startsWith("[TIER_ASSIGNMENT]") && !desc.startsWith("[DEVICE_BINDING]") && !desc.startsWith("[APP_REQUEST]")) {
                            continue
                        }
                        val isTierAssignment = desc.startsWith("[TIER_ASSIGNMENT]")
                        val isDeviceBinding = desc.startsWith("[DEVICE_BINDING]")
                        val isAppRequest = desc.startsWith("[APP_REQUEST]")
                        val emailCandidate = when {
                            isDeviceBinding -> obj.optString("caption", "")
                            else -> obj.optString("title", "")
                        }.ifBlank {
                            Regex("""Email:\s*([^\s|]+)""").find(desc)?.groupValues?.getOrNull(1).orEmpty()
                        }.trim().lowercase()

                        if (emailCandidate.isBlank() || emailCandidate == "null") continue

                        val existing = usersByKey[emailCandidate]
                        if (existing != null) {
                            val updatedTier = resolveEffectiveUserTier(existing.id, emailCandidate, existing.tier, existing.isAdmin)
                            val updatedCount = if (isAppRequest) existing.requestsCount + 1 else existing.requestsCount
                            usersByKey[emailCandidate] = existing.copy(
                                tier = updatedTier,
                                requestsCount = updatedCount
                            )
                        } else {
                            val targetUuid = Regex("""TargetId:\s*([0-9a-fA-F-]{36})""").find(desc)?.groupValues?.getOrNull(1)
                                ?: Regex("""UserId:\s*([0-9a-fA-F-]{36})""").find(desc)?.groupValues?.getOrNull(1)
                                ?: obj.optString("user_id", "").takeIf { it.matches(Regex("^[0-9a-fA-F-]{36}$")) }
                                ?: UUID.nameUUIDFromBytes("justfan:$emailCandidate".toByteArray()).toString()
                            val isAdmin = emailCandidate.equals("reytherapper12@gmail.com", ignoreCase = true)
                            val subTier = resolveEffectiveUserTier(targetUuid, emailCandidate, null, isAdmin)
                            val uname = when {
                                isDeviceBinding -> Regex("""Username:\s*([^|]+)""").find(desc)?.groupValues?.getOrNull(1)?.trim()
                                isAppRequest -> obj.optString("caption", "").trim().takeIf { it.isNotBlank() && it != "null" }
                                else -> null
                            }?.takeIf { it.isNotBlank() } ?: emailCandidate.substringBefore("@")

                            usersByKey[emailCandidate] = UserEntity(
                                id = targetUuid,
                                username = uname,
                                email = emailCandidate,
                                tier = subTier,
                                requestsCount = if (isAppRequest) 1 else 0,
                                isAdmin = isAdmin,
                                status = "Active",
                                joinedAt = parseIsoToMillis(obj.optString("updated_at", obj.optString("created_at", "")))
                            )
                        }
                    }
                } else {
                    upConn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchProfiles upload_requests merge notice", e)
            }

            // 3. Merge users from `user_devices` (captures Google OAuth / biometric logins even if `profiles` row wasn't created)
            try {
                val devConn = openConnection("user_devices?select=*&limit=$limit", "GET")
                if (devConn.responseCode in 200..299) {
                    val devArr = JSONArray(devConn.inputStream.bufferedReader().readText())
                    devConn.disconnect()
                    for (i in 0 until devArr.length()) {
                        val obj = devArr.getJSONObject(i)
                        val email = obj.optString("user_email", obj.optString("email", "")).trim().lowercase()
                        if (email.isBlank() || usersByKey.containsKey(email)) continue
                        val uid = obj.optString("user_id", "").takeIf { it.matches(Regex("^[0-9a-fA-F-]{36}$")) }
                            ?: UUID.nameUUIDFromBytes("justfan:$email".toByteArray()).toString()
                        val isAdmin = email.equals("reytherapper12@gmail.com", ignoreCase = true)
                        val subTier = resolveEffectiveUserTier(uid, email, null, isAdmin)
                        usersByKey[email] = UserEntity(
                            id = uid,
                            username = email.substringBefore("@"),
                            email = email,
                            tier = subTier,
                            requestsCount = 0,
                            isAdmin = isAdmin,
                            status = "Active",
                            joinedAt = parseIsoToMillis(obj.optString("updated_at", obj.optString("created_at", "")))
                        )
                    }
                } else {
                    devConn.disconnect()
                }
            } catch (_: Exception) {}

            // 4. Merge users from `RequestfromApp` (includes content requests, payment proofs, AND manual Admin tier upgrades)
            try {
                val reqConn = openConnection("RequestfromApp?select=user_id,email,name,message,status,download_link,created_at&order=created_at.desc&limit=$limit", "GET")
                if (reqConn.responseCode in 200..299) {
                    val reqArr = JSONArray(reqConn.inputStream.bufferedReader().readText())
                    reqConn.disconnect()
                    for (i in 0 until reqArr.length()) {
                        val obj = reqArr.getJSONObject(i)
                        val email = obj.optString("email", "").trim().lowercase()
                        if (email.isBlank()) continue
                        val msg = obj.optString("message", "")
                        val nameVal = obj.optString("name", "")
                        val isTierAssignment = msg.startsWith("[TIER_ASSIGNMENT]") || nameVal.equals("TIER_ASSIGNMENT", ignoreCase = true)
                        val existing = usersByKey[email]
                        if (existing != null) {
                            val updatedCount = if (isTierAssignment) existing.requestsCount else existing.requestsCount + 1
                            val updatedTier = resolveEffectiveUserTier(existing.id, email, existing.tier, existing.isAdmin)
                            usersByKey[email] = existing.copy(
                                tier = updatedTier,
                                requestsCount = updatedCount
                            )
                        } else {
                            val targetIdFromMsg = Regex("""TargetId:\s*([0-9a-fA-F-]{36})""")
                                .find(msg)?.groupValues?.getOrNull(1)
                            val uid = targetIdFromMsg
                                ?: obj.optString("user_id", "").takeIf { it.matches(Regex("^[0-9a-fA-F-]{36}$")) }
                                ?: UUID.nameUUIDFromBytes("justfan:$email".toByteArray()).toString()
                            val isAdmin = email.equals("reytherapper12@gmail.com", ignoreCase = true)
                            val subTier = resolveEffectiveUserTier(uid, email, null, isAdmin)
                            val displayName = if (!isTierAssignment && nameVal.isNotBlank() && nameVal != "null") {
                                nameVal
                            } else {
                                email.substringBefore("@")
                            }
                            usersByKey[email] = UserEntity(
                                id = uid,
                                username = displayName,
                                email = email,
                                tier = subTier,
                                requestsCount = if (isTierAssignment) 0 else 1,
                                isAdmin = isAdmin,
                                status = "Active",
                                joinedAt = parseIsoToMillis(obj.optString("created_at", ""))
                            )
                        }
                    }
                } else {
                    reqConn.disconnect()
                }
            } catch (_: Exception) {}

            // 5. Also include any remaining user_ids/emails from `subscriptions`
            for ((uid, plan) in subscriptionsMap) {
                if (uid.startsWith("device:")) continue
                val alreadyMatched = usersByKey.values.any { it.id == uid || it.email.equals(uid, ignoreCase = true) }
                if (!alreadyMatched && uid.isNotBlank()) {
                    val syntheticEmail = if (uid.contains("@")) uid.lowercase() else "${uid.take(8)}@user.justfan"
                    usersByKey[syntheticEmail] = UserEntity(
                        id = if (uid.contains("@")) UUID.nameUUIDFromBytes("justfan:${uid.lowercase()}".toByteArray()).toString() else uid,
                        username = syntheticEmail.substringBefore("@"),
                        email = syntheticEmail,
                        tier = plan,
                        requestsCount = 0,
                        isAdmin = syntheticEmail.equals("reytherapper12@gmail.com", ignoreCase = true),
                        status = "Active",
                        joinedAt = System.currentTimeMillis()
                    )
                }
            }

            Result.success(usersByKey.values.toList())
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
        // 1. Check user's tier in remote ledger, `subscriptions`, and `users` tables first
        try {
            val subDetails = fetchUserSubscriptionDetails(userId, email, accessToken, deviceId).getOrNull()
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

        // 2. For Free tier users: enforce 3 requests per calendar month (resets on the 1st of each month per device & account)
        val startOfCalendarMonth = ZonedDateTime.now(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .truncatedTo(ChronoUnit.DAYS)
        val startOfMonthMillis = startOfCalendarMonth.toInstant().toEpochMilli()
        val startOfMonthIso = startOfCalendarMonth.toInstant().toString()
        val nextMonthResetIso = startOfCalendarMonth.plusMonths(1).toInstant().toString()

        var currentMonthCount = 0
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
                val endpoint = "RequestfromApp?select=id,device_id,email,name,message,created_at&created_at=gte.$startOfMonthIso&or=($orClause)"
                val conn = openConnection(endpoint, "GET")
                if (conn.responseCode in 200..299) {
                    val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                    for (i in 0 until arr.length()) {
                        val row = arr.getJSONObject(i)
                        val msg = row.optString("message", "")
                        val nameVal = row.optString("name", "")
                        // Exclude payment verification screenshots and internal tier assignments from the 3-request content quota
                        if (msg.startsWith("[PAYMENT_PROOF]") || msg.startsWith("[TIER_ASSIGNMENT]") || nameVal.equals("TIER_ASSIGNMENT", ignoreCase = true)) {
                            continue
                        }

                        val createdStr = row.optString("created_at", "")
                        val createdMillis = parseIsoToMillis(createdStr, defaultMillis = 0L)
                        if (createdMillis >= startOfMonthMillis) {
                            currentMonthCount++
                        }
                    }
                }
                conn.disconnect()
            }

            // Also count current-month `[APP_REQUEST]` rows in `upload_requests` for this device/email
            val upReqConn = openConnection(
                "upload_requests?select=id,title,description,created_at&host=eq.app_request&created_at=gte.$startOfMonthIso&limit=200",
                "GET"
            )
            if (upReqConn.responseCode in 200..299) {
                val upArr = JSONArray(upReqConn.inputStream.bufferedReader().readText())
                upReqConn.disconnect()
                val cleanEmail = email?.trim()?.lowercase().orEmpty()
                for (i in 0 until upArr.length()) {
                    val row = upArr.getJSONObject(i)
                    val desc = row.optString("description", "")
                    if (desc.contains("[PAYMENT_PROOF]") || desc.contains("[TIER_ASSIGNMENT]")) continue
                    val rowEmail = row.optString("title", "").trim().lowercase()
                    val matchesDevice = deviceId.isNotBlank() && desc.contains("DeviceId: $deviceId")
                    val matchesEmail = cleanEmail.isNotBlank() && rowEmail == cleanEmail
                    if (matchesDevice || matchesEmail) {
                        val createdMillis = parseIsoToMillis(row.optString("created_at", ""), 0L)
                        if (createdMillis >= startOfMonthMillis) {
                            currentMonthCount++
                        }
                    }
                }
            } else {
                upReqConn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRequestQuota calendar month count notice", e)
        }

        val limit = 3
        val remaining = (limit - currentMonthCount).coerceAtLeast(0)
        Result.success(
            RequestQuota(
                unlimited = false,
                limit = limit,
                used = currentMonthCount,
                remaining = remaining,
                resetsAt = nextMonthResetIso
            )
        )
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
            val currentMonthKey = ZonedDateTime.now(ZoneOffset.UTC).let { "${it.year}-${it.monthValue.toString().padStart(2, '0')}" }
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
                            put("quota_month", currentMonthKey)
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

            // 2. Only link current-month RequestfromApp rows for this email that lack a device_id
            if (email.isNotBlank()) {
                val startOfMonthIso = ZonedDateTime.now(ZoneOffset.UTC)
                    .withDayOfMonth(1)
                    .truncatedTo(ChronoUnit.DAYS)
                    .toInstant()
                    .toString()
                val patchPayload = JSONObject().apply {
                    put("device_id", deviceId)
                }
                executePatch(
                    "RequestfromApp?email=eq.${email.trim().lowercase()}&device_id=is.null&created_at=gte.$startOfMonthIso",
                    patchPayload.toString(),
                    accessToken
                )
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
