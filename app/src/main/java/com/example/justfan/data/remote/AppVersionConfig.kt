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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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
    const val APP_VERSION: Int = 3
    const val APP_VERSION_NAME: String = "3.0"
    const val HEADER_APP_VERSION: String = "x-app-version"
    const val DEFAULT_UPDATE_URL: String =
        "https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk"
    const val DEFAULT_UPDATE_MESSAGE: String =
        "A new version (v3.0) is required to access JustFan database contents. Please download and install the latest update."
}

data class AppConfig(
    val id: Int = 1,
    val minVersion: Int = AppVersionConfig.APP_VERSION,
    val latestVersion: Int = AppVersionConfig.APP_VERSION,
    val latestVersionName: String = "${maxOf(minVersion, latestVersion)}.0",
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

    private val _remoteConfig = MutableStateFlow(AppConfig())
    val remoteConfig: StateFlow<AppConfig> = _remoteConfig.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    @Volatile
    private var lastRlsRecheckTimeMs: Long = 0L

    @Volatile
    private var hasAutoSyncedCurrentVersion: Boolean = false

    fun dismissOptionalBanner() {
        _bannerDismissed.value = true
    }

    /**
     * Checks whether the installed app is at the latest database version:
     * - Queries `app_config (id = 1)`, `app_version`, and the `upload_requests` version ledger.
     * - If the database requires a newer version (`installed < minVersion` or `installed < latestVersion`),
     *   blocks access to database contents with [VersionGateState.UpdateRequired] and prompts the user
     *   to download and install the latest APK.
     * - If the database has an older version number than this client (`remote < APP_VERSION`),
     *   automatically upgrades the remote database version records to `APP_VERSION` (`3` / `"3.0"`)
     *   so previous app versions (`v1`, `v2`) are blocked from accessing database contents.
     */
    suspend fun checkVersionGate(): VersionGateState = withContext(Dispatchers.IO) {
        checkMutex.withLock {
            val fetched = fetchCombinedDatabaseVersionConfig()
            if (fetched != null) {
                _remoteConfig.value = fetched
                val installed = AppVersionConfig.APP_VERSION
                val requiredVersion = maxOf(fetched.minVersion, fetched.latestVersion)

                // If remote DB is still on an older version (< 3), automatically upgrade the DB to v3.0
                if (fetched.minVersion < installed || fetched.latestVersion < installed) {
                    if (!hasAutoSyncedCurrentVersion) {
                        hasAutoSyncedCurrentVersion = true
                        scope.launch {
                            syncDatabaseVersionToCurrent()
                        }
                    }
                }

                val nextState = when {
                    installed < fetched.minVersion -> {
                        Log.w(
                            TAG,
                            "Forced update required: installed=$installed < min_version=${fetched.minVersion}"
                        )
                        VersionGateState.UpdateRequired(fetched)
                    }
                    installed < requiredVersion -> {
                        Log.w(
                            TAG,
                            "Database is at newer version ($requiredVersion > $installed); requiring update"
                        )
                        VersionGateState.UpdateRequired(
                            fetched.copy(minVersion = requiredVersion, latestVersion = requiredVersion)
                        )
                    }
                    else -> {
                        VersionGateState.Allowed
                    }
                }
                _state.value = nextState
                nextState
            } else {
                // If remote version tables have not been seeded yet, seed them in background
                if (!hasAutoSyncedCurrentVersion) {
                    hasAutoSyncedCurrentVersion = true
                    scope.launch {
                        syncDatabaseVersionToCurrent()
                    }
                }
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
        val isExplicitVersionGateRejection =
            responseBody.contains("version gate", ignoreCase = true)
        val isSingleTableWriteRls =
            responseBody.contains("new row violates row-level security policy", ignoreCase = true) &&
                !isExplicitVersionGateRejection

        // Ignore single-table write policy violations when the app is already at or above the remote minVersion
        if (isSingleTableWriteRls && AppVersionConfig.APP_VERSION >= _remoteConfig.value.minVersion) {
            return
        }

        val looksLikePermissionFailure =
            httpCode == 401 ||
                httpCode == 403 ||
                responseBody.contains("42501") ||
                responseBody.contains("row-level security", ignoreCase = true) ||
                responseBody.contains("permission denied", ignoreCase = true) ||
                isExplicitVersionGateRejection

        if (!looksLikePermissionFailure) return
        lastRlsRecheckTimeMs = now

        scope.launch {
            Log.w(TAG, "Supabase permission/RLS rejection (HTTP $httpCode); re-checking app_config...")
            val stateAfterCheck = checkVersionGate()
            if (stateAfterCheck !is VersionGateState.UpdateRequired && isExplicitVersionGateRejection) {
                val blockedConfig = AppConfig(
                    id = 1,
                    minVersion = AppVersionConfig.APP_VERSION + 1,
                    latestVersion = AppVersionConfig.APP_VERSION + 1,
                    latestVersionName = "${AppVersionConfig.APP_VERSION + 1}.0",
                    updateUrl = AppVersionConfig.DEFAULT_UPDATE_URL,
                    updateMessage = AppVersionConfig.DEFAULT_UPDATE_MESSAGE
                )
                _state.value = VersionGateState.UpdateRequired(blockedConfig)
            }
        }
    }

    /**
     * Updates the remote Supabase database (`app_config`, `app_version`, and `upload_requests` version ledger)
     * to require [targetVersion] (defaults to [AppVersionConfig.APP_VERSION] = 3 / "3.0") so all previous
     * versions are blocked from accessing database contents.
     */
    suspend fun syncDatabaseVersionToCurrent(
        targetVersion: Int = AppVersionConfig.APP_VERSION,
        targetVersionName: String = AppVersionConfig.APP_VERSION_NAME,
        updateUrl: String = AppVersionConfig.DEFAULT_UPDATE_URL,
        updateMessage: String = AppVersionConfig.DEFAULT_UPDATE_MESSAGE,
        accessToken: String? = null
    ): Result<AppConfig> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_KEY
        val bearer = if (!accessToken.isNullOrBlank()) accessToken else apiKey
        var anySucceeded = false

        // 1. Upsert public.app_config (id = 1)
        runCatching {
            val endpoint = "$baseUrl/rest/v1/app_config?on_conflict=id"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                setRequestProperty(
                    AppVersionConfig.HEADER_APP_VERSION,
                    maxOf(AppVersionConfig.APP_VERSION, targetVersion).toString()
                )
            }
            val payload = JSONObject().apply {
                put("id", 1)
                put("min_version", targetVersion)
                put("latest_version", targetVersion)
                put("update_url", updateUrl)
                put("update_message", updateMessage)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                anySucceeded = true
            } else {
                // Try PATCH if row id=1 already exists
                val patchConn = (URL("$baseUrl/rest/v1/app_config?id=eq.1").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("apikey", apiKey)
                    setRequestProperty("Authorization", "Bearer $bearer")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Prefer", "return=minimal")
                    setRequestProperty(
                        AppVersionConfig.HEADER_APP_VERSION,
                        maxOf(AppVersionConfig.APP_VERSION, targetVersion).toString()
                    )
                }
                OutputStreamWriter(patchConn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                val patchCode = patchConn.responseCode
                patchConn.disconnect()
                if (patchCode in 200..299) {
                    anySucceeded = true
                }
            }
        }

        // 2. Insert latest record into public.app_version
        runCatching {
            val endpoint = "$baseUrl/rest/v1/app_version"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
                setRequestProperty(
                    AppVersionConfig.HEADER_APP_VERSION,
                    maxOf(AppVersionConfig.APP_VERSION, targetVersion).toString()
                )
            }
            val payload = JSONObject().apply {
                put("version", targetVersionName)
                put("download_link", updateUrl)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                anySucceeded = true
            }
        }

        // 3. Also record in public.upload_requests (host = "app_version_gate") so version gate works
        //    immediately even before custom SQL migrations are executed
        runCatching {
            val delConn = (URL("$baseUrl/rest/v1/upload_requests?host=eq.app_version_gate").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty(
                    AppVersionConfig.HEADER_APP_VERSION,
                    maxOf(AppVersionConfig.APP_VERSION, targetVersion).toString()
                )
            }
            delConn.responseCode
            delConn.disconnect()

            val insConn = (URL("$baseUrl/rest/v1/upload_requests").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
                setRequestProperty(
                    AppVersionConfig.HEADER_APP_VERSION,
                    maxOf(AppVersionConfig.APP_VERSION, targetVersion).toString()
                )
            }
            val meta = JSONObject().apply {
                put("min_version", targetVersion)
                put("latest_version", targetVersion)
                put("version_name", targetVersionName)
                put("update_url", updateUrl)
                put("update_message", updateMessage)
            }
            val payload = JSONObject().apply {
                put("host", "app_version_gate")
                put("status", "active")
                put("title", targetVersion.toString())
                put("caption", targetVersionName)
                put("description", meta.toString())
            }
            OutputStreamWriter(insConn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val insCode = insConn.responseCode
            insConn.disconnect()
            if (insCode in 200..299) {
                anySucceeded = true
            }
        }

        val updatedConfig = AppConfig(
            id = 1,
            minVersion = targetVersion,
            latestVersion = targetVersion,
            latestVersionName = targetVersionName,
            updateUrl = updateUrl,
            updateMessage = updateMessage
        )
        _remoteConfig.value = updatedConfig
        if (anySucceeded) {
            Result.success(updatedConfig)
        } else {
            Result.failure(Exception("Could not write version config to remote database"))
        }
    }

    private fun fetchCombinedDatabaseVersionConfig(): AppConfig? {
        val fromAppConfig = fetchAppConfigFromSupabase()
        val fromAppVersion = fetchAppVersionTableFromSupabase()
        val fromLedger = fetchVersionLedgerFromSupabase()

        if (fromAppConfig == null && fromAppVersion == null && fromLedger == null) {
            return null
        }

        val minVer = maxOf(
            fromAppConfig?.minVersion ?: 1,
            fromAppVersion?.minVersion ?: 1,
            fromLedger?.minVersion ?: 1
        )
        val latestVer = maxOf(
            minVer,
            fromAppConfig?.latestVersion ?: 1,
            fromAppVersion?.latestVersion ?: 1,
            fromLedger?.latestVersion ?: 1
        )
        val updateUrl = listOfNotNull(
            fromAppConfig?.updateUrl?.takeIf { it.isNotBlank() },
            fromLedger?.updateUrl?.takeIf { it.isNotBlank() },
            fromAppVersion?.updateUrl?.takeIf { it.isNotBlank() }
        ).firstOrNull() ?: AppVersionConfig.DEFAULT_UPDATE_URL

        val updateMsg = listOfNotNull(
            fromAppConfig?.updateMessage?.takeIf { it.isNotBlank() },
            fromLedger?.updateMessage?.takeIf { it.isNotBlank() }
        ).firstOrNull() ?: AppVersionConfig.DEFAULT_UPDATE_MESSAGE

        val versionName = when {
            latestVer == AppVersionConfig.APP_VERSION -> AppVersionConfig.APP_VERSION_NAME
            fromLedger != null && fromLedger.latestVersion == latestVer -> fromLedger.latestVersionName
            fromAppVersion != null && fromAppVersion.latestVersion == latestVer -> fromAppVersion.latestVersionName
            else -> "$latestVer.0"
        }

        return AppConfig(
            id = 1,
            minVersion = minVer,
            latestVersion = latestVer,
            latestVersionName = versionName,
            updateUrl = updateUrl,
            updateMessage = updateMsg
        )
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
                        latestVersionName = "${maxOf(minVer, latestVer)}.0",
                        updateUrl = url,
                        updateMessage = msg
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAppConfigFromSupabase note: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchAppVersionTableFromSupabase(): AppConfig? {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_KEY
        val endpoint = "$baseUrl/rest/v1/app_version?select=version,download_link,created_at&order=created_at.desc&limit=1"
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
            if (conn.responseCode in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val rawVer = obj.optString("version", "").trim().removePrefix("v")
                    val majorInt = rawVer.substringBefore(".").toIntOrNull() ?: return null
                    val link = obj.optString("download_link", AppVersionConfig.DEFAULT_UPDATE_URL)
                        .trim()
                        .ifBlank { AppVersionConfig.DEFAULT_UPDATE_URL }
                    AppConfig(
                        id = 1,
                        minVersion = majorInt,
                        latestVersion = majorInt,
                        latestVersionName = rawVer.ifBlank { "$majorInt.0" },
                        updateUrl = link,
                        updateMessage = AppVersionConfig.DEFAULT_UPDATE_MESSAGE
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchVersionLedgerFromSupabase(): AppConfig? {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_KEY
        val endpoint = "$baseUrl/rest/v1/upload_requests?host=eq.app_version_gate&order=created_at.desc&limit=1"
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
            if (conn.responseCode in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val rawMeta = obj.optString("description", "").ifBlank {
                        obj.optString("error_message", "")
                    }
                    val metaObj = runCatching { JSONObject(rawMeta) }.getOrNull()
                    val fileVer = obj.optString("title", "").trim().toIntOrNull()
                        ?: metaObj?.optInt("min_version", 0)?.takeIf { it > 0 }
                        ?: obj.optString("file_name", "").trim().toIntOrNull()
                        ?: obj.optInt("file_size", 0).takeIf { it > 0 }
                        ?: return null
                    val verName = obj.optString("caption", "")
                        .ifBlank { metaObj?.optString("version_name", "").orEmpty() }
                        .ifBlank { obj.optString("file_type", "$fileVer.0") }
                        .trim()
                        .ifBlank { "$fileVer.0" }
                    val url = metaObj?.optString("update_url", "").orEmpty()
                        .ifBlank { obj.optString("file_url", AppVersionConfig.DEFAULT_UPDATE_URL) }
                        .trim()
                        .ifBlank { AppVersionConfig.DEFAULT_UPDATE_URL }
                    val msg = metaObj?.optString("update_message", AppVersionConfig.DEFAULT_UPDATE_MESSAGE)
                        .orEmpty()
                        .trim()
                        .ifBlank { AppVersionConfig.DEFAULT_UPDATE_MESSAGE }
                    AppConfig(
                        id = 1,
                        minVersion = fileVer,
                        latestVersion = fileVer,
                        latestVersionName = verName,
                        updateUrl = url,
                        updateMessage = msg
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
