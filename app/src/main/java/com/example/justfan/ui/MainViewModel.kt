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

    fun createPost(post: PostEntity, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.createPost(post)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        return repository.signInWithEmail(email, pass)
    }

    fun signOut() {
        repository.signOut()
    }
}
