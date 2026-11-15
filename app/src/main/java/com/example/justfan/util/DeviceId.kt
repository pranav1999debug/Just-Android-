package com.example.justfan.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

data class DeviceBiometricInfo(
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkInt: Int,
    val biometricEnabled: Boolean,
    val userEmail: String = "",
    val verifiedAt: Long = System.currentTimeMillis()
)

private var cachedDeviceId: String? = null

@SuppressLint("HardwareIds")
fun getDeviceId(context: Context): String {
    cachedDeviceId?.let { return it }
    val androidId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown_android_id"
    val input = "$androidId:${Build.MANUFACTURER}:${Build.MODEL}:justfan-v1"
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
    cachedDeviceId = hex
    return hex
}

fun getDeviceBiometricInfo(context: Context, email: String = "", biometricEnabled: Boolean = false): DeviceBiometricInfo {
    return DeviceBiometricInfo(
        deviceId = getDeviceId(context),
        manufacturer = Build.MANUFACTURER ?: "Android",
        model = Build.MODEL ?: "Device",
        osVersion = "Android ${Build.VERSION.RELEASE ?: "Unknown"}",
        sdkInt = Build.VERSION.SDK_INT,
        biometricEnabled = biometricEnabled,
        userEmail = email
    )
}

/**
 * Generates a persistent `anon_<6_digits>` identifier derived deterministically from the device ID
 * for unsigned users placing comments.
 */
fun getAnonCommentUserId(context: Context): String {
    val prefs = context.getSharedPreferences("justfan_prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("anon_comment_user_id", null)
    if (!existing.isNullOrBlank() && existing.startsWith("anon_")) {
        return existing
    }
    val devId = getDeviceId(context)
    var hash = 0L
    for (ch in devId) {
        hash = (hash * 31L + ch.code) and 0x7FFFFFFFL
    }
    val sixDigits = (100000L + (hash % 900000L)).toString()
    val generated = "anon_$sixDigits"
    prefs.edit().putString("anon_comment_user_id", generated).apply()
    return generated
}

