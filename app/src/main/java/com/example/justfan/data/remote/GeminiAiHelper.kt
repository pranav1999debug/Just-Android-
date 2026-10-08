package com.example.justfan.data.remote

import android.util.Log
import com.example.justfan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class GeneratedContentDetails(
    val description: String,
    val hashtags: List<String>
)

object GeminiAiHelper {
    private const val TAG = "GeminiAiHelper"

    /**
     * Generates a captivating description and relevant hashtags for a post or request
     * using the Google Gemini API (gemini-3.5-flash with fallback models and smart local fallback).
     */
    suspend fun generateDescriptionAndHashtags(title: String): Result<GeneratedContentDetails> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.success(fallbackGenerate(title))
        }

        // Try gemini-3.5-flash first, then flash-latest and 3.8-flash
        val candidateModels = listOf("gemini-3.5-flash", "gemini-flash-latest", "gemini-3.8-flash")
        for (model in candidateModels) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }

                val prompt = """
                    You are a creative social media and content copywriter for the fan platform JUSTFAN.
                    Given the following post or gallery title: "$title"

                    Generate:
                    1. An engaging, alluring, and professional description (2 to 3 sentences) suitable for a creator gallery.
                    2. 5 to 7 trending, relevant hashtags (e.g. #Cosplay, #Exclusive, #Model, #4K).

                    Respond ONLY in raw JSON matching this exact structure:
                    {
                      "description": "...",
                      "hashtags": ["#tag1", "#tag2", "#tag3"]
                    }
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    val contentsArr = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        }
                        put(partObj)
                    }
                    put("contents", contentsArr)
                    val genConfig = JSONObject().apply {
                        put("responseMimeType", "application/json")
                    }
                    put("generationConfig", genConfig)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(requestJson.toString()) }

                val code = conn.responseCode
                if (code in 200..299) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    val root = JSONObject(responseText)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val contentObj = firstCandidate.optJSONObject("content")
                        val partsArr = contentObj?.optJSONArray("parts")
                        val text = partsArr?.optJSONObject(0)?.optString("text")

                        if (!text.isNullOrBlank()) {
                            val cleanJson = text.trim()
                                .removePrefix("```json")
                                .removePrefix("```")
                                .removeSuffix("```")
                                .trim()
                            val parsed = JSONObject(cleanJson)
                            val desc = parsed.optString("description", "").trim()
                            val tagsArray = parsed.optJSONArray("hashtags") ?: JSONArray()
                            val tagsList = mutableListOf<String>()
                            for (i in 0 until tagsArray.length()) {
                                val t = tagsArray.getString(i).trim()
                                if (t.isNotEmpty()) {
                                    tagsList.add(if (t.startsWith("#")) t else "#$t")
                                }
                            }

                            if (desc.isNotBlank()) {
                                return@withContext Result.success(
                                    GeneratedContentDetails(
                                        description = desc,
                                        hashtags = tagsList
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Model $model returned HTTP $code")
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception calling Gemini with $model: ${e.message}")
            }
        }

        // Reliable fallback if model unavailable or network offline
        Result.success(fallbackGenerate(title))
    }

    private fun fallbackGenerate(title: String): GeneratedContentDetails {
        val cleanTitle = title.trim()
        val desc = "Explore exclusive high-resolution previews and premium showcase content for $cleanTitle. Curated with ultra high definition quality for JUSTFAN members."
        val words = cleanTitle.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.length > 2 }
            .take(5)

        val tags = mutableListOf("#JUSTFAN", "#Exclusive", "#Trending")
        words.forEach { w ->
            val tag = "#" + w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            if (!tags.contains(tag)) tags.add(tag)
        }
        return GeneratedContentDetails(description = desc, hashtags = tags)
    }
}
