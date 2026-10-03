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

    val preferences: StateFlow<UserPreferences> = repository.preferences
    val userProfile: StateFlow<UserProfile> = repository.userProfile

    fun getCommentsForPost(postId: String): Flow<List<CommentEntity>> =
        repository.getCommentsForPost(postId)

    fun incrementClicks(postId: String) {
        viewModelScope.launch {
            repository.incrementPostClicks(postId)
        }
    }

    fun toggleFavorite(postId: String) {
        viewModelScope.launch {
            val isFav = favorites.value.any { it.postId == postId }
            repository.toggleFavorite(postId, isFav)
        }
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch {
            repository.addComment(postId, content, userProfile.value.username)
        }
    }

    fun submitRequest(name: String, email: String, telegram: String?, message: String, imageUrl: String?) {
        viewModelScope.launch {
            repository.submitRequest(name, email, telegram, message, imageUrl)
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
        link: String,
        premLink: String?,
        tags: List<String>,
        isFree: Boolean,
        isNsfw: Boolean
    ) {
        viewModelScope.launch {
            repository.createPost(title, desc, img, link, premLink, tags, isFree, isNsfw)
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
}
