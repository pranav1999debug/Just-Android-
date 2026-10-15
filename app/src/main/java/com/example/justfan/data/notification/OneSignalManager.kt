package com.example.justfan.data.notification

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object OneSignalManager {
    private const val APP_ID = "1fbba4bb-4446-4bae-829d-36a74cd95adb"
    private const val PREFS = "onesignal_prefs"
    private const val KEY_DIALOG_SHOWN = "verification_dialog_shown"

    private var initialized = false
    private var pushSubscriptionObserver: IPushSubscriptionObserver? = null
    private var activityRef: WeakReference<Activity>? = null

    fun initialize(context: Context) {
        if (initialized) return
        OneSignal.initWithContext(context.applicationContext, APP_ID)
        initialized = true
    }

    fun setLogLevel(level: LogLevel) { OneSignal.Debug.logLevel = level }
    fun login(externalId: String) = OneSignal.login(externalId)
    fun logout() = OneSignal.logout()
    fun setEmail(email: String) = OneSignal.User.addEmail(email)
    fun setSmsNumber(number: String) = OneSignal.User.addSms(number)
    fun setTag(key: String, value: String) = OneSignal.User.addTag(key, value)

    fun requestPushPermission() {
        CoroutineScope(Dispatchers.Main).launch {
            OneSignal.Notifications.requestPermission(true)
        }
    }

    private fun isRegistered(id: String?): Boolean =
        !id.isNullOrEmpty() && !id.startsWith("local-")

    private fun alreadyShown(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DIALOG_SHOWN, false)

    private fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DIALOG_SHOWN, true).apply()
    }

    private fun maybeShowDialog(subscriptionId: String?) {
        val activity = activityRef?.get() ?: return
        if (!isRegistered(subscriptionId) || alreadyShown(activity)) return
        markShown(activity)
        Handler(Looper.getMainLooper()).post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            AlertDialog.Builder(activity)
                .setTitle("Your OneSignal SDK integration is complete!")
                .setMessage("You can now send Push Notifications & In-App Messages through OneSignal. Tap below to enable push notifications.")
                .setPositiveButton("Got it") { _, _ -> requestPushPermission() }
                .setCancelable(false)
                .show()
        }
    }

    fun setupPushSubscriptionObserver(activity: Activity) {
        activityRef = WeakReference(activity)
        pushSubscriptionObserver?.let { OneSignal.User.pushSubscription.removeObserver(it) }
        val observer = object : IPushSubscriptionObserver {
            override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
                maybeShowDialog(state.current.id)
            }
        }
        pushSubscriptionObserver = observer
        OneSignal.User.pushSubscription.addObserver(observer)
        maybeShowDialog(OneSignal.User.pushSubscription.id)
    }
}
