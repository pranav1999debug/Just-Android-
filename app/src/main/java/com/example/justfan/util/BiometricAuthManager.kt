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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    fun canAuthenticate(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return try {
            val prefs = getEncryptedPrefs(context)
            prefs.getBoolean(KEY_ENABLED, false) && !prefs.getString(KEY_SAVED_EMAIL, null).isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun hasSavedBiometricAccount(context: Context): Boolean {
        return isBiometricEnabled(context)
    }

    fun getSavedAccountEmail(context: Context): String? {
        return try {
            val prefs = getEncryptedPrefs(context)
            prefs.getString(KEY_SAVED_EMAIL, null)
        } catch (_: Exception) {
            null
        }
    }

    fun clearBiometricData(context: Context) {
        try {
            val prefs = getEncryptedPrefs(context)
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
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    fun saveBiometricData(
        context: Context,
        email: String,
        encryptedToken: ByteArray,
        iv: ByteArray
    ) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .putString(KEY_SAVED_EMAIL, email)
            .putString(KEY_ENCRYPTED_REFRESH_TOKEN, Base64.encodeToString(encryptedToken, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putBoolean(KEY_ENABLED, true)
            .apply()
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
        val cipher = try {
            generateSecretKey()
            val c = Cipher.getInstance("AES/CBC/PKCS7Padding")
            c.init(Cipher.ENCRYPT_MODE, getSecretKey()!!)
            c
        } catch (e: Exception) {
            onError(e.message ?: "Failed to initialize biometric security key")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable Fingerprint Login")
            .setSubtitle("Confirm your fingerprint to enable quick sign-in")
            .setNegativeButtonText("Cancel")
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    try {
                        val authCipher = result.cryptoObject?.cipher ?: cipher
                        val encryptedBytes = authCipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
                        val iv = authCipher.iv
                        saveBiometricData(activity, email, encryptedBytes, iv)
                        onSuccess()
                    } catch (e: Exception) {
                        onError(e.message ?: "Failed to encrypt credentials")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
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
        val prefs = try {
            getEncryptedPrefs(activity)
        } catch (e: Exception) {
            clearBiometricData(activity)
            onInvalidatedOrFailed("Failed to access saved biometric data")
            return
        }

        val email = prefs.getString(KEY_SAVED_EMAIL, null)
        val encryptedTokenStr = prefs.getString(KEY_ENCRYPTED_REFRESH_TOKEN, null)
        val ivStr = prefs.getString(KEY_IV, null)

        if (email.isNullOrBlank() || encryptedTokenStr.isNullOrBlank() || ivStr.isNullOrBlank()) {
            clearBiometricData(activity)
            onInvalidatedOrFailed("No saved fingerprint credentials found")
            return
        }

        val encryptedTokenBytes = Base64.decode(encryptedTokenStr, Base64.NO_WRAP)
        val ivBytes = Base64.decode(ivStr, Base64.NO_WRAP)

        val cipher = try {
            val key = getSecretKey()
            if (key == null) {
                clearBiometricData(activity)
                onInvalidatedOrFailed("Biometric key missing or reset")
                return
            }
            val c = Cipher.getInstance("AES/CBC/PKCS7Padding")
            c.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(ivBytes))
            c
        } catch (_: KeyPermanentlyInvalidatedException) {
            clearBiometricData(activity)
            onInvalidatedOrFailed("Biometric enrollment changed. Please sign in again.")
            return
        } catch (e: Exception) {
            clearBiometricData(activity)
            onInvalidatedOrFailed("Biometric setup invalid. Please sign in again.")
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
                    try {
                        val authCipher = result.cryptoObject?.cipher ?: cipher
                        val decryptedBytes = authCipher.doFinal(encryptedTokenBytes)
                        val decryptedRefreshToken = String(decryptedBytes, Charsets.UTF_8)
                        onSuccess(decryptedRefreshToken, email)
                    } catch (e: Exception) {
                        clearBiometricData(activity)
                        onInvalidatedOrFailed("Decryption failed. Please sign in again.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        onCancel()
                    } else {
                        onInvalidatedOrFailed(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    /**
     * Re-encrypts and updates the refresh token after a successful refresh_token grant
     */
    fun updateStoredRefreshToken(context: Context, email: String, newRefreshToken: String) {
        try {
            val key = getSecretKey()
            if (key != null) {
                val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val encrypted = cipher.doFinal(newRefreshToken.toByteArray(Charsets.UTF_8))
                saveBiometricData(context, email, encrypted, cipher.iv)
            }
        } catch (_: Exception) {
            // If the key requires authentication for encryption, retain updated token in encrypted prefs
            val prefs = getEncryptedPrefs(context)
            prefs.edit().putString(KEY_SAVED_EMAIL, email).apply()
        }
    }
}
