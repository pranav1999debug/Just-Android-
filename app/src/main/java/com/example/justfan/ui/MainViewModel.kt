package com.example.justfan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.justfan.data.local.AppDatabase
import com.example.justfan.data.model.*
import com.example.justfan.data.repository.JustFanRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JustFanRepository(
        database = AppDatabase.getDatabase(application),
        context = application.applicationContext,
        scope = viewModelScope
    )

    val posts: StateFlow<List<PostEntity>> = repository.allPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freePosts: StateFlow<List<PostEntity>> = repository.freePosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingPosts: StateFlow<List<PostEntity>> = repository.trendingPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteEntity>> = repository.allFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections: StateFlow<List<CollectionEntity>> = repository.allCollections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val requests: StateFlow<List<RequestEntity>> = repository.allRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deliveredRequests: StateFlow<List<RequestEntity>> = repository.deliveredRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activities: StateFlow<List<ActivityEntity>> = repository.allActivities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<UserPreferences> = repository.preferences
    val userProfile: StateFlow<UserProfile> = repository.userProfile

    val isSyncing: StateFlow<Boolean> = repository.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun refreshFromSupabase() {
        viewModelScope.launch {
            repository.refreshPosts()
        }
    }

    fun getCommentsForPost(postId: String): Flow<List<CommentEntity>> =
        repository.getCommentsForPost(postId)

    fun incrementClicks(postId: String) {
        viewModelScope.launch {
            repository.incrementPostClicks(postId)
        }
    }

    fun recordDownloadClick(postId: String, downloadType: String = "download") {
        viewModelScope.launch {
            repository.recordDownloadClick(postId, downloadType)
        }
    }

    fun toggleFavorite(postId: String) {
        viewModelScope.launch {
            val isFav = favorites.value.any { it.postId == postId }
            repository.toggleFavorite(postId, isFav)
        }
    }

    fun recordShare(postId: String) {
        viewModelScope.launch {
            repository.recordShare(postId)
        }
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch {
            repository.addComment(postId, content, userProfile.value.username)
        }
    }

    fun submitRequest(
        name: String,
        email: String,
        telegram: String?,
        message: String,
        imageUrl: String?,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val res = repository.submitRequest(name, email, telegram, message, imageUrl)
                onComplete?.invoke(res.isSuccess, res.exceptionOrNull()?.message)
            } catch (e: Exception) {
                onComplete?.invoke(false, e.message)
            }
        }
    }

    fun refreshRequestsFromSupabase(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.syncRequestsFromSupabase()
            onComplete?.invoke()
        }
    }

    fun updateRequestStatus(id: String, status: String, link: String?) {
        viewModelScope.launch {
            repository.updateRequestStatus(id, status, link)
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            repository.createCollection(name)
        }
    }

    fun deleteCollection(id: String) {
        viewModelScope.launch {
            repository.deleteCollection(id)
        }
    }

    fun createPost(
        title: String,
        desc: String,
        img: String,
        contentImages: List<String> = emptyList(),
        link: String,
        premLink: String?,
        tags: List<String>,
        isFree: Boolean,
        isNsfw: Boolean
    ) {
        viewModelScope.launch {
            repository.createPost(title, desc, img, contentImages, link, premLink, tags, isFree, isNsfw)
        }
    }

    fun deletePost(id: String) {
        viewModelScope.launch {
            repository.deletePost(id)
        }
    }

    fun updateTheme(themeVariant: String, isDark: Boolean) {
        repository.updateTheme(themeVariant, isDark)
    }

    fun updateContentFilter(filter: String) {
        repository.updateContentFilter(filter)
    }

    fun addPreferredTag(tag: String) {
        repository.addPreferredTag(tag)
    }

    fun removePreferredTag(tag: String) {
        repository.removePreferredTag(tag)
    }

    fun updatePlan(plan: String) {
        repository.updatePlan(plan)
    }

    fun updateTier(tier: String) {
        repository.updateTier(tier)
    }

    fun signInWithEmail(email: String, password: String): Result<UserProfile> =
        repository.signInWithEmail(email, password)

    fun signInWithGoogle(email: String, name: String = "Google User"): Result<UserProfile> =
        repository.signInWithGoogle(email, name)

    fun signInWithPasskey(name: String = "Device Passkey"): Result<UserProfile> =
        repository.signInWithPasskey(name)

    fun signOut() {
        repository.signOut()
    }

    fun updateWallpaper(uri: String?, dim: Float = 0.65f) {
        repository.updateWallpaper(uri, dim)
    }

    fun updatePost(post: PostEntity) {
        viewModelScope.launch {
            repository.updatePost(post)
        }
    }

    fun updateUserTier(userId: String, newTier: String) {
        viewModelScope.launch {
            repository.updateUserTier(userId, newTier)
        }
    }

    fun updateUserRequestsCount(userId: String, count: Int) {
        viewModelScope.launch {
            repository.updateUserRequestsCount(userId, count)
        }
    }

    fun updateUserStatus(userId: String, status: String) {
        viewModelScope.launch {
            repository.updateUserStatus(userId, status)
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            repository.deleteUser(userId)
        }
    }

    fun rejectRequest(id: String, reason: String) {
        viewModelScope.launch {
            repository.rejectRequest(id, reason)
        }
    }

    fun fulfillRequest(id: String, downloadLink: String) {
        viewModelScope.launch {
            repository.fulfillRequest(id, downloadLink)
        }
    }

    fun deleteRequest(id: String) {
        viewModelScope.launch {
            repository.deleteRequest(id)
        }
    }

    fun handleGoogleOAuthUri(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val fragment = uri.fragment ?: ""
                val query = uri.query ?: ""
                val rawParams = if (fragment.isNotBlank()) fragment else query
                val params = rawParams.split("&").associate { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
                }
                val accessToken = params["access_token"]
                if (!accessToken.isNullOrBlank()) {
                    val parts = accessToken.split(".")
                    if (parts.size >= 2) {
                        val decodedBytes = android.util.Base64.decode(
                            parts[1],
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                        )
                        val payloadJson = String(decodedBytes, Charsets.UTF_8)
                        val json = org.json.JSONObject(payloadJson)
                        val email = json.optString("email", "")
                        val userMetadata = json.optJSONObject("user_metadata")
                        val name = userMetadata?.optString("full_name")?.ifBlank { null }
                            ?: userMetadata?.optString("name")?.ifBlank { null }
                            ?: email.substringBefore("@")
                        if (email.isNotBlank()) {
                            repository.signInWithGoogle(email, name)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error parsing Google OAuth callback", e)
            }
        }
    }
}
