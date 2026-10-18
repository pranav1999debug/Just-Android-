package com.example.justfan.data.notification

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.justfan.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object OneSignalManager {
    private const val TAG = "OneSignalManager"
    private const val APP_ID = "1fbba4bb-4446-4bae-829d-36a74cd95adb"
    private const val PREFS = "onesignal_prefs"
    private const val KEY_DIALOG_SHOWN = "verification_dialog_shown"
    private const val KEY_REST_API_KEY = "onesignal_rest_api_key"

    private var initialized = false
    private var activityRef: WeakReference<Activity>? = null
    private var appContext: Context? = null

    fun getAppId(): String = APP_ID

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
        Log.i(TAG, "Notification service initialized")
    }

    fun setLogLevel(level: Any?) {}
    fun login(externalId: String) {}
    fun logout() {}
    fun setEmail(email: String) {}
    fun setSmsNumber(number: String) {}
    fun setTag(key: String, value: String) {}

    fun requestPushPermission() {}

    fun getRestApiKey(context: Context? = null): String? {
        val ctx = context ?: appContext
        val fromPrefs = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_REST_API_KEY, null)
        if (!fromPrefs.isNullOrBlank()) return fromPrefs.trim()
        val fromConfig = try { BuildConfig.ONESIGNAL_REST_API_KEY } catch (_: Throwable) { "" }
        if (fromConfig.isNotBlank()) return fromConfig.trim()
        return null
    }

    fun setRestApiKey(context: Context, key: String) {
        val ctx = context.applicationContext ?: context
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REST_API_KEY, key.trim())
            .apply()
    }

    /**
     * Sends a push notification to all subscribed users via OneSignal REST API.
     */
    suspend fun sendPushNotificationToAll(
        title: String,
        message: String,
        data: Map<String, String> = emptyMap(),
        context: Context? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val targetContext = context ?: appContext
        var apiKey = getRestApiKey(targetContext)

        // If not in SharedPreferences or BuildConfig, check SecretsStore
        if (apiKey.isNullOrBlank()) {
            apiKey = try {
                com.example.justfan.data.remote.SecretsStore.getOrNull("onesignal_rest_api_key")
                    ?: com.example.justfan.data.remote.SecretsStore.getOrNull("onesignal_api_key")
            } catch (_: Exception) {
                null
            }
        }

        if (apiKey.isNullOrBlank()) {
            Log.w(
                TAG,
                "OneSignal REST API key is not configured. Push notification (\"$title\") was not broadcast."
            )
            return@withContext Result.failure(
                IllegalStateException("OneSignal REST API key not configured. Set it in Executive Dashboard or app_secrets.")
            )
        }

        try {
            val url = URL("https://onesignal.com/api/v1/notifications")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val cleanKey = apiKey.trim()
                val authHeader = if (cleanKey.startsWith("Key ") || cleanKey.startsWith("Basic ")) cleanKey else "Key $cleanKey"
                setRequestProperty("Authorization", authHeader)
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
            }

            val payload = JSONObject().apply {
                put("app_id", APP_ID)
                put("included_segments", JSONArray().apply { put("Subscribed Users") })
                put("headings", JSONObject().apply { put("en", title) })
                put("contents", JSONObject().apply { put("en", message) })
                if (data.isNotEmpty()) {
                    val customData = JSONObject()
                    data.forEach { (k, v) -> customData.put(k, v) }
                    put("data", customData)
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
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
                Log.e(TAG, "OneSignal send notification failed ($code): $err")
                return@withContext Result.failure(Exception("OneSignal broadcast failed ($code): $err"))
            }
            conn.disconnect()
            Log.i(TAG, "OneSignal push notification broadcasted successfully: $title - Response: $responseText")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting notification", e)
            Result.failure(e)
        }
    }

    /**
     * Fire-and-forget helper to broadcast notifications from background/coroutines.
     */
    fun sendNotification(
        title: String,
        message: String,
        data: Map<String, String> = emptyMap(),
        context: Context? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            sendPushNotificationToAll(title, message, data, context)
        }
    }

    fun setupPushSubscriptionObserver(activity: Activity) {
        activityRef = WeakReference(activity)
    }
}
