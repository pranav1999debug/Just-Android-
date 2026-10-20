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
        val tier = prefs.getString("user_tier", if (isRey && isSignedIn) "Legendary" else "Free") ?: "Free"

        return UserProfile(
            id = prefs.getString("user_id", "guest_user") ?: "guest_user",
            username = prefs.getString("user_username", "Guest Fan") ?: "Guest Fan",
            email = email,
            isSignedIn = isSignedIn,
            authMethod = prefs.getString("user_auth_method", "guest") ?: "guest",
            tier = if (isRey && isSignedIn) "Legendary" else tier,
            isAdmin = isRey && isSignedIn,
            proExpiresAt = prefs.getLong("user_pro_expires_at", 0L),
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
            // 1. Sync Posts (includes click counts from post_click_counts)
            val postsResult = SupabaseClient.fetchPosts()
            if (postsResult.isSuccess) {
                val remotePosts = postsResult.getOrNull().orEmpty()
                if (remotePosts.isNotEmpty()) {
                    postDao.insertPosts(remotePosts)
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
        } catch (e: Exception) {
            Log.e("JustFanRepository", "syncData error", e)
        } finally {
            _isRefreshing.value = false
        }
    }

    fun syncUserDataOnLogin(profile: UserProfile) {
        if (!profile.isSignedIn || profile.id == "guest_user") return
        val cleanEmail = profile.email.trim().lowercase()

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

        scope.launch {
            try {
                val isRey = cleanEmail.equals("reytherapper12@gmail.com", ignoreCase = true)
                val activeToken = ensureValidAccessToken(_userProfile.value)
                val activeUserId = _userProfile.value.id

                // 1. Sync subscription tier from Supabase subscriptions table
                val subResult = SupabaseClient.fetchUserSubscription(activeUserId, activeToken)
                val remoteTier = subResult.getOrNull()
                if (!remoteTier.isNullOrBlank() || isRey) {
                    val isPro = remoteTier?.equals("pro", ignoreCase = true) == true
                    val isLegendary = remoteTier?.equals("legendary", ignoreCase = true) == true || isRey
                    val expiresAt = if (isLegendary) Long.MAX_VALUE else if (isPro) System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L) else 0L
                    val finalTier = if (isLegendary) "Legendary" else if (isPro) "Pro" else (remoteTier ?: "Free")

                    val updatedProfile = _userProfile.value.copy(
                        tier = finalTier,
                        isAdmin = isRey,
                        proExpiresAt = expiresAt
                    )
                    _userProfile.value = updatedProfile
                    saveProfileToPrefs(updatedProfile)
                    userDao.updateUserTier(activeUserId, finalTier)
                }

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

                // 3. Refresh request quota
                fetchRequestQuota()
            } catch (e: Exception) {
                Log.w("JustFanRepository", "syncUserDataOnLogin warning", e)
            }
        }
    }

    suspend fun fetchRequestQuota(): RequestQuota {
        val current = _userProfile.value
        val hardwareDeviceId = getDeviceId(context)
        if (current.tier.equals("legendary", ignoreCase = true) || current.tier.equals("pro", ignoreCase = true) || current.isAdmin) {
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
            fetchRequestQuota()
        }
    }

    suspend fun updateUserTier(userId: String, newTier: String) {
        userDao.updateUserTier(userId, newTier)

        val current = _userProfile.value
        if (current.id == userId || current.email.equals(userId, ignoreCase = true)) {
            val isRey = current.email.equals("reytherapper12@gmail.com", ignoreCase = true)
            val isLegendary = newTier.equals("legendary", ignoreCase = true) || isRey
            val isPro = newTier.equals("pro", ignoreCase = true)
            val expiresAt = if (isLegendary) Long.MAX_VALUE else if (isPro) System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L) else 0L

            val updated = current.copy(
                tier = if (isLegendary) "Legendary" else if (isPro) "Pro" else newTier,
                proExpiresAt = expiresAt
            )
            _userProfile.value = updated
            saveProfileToPrefs(updated)
        }

        scope.launch {
            val adminToken = ensureValidAccessToken(current)
            val res = SupabaseClient.updateUserTier(userId, newTier, adminToken)
            if (res.isSuccess) {
                Log.i("JustFanRepository", "Remote subscription updated for $userId to $newTier")
            } else {
                Log.w("JustFanRepository", "Remote subscription update failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun createRequest(name: String, email: String, telegram: String?, message: String, imageUrl: String?): Result<Boolean> {
        val current = _userProfile.value
        val hardwareDeviceId = getDeviceId(context)
        val isPaidOrLegendary = current.tier.equals("legendary", ignoreCase = true) ||
                current.tier.equals("pro", ignoreCase = true) || current.isAdmin

        // Enforce 3 free requests per device for free users
        if (!isPaidOrLegendary) {
            val currentQuota = fetchRequestQuota()
            if (currentQuota.used >= 3) {
                return Result.failure(Exception("Device limit reached (3/3 free requests used on this device). Upgrade to Pro or Legendary for unlimited requests."))
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
        }
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

        val user = UserProfile(
            id = resolvedUid,
            username = if (isRey) "reytherapper12 (Admin)" else username.ifBlank { cleanEmail.substringBefore("@") },
            email = cleanEmail,
            isSignedIn = true,
            authMethod = "password",
            tier = if (isRey) "Legendary" else "Free",
            isAdmin = isRey,
            proExpiresAt = if (isRey) Long.MAX_VALUE else 0L,
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
            .putString("pref_theme_variant", defaultPref.themeVariant)
            .putBoolean("pref_dark_mode", defaultPref.isDarkMode)
            .putString("pref_wallpaper_uri", null)
            .putFloat("pref_wallpaper_dim", defaultPref.wallpaperDim)
            .apply()
    }
}
