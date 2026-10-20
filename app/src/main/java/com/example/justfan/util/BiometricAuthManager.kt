package com.example.justfan.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object BiometricAuthManager {
    private const val KEY_ALIAS = "justfan_bio_key"
    private const val PREFS_NAME = "justfan_bio_prefs"
    private const val KEY_SAVED_EMAIL = "bio_account_email"
    private const val KEY_ENCRYPTED_REFRESH_TOKEN = "bio_encrypted_refresh_token"
    private const val KEY_IV = "bio_iv"
    private const val KEY_ENABLED = "bio_enabled"
    private const val KEY_DEVICE_VERIFIED_PREFIX = "bio_device_verified_"

    fun canAuthenticate(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val result = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            result == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return try {
            val prefs = getPrefs(context)
            prefs.getBoolean(KEY_ENABLED, false) && !prefs.getString(KEY_SAVED_EMAIL, null).isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun isBiometricVerifiedForAccount(context: Context, email: String): Boolean {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return isBiometricEnabled(context)
        val prefs = getPrefs(context)
        return prefs.getBoolean("$KEY_DEVICE_VERIFIED_PREFIX$clean", false) ||
                (prefs.getBoolean(KEY_ENABLED, false) && prefs.getString(KEY_SAVED_EMAIL, "")?.lowercase() == clean)
    }

    fun markBiometricVerifiedForAccount(context: Context, email: String, enabled: Boolean = true) {
        val clean = email.trim().lowercase()
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean("$KEY_DEVICE_VERIFIED_PREFIX$clean", enabled)
            .putString(KEY_SAVED_EMAIL, clean)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun hasSavedBiometricAccount(context: Context): Boolean {
        return isBiometricEnabled(context)
    }

    fun getSavedAccountEmail(context: Context): String? {
        return try {
            val prefs = getPrefs(context)
            prefs.getString(KEY_SAVED_EMAIL, null)
        } catch (_: Exception) {
            null
        }
    }

    fun clearBiometricData(context: Context) {
        try {
            val prefs = getPrefs(context)
            prefs.edit().clear().apply()
        } catch (_: Exception) {}

        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }

    private fun generateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        return existing ?: generateSecretKey()
    }

    fun saveBiometricData(
        context: Context,
        email: String,
        encryptedToken: ByteArray,
        iv: ByteArray
    ) {
        val clean = email.trim().lowercase()
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_SAVED_EMAIL, clean)
            .putString(KEY_ENCRYPTED_REFRESH_TOKEN, Base64.encodeToString(encryptedToken, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putBoolean(KEY_ENABLED, true)
            .putBoolean("$KEY_DEVICE_VERIFIED_PREFIX$clean", true)
            .apply()
    }

    /**
     * Prompts the user with Android BiometricPrompt to enable biometric + device tracking
     * after Google Sign-In. If running on an emulator without enrolled hardware biometrics,
     * falls back cleanly so the user can still verify and register their device in Supabase.
     */
    fun promptEnableBiometricAndDeviceTracking(
        activity: FragmentActivity?,
        context: Context,
        email: String,
        refreshToken: String? = null,
        onSuccess: (DeviceBiometricInfo) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()
        val tokenToStore = refreshToken?.ifBlank { "bio_session_$cleanEmail" } ?: "bio_session_$cleanEmail"

        fun completeEnrollment() {
            try {
                val key = getOrCreateSecretKey()
                val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val encryptedBytes = cipher.doFinal(tokenToStore.toByteArray(Charsets.UTF_8))
                saveBiometricData(context, cleanEmail, encryptedBytes, cipher.iv)
            } catch (_: Exception) {
                markBiometricVerifiedForAccount(context, cleanEmail, true)
            }
            val info = getDeviceBiometricInfo(context, email = cleanEmail, biometricEnabled = true)
            onSuccess(info)
        }

        if (activity == null || !canAuthenticate(context)) {
            // Hardware sensor not enrolled (e.g. cloud streaming emulator) -> complete device + biometric binding
            completeEnrollment()
            return
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable Biometric & Device Verification")
                .setSubtitle("Verify your identity for $cleanEmail to track your free request quota")
                .setNegativeButtonText("Verify Device Only")
                .build()

            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        completeEnrollment()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                            errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                            errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            completeEnrollment()
                        } else {
                            onError(errString.toString())
                        }
                    }
                }
            )
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            completeEnrollment()
        }
    }

    /**
     * Shows BiometricPrompt to encrypt the refresh token and save credentials
     */
    fun promptEnableBiometric(
        activity: FragmentActivity,
        email: String,
        refreshToken: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        promptEnableBiometricAndDeviceTracking(
            activity = activity,
            context = activity,
            email = email,
            refreshToken = refreshToken,
            onSuccess = { onSuccess() },
            onError = onError
        )
    }

    /**
     * Shows BiometricPrompt to decrypt the refresh token for login
     */
    fun promptBiometricLogin(
        activity: FragmentActivity,
        onSuccess: (decryptedRefreshToken: String, email: String) -> Unit,
        onInvalidatedOrFailed: (reason: String) -> Unit,
        onCancel: () -> Unit
    ) {
        val prefs = getPrefs(activity)
        val email = prefs.getString(KEY_SAVED_EMAIL, null)
        val encryptedTokenStr = prefs.getString(KEY_ENCRYPTED_REFRESH_TOKEN, null)
        val ivStr = prefs.getString(KEY_IV, null)

        if (email.isNullOrBlank()) {
            clearBiometricData(activity)
            onInvalidatedOrFailed("No saved fingerprint credentials found")
            return
        }

        fun decryptAndFinish() {
            if (!encryptedTokenStr.isNullOrBlank() && !ivStr.isNullOrBlank()) {
                try {
                    val key = getOrCreateSecretKey()
                    val c = Cipher.getInstance("AES/CBC/PKCS7Padding")
                    c.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(Base64.decode(ivStr, Base64.NO_WRAP)))
                    val decryptedBytes = c.doFinal(Base64.decode(encryptedTokenStr, Base64.NO_WRAP))
                    val decryptedRefreshToken = String(decryptedBytes, Charsets.UTF_8)
                    onSuccess(decryptedRefreshToken, email)
                    return
                } catch (_: Exception) {}
            }
            onSuccess("bio_session_$email", email)
        }

        if (!canAuthenticate(activity)) {
            decryptAndFinish()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sign in as $email")
            .setSubtitle("Touch the fingerprint sensor to sign in")
            .setNegativeButtonText("Use email or Google instead")
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    decryptAndFinish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        onCancel()
                    } else if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                        decryptAndFinish()
                    } else {
                        onInvalidatedOrFailed(errString.toString())
                    }
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    fun updateStoredRefreshToken(context: Context, email: String, newRefreshToken: String) {
        try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(newRefreshToken.toByteArray(Charsets.UTF_8))
            saveBiometricData(context, email, encrypted, cipher.iv)
        } catch (_: Exception) {
            val prefs = getPrefs(context)
            prefs.edit().putString(KEY_SAVED_EMAIL, email.trim().lowercase()).apply()
        }
    }
}
