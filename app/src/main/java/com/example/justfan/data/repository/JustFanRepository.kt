package com.example.justfan.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.justfan.data.local.AppDatabase
import com.example.justfan.data.model.FavoriteEntity
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.RequestQuota
import com.example.justfan.data.model.UserEntity
import com.example.justfan.data.model.UserPreferencesState
import com.example.justfan.data.model.UserProfile
import com.example.justfan.data.notification.OneSignalManager
import com.example.justfan.data.remote.SupabaseClient
import com.example.justfan.util.BiometricAuthManager
import com.example.justfan.util.DeviceBiometricInfo
import com.example.justfan.util.getDeviceBiometricInfo
import com.example.justfan.util.getDeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

class JustFanRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = AppDatabase.getDatabase(context)
    private val postDao = database.postDao()
    private val requestDao = database.requestDao()
    private val userDao = database.userDao()
    private val favoriteDao = database.favoriteDao()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("justfan_prefs", Context.MODE_PRIVATE)

    val allPosts: Flow<List<PostEntity>> = postDao.getAllPosts()
    val allRequests: Flow<List<RequestEntity>> = requestDao.getAllRequests()
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()
    val favorites: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

    private val _userProfile = MutableStateFlow(loadProfileFromPrefs())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _preferences = MutableStateFlow(loadPreferencesFromPrefs())
    val preferences: StateFlow<UserPreferencesState> = _preferences.asStateFlow()

    private val _requestQuota = MutableStateFlow(RequestQuota(unlimited = false, limit = 3, used = 0, remaining = 3))
    val requestQuota: StateFlow<RequestQuota> = _requestQuota.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _deviceBiometricInfo = MutableStateFlow(
        getDeviceBiometricInfo(
            context = context,
            email = _userProfile.value.email,
            biometricEnabled = BiometricAuthManager.isBiometricVerifiedForAccount(context, _userProfile.value.email)
        )
    )
    val deviceBiometricInfo: StateFlow<DeviceBiometricInfo> = _deviceBiometricInfo.asStateFlow()

    private val _showBiometricPromptAfterLogin = MutableStateFlow(false)
    val showBiometricPromptAfterLogin: StateFlow<Boolean> = _showBiometricPromptAfterLogin.asStateFlow()

    private val validThemeVariants = setOf("cyan", "purple", "crimson", "emerald", "gold", "rose")

    init {
        scope.launch {
            syncData()
            fetchRequestQuota()
            val current = _userProfile.value
            if (current.isSignedIn && current.id != "guest_user") {
                syncUserDataOnLogin(current)
            }
        }
    }

    private fun loadProfileFromPrefs(): UserProfile {
        val isSignedIn = prefs.getBoolean("user_signed_in", false)
        val email = (prefs.getString("user_email", "") ?: "").trim().lowercase()
        val isRey = email.equals("reytherapper12@gmail.com", ignoreCase = true)
        val rawTier = prefs.getString("user_tier", if (isRey && isSignedIn) "Legendary" else "Free") ?: "Free"
        val rawProExpiresAt = prefs.getLong("user_pro_expires_at", 0L)

        // Automatically downgrade Pro to Free if 1-month period has expired
        val isProExpired = rawTier.equals("Pro", ignoreCase = true) &&
                rawProExpiresAt > 0L &&
                rawProExpiresAt != Long.MAX_VALUE &&
                rawProExpiresAt < System.currentTimeMillis()

        val resolvedTier = when {
            isRey && isSignedIn -> "Legendary"
            isProExpired -> "Free"
            else -> rawTier
        }
        val resolvedExpiresAt = if (isProExpired) 0L else rawProExpiresAt

        return UserProfile(
            id = prefs.getString("user_id", "guest_user") ?: "guest_user",
            username = prefs.getString("user_username", "Guest Fan") ?: "Guest Fan",
            email = email,
            isSignedIn = isSignedIn,
            authMethod = prefs.getString("user_auth_method", "guest") ?: "guest",
            tier = resolvedTier,
            isAdmin = isRey && isSignedIn,
            proExpiresAt = resolvedExpiresAt,
            accessToken = prefs.getString("user_access_token", null) ?: prefs.getString("cached_token_$email", null),
            refreshToken = prefs.getString("user_refresh_token", null) ?: prefs.getString("cached_refresh_$email", null),
            customWallpaperUri = prefs.getString("user_wallpaper_uri", null)
        )
    }

    private fun saveProfileToPrefs(profile: UserProfile) {
        val cleanEmail = profile.email.trim().lowercase()
        val editor = prefs.edit()
            .putBoolean("user_signed_in", profile.isSignedIn)
            .putString("user_id", profile.id)
            .putString("user_username", profile.username)
            .putString("user_email", cleanEmail)
            .putString("user_auth_method", profile.authMethod)
            .putString("user_tier", profile.tier)
            .putBoolean("user_is_admin", profile.isAdmin && cleanEmail == "reytherapper12@gmail.com")
            .putLong("user_pro_expires_at", profile.proExpiresAt)
            .putString("user_access_token", profile.accessToken)
            .putString("user_refresh_token", profile.refreshToken)
            .putString("user_wallpaper_uri", profile.customWallpaperUri)

        if (profile.isSignedIn && cleanEmail.isNotBlank()) {
            editor.putString("cached_uid_$cleanEmail", profile.id)
            if (!profile.accessToken.isNullOrBlank()) {
                editor.putString("cached_token_$cleanEmail", profile.accessToken)
            }
            if (!profile.refreshToken.isNullOrBlank()) {
                editor.putString("cached_refresh_$cleanEmail", profile.refreshToken)
            }
        }
        editor.apply()
    }

    private fun loadPreferencesFromPrefs(): UserPreferencesState {
        return UserPreferencesState(
            themeVariant = prefs.getString("pref_theme_variant", "cyan") ?: "cyan",
            isDarkMode = prefs.getBoolean("pref_dark_mode", true),
            customWallpaperUri = prefs.getString("pref_wallpaper_uri", null),
            wallpaperDim = prefs.getFloat("pref_wallpaper_dim", 0.65f)
        )
    }

    private fun savePreferencesToPrefs(state: UserPreferencesState, accountEmail: String? = _userProfile.value.email) {
        val editor = prefs.edit()
            .putString("pref_theme_variant", state.themeVariant)
            .putBoolean("pref_dark_mode", state.isDarkMode)
            .putString("pref_wallpaper_uri", state.customWallpaperUri)
            .putFloat("pref_wallpaper_dim", state.wallpaperDim)

        val cleanEmail = accountEmail?.trim()?.lowercase().orEmpty()
        if (cleanEmail.isNotBlank() && _userProfile.value.isSignedIn) {
            editor
                .putBoolean("account_pref_saved_$cleanEmail", true)
                .putString("account_pref_variant_$cleanEmail", state.themeVariant)
                .putBoolean("account_pref_dark_$cleanEmail", state.isDarkMode)
                .putString("account_pref_wallpaper_$cleanEmail", state.customWallpaperUri)
                .putFloat("account_pref_dim_$cleanEmail", state.wallpaperDim)
        }
        editor.apply()
    }

    private fun loadAccountPreferences(email: String): UserPreferencesState? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !prefs.getBoolean("account_pref_saved_$cleanEmail", false)) {
            return null
        }
        return UserPreferencesState(
            themeVariant = prefs.getString("account_pref_variant_$cleanEmail", "cyan") ?: "cyan",
            isDarkMode = prefs.getBoolean("account_pref_dark_$cleanEmail", true),
            customWallpaperUri = prefs.getString("account_pref_wallpaper_$cleanEmail", null),
            wallpaperDim = prefs.getFloat("account_pref_dim_$cleanEmail", 0.65f)
        )
    }

    fun updateTheme(themeVariant: String, isDark: Boolean) {
        val updated = _preferences.value.copy(themeVariant = themeVariant, isDarkMode = isDark)
        _preferences.value = updated
        savePreferencesToPrefs(updated)

        val currentProfile = _userProfile.value
        if (currentProfile.isSignedIn && currentProfile.id != "guest_user") {
            scope.launch {
                val activeToken = ensureValidAccessToken(currentProfile)
                SupabaseClient.saveUserPreferences(
                    userId = currentProfile.id,
                    theme = if (isDark) "dark" else "light",
                    themeVariant = themeVariant,
                    wallpaperUrl = updated.customWallpaperUri,
                    wallpaperOverlay = updated.wallpaperDim,
                    accessToken = activeToken
                )
            }
        }
    }

    fun updateWallpaper(wallpaperUri: String?, dim: Float = 0.65f) {
        val updatedPref = _preferences.value.copy(customWallpaperUri = wallpaperUri, wallpaperDim = dim)
        _preferences.value = updatedPref
        savePreferencesToPrefs(updatedPref)

        val updatedProfile = _userProfile.value.copy(customWallpaperUri = wallpaperUri)
        _userProfile.value = updatedProfile
        saveProfileToPrefs(updatedProfile)

        val currentProfile = _userProfile.value
        if (currentProfile.isSignedIn && currentProfile.id != "guest_user") {
            scope.launch {
                val activeToken = ensureValidAccessToken(currentProfile)
                SupabaseClient.saveUserPreferences(
                    userId = currentProfile.id,
                    theme = if (updatedPref.isDarkMode) "dark" else "light",
                    themeVariant = updatedPref.themeVariant,
                    wallpaperUrl = wallpaperUri,
                    wallpaperOverlay = dim,
                    accessToken = activeToken
                )
            }
        }
    }

    private suspend fun ensureValidAccessToken(profile: UserProfile): String? {
        val currentToken = profile.accessToken
        val refresh = profile.refreshToken ?: prefs.getString("cached_refresh_${profile.email.trim().lowercase()}", null)
        if (!refresh.isNullOrBlank()) {
            val refreshed = SupabaseClient.refreshAccessToken(refresh).getOrNull()
            if (refreshed != null && refreshed.accessToken.isNotBlank()) {
                val updated = _userProfile.value.copy(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken ?: refresh,
                    id = if (refreshed.userId.isNotBlank()) refreshed.userId else profile.id
                )
                _userProfile.value = updated
                saveProfileToPrefs(updated)
                return refreshed.accessToken
            }
        }
        return currentToken
    }

    suspend fun syncData() {
        _isRefreshing.value = true
        try {
            val hardwareDeviceId = getDeviceId(context)

            // 0. If this is a fresh install / reinstall (`guest_user` and not explicitly signed out locally),
            //    check Supabase for an active device binding for this hardware ANDROID_ID and restore the account & tier!
            val initialProfile = _userProfile.value
            val explicitlySignedOutLocally = prefs.getBoolean("explicitly_signed_out", false)
            if ((!initialProfile.isSignedIn || initialProfile.id == "guest_user") && !explicitlySignedOutLocally) {
                val remoteBinding = SupabaseClient.fetchDeviceBindingFromSupabase(hardwareDeviceId)
                if (remoteBinding != null && remoteBinding.email.isNotBlank()) {
                    val cleanEmail = remoteBinding.email.trim().lowercase()
                    val isRey = cleanEmail == "reytherapper12@gmail.com"
                    val subDetails = SupabaseClient.fetchUserSubscriptionDetails(
                        userId = remoteBinding.userId,
                        email = cleanEmail,
                        accessToken = null,
                        deviceId = hardwareDeviceId
                    ).getOrNull()
                    val resolvedTier = subDetails?.tier ?: if (isRey) "Legendary" else remoteBinding.tier
                    val resolvedExpires = when {
                        resolvedTier.equals("Legendary", ignoreCase = true) -> Long.MAX_VALUE
                        resolvedTier.equals("Pro", ignoreCase = true) -> subDetails?.expiresAtMillis?.takeIf { it > 0L }
                            ?: (System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000L)
                        else -> 0L
                    }
                    val restoredProfile = UserProfile(
                        id = remoteBinding.userId,
                        username = if (isRey) "reytherapper12 (Admin)" else remoteBinding.username,
                        email = cleanEmail,
                        isSignedIn = true,
                        authMethod = remoteBinding.authMethod,
                        tier = resolvedTier,
                        isAdmin = isRey,
                        proExpiresAt = resolvedExpires
                    )
                    _userProfile.value = restoredProfile
                    saveProfileToPrefs(restoredProfile)
                    prefs.edit()
                        .putString("account_tier_$cleanEmail", resolvedTier)
                        .putLong("account_expires_$cleanEmail", resolvedExpires)
                        .apply()
                }
            }

            // 1. Sync Posts (includes click counts from post_click_counts across all paginated batches)
            val postsResult = SupabaseClient.fetchPosts()
            if (postsResult.isSuccess) {
                val remotePosts = postsResult.getOrNull().orEmpty()
                if (remotePosts.isNotEmpty()) {
                    val remoteIds = remotePosts.map { it.id }.toSet()
                    val existingLocal = postDao.getAllPostsList()
                    existingLocal.forEach { localPost ->
                        if (localPost.id !in remoteIds) {
                            postDao.deletePost(localPost.id)
                        }
                    }
                    remotePosts.chunked(500).forEach { chunk ->
                        postDao.insertPosts(chunk)
                    }
                }
            }

            // 2. Sync Requests
            val reqResult = SupabaseClient.fetchRequests()
            if (reqResult.isSuccess) {
                val remoteReqs = reqResult.getOrNull().orEmpty()
                if (remoteReqs.isNotEmpty()) {
                    requestDao.insertRequests(remoteReqs)
                }
            }

            // 3. Sync Users & Subscriptions
            val profilesResult = SupabaseClient.fetchProfiles()
            if (profilesResult.isSuccess) {
                val remoteUsers = profilesResult.getOrNull().orEmpty()
                if (remoteUsers.isNotEmpty()) {
                    userDao.insertUsers(remoteUsers)
                }
            }

            // 4. Refresh active signed-in user's subscription tier if changed remotely
            val current = _userProfile.value
            if (current.isSignedIn && current.id != "guest_user") {
                val cleanEmail = current.email.trim().lowercase()
                val activeToken = ensureValidAccessToken(current)
                val subDetails = SupabaseClient.fetchUserSubscriptionDetails(
                    userId = current.id,
                    email = cleanEmail,
                    accessToken = activeToken,
                    deviceId = hardwareDeviceId
                ).getOrNull()
                if (subDetails != null) {
                    val updated = current.copy(
                        tier = subDetails.tier,
                        proExpiresAt = subDetails.expiresAtMillis
                    )
                    _userProfile.value = updated
                    saveProfileToPrefs(updated)
                    if (cleanEmail.isNotBlank()) {
                        prefs.edit()
                            .putString("account_tier_$cleanEmail", subDetails.tier)
                            .putLong("account_expires_$cleanEmail", subDetails.expiresAtMillis)
                            .apply()
                    }
                    userDao.updateUserTier(current.id, subDetails.tier)
                }
            }
        } catch (e: Exception) {
            Log.e("JustFanRepository", "syncData error", e)
        } finally {
            _isRefreshing.value = false
        }
    }

    fun syncUserDataOnLogin(profile: UserProfile) {
        if (!profile.isSignedIn || profile.id == "guest_user") return
        val cleanEmail = profile.email.trim().lowercase()
        prefs.edit().putBoolean("explicitly_signed_out", false).apply()

        // 0. Immediately restore saved per-account preferences so theme & wallpaper apply right away
        val cachedAccountPref = loadAccountPreferences(cleanEmail)
        if (cachedAccountPref != null) {
            _preferences.value = cachedAccountPref
            savePreferencesToPrefs(cachedAccountPref, cleanEmail)
            if (!cachedAccountPref.customWallpaperUri.isNullOrBlank()) {
                _userProfile.value = _userProfile.value.copy(customWallpaperUri = cachedAccountPref.customWallpaperUri)
                saveProfileToPrefs(_userProfile.value)
            }
        }

        // Restore cached per-account manual tier override immediately if present
        val cachedAccountTier = prefs.getString("account_tier_$cleanEmail", null)
        val cachedAccountExpires = prefs.getLong("account_expires_$cleanEmail", 0L)
        if (!cachedAccountTier.isNullOrBlank()) {
            val restoredProfile = _userProfile.value.copy(
                tier = cachedAccountTier,
                proExpiresAt = cachedAccountExpires
            )
            _userProfile.value = restoredProfile
            saveProfileToPrefs(restoredProfile)
        }

        scope.launch {
            try {
                val hardwareDeviceId = getDeviceId(context)
                val isRey = cleanEmail.equals("reytherapper12@gmail.com", ignoreCase = true)
                val activeToken = ensureValidAccessToken(_userProfile.value)
                val activeUserId = _userProfile.value.id

                // 1. Sync subscription tier from Supabase tier ledger, subscriptions, and users tables
                val subDetails = SupabaseClient.fetchUserSubscriptionDetails(
                    userId = activeUserId,
                    email = cleanEmail,
                    accessToken = activeToken,
                    deviceId = hardwareDeviceId
                ).getOrNull()
                val effectiveTier = when {
                    subDetails != null -> subDetails.tier
                    !cachedAccountTier.isNullOrBlank() -> cachedAccountTier
                    isRey -> "Legendary"
                    else -> _userProfile.value.tier
                }
                val isLegendary = effectiveTier.equals("legendary", ignoreCase = true)
                val isPro = effectiveTier.equals("pro", ignoreCase = true)
                val expiresAt = when {
                    isLegendary -> Long.MAX_VALUE
                    isPro -> subDetails?.expiresAtMillis?.takeIf { it > 0L }
                        ?: cachedAccountExpires.takeIf { it > 0L }
                        ?: (System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000L)
                    else -> 0L
                }
                val finalTier = when {
                    isLegendary -> "Legendary"
                    isPro -> "Pro"
                    else -> "Free"
                }

                val updatedProfile = _userProfile.value.copy(
                    tier = finalTier,
                    isAdmin = isRey,
                    proExpiresAt = expiresAt
                )
                _userProfile.value = updatedProfile
                saveProfileToPrefs(updatedProfile)
                prefs.edit()
                    .putString("account_tier_$cleanEmail", finalTier)
                    .putLong("account_expires_$cleanEmail", expiresAt)
                    .apply()
                userDao.updateUserTier(activeUserId, finalTier)

                // Sync identity & tags with OneSignal SDK v5
                OneSignalManager.login(activeUserId)
                OneSignalManager.setEmail(cleanEmail)
                OneSignalManager.setTags(
                    mapOf(
                        "tier" to finalTier,
                        "is_admin" to isRey.toString(),
                        "email" to cleanEmail
                    )
                )

                // Persist device binding in Supabase so even after uninstall & reinstall on this device,
                // the account and its upgraded tier are recovered automatically!
                SupabaseClient.saveDeviceBindingToSupabase(
                    deviceId = hardwareDeviceId,
                    email = cleanEmail,
                    userId = activeUserId,
                    username = updatedProfile.username,
                    authMethod = updatedProfile.authMethod,
                    tier = finalTier
                )

                // 2. Sync theme & wallpaper from Supabase user_preferences table
                val prefResult = SupabaseClient.fetchUserPreferences(activeUserId, activeToken)
                val remotePref = prefResult.getOrNull()
                if (remotePref != null) {
                    var newPref = _preferences.value
                    val resolvedVariant = when {
                        !remotePref.themeVariant.isNullOrBlank() && remotePref.themeVariant.lowercase() in validThemeVariants ->
                            remotePref.themeVariant.lowercase()
                        !remotePref.theme.isNullOrBlank() && remotePref.theme.lowercase() in validThemeVariants ->
                            remotePref.theme.lowercase()
                        else -> newPref.themeVariant
                    }
                    val resolvedDark = when (remotePref.theme?.lowercase()) {
                        "light" -> false
                        "dark" -> true
                        else -> newPref.isDarkMode
                    }
                    newPref = newPref.copy(
                        themeVariant = resolvedVariant,
                        isDarkMode = resolvedDark
                    )
                    if (!remotePref.wallpaperUrl.isNullOrBlank()) {
                        newPref = newPref.copy(customWallpaperUri = remotePref.wallpaperUrl)
                        _userProfile.value = _userProfile.value.copy(customWallpaperUri = remotePref.wallpaperUrl)
                        saveProfileToPrefs(_userProfile.value)
                    }
                    if (remotePref.wallpaperOverlay != null) {
                        newPref = newPref.copy(wallpaperDim = remotePref.wallpaperOverlay)
                    }
                    _preferences.value = newPref
                    savePreferencesToPrefs(newPref, cleanEmail)
                    Log.i("JustFanRepository", "Loaded remote preferences: variant=$resolvedVariant, dark=$resolvedDark, wallpaper=${remotePref.wallpaperUrl}")
                } else if (cachedAccountPref != null || _preferences.value.themeVariant != "cyan" || _preferences.value.customWallpaperUri != null) {
                    val currentPref = _preferences.value
                    SupabaseClient.saveUserPreferences(
                        userId = activeUserId,
                        theme = if (currentPref.isDarkMode) "dark" else "light",
                        themeVariant = currentPref.themeVariant,
                        wallpaperUrl = currentPref.customWallpaperUri,
                        wallpaperOverlay = currentPref.wallpaperDim,
                        accessToken = activeToken
                    )
                }

                // 3. Refresh remote profiles & request quota
                val profilesRes = SupabaseClient.fetchProfiles()
                if (profilesRes.isSuccess) {
                    profilesRes.getOrNull()?.takeIf { it.isNotEmpty() }?.let { userDao.insertUsers(it) }
                }
                fetchRequestQuota()
            } catch (e: Exception) {
                Log.w("JustFanRepository", "syncUserDataOnLogin warning", e)
            }
        }
    }

    suspend fun fetchRequestQuota(): RequestQuota {
        var current = _userProfile.value
        val hardwareDeviceId = getDeviceId(context)

        // Check if local Pro tier has expired past its 1-month window
        if (current.tier.equals("pro", ignoreCase = true) &&
            current.proExpiresAt > 0L &&
            current.proExpiresAt != Long.MAX_VALUE &&
            current.proExpiresAt < System.currentTimeMillis()
        ) {
            current = current.copy(tier = "Free", proExpiresAt = 0L)
            _userProfile.value = current
            saveProfileToPrefs(current)
            scope.launch {
                SupabaseClient.updateUserTier(current.id, "Free", current.accessToken, current.email, hardwareDeviceId)
            }
        }

        if (current.tier.equals("legendary", ignoreCase = true) || current.isProActive) {
            val unlimited = RequestQuota(unlimited = true, limit = 9999, used = 0, remaining = 9999)
            _requestQuota.value = unlimited
            return unlimited
        }
        val quotaResult = SupabaseClient.fetchRequestQuota(
            userId = current.id,
            deviceId = hardwareDeviceId,
            email = current.email,
            accessToken = current.accessToken
        )
        val quota = quotaResult.getOrDefault(RequestQuota(unlimited = false, limit = 3, used = 0, remaining = 3))
        _requestQuota.value = quota
        return quota
    }

    fun dismissBiometricPrompt() {
        _showBiometricPromptAfterLogin.value = false
    }

    fun completeBiometricDeviceSetup(info: DeviceBiometricInfo) {
        _deviceBiometricInfo.value = info
        _showBiometricPromptAfterLogin.value = false
        val current = _userProfile.value
        scope.launch {
            val activeToken = ensureValidAccessToken(current)
            SupabaseClient.syncDeviceBiometricToSupabase(
                userId = current.id,
                email = current.email.ifBlank { info.userEmail },
                deviceId = info.deviceId,
                manufacturer = info.manufacturer,
                model = info.model,
                osVersion = info.osVersion,
                biometricEnabled = info.biometricEnabled,
                accessToken = activeToken
            )
            SupabaseClient.saveDeviceBindingToSupabase(
                deviceId = info.deviceId,
                email = current.email.ifBlank { info.userEmail },
                userId = current.id,
                username = current.username,
                authMethod = current.authMethod,
                tier = current.tier
            )
            fetchRequestQuota()
        }
    }

    suspend fun updateUserTier(
        userId: String,
        newTier: String,
        emailHint: String? = null
    ): Result<Boolean> {
        val trimmedInput = userId.trim()
        val normalizedTier = when {
            newTier.equals("legendary", ignoreCase = true) -> "Legendary"
            newTier.equals("pro", ignoreCase = true) -> "Pro"
            else -> "Free"
        }
        val isLegendary = normalizedTier == "Legendary"
        val isPro = normalizedTier == "Pro"
        val expiresAt = if (isLegendary) Long.MAX_VALUE else if (isPro) System.currentTimeMillis() + (31L * 24 * 60 * 60 * 1000L) else 0L

        val cleanHint = emailHint?.trim()?.lowercase().orEmpty()
        val existingUser = userDao.findUserById(trimmedInput)
            ?: userDao.findUserByEmail(trimmedInput.lowercase())
            ?: (if (cleanHint.isNotBlank()) userDao.findUserByEmail(cleanHint) else null)
            ?: userDao.getAllUsersList().find {
                it.email.equals(trimmedInput, ignoreCase = true) ||
                        (cleanHint.isNotBlank() && it.email.equals(cleanHint, ignoreCase = true)) ||
                        it.id.equals(trimmedInput, ignoreCase = true) ||
                        it.username.equals(trimmedInput, ignoreCase = true)
            }

        val resolvedTargetEmail = when {
            cleanHint.contains("@") -> cleanHint
            !existingUser?.email.isNullOrBlank() -> existingUser!!.email.trim().lowercase()
            trimmedInput.contains("@") -> trimmedInput.lowercase()
            else -> ""
        }

        if (existingUser != null) {
            userDao.insertUsers(listOf(existingUser.copy(tier = normalizedTier)))
        } else if (trimmedInput.isNotBlank() || resolvedTargetEmail.isNotBlank()) {
            val emailVal = resolvedTargetEmail.ifBlank { "${trimmedInput.take(8)}@user.justfan" }
            val idVal = if (trimmedInput.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                trimmedInput
            } else {
                UUID.nameUUIDFromBytes("justfan:$emailVal".toByteArray()).toString()
            }
            userDao.insertUsers(
                listOf(
                    UserEntity(
                        id = idVal,
                        username = emailVal.substringBefore("@"),
                        email = emailVal,
                        tier = normalizedTier,
                        requestsCount = 0,
                        isAdmin = emailVal.equals("reytherapper12@gmail.com", ignoreCase = true),
                        status = "Active",
                        joinedAt = System.currentTimeMillis()
                    )
                )
            )
        }
        userDao.updateUserTier(trimmedInput, normalizedTier)
        if (resolvedTargetEmail.isNotBlank() && !resolvedTargetEmail.equals(trimmedInput, ignoreCase = true)) {
            userDao.updateUserTier(resolvedTargetEmail, normalizedTier)
        }

        val current = _userProfile.value
        val isCurrentTarget = current.id.equals(trimmedInput, ignoreCase = true) ||
                current.email.equals(trimmedInput, ignoreCase = true) ||
                (resolvedTargetEmail.isNotBlank() && current.email.equals(resolvedTargetEmail, ignoreCase = true)) ||
                trimmedInput == "guest_user" ||
                trimmedInput.isBlank()

        val hardwareDeviceId = getDeviceId(context)

        if (isCurrentTarget) {
            val updated = current.copy(
                tier = normalizedTier,
                proExpiresAt = expiresAt
            )
            _userProfile.value = updated
            saveProfileToPrefs(updated)
            val cleanCurrentEmail = current.email.trim().lowercase()
            if (cleanCurrentEmail.isNotBlank()) {
                prefs.edit()
                    .putString("account_tier_$cleanCurrentEmail", normalizedTier)
                    .putLong("account_expires_$cleanCurrentEmail", expiresAt)
                    .apply()
            }
        } else if (resolvedTargetEmail.isNotBlank()) {
            val cleanTargetEmail = resolvedTargetEmail.trim().lowercase()
            prefs.edit()
                .putString("account_tier_$cleanTargetEmail", normalizedTier)
                .putLong("account_expires_$cleanTargetEmail", expiresAt)
                .apply()
        }

        val adminToken = ensureValidAccessToken(_userProfile.value)
        val effectiveEmailHint = when {
            resolvedTargetEmail.isNotBlank() -> resolvedTargetEmail
            isCurrentTarget -> _userProfile.value.email
            else -> null
        }
        val targetIdOrEmail = existingUser?.id ?: if (trimmedInput.isBlank()) _userProfile.value.id else trimmedInput
        val targetDeviceId = if (isCurrentTarget) hardwareDeviceId else null

        val res = SupabaseClient.updateUserTier(
            userIdOrEmail = targetIdOrEmail,
            tier = normalizedTier,
            accessToken = adminToken,
            emailHint = effectiveEmailHint,
            deviceId = targetDeviceId
        )

        if (isCurrentTarget && _userProfile.value.email.isNotBlank()) {
            SupabaseClient.saveDeviceBindingToSupabase(
                deviceId = hardwareDeviceId,
                email = _userProfile.value.email,
                userId = _userProfile.value.id,
                username = _userProfile.value.username,
                authMethod = _userProfile.value.authMethod,
                tier = normalizedTier
            )
        }

        val profilesResult = SupabaseClient.fetchProfiles()
        if (profilesResult.isSuccess) {
            val remoteUsers = profilesResult.getOrNull().orEmpty()
            if (remoteUsers.isNotEmpty()) {
                userDao.insertUsers(remoteUsers)
            }
        }
        fetchRequestQuota()

        if (res.isSuccess) {
            Log.i("JustFanRepository", "Remote subscription updated in Supabase for $targetIdOrEmail ($effectiveEmailHint) to $normalizedTier")
        } else {
            Log.w("JustFanRepository", "Remote subscription update failed: ${res.exceptionOrNull()?.message}")
        }
        return res
    }

    suspend fun createRequest(name: String, email: String, telegram: String?, message: String, imageUrl: String?): Result<Boolean> {
        val current = _userProfile.value
        val hardwareDeviceId = getDeviceId(context)
        val isPaidOrLegendary = current.tier.equals("legendary", ignoreCase = true) ||
                current.isProActive || current.isAdmin

        // Enforce 3 free requests per month only for Free tier users (Pro 1-month & Legendary lifetime are unlimited)
        if (!isPaidOrLegendary) {
            val currentQuota = fetchRequestQuota()
            if (!currentQuota.unlimited && currentQuota.used >= 3) {
                return Result.failure(Exception("Monthly Free Tier limit reached (3/3 requests used this month). Upgrade to Pro (1 Month Unlimited) or Legendary (Lifetime Unlimited) to submit more requests."))
            }
        }

        val res = SupabaseClient.createRequest(
            name = name,
            email = email,
            telegram = telegram,
            message = message,
            imageUrl = imageUrl,
            userId = if (current.isSignedIn) current.id else null,
            deviceId = hardwareDeviceId
        )
        if (res.isSuccess) {
            val newEntity = RequestEntity(
                id = UUID.randomUUID().toString(),
                userId = if (current.isSignedIn) current.id else null,
                name = name,
                email = email,
                telegramUsername = telegram,
                message = message,
                imageUrl = imageUrl,
                status = "pending",
                createdAt = System.currentTimeMillis()
            )
            requestDao.insertRequest(newEntity)
            fetchRequestQuota()
            OneSignalManager.triggerNewRequestNotification(
                name = name,
                email = email,
                requestMessage = message,
                imageUrl = imageUrl,
                context = context
            )
        }
        return res
    }

    suspend fun submitPaymentProof(
        tier: String,
        method: String,
        amount: String,
        proofImageUrl: String,
        note: String
    ): Result<Boolean> {
        val current = _userProfile.value
        val hardwareDeviceId = getDeviceId(context)
        val userEmail = current.email.ifBlank { "subscriber@justfan.app" }
        val userName = current.username.ifBlank { userEmail.substringBefore("@") }
        val structuredMessage = buildString {
            append("[PAYMENT_PROOF] Tier: ")
            append(tier.trim())
            append(" | Method: ")
            append(method.trim())
            append(" | Amount: ")
            append(amount.trim())
            if (note.isNotBlank()) {
                append(" | Ref: ")
                append(note.trim())
            }
        }

        val res = SupabaseClient.createRequest(
            name = userName,
            email = userEmail,
            telegram = note.ifBlank { null },
            message = structuredMessage,
            imageUrl = proofImageUrl,
            userId = if (current.isSignedIn && current.id != "guest_user") current.id else null,
            deviceId = hardwareDeviceId
        )

        val newEntity = RequestEntity(
            id = UUID.randomUUID().toString(),
            userId = if (current.isSignedIn && current.id != "guest_user") current.id else null,
            name = userName,
            email = userEmail,
            telegramUsername = note.ifBlank { null },
            message = structuredMessage,
            imageUrl = proofImageUrl,
            status = "pending",
            createdAt = System.currentTimeMillis()
        )
        requestDao.insertRequest(newEntity)
        OneSignalManager.triggerNewPaymentNotification(
            tier = tier.trim(),
            method = method.trim(),
            amount = amount.trim(),
            userEmail = userEmail,
            proofImageUrl = proofImageUrl,
            context = context
        )
        return res
    }

    private fun isCurrentUserAdmin(): Boolean {
        val current = _userProfile.value
        return current.isSignedIn && (current.isAdmin || current.email.equals("reytherapper12@gmail.com", ignoreCase = true))
    }

    suspend fun updateRequestStatus(id: String, status: String, link: String?, reason: String? = null) {
        if (!isCurrentUserAdmin()) return
        requestDao.updateStatus(id, status, link, reason)
        scope.launch {
            val token = ensureValidAccessToken(_userProfile.value)
            if (status == "rejected" && !reason.isNullOrBlank()) {
                SupabaseClient.rejectRequestInSupabase(id, reason, token)
            } else {
                SupabaseClient.updateRequestStatusInSupabase(id, status, link, token)
            }
        }
    }

    suspend fun deleteRequest(id: String) {
        if (!isCurrentUserAdmin()) return
        requestDao.deleteRequest(id)
        scope.launch {
            val token = ensureValidAccessToken(_userProfile.value)
            SupabaseClient.deleteRequestFromSupabase(id, token)
        }
    }

    suspend fun toggleFavorite(postId: String) {
        if (favoriteDao.isFavorite(postId)) {
            favoriteDao.removeFavorite(postId)
        } else {
            favoriteDao.addFavorite(FavoriteEntity(postId = postId))
        }
    }

    suspend fun isFavorite(postId: String): Boolean = favoriteDao.isFavorite(postId)

    suspend fun recordPostClick(postId: String) {
        postDao.incrementClicks(postId)
        scope.launch {
            SupabaseClient.recordPostClick(postId, _userProfile.value.id)
        }
    }

    suspend fun recordDownloadClick(postId: String) {
        recordPostClick(postId)
    }

    suspend fun createPost(post: PostEntity): Result<Boolean> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(Exception("Only admin can create posts"))
        }
        // Always insert into local Room DB immediately so UI reflects the new post
        postDao.insertPost(post)
        val token = ensureValidAccessToken(_userProfile.value)
        val res = SupabaseClient.insertPost(post, token)
        OneSignalManager.triggerNewPostNotification(post, context)
        return res
    }

    suspend fun updatePost(post: PostEntity): Result<Boolean> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(Exception("Only admin can edit posts"))
        }
        postDao.insertPost(post)
        val token = ensureValidAccessToken(_userProfile.value)
        return SupabaseClient.updatePost(post, token)
    }

    suspend fun deletePost(postId: String): Result<Boolean> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(Exception("Only admin can delete posts"))
        }
        postDao.deletePost(postId)
        val token = ensureValidAccessToken(_userProfile.value)
        return SupabaseClient.deletePost(postId, token)
    }

    suspend fun setUserBlocked(userId: String, isBlocked: Boolean): Result<Boolean> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(Exception("Only admin can modify user status"))
        }
        val status = if (isBlocked) "Suspended" else "Active"
        userDao.updateUserStatus(userId, status)
        val token = ensureValidAccessToken(_userProfile.value)
        return SupabaseClient.setUserBlocked(userId, isBlocked, token)
    }

    fun signInWithGoogle(
        email: String,
        name: String,
        accessToken: String? = null,
        refreshToken: String? = null,
        userId: String? = null
    ): UserProfile {
        val jwtInfo = if (!accessToken.isNullOrBlank()) SupabaseClient.extractUserFromJwt(accessToken) else Triple(null, null, null)
        val cleanEmail = (jwtInfo.second ?: email).trim().lowercase()
        val isRey = cleanEmail == "reytherapper12@gmail.com"
        val resolvedName = (jwtInfo.third ?: name).ifBlank { cleanEmail.substringBefore("@") }

        val cachedToken = accessToken ?: prefs.getString("cached_token_$cleanEmail", null)
        val cachedRefresh = refreshToken ?: prefs.getString("cached_refresh_$cleanEmail", null)
        val resolvedUid = jwtInfo.first
            ?: userId
            ?: prefs.getString("cached_uid_$cleanEmail", null)
            ?: if (isRey) "a2d7bbad-c31f-4679-bf51-2d2684bb3216" else UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()

        val cachedWallpaper = loadAccountPreferences(cleanEmail)?.customWallpaperUri

        val user = UserProfile(
            id = resolvedUid,
            username = if (isRey) "reytherapper12 (Admin)" else resolvedName,
            email = cleanEmail,
            isSignedIn = true,
            authMethod = "google",
            tier = if (isRey) "Legendary" else "Free",
            isAdmin = isRey,
            proExpiresAt = if (isRey) Long.MAX_VALUE else 0L,
            accessToken = cachedToken,
            refreshToken = cachedRefresh,
            customWallpaperUri = cachedWallpaper
        )
        _userProfile.value = user
        saveProfileToPrefs(user)
        val alreadyVerified = BiometricAuthManager.isBiometricVerifiedForAccount(context, cleanEmail)
        _deviceBiometricInfo.value = getDeviceBiometricInfo(context, cleanEmail, alreadyVerified)
        if (!alreadyVerified) {
            _showBiometricPromptAfterLogin.value = true
        } else {
            completeBiometricDeviceSetup(_deviceBiometricInfo.value)
        }
        syncUserDataOnLogin(user)
        return user
    }

    fun signInWithPasskey(name: String): UserProfile {
        val cleanName = name.trim().ifBlank { "Passkey User" }
        val cleanEmail = "${cleanName.lowercase().replace(" ", "")}@passkey.local"
        val resolvedUid = prefs.getString("cached_uid_$cleanEmail", null)
            ?: UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()

        val user = UserProfile(
            id = resolvedUid,
            username = cleanName,
            email = cleanEmail,
            isSignedIn = true,
            authMethod = "passkey",
            tier = "Free",
            isAdmin = false,
            proExpiresAt = 0L,
            customWallpaperUri = loadAccountPreferences(cleanEmail)?.customWallpaperUri
        )
        _userProfile.value = user
        saveProfileToPrefs(user)
        syncUserDataOnLogin(user)
        return user
    }

    suspend fun authenticateWithEmail(
        email: String,
        pass: String,
        isSignUp: Boolean = false,
        username: String = ""
    ): Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (pass.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val isRey = cleanEmail == "reytherapper12@gmail.com"
        var session: SupabaseClient.AuthSession? = null

        if (isSignUp) {
            val signUpRes = SupabaseClient.signUpWithEmailPassword(cleanEmail, pass, username)
            session = signUpRes.getOrNull()
        }

        if (session == null) {
            val signInRes = SupabaseClient.signInWithEmailPassword(cleanEmail, pass)
            if (signInRes.isSuccess) {
                session = signInRes.getOrNull()
            } else if (isRey) {
                // Admin account must authenticate with valid Supabase credentials
                return Result.failure(
                    Exception("Invalid password for admin account ($cleanEmail). Please enter your Supabase account password or sign in with Google.")
                )
            }
        }

        val resolvedUid = session?.userId
            ?: SupabaseClient.resolveUserUuid(cleanEmail)
            ?: prefs.getString("cached_uid_$cleanEmail", null)
            ?: if (isRey) "a2d7bbad-c31f-4679-bf51-2d2684bb3216" else UUID.nameUUIDFromBytes("justfan:$cleanEmail".toByteArray()).toString()

        val resolvedToken = session?.accessToken ?: prefs.getString("cached_token_$cleanEmail", null)
        val resolvedRefresh = session?.refreshToken ?: prefs.getString("cached_refresh_$cleanEmail", null)
        val cachedWallpaper = loadAccountPreferences(cleanEmail)?.customWallpaperUri

        val remoteSub = SupabaseClient.fetchUserSubscriptionDetails(
            userId = resolvedUid,
            email = cleanEmail,
            accessToken = resolvedToken,
            deviceId = getDeviceId(context)
        ).getOrNull()
        val initialTier = remoteSub?.tier ?: if (isRey) "Legendary" else "Free"
        val initialExpires = when {
            initialTier.equals("Legendary", ignoreCase = true) -> Long.MAX_VALUE
            initialTier.equals("Pro", ignoreCase = true) -> remoteSub?.expiresAtMillis?.takeIf { it > 0L }
                ?: (System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000L)
            else -> 0L
        }

        val user = UserProfile(
            id = resolvedUid,
            username = if (isRey) "reytherapper12 (Admin)" else username.ifBlank { cleanEmail.substringBefore("@") },
            email = cleanEmail,
            isSignedIn = true,
            authMethod = "password",
            tier = initialTier,
            isAdmin = isRey,
            proExpiresAt = initialExpires,
            accessToken = resolvedToken,
            refreshToken = resolvedRefresh,
            customWallpaperUri = cachedWallpaper
        )
        _userProfile.value = user
        saveProfileToPrefs(user)
        syncUserDataOnLogin(user)
        return Result.success(user)
    }

    fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        return runBlocking(Dispatchers.IO) {
            authenticateWithEmail(email, pass, isSignUp = false)
        }
    }

    fun signOut() {
        val hardwareDeviceId = getDeviceId(context)
        OneSignalManager.logout()
        scope.launch {
            SupabaseClient.clearDeviceBindingInSupabase(hardwareDeviceId)
        }
        val guest = UserProfile(
            id = "guest_user",
            username = "Guest Fan",
            email = "",
            isSignedIn = false,
            authMethod = "guest",
            tier = "Free",
            isAdmin = false,
            proExpiresAt = 0L
        )
        _userProfile.value = guest
        saveProfileToPrefs(guest)

        // Reset active UI session preferences without deleting saved per-account preferences
        val defaultPref = UserPreferencesState(
            themeVariant = "cyan",
            isDarkMode = true,
            customWallpaperUri = null,
            wallpaperDim = 0.65f
        )
        _preferences.value = defaultPref
        prefs.edit()
            .putBoolean("explicitly_signed_out", true)
            .putString("pref_theme_variant", defaultPref.themeVariant)
            .putBoolean("pref_dark_mode", defaultPref.isDarkMode)
            .putString("pref_wallpaper_uri", null)
            .putFloat("pref_wallpaper_dim", defaultPref.wallpaperDim)
            .apply()
    }
}
