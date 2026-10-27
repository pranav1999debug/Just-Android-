package com.example.justfan.data.remote

import android.util.Log
import com.example.justfan.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Single source of truth for the JustFan Android client version.
 *
 * IMPORTANT FOR RELEASES:
 * Must always match `versionCode` in `app/build.gradle.kts`.
 * Bump both `AppVersionConfig.APP_VERSION` and `versionCode` together on every release.
 */
object AppVersionConfig {
    const val APP_VERSION: Int = 2
    const val APP_VERSION_NAME: String = "2.0"
    const val HEADER_APP_VERSION: String = "x-app-version"
    const val DEFAULT_UPDATE_URL: String =
        "https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk"
    const val DEFAULT_UPDATE_MESSAGE: String =
        "A new version is required to continue."
}

data class AppConfig(
    val id: Int = 1,
    val minVersion: Int = AppVersionConfig.APP_VERSION,
    val latestVersion: Int = AppVersionConfig.APP_VERSION,
    val updateUrl: String = AppVersionConfig.DEFAULT_UPDATE_URL,
    val updateMessage: String = AppVersionConfig.DEFAULT_UPDATE_MESSAGE
)

sealed interface VersionGateState {
    data object Checking : VersionGateState
    data object Allowed : VersionGateState
    data class OptionalUpdateAvailable(val config: AppConfig) : VersionGateState
    data class UpdateRequired(val config: AppConfig) : VersionGateState
}

object VersionGateManager {
    private const val TAG = "VersionGateManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkMutex = Mutex()

    private val _state = MutableStateFlow<VersionGateState>(VersionGateState.Checking)
    val state: StateFlow<VersionGateState> = _state.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    @Volatile
    private var lastRlsRecheckTimeMs: Long = 0L

    fun dismissOptionalBanner() {
        _bannerDismissed.value = true
    }

    /**
     * Fetches `app_config (id = 1)` from Supabase and updates [state].
     * - If `APP_VERSION < min_version`: sets [VersionGateState.UpdateRequired] (blocks all screens).
     * - If `min_version <= APP_VERSION < latest_version`: sets [VersionGateState.OptionalUpdateAvailable].
     * - If the fetch fails (offline / no internet): does NOT block the user; lets the app continue.
     */
    suspend fun checkVersionGate(): VersionGateState = withContext(Dispatchers.IO) {
        checkMutex.withLock {
            val fetched = fetchAppConfigFromSupabase()
            if (fetched != null) {
                val installed = AppVersionConfig.APP_VERSION
                val nextState = when {
                    installed < fetched.minVersion -> {
                        Log.w(
                            TAG,
                            "Forced update required: installed=$installed < min_version=${fetched.minVersion}"
                        )
                        VersionGateState.UpdateRequired(fetched)
                    }
                    installed < fetched.latestVersion -> {
                        Log.i(
                            TAG,
                            "Optional update available: installed=$installed < latest_version=${fetched.latestVersion}"
                        )
                        VersionGateState.OptionalUpdateAvailable(fetched)
                    }
                    else -> {
                        VersionGateState.Allowed
                    }
                }
                _state.value = nextState
                nextState
            } else {
                // Offline or table not reachable: do not block the user unless already in UpdateRequired
                val current = _state.value
                val fallback = if (current is VersionGateState.UpdateRequired) {
                    current
                } else {
                    VersionGateState.Allowed
                }
                _state.value = fallback
                fallback
            }
        }
    }

    /**
     * Triggered when any Supabase REST, Auth, or Edge Function request fails with a permission/RLS
     * error (HTTP 401/403 or Postgres 42501). Re-fetches `app_config` and activates the full-screen
     * Update Required screen if `APP_VERSION < min_version`.
     */
    fun onPermissionOrRlsRejected(httpCode: Int, responseBody: String = "") {
        val now = System.currentTimeMillis()
        if (now - lastRlsRecheckTimeMs < 3_000L) return
        val looksLikePermissionFailure =
            httpCode == 401 ||
                httpCode == 403 ||
                responseBody.contains("42501") ||
                responseBody.contains("row-level security", ignoreCase = true) ||
                responseBody.contains("permission denied", ignoreCase = true) ||
                responseBody.contains("version gate", ignoreCase = true)

        if (!looksLikePermissionFailure) return
        lastRlsRecheckTimeMs = now

        scope.launch {
            Log.w(TAG, "Supabase permission/RLS rejection (HTTP $httpCode); re-checking app_config...")
            checkVersionGate()
        }
    }

    private fun fetchAppConfigFromSupabase(): AppConfig? {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_KEY
        val endpoint = "$baseUrl/rest/v1/app_config?id=eq.1&select=id,min_version,latest_version,update_url,update_message&limit=1"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty(
                    AppVersionConfig.HEADER_APP_VERSION,
                    AppVersionConfig.APP_VERSION.toString()
                )
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val id = obj.optInt("id", 1)
                    val minVer = obj.optInt("min_version", AppVersionConfig.APP_VERSION)
                    val latestVer = obj.optInt("latest_version", minVer)
                    val url = obj.optString("update_url", AppVersionConfig.DEFAULT_UPDATE_URL)
                        .trim()
                        .ifBlank { AppVersionConfig.DEFAULT_UPDATE_URL }
                    val msg = if (obj.has("update_message") && !obj.isNull("update_message")) {
                        obj.optString("update_message", AppVersionConfig.DEFAULT_UPDATE_MESSAGE)
                            .trim()
                            .ifBlank { AppVersionConfig.DEFAULT_UPDATE_MESSAGE }
                    } else {
                        AppVersionConfig.DEFAULT_UPDATE_MESSAGE
                    }
                    AppConfig(
                        id = id,
                        minVersion = minVer,
                        latestVersion = latestVer,
                        updateUrl = url,
                        updateMessage = msg
                    )
                } else {
                    null
                }
            } else {
                Log.w(TAG, "fetchAppConfigFromSupabase non-200 HTTP $code")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAppConfigFromSupabase failed (offline or unreachable): ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
