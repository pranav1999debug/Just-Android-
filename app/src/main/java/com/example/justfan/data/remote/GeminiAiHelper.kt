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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class GeneratedContentDetails(
    val description: String,
    val hashtags: List<String>
)

object GeminiAiHelper {
    private const val TAG = "GeminiAiHelper"
    private val variationCounter = AtomicInteger(Random.nextInt(0, 100))

    private val EDITORIAL_ANGLES = listOf(
        "Velvet & Late-Night Intrigue — sultry, magnetic, and effortlesslyconfident",
        "High-Voltage Glamour — bold, unapologetic, and impossible to scroll past",
        "Playful Tease & Siren Energy — witty, seductive, and leaving just enough to the imagination",
        "Editorial Luxury & Heat — sleek, Vogue-after-dark sophistication with a spicy edge",
        "Behind-Closed-Doors Exclusive — intimate, raw, and dangerously captivating",
        "Obsession-Worthy Drop — punchy, breathless, and dripping with main-character energy",
        "Forbidden Fruit Aesthetic — tempting, sharp, and crafted for true connoisseurs",
        "Golden-Hour Fever — warm, intoxicating, and unapologetically alluring"
    )

    /**
     * Generates a short, spicy, professional-writer description and relevant hashtags
     * based on the post title using Gemini (gemini-3.5-flash / gemini-flash-latest)
     * with a rich, non-repetitive copywriter fallback engine.
     */
    suspend fun generateDescriptionAndHashtags(
        title: String,
        variationHint: Int = -1
    ): Result<GeneratedContentDetails> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { "Midnight Exclusive Drop" }
        val step = if (variationHint >= 0) variationHint else variationCounter.incrementAndGet()
        val angle = EDITORIAL_ANGLES[Math.floorMod(step, EDITORIAL_ANGLES.size)]

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }

        if (apiKey.isNotBlank() &&
            !apiKey.equals("YOUR_GEMINI_API_KEY", ignoreCase = true) &&
            !apiKey.equals("null", ignoreCase = true)
        ) {
            val candidateModels = listOf("gemini-3.5-flash", "gemini-flash-latest")
            for (model in candidateModels) {
                try {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val url = URL(endpoint)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = 20000
                        readTimeout = 25000
                        doOutput = true
                    }

                    val prompt = """
                        You are an elite, high-converting creative copywriter for an exclusive glamour and creator platform.
                        Post Title: "$cleanTitle"
                        Creative Angle (Variation #$step): $angle

                        Write:
                        1. A SHORT, SPICY, magnetic description (1 to 2 punchy sentences, max 32 words).
                           - Must sound like a professional human writer — teasing, alluring, confident, and vivid.
                           - NEVER use generic AI clichés like "Explore exclusive high-resolution previews", "Curated with ultra high definition", "Dive into", or "Unleash".
                           - Make it specific to the vibe and subject of "$cleanTitle".
                        2. 5 to 7 trending, high-converting hashtags (include specific tags derived from "$cleanTitle" plus spicy/glamour tags).

                        Respond ONLY with valid JSON in this exact format:
                        {
                          "description": "Your short, spicy 1-2 sentence copy here",
                          "hashtags": ["#Tag1", "#Tag2", "#Tag3", "#Tag4", "#Tag5"]
                        }
                    """.trimIndent()

                    val requestJson = JSONObject().apply {
                        val contentsArr = JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put(
                                        "parts",
                                        JSONArray().apply {
                                            put(JSONObject().apply { put("text", prompt) })
                                        }
                                    )
                                }
                            )
                        }
                        put("contents", contentsArr)
                        put(
                            "generationConfig",
                            JSONObject().apply {
                                put("responseMimeType", "application/json")
                                put("temperature", 0.95)
                                put("topP", 0.95)
                            }
                        )
                    }

                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                        it.write(requestJson.toString())
                    }

                    val code = conn.responseCode
                    if (code in 200..299) {
                        val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
                                    val t = tagsArray.optString(i, "").trim()
                                    if (t.isNotEmpty()) {
                                        tagsList.add(if (t.startsWith("#")) t else "#$t")
                                    }
                                }

                                if (desc.isNotBlank()) {
                                    return@withContext Result.success(
                                        GeneratedContentDetails(
                                            description = desc,
                                            hashtags = if (tagsList.isNotEmpty()) tagsList else buildSmartHashtags(cleanTitle, step)
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
        }

        // Dynamic, non-repetitive professional copywriter generator when offline or API key not set
        Result.success(fallbackGenerate(cleanTitle, step))
    }

    private fun fallbackGenerate(title: String, step: Int): GeneratedContentDetails {
        val cleanTitle = title.trim().ifBlank { "After Hours Exclusive" }
        val templates = listOf< (String) -> String >(
            { t -> "Too hot for the main feed—$t turns the heat all the way up with zero apologies. Blink and you'll miss the best angles." },
            { t -> "Some secrets look better in the dark, and $t is pure late-night temptation from the very first frame. Handle with care." },
            { t -> "Silky lighting, killer curves, and that unmistakable look in her eyes—$t is the definition of a guilty pleasure." },
            { t -> "We pushed the boundaries on $t just to see how much you can handle. Every single shot in this set hits different." },
            { t -> "Soft touch, sharp attitude—$t brings dangerous levels of allure that will live rent-free in your head all week." },
            { t -> "Close the door and turn your brightness up. $t is an unfiltered masterclass in tease, tension, and pure glamour." },
            { t -> "Nothing subtle about this drop: $t serves raw confidence, breathless close-ups, and the kind of energy you can't fake." },
            { t -> "You asked for spicier, so we delivered $t—a slow-burn visual feast where every frame feels like a private invitation." },
            { t -> "Dressed to distract and shot to obsess over. $t blends velvet aesthetics with a bold, after-hours edge." },
            { t -> "Warning: $t might ruin your focus for the rest of the day. Uncut, up-close, and worth every second of the wait." },
            { t -> "When the cameras kept rolling after midnight, $t happened—intimate, magnetic, and dripping with main-character heat." },
            { t -> "Pure eye candy with a wicked twist: $t captures every teasing glance and sultry detail in crystal-clear perfection." },
            { t -> "Not your average gallery drop. $t is bold, skin-deep luxury crafted for fans who crave the real, unedited vibe." },
            { t -> "From the first tease to the final frame, $t is a masterclass in seduction—raw, playful, and impossible to look away from." },
            { t -> "Consider this your VIP pass to $t: high-voltage allure, intimate framing, and zero filler." },
            { t -> "A little reckless, a lot irresistible—$t brings that backstage heat straight to your screen." }
        )

        val chosenTemplate = templates[Math.floorMod(step, templates.size)]
        val desc = chosenTemplate(cleanTitle)
        val tags = buildSmartHashtags(cleanTitle, step)
        return GeneratedContentDetails(description = desc, hashtags = tags)
    }

    private fun buildSmartHashtags(title: String, step: Int): List<String> {
        val stopWords = setOf("the", "and", "for", "with", "from", "that", "this", "new", "post", "set", "vol", "part")
        val words = title.split(Regex("[^A-Za-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it.lowercase() !in stopWords }
            .distinctBy { it.lowercase() }
            .take(4)

        val result = mutableListOf<String>()
        words.forEach { w ->
            val formatted = "#" + w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            result.add(formatted)
        }

        // Combined compound tag if 2+ words exist
        if (words.size >= 2) {
            val compound = "#" + words.take(2).joinToString("") { w ->
                w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            if (compound !in result) result.add(compound)
        }

        val spicyPools = listOf(
            listOf("#AfterHours", "#SpicyDrop", "#UncutGlamour", "#ExclusiveSet", "#TooHot"),
            listOf("#LateNightVibes", "#VIPOnly", "#SultryAesthetic", "#CreatorExclusive", "#Obsessed"),
            listOf("#BehindClosedDoors", "#GlamourShot", "#SirenEnergy", "#PrivateGallery", "#TrendingNow"),
            listOf("#UnfilteredHeat", "#MuseVibes", "#Breathtaking", "#FanFavorite", "#UltraHD")
        )
        val pool = spicyPools[Math.floorMod(step, spicyPools.size)]
        for (tag in pool) {
            if (result.size >= 7) break
            if (result.none { it.equals(tag, ignoreCase = true) }) {
                result.add(tag)
            }
        }
        return result
    }
}
