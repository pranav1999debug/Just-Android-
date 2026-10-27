package com.example.justfan.data.remote

import com.example.justfan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * SecretsStore provides an in-memory cache for application secrets retrieved
 * from the Supabase `app_secrets` table.
 *
 * Rules:
 * - Keys are loaded ONCE into an in-memory cache and reused.
 * - Never hardcoded, never written to disk, logs, or SharedPreferences.
 * - If a host replies 401 or 403, clearCache() and reload once.
 * - Throws a clear error if the requested secret is missing.
 */
object SecretsStore {
    private var cache: Map<String, String>? = null
    private val mutex = Mutex()

    fun clearCache() {
        cache = null
    }

    /**
     * Retrieves the secret value by name from the cache.
     * On first call, loads all requested secrets from Supabase app_secrets.
     * If forceReload is true, clears cache and reloads once.
     */
    suspend fun get(name: String, forceReload: Boolean = false): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (forceReload || cache == null) {
                reloadInternal()
            }
            cache?.get(name) ?: run {
                if (!forceReload) {
                    reloadInternal()
                }
                cache?.get(name) ?: throw IllegalStateException("Secret $name not found in app_secrets")
            }
        }
    }

    suspend fun getOrNull(name: String, forceReload: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            get(name, forceReload)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reloads secrets from the Supabase REST API:
     * GET {BuildConfig.SUPABASE_URL}/rest/v1/app_secrets?select=name,value
     * Headers: apikey = BuildConfig.SUPABASE_KEY, Authorization = Bearer BuildConfig.SUPABASE_KEY
     */
    private fun reloadInternal() {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val endpoint = "$baseUrl/rest/v1/app_secrets?select=name,value"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            setRequestProperty(
                AppVersionConfig.HEADER_APP_VERSION,
                AppVersionConfig.APP_VERSION.toString()
            )
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15000
            readTimeout = 15000
        }

        val code = conn.responseCode
        val responseBody = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } else {
            val errBody = conn.errorStream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: "HTTP $code"
            conn.disconnect()
            VersionGateManager.onPermissionOrRlsRejected(code, errBody)

            val errorMsg = try {
                val j = JSONObject(errBody)
                j.optString("message", errBody)
            } catch (_: Exception) {
                errBody
            }
            throw IllegalStateException("Failed to load app_secrets (HTTP $code): $errorMsg")
        }
        conn.disconnect()

        val jsonArray = JSONArray(responseBody)
        val map = mutableMapOf<String, String>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val n = item.optString("name", "")
            val v = item.optString("value", "")
            if (n.isNotEmpty()) {
                map[n] = v
            }
        }

        // Also check upload_requests (host = 'app_secret') for admin-provisioned runtime secrets
        try {
            val secEndpoint = "$baseUrl/rest/v1/upload_requests?select=title,caption&host=eq.app_secret&order=created_at.desc&limit=50"
            val secConn = (URL(secEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                setRequestProperty(
                    AppVersionConfig.HEADER_APP_VERSION,
                    AppVersionConfig.APP_VERSION.toString()
                )
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            val secCode = secConn.responseCode
            if (secCode in 200..299) {
                val secBody = BufferedReader(InputStreamReader(secConn.inputStream, Charsets.UTF_8)).use { it.readText() }
                secConn.disconnect()
                val secArr = JSONArray(secBody)
                for (i in 0 until secArr.length()) {
                    val obj = secArr.getJSONObject(i)
                    val k = obj.optString("title", "").trim()
                    val v = obj.optString("caption", "").trim()
                    if (k.isNotEmpty() && v.isNotEmpty() && !map.containsKey(k)) {
                        map[k] = v
                    }
                }
            } else {
                val secErr = secConn.errorStream?.bufferedReader()?.readText().orEmpty()
                secConn.disconnect()
                VersionGateManager.onPermissionOrRlsRejected(secCode, secErr)
            }
        } catch (_: Exception) {}

        cache = map
    }
}
