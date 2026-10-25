package com.example.justfan.data.notification

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.justfan.BuildConfig
import com.example.justfan.MainActivity
import com.example.justfan.R
import com.example.justfan.data.model.ActivityEntity
import com.example.justfan.data.model.PostEntity
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object OneSignalManager {
    private const val TAG = "OneSignalManager"
    const val APP_ID = "1fbba4bb-4446-4bae-829d-36a74cd95adb"
    private const val PREFS = "onesignal_prefs"
    private const val KEY_REST_API_KEY = "onesignal_rest_api_key"
    private const val CHANNEL_ID = "justfan_push_alerts"
    private const val CHANNEL_NAME = "JUSTFAN Push Notifications"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var initialized = false
    private var activityRef: WeakReference<Activity>? = null
    private var appContext: Context? = null

    private val _liveEvents = MutableStateFlow<List<ActivityEntity>>(emptyList())
    val liveEvents: StateFlow<List<ActivityEntity>> = _liveEvents.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    fun getAppId(): String = APP_ID

    fun initialize(context: Context) {
        if (initialized) return
        val appCtx = context.applicationContext
        appContext = appCtx
        initialized = true

        createNotificationChannel(appCtx)

        try {
            // Ensure OneSignal SDK v5 is initialized if not already called in Application.onCreate()
            OneSignal.consentRequired = false
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
            OneSignal.initWithContext(appCtx, APP_ID)

            _permissionGranted.value = OneSignal.Notifications.permission

            // Listen for foreground notifications so they appear in the system tray AND in-app feed
            OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
                override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                    val notification = event.notification
                    val title = notification.title ?: "JUSTFAN Update"
                    val body = notification.body ?: ""
                    val additional = notification.additionalData
                    val eventType = additional?.optString("type", "push_alert") ?: "push_alert"
                    val targetPostId = additional?.optString("post_id", null)

                    recordInAppEvent(
                        type = eventType,
                        title = title,
                        body = body,
                        targetPostId = targetPostId
                    )
                    // Display notification even while app is in foreground
                    event.notification.display()
                }
            })

            // Listen for notification clicks
            OneSignal.Notifications.addClickListener(object : INotificationClickListener {
                override fun onClick(event: INotificationClickEvent) {
                    val notification = event.notification
                    val title = notification.title ?: "Notification Opened"
                    val body = notification.body ?: ""
                    val additional = notification.additionalData
                    val eventType = additional?.optString("type", "push_clicked") ?: "push_clicked"
                    val targetPostId = additional?.optString("post_id", null)

                    recordInAppEvent(
                        type = eventType,
                        title = title,
                        body = body,
                        targetPostId = targetPostId
                    )
                }
            })

            Log.i(TAG, "OneSignal v5 initialized with App ID: $APP_ID")
        } catch (e: Throwable) {
            Log.w(TAG, "OneSignal v5 initialization note: ${e.message}")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time notifications for new posts, creator requests, and membership payments."
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Requests Android 13+ POST_NOTIFICATIONS runtime permission via OneSignal SDK v5 inside a coroutine.
     *
     * @param fallbackToSettings If true, prompts the user to open system notification settings if previously denied.
     */
    suspend fun requestPushPermissionSuspend(fallbackToSettings: Boolean = true): Boolean {
        return try {
            val granted = OneSignal.Notifications.requestPermission(fallbackToSettings)
            _permissionGranted.value = granted
            Log.i(TAG, "OneSignal.Notifications.requestPermission($fallbackToSettings) -> $granted")
            granted
        } catch (e: Throwable) {
            Log.w(TAG, "requestPushPermissionSuspend note: ${e.message}")
            false
        }
    }

    fun requestPushPermission(fallbackToSettings: Boolean = true) {
        scope.launch {
            requestPushPermissionSuspend(fallbackToSettings)
        }
    }

    fun setLogLevel(level: LogLevel = LogLevel.VERBOSE) {
        try {
            OneSignal.Debug.logLevel = level
        } catch (_: Throwable) {}
    }

    fun login(externalId: String) {
        if (externalId.isBlank() || externalId == "guest_user") return
        try {
            OneSignal.login(externalId)
        } catch (e: Throwable) {
            Log.w(TAG, "OneSignal.login note: ${e.message}")
        }
    }

    fun logout() {
        try {
            OneSignal.logout()
        } catch (e: Throwable) {
            Log.w(TAG, "OneSignal.logout note: ${e.message}")
        }
    }

    fun setEmail(email: String) {
        if (email.isBlank() || !email.contains("@")) return
        try {
            OneSignal.User.addEmail(email.trim().lowercase())
        } catch (e: Throwable) {
            Log.w(TAG, "OneSignal.User.addEmail note: ${e.message}")
        }
    }

    fun setSmsNumber(number: String) {
        if (number.isBlank()) return
        try {
            OneSignal.User.addSms(number.trim())
        } catch (_: Throwable) {}
    }

    fun setTag(key: String, value: String) {
        if (key.isBlank()) return
        try {
            OneSignal.User.addTag(key, value)
        } catch (_: Throwable) {}
    }

    fun setTags(tags: Map<String, String>) {
        if (tags.isEmpty()) return
        try {
            OneSignal.User.addTags(tags)
        } catch (_: Throwable) {}
    }

    /**
     * Fires OneSignal In-App Message / Automation Triggers via OneSignal SDK v5.
     */
    fun addTriggers(triggers: Map<String, String>) {
        if (triggers.isEmpty()) return
        try {
            OneSignal.InAppMessages.addTriggers(triggers)
            Log.i(TAG, "OneSignal triggers added: $triggers")
        } catch (e: Throwable) {
            Log.w(TAG, "OneSignal.InAppMessages.addTriggers note: ${e.message}")
        }
    }

    /**
     * Trigger 1: Fired when a New Post is published.
     * - Fires OneSignal v5 trigger `new_post`
     * - Broadcasts push notification to all subscribed users
     * - Displays local system notification & records in-app activity item
     */
    fun triggerNewPostNotification(post: PostEntity, context: Context? = null) {
        val accessLabel = if (post.isFree) "Free Gallery" else "VIP Exclusive"
        val title = "🔥 New Post: ${post.title}"
        val message = "By ${post.author} ($accessLabel) — Tap to view in JUSTFAN!"

        addTriggers(
            mapOf(
                "event_type" to "new_post",
                "new_post" to post.id,
                "post_title" to post.title,
                "post_author" to post.author,
                "is_free" to post.isFree.toString()
            )
        )
        setTag("last_seen_post", post.id)

        recordInAppEvent(
            type = "post_created",
            title = title,
            body = message,
            targetPostId = post.id
        )
        showLocalSystemNotification(
            context = context ?: appContext,
            title = title,
            message = message,
            targetPostId = post.id
        )
        sendNotification(
            title = title,
            message = message,
            data = mapOf(
                "type" to "post_created",
                "trigger" to "new_post",
                "post_id" to post.id,
                "title" to post.title
            ),
            bigPictureUrl = post.imageUrl,
            context = context
        )
    }

    /**
     * Trigger 2: Fired when a New Content Request is submitted.
     * - Fires OneSignal v5 trigger `new_request`
     * - Broadcasts push notification to all subscribed users
     * - Displays local system notification & records in-app activity item
     */
    fun triggerNewRequestNotification(
        name: String,
        email: String,
        requestMessage: String,
        imageUrl: String? = null,
        context: Context? = null
    ) {
        val cleanSummary = requestMessage.trim().take(90)
        val title = "📩 New Creator Request from $name"
        val message = if (cleanSummary.isNotBlank()) cleanSummary else "A new custom content request was just submitted."

        addTriggers(
            mapOf(
                "event_type" to "new_request",
                "new_request" to System.currentTimeMillis().toString(),
                "requester_name" to name
            )
        )
        setTag("last_request_status", "pending")

        recordInAppEvent(
            type = "new_request",
            title = title,
            body = message
        )
        showLocalSystemNotification(
            context = context ?: appContext,
            title = title,
            message = message
        )
        sendNotification(
            title = title,
            message = message,
            data = mapOf(
                "type" to "new_request",
                "trigger" to "new_request",
                "requester_name" to name,
                "requester_email" to email
            ),
            bigPictureUrl = imageUrl,
            context = context
        )
    }

    /**
     * Trigger 3: Fired when a New Payment Proof is submitted.
     * - Fires OneSignal v5 trigger `new_payment`
     * - Broadcasts push notification to all subscribed users
     * - Displays local system notification & records in-app activity item
     */
    fun triggerNewPaymentNotification(
        tier: String,
        method: String,
        amount: String,
        userEmail: String,
        proofImageUrl: String? = null,
        context: Context? = null
    ) {
        val displayUser = userEmail.substringBefore("@").ifBlank { "Member" }
        val title = "💎 New $tier Membership Payment ($amount)"
        val message = "$displayUser submitted a $tier upgrade payment via $method."

        addTriggers(
            mapOf(
                "event_type" to "new_payment",
                "new_payment" to tier,
                "payment_method" to method,
                "payment_amount" to amount
            )
        )
        setTag("pending_payment_tier", tier)

        recordInAppEvent(
            type = "new_payment",
            title = title,
            body = message
        )
        showLocalSystemNotification(
            context = context ?: appContext,
            title = title,
            message = message
        )
        sendNotification(
            title = title,
            message = message,
            data = mapOf(
                "type" to "new_payment",
                "trigger" to "new_payment",
                "tier" to tier,
                "method" to method,
                "amount" to amount,
                "email" to userEmail
            ),
            bigPictureUrl = proofImageUrl,
            context = context
        )
    }

    fun recordInAppEvent(
        type: String,
        title: String,
        body: String,
        targetPostId: String? = null
    ) {
        val event = ActivityEntity(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            body = body,
            targetPostId = targetPostId,
            createdAt = System.currentTimeMillis()
        )
        _liveEvents.value = (listOf(event) + _liveEvents.value).take(100)
    }

    private fun showLocalSystemNotification(
        context: Context?,
        title: String,
        message: String,
        targetPostId: String? = null
    ) {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return
        }

        try {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!targetPostId.isNullOrBlank()) {
                    putExtra("post_id", targetPostId)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                ctx,
                Random.nextInt(1000, 9999),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(ctx).notify(Random.nextInt(10000, 99999), notification)
        } catch (e: Throwable) {
            Log.w(TAG, "Local notification display note: ${e.message}")
        }
    }

    fun getRestApiKey(context: Context? = null): String? {
        val ctx = context ?: appContext
        val fromPrefs = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_REST_API_KEY, null)
        if (!fromPrefs.isNullOrBlank() && fromPrefs != "YOUR_ONESIGNAL_REST_API_KEY") return fromPrefs.trim()
        val fromConfig = try { BuildConfig.ONESIGNAL_REST_API_KEY } catch (_: Throwable) { "" }
        if (fromConfig.isNotBlank() && fromConfig != "YOUR_ONESIGNAL_REST_API_KEY") return fromConfig.trim()
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
        bigPictureUrl: String? = null,
        context: Context? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val targetContext = context ?: appContext
        var apiKey = getRestApiKey(targetContext)

        if (apiKey.isNullOrBlank()) {
            apiKey = try {
                com.example.justfan.data.remote.SecretsStore.getOrNull("onesignal_rest_api_key")
                    ?: com.example.justfan.data.remote.SecretsStore.getOrNull("onesignal_api_key")
                    ?: com.example.justfan.data.remote.SecretsStore.getOrNull("ONESIGNAL_REST_API_KEY")
            } catch (_: Exception) {
                null
            }
        }

        if (apiKey.isNullOrBlank()) {
            Log.i(
                TAG,
                "OneSignal trigger & local notification dispatched for \"$title\" (set ONESIGNAL_REST_API_KEY in Secrets or app_secrets for cross-device REST broadcast)."
            )
            return@withContext Result.success(true)
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
                if (!bigPictureUrl.isNullOrBlank() && !bigPictureUrl.endsWith(".mp4", ignoreCase = true)) {
                    put("big_picture", bigPictureUrl)
                }
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
            Log.i(TAG, "OneSignal push notification broadcasted: $title - Response: $responseText")
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
        bigPictureUrl: String? = null,
        context: Context? = null
    ) {
        scope.launch {
            sendPushNotificationToAll(title, message, data, bigPictureUrl, context)
        }
    }

    fun setupPushSubscriptionObserver(activity: Activity) {
        activityRef = WeakReference(activity)
    }
}
