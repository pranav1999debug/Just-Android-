package com.example.justfan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.justfan.data.model.FavoriteEntity
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.RequestQuota
import com.example.justfan.data.model.UserEntity
import com.example.justfan.data.model.UserPreferencesState
import com.example.justfan.data.model.UserProfile
import com.example.justfan.data.repository.JustFanRepository
import com.example.justfan.util.DeviceBiometricInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = JustFanRepository(application.applicationContext)

    val posts: StateFlow<List<PostEntity>> = repository.allPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val requests: StateFlow<List<RequestEntity>> = repository.allRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfile> = repository.userProfile
    val preferences: StateFlow<UserPreferencesState> = repository.preferences
    val requestQuota: StateFlow<RequestQuota> = repository.requestQuota
    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing
    val deviceBiometricInfo: StateFlow<DeviceBiometricInfo> = repository.deviceBiometricInfo
    val showBiometricPromptAfterLogin: StateFlow<Boolean> = repository.showBiometricPromptAfterLogin

    fun completeBiometricDeviceSetup(info: DeviceBiometricInfo) {
        repository.completeBiometricDeviceSetup(info)
    }

    fun dismissBiometricPrompt() {
        repository.dismissBiometricPrompt()
    }

    fun updateTheme(themeVariant: String, isDark: Boolean) {
        repository.updateTheme(themeVariant, isDark)
    }

    fun updateWallpaper(wallpaperUri: String?, dim: Float = 0.65f) {
        repository.updateWallpaper(wallpaperUri, dim)
    }

    fun updateUserTier(userId: String, newTier: String) {
        viewModelScope.launch {
            repository.updateUserTier(userId, newTier)
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            repository.syncData()
            repository.fetchRequestQuota()
        }
    }

    fun createRequest(
        name: String,
        email: String,
        telegram: String?,
        message: String,
        imageUrl: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.createRequest(name, email, telegram, message, imageUrl)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun submitPaymentProof(
        tier: String,
        method: String,
        amount: String,
        proofImageUrl: String,
        note: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.submitPaymentProof(tier, method, amount, proofImageUrl, note)
            onResult(res.isSuccess)
        }
    }

    fun updateRequestStatus(id: String, status: String, link: String?, reason: String? = null) {
        viewModelScope.launch {
            repository.updateRequestStatus(id, status, link, reason)
        }
    }

    fun deleteRequest(id: String) {
        viewModelScope.launch {
            repository.deleteRequest(id)
        }
    }

    fun toggleFavorite(postId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(postId)
        }
    }

    fun createPost(post: PostEntity, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = repository.createPost(post)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun signInWithGoogle(
        email: String,
        name: String,
        accessToken: String? = null,
        refreshToken: String? = null,
        userId: String? = null
    ): UserProfile {
        return repository.signInWithGoogle(email, name, accessToken, refreshToken, userId)
    }

    fun signInWithPasskey(name: String): UserProfile {
        return repository.signInWithPasskey(name)
    }

    fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        return repository.signInWithEmail(email, pass)
    }

    fun authenticateWithEmail(
        email: String,
        pass: String,
        isSignUp: Boolean = false,
        username: String = "",
        onResult: (Result<UserProfile>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.authenticateWithEmail(email, pass, isSignUp, username)
            onResult(res)
        }
    }

    fun recordPostClick(postId: String) {
        viewModelScope.launch {
            repository.recordPostClick(postId)
        }
    }

    fun recordDownloadClick(postId: String) {
        viewModelScope.launch {
            repository.recordDownloadClick(postId)
        }
    }

    fun updatePost(post: PostEntity, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.updatePost(post)
            onResult(res.isSuccess)
        }
    }

    fun deletePost(postId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.deletePost(postId)
            onResult(res.isSuccess)
        }
    }

    fun setUserBlocked(userId: String, isBlocked: Boolean, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.setUserBlocked(userId, isBlocked)
            onResult(res.isSuccess)
        }
    }

    fun signOut() {
        repository.signOut()
    }
}
