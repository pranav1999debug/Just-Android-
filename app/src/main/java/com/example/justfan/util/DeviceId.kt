package com.example.justfan.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

private var cachedDeviceId: String? = null

@SuppressLint("HardwareIds")
fun getDeviceId(context: Context): String {
    cachedDeviceId?.let { return it }
    val androidId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown_android_id"
    val input = androidId + "justfan-v1"
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }
    cachedDeviceId = hex
    return hex
}
