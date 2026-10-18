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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    init {
        scope.launch {
            syncData()
            val current = _userProfile.value
            if (current.isSignedIn && current.id != "guest_user") {
                syncUserDataOnLogin(current)
            }
        }
    }

    private fun loadProfileFromPrefs(): UserProfile {
        val isSignedIn = prefs.getBoolean("user_signed_in", false)
        val email = prefs.getString("user_email", "") ?: ""
        val isRey = email.equals("reytherapper12@gmail.com", ignoreCase = true)
        val defaultTier = if (isRey) "Legendary" else "Free"
        val tier = prefs.getString("user_tier", defaultTier) ?: defaultTier

        return UserProfile(
            id = prefs.getString("user_id", if (isRey) "a2d7bbad-c31f-4679-bf51-2d2684bb3216" else "guest_user") ?: "guest_user",
            username = prefs.getString("user_username", if (isRey) "reytherapper12 (Admin)" else "Guest Fan") ?: "Guest Fan",
            email = email,
            isSignedIn = isSignedIn || isRey,
            authMethod = prefs.getString("user_auth_method", if (isRey) "admin" else "guest") ?: "guest",
            tier = if (isRey) "Legendary" else tier,
            isAdmin = isRey,
            proExpiresAt = if (isRey) Long.MAX_VALUE else prefs.getLong("user_pro_expires_at", 0L),
            accessToken = prefs.getString("user_access_token", null),
            refreshToken = prefs.getString("user_refresh_token", null),
            customWallpaperUri = prefs.getString("user_wallpaper_uri", null)
        )
    }

    private fun saveProfileToPrefs(profile: UserProfile) {
        prefs.edit()
            .putBoolean("user_signed_in", profile.isSignedIn)
            .putString("user_id", profile.id)
            .putString("user_username", profile.username)
            .putString("user_email", profile.email)
            .putString("user_auth_method", profile.authMethod)
            .putString("user_tier", profile.tier)
            .putBoolean("user_is_admin", profile.isAdmin)
            .putLong("user_pro_expires_at", profile.proExpiresAt)
            .putString("user_access_token", profile.accessToken)
            .putString("user_refresh_token", profile.refreshToken)
            .putString("user_wallpaper_uri", profile.customWallpaperUri)
            .apply()
    }

    private fun loadPreferencesFromPrefs(): UserPreferencesState {
        return UserPreferencesState(
            themeVariant = prefs.getString("pref_theme_variant", "cyan") ?: "cyan",
            isDarkMode = prefs.getBoolean("pref_dark_mode", true),
            customWallpaperUri = prefs.getString("pref_wallpaper_uri", null),
            wallpaperDim = prefs.getFloat("pref_wallpaper_dim", 0.65f)
        )
    }

    private fun savePreferencesToPrefs(state: UserPreferencesState) {
        prefs.edit()
            .putString("pref_theme_variant", state.themeVariant)
            .putBoolean("pref_dark_mode", state.isDarkMode)
            .putString("pref_wallpaper_uri", state.customWallpaperUri)
            .putFloat("pref_wallpaper_dim", state.wallpaperDim)
            .apply()
    }

    fun updateTheme(themeVariant: String, isDark: Boolean) {
        val updated = _preferences.value.copy(themeVariant = themeVariant, isDarkMode = isDark)
        _preferences.value = updated
        savePreferencesToPrefs(updated)

        val currentProfile = _userProfile.value
        if (currentProfile.isSignedIn && currentProfile.id != "guest_user") {
            scope.launch {
                SupabaseClient.saveUserPreferences(
                    userId = currentProfile.id,
                    theme = themeVariant,
                    wallpaperUrl = updated.customWallpaperUri,
                    accessToken = currentProfile.accessToken
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
                SupabaseClient.saveUserPreferences(
                    userId = currentProfile.id,
                    theme = updatedPref.themeVariant,
                    wallpaperUrl = wallpaperUri,
                    accessToken = currentProfile.accessToken
                )
            }
        }
    }

    suspend fun syncData() {
        _isRefreshing.value = true
        try {
            // 1. Sync Posts
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
        scope.launch {
            try {
                val isRey = profile.email.equals("reytherapper12@gmail.com", ignoreCase = true)

                // 1. Sync subscription tier from Supabase subscriptions table
                val subResult = SupabaseClient.fetchUserSubscription(profile.id, profile.accessToken)
                val remoteTier = subResult.getOrNull()
                if (!remoteTier.isNullOrBlank()) {
                    val isPro = remoteTier.equals("pro", ignoreCase = true)
                    val isLegendary = remoteTier.equals("legendary", ignoreCase = true) || isRey
                    val expiresAt = if (isLegendary) Long.MAX_VALUE else if (isPro) System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L) else 0L
                    val finalTier = if (isLegendary) "Legendary" else if (isPro) "Pro" else remoteTier

                    val updatedProfile = _userProfile.value.copy(
                        tier = finalTier,
                        isAdmin = isRey,
                        proExpiresAt = expiresAt
                    )
                    _userProfile.value = updatedProfile
                    saveProfileToPrefs(updatedProfile)
                    userDao.updateUserTier(profile.id, finalTier)
                    Log.i("JustFanRepository", "Synced remote subscription plan: $finalTier for ${profile.email}")
                }

                // 2. Sync theme & wallpaper from Supabase user_preferences table
                val prefResult = SupabaseClient.fetchUserPreferences(profile.id, profile.accessToken)
                val remotePref = prefResult.getOrNull()
                if (remotePref != null) {
                    var newPref = _preferences.value
                    if (!remotePref.theme.isNullOrBlank()) {
                        newPref = newPref.copy(themeVariant = remotePref.theme)
                    }
                    if (!remotePref.wallpaperUrl.isNullOrBlank()) {
                        newPref = newPref.copy(customWallpaperUri = remotePref.wallpaperUrl)
                        _userProfile.value = _userProfile.value.copy(customWallpaperUri = remotePref.wallpaperUrl)
                        saveProfileToPrefs(_userProfile.value)
                    }
                    _preferences.value = newPref
                    savePreferencesToPrefs(newPref)
                    Log.i("JustFanRepository", "Loaded remote preferences: theme=${remotePref.theme}, wallpaper=${remotePref.wallpaperUrl}")
                } else {
                    // Seed current preferences to user_preferences table for this user
                    SupabaseClient.saveUserPreferences(
                        userId = profile.id,
                        theme = _preferences.value.themeVariant,
                        wallpaperUrl = _preferences.value.customWallpaperUri,
                        accessToken = profile.accessToken
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
        if (current.tier.equals("legendary", ignoreCase = true) || current.isAdmin) {
            val unlimited = RequestQuota(unlimited = true, limit = 9999, used = 0, remaining = 9999)
            _requestQuota.value = unlimited
            return unlimited
        }
        val quotaResult = SupabaseClient.fetchRequestQuota(current.id, "device_app", current.accessToken)
        val quota = quotaResult.getOrDefault(RequestQuota(unlimited = false, limit = 3, used = 0, remaining = 3))
        _requestQuota.value = quota
        return quota
    }

    suspend fun updateUserTier(userId: String, newTier: String) {
        // 1. Update local database immediately
        userDao.updateUserTier(userId, newTier)

        // If the updated user is currently logged in, update their local session
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

        // 2. Sync to Supabase subscriptions table in background
        scope.launch {
            val adminToken = current.accessToken
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
        val res = SupabaseClient.createRequest(
            name = name,
            email = email,
            telegram = telegram,
            message = message,
            imageUrl = imageUrl,
            userId = if (current.isSignedIn) current.id else null,
            deviceId = "device_app"
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
        }
        return res
    }

    suspend fun updateRequestStatus(id: String, status: String, link: String?, reason: String? = null) {
        requestDao.updateStatus(id, status, link, reason)
        scope.launch {
            if (status == "rejected" && !reason.isNullOrBlank()) {
                SupabaseClient.rejectRequestInSupabase(id, reason, _userProfile.value.accessToken)
            } else {
                SupabaseClient.updateRequestStatusInSupabase(id, status, link, _userProfile.value.accessToken)
            }
        }
    }

    suspend fun deleteRequest(id: String) {
        requestDao.deleteRequest(id)
        scope.launch {
            SupabaseClient.deleteRequestFromSupabase(id, _userProfile.value.accessToken)
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

    suspend fun createPost(post: PostEntity): Result<Boolean> {
        val res = SupabaseClient.insertPost(post, _userProfile.value.accessToken)
        if (res.isSuccess) {
            postDao.insertPost(post)
        }
        return res
    }

    fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        val isRey = cleanEmail == "reytherapper12@gmail.com"

        // If admin email, grant full Legendary admin rights immediately
        if (isRey) {
            val adminUser = UserProfile(
                id = "a2d7bbad-c31f-4679-bf51-2d2684bb3216",
                username = "reytherapper12 (Admin)",
                email = cleanEmail,
                isSignedIn = true,
                authMethod = "password",
                tier = "Legendary",
                isAdmin = true,
                proExpiresAt = Long.MAX_VALUE
            )
            _userProfile.value = adminUser
            saveProfileToPrefs(adminUser)
            syncUserDataOnLogin(adminUser)
            return Result.success(adminUser)
        }

        val regularUser = UserProfile(
            id = UUID.randomUUID().toString(),
            username = cleanEmail.substringBefore("@"),
            email = cleanEmail,
            isSignedIn = true,
            authMethod = "password",
            tier = "Free",
            isAdmin = false,
            proExpiresAt = 0L
        )
        _userProfile.value = regularUser
        saveProfileToPrefs(regularUser)
        syncUserDataOnLogin(regularUser)
        return Result.success(regularUser)
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

        val defaultPref = _preferences.value.copy(themeVariant = "cyan", customWallpaperUri = null)
        _preferences.value = defaultPref
        savePreferencesToPrefs(defaultPref)
    }
}
