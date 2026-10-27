package com.example.justfan.data.remote

import android.util.Log
import com.example.justfan.BuildConfig
import com.example.justfan.data.model.PostEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BufferClient {
    private const val TAG = "BufferClient"
    private const val BUFFER_GRAPHQL_URL = "https://api.buffer.com"
    const val DEFAULT_SOCIAL_DOWNLOAD_LINK = "https://t.me/excJustfan/3"

    data class BufferChannel(
        val id: String,
        val name: String,
        val service: String
    )

    data class BufferForwardResult(
        val channelsCount: Int,
        val channelNames: List<String>,
        val postIds: List<String>
    )

    private suspend fun resolveBufferApiKey(): String? {
        val fromBuildConfig = try {
            BuildConfig.BUFFER_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
        if (fromBuildConfig.isNotBlank() && fromBuildConfig != "YOUR_BUFFER_API_KEY") {
            return fromBuildConfig
        }
        return SecretsStore.getOrNull("buffer_api_key")
            ?: SecretsStore.getOrNull("BUFFER_API_KEY")
    }

    /**
     * Builds the social media caption containing the post's spicy description,
     * the Telegram download link (https://t.me/excJustfan/3), and hashtags.
     */
    fun buildSocialCaption(
        post: PostEntity,
        downloadLink: String = DEFAULT_SOCIAL_DOWNLOAD_LINK,
        maxLength: Int? = null
    ): String {
        val formattedTags = post.tags
            .map { it.trim().removePrefix("#").replace(" ", "") }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ") { "#$it" }
            .ifBlank { "#JustFan #Exclusive #VIP" }

        val cleanDesc = post.description.trim().ifBlank { post.title.trim() }
        // Remove duplicate trailing hashtags from description if already included
        val bodyWithoutTags = cleanDesc
            .replace(Regex("(#\\w+\\s*)+$"), "")
            .trim()
            .ifBlank { post.title.trim() }

        val linkLine = "📥 Download: $downloadLink"
        val fullText = buildString {
            if (!bodyWithoutTags.contains(post.title.trim(), ignoreCase = true)) {
                append("🔥 ").append(post.title.trim()).append("\n\n")
            }
            append(bodyWithoutTags)
            append("\n\n")
            append(linkLine)
            append("\n\n")
            append(formattedTags)
        }

        if (maxLength == null || fullText.length <= maxLength) {
            return fullText
        }

        // For short-form channels like Twitter/X (280 char limit), keep the link + top hashtags intact
        val shortTags = post.tags
            .take(4)
            .map { it.trim().removePrefix("#").replace(" ", "") }
            .filter { it.isNotBlank() }
            .joinToString(" ") { "#$it" }
            .ifBlank { "#JustFan #VIP" }

        val suffix = "\n\n$linkLine\n$shortTags"
        val availableForBody = (maxLength - suffix.length - 4).coerceAtLeast(30)
        val truncatedBody = if (bodyWithoutTags.length > availableForBody) {
            bodyWithoutTags.take(availableForBody).trimEnd() + "…"
        } else {
            bodyWithoutTags
        }
        return truncatedBody + suffix
    }

    /**
     * Forwards the newly created post (cover image/video, spicy description, hashtags,
     * and https://t.me/excJustfan/3 download link) to all connected social media channels in Buffer.
     */
    suspend fun forwardPostToSocialMedia(
        post: PostEntity,
        downloadLink: String = DEFAULT_SOCIAL_DOWNLOAD_LINK
    ): Result<BufferForwardResult> = withContext(Dispatchers.IO) {
        val apiKey = resolveBufferApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Buffer API key not configured. Add BUFFER_API_KEY in the Secrets panel.")
            )
        }

        try {
            val channels = fetchConnectedChannels(apiKey)
            if (channels.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No connected social media channels found on this Buffer account.")
                )
            }

            val publishedChannels = mutableListOf<String>()
            val createdPostIds = mutableListOf<String>()
            var lastError: String? = null

            for (channel in channels) {
                val isShortForm = channel.service.equals("twitter", ignoreCase = true) ||
                        channel.service.equals("x", ignoreCase = true)
                val caption = buildSocialCaption(
                    post = post,
                    downloadLink = downloadLink,
                    maxLength = if (isShortForm) 275 else null
                )

                // 1. Try publishing immediately (`shareNow`)
                var postRes = createBufferPostForChannel(
                    apiKey = apiKey,
                    channelId = channel.id,
                    text = caption,
                    mediaUrl = post.imageUrl,
                    shareMode = "shareNow"
                )

                // 2. Automatic fallback to `addToQueue` if `shareNow` is restricted for the channel
                if (postRes.isFailure) {
                    val queueRes = createBufferPostForChannel(
                        apiKey = apiKey,
                        channelId = channel.id,
                        text = caption,
                        mediaUrl = post.imageUrl,
                        shareMode = "addToQueue"
                    )
                    if (queueRes.isSuccess) {
                        postRes = queueRes
                    }
                }

                postRes.onSuccess { bufferPostId ->
                    publishedChannels.add("${channel.service}: ${channel.name}")
                    createdPostIds.add(bufferPostId)
                }.onFailure { err ->
                    lastError = err.message
                    Log.w(TAG, "Buffer channel ${channel.name} error: ${err.message}")
                }
            }

            if (publishedChannels.isNotEmpty()) {
                Result.success(
                    BufferForwardResult(
                        channelsCount = publishedChannels.size,
                        channelNames = publishedChannels,
                        postIds = createdPostIds
                    )
                )
            } else {
                Result.failure(Exception(lastError ?: "Failed to forward post to Buffer channels"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "forwardPostToSocialMedia error", e)
            Result.failure(e)
        }
    }

    private fun fetchConnectedChannels(apiKey: String): List<BufferChannel> {
        val channels = mutableListOf<BufferChannel>()
        val orgsQuery = JSONObject().apply {
            put("query", "query { account { organizations { id name } } }")
        }
        val orgsResp = executeGraphQl(apiKey, orgsQuery.toString())
        val orgsArr = orgsResp
            .optJSONObject("data")
            ?.optJSONObject("account")
            ?.optJSONArray("organizations")
            ?: JSONArray()

        for (i in 0 until orgsArr.length()) {
            val orgId = orgsArr.getJSONObject(i).optString("id", "")
            if (orgId.isBlank()) continue

            val chQuery = JSONObject().apply {
                put(
                    "query",
                    "query GetChannels(\$orgId: OrganizationId!) { channels(input: { organizationId: \$orgId }) { id name service } }"
                )
                put("variables", JSONObject().apply { put("orgId", orgId) })
            }
            val chResp = executeGraphQl(apiKey, chQuery.toString())
            val chArr = chResp.optJSONObject("data")?.optJSONArray("channels") ?: JSONArray()
            for (j in 0 until chArr.length()) {
                val chObj = chArr.getJSONObject(j)
                val cid = chObj.optString("id", "")
                val cname = chObj.optString("name", "Social Channel")
                val cservice = chObj.optString("service", "social")
                if (cid.isNotBlank()) {
                    channels.add(BufferChannel(id = cid, name = cname, service = cservice))
                }
            }
        }
        return channels
    }

    private fun createBufferPostForChannel(
        apiKey: String,
        channelId: String,
        text: String,
        mediaUrl: String,
        shareMode: String
    ): Result<String> {
        val cleanMedia = mediaUrl.trim()
        val isVideo = cleanMedia.endsWith(".mp4", ignoreCase = true) ||
                cleanMedia.endsWith(".mov", ignoreCase = true) ||
                cleanMedia.endsWith(".webm", ignoreCase = true)

        val assetsArray = JSONArray()
        if (cleanMedia.startsWith("http", ignoreCase = true)) {
            val assetObj = JSONObject()
            if (isVideo) {
                assetObj.put("video", JSONObject().apply { put("url", cleanMedia) })
            } else {
                assetObj.put("image", JSONObject().apply { put("url", cleanMedia) })
            }
            assetsArray.put(assetObj)
        }

        val inputObj = JSONObject().apply {
            put("channelId", channelId)
            put("text", text)
            put("schedulingType", "automatic")
            put("mode", shareMode)
            put("assets", assetsArray)
        }

        val mutation = """
            mutation CreateSocialPost(${'$'}input: CreatePostInput!) {
              createPost(input: ${'$'}input) {
                __typename
                ... on PostActionSuccess {
                  post {
                    id
                    text
                  }
                }
                ... on MutationError {
                  message
                }
              }
            }
        """.trimIndent()

        val bodyJson = JSONObject().apply {
            put("query", mutation)
            put("variables", JSONObject().apply { put("input", inputObj) })
        }

        val resp = executeGraphQl(apiKey, bodyJson.toString())
        val errorsArr = resp.optJSONArray("errors")
        if (errorsArr != null && errorsArr.length() > 0) {
            val errMsg = errorsArr.getJSONObject(0).optString("message", "GraphQL Error")
            return Result.failure(Exception(errMsg))
        }

        val createPostObj = resp.optJSONObject("data")?.optJSONObject("createPost")
            ?: return Result.failure(Exception("Empty createPost response from Buffer"))

        val typeName = createPostObj.optString("__typename", "")
        if (typeName == "PostActionSuccess" || createPostObj.has("post")) {
            val postId = createPostObj.optJSONObject("post")?.optString("id", "buffer_post") ?: "buffer_post"
            return Result.success(postId)
        }

        val mutationErrorMsg = createPostObj.optString("message", "Buffer mutation error ($typeName)")
        return Result.failure(Exception(mutationErrorMsg))
    }

    private fun executeGraphQl(apiKey: String, jsonPayload: String): JSONObject {
        val url = URL(BUFFER_GRAPHQL_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "JustFan-Android/1.0")
            connectTimeout = 20000
            readTimeout = 20000
            doOutput = true
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }

        val code = conn.responseCode
        val responseText = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } else {
            val err = conn.errorStream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: "HTTP $code"
            conn.disconnect()
            throw IllegalStateException("Buffer API HTTP $code: $err")
        }
        conn.disconnect()
        return JSONObject(responseText)
    }
}
