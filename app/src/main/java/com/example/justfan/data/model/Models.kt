package com.example.justfan.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val contentImages: List<String> = emptyList(),
    val linkUrl: String = "",
    val premiumLinkUrl: String? = null,
    val directLinkUrl: String? = null,
    val tags: List<String> = emptyList(),
    val author: String = "JUSTFAN Creator",
    val isFree: Boolean = true,
    val isNsfw: Boolean = false,
    val section: String = "home",
    val clicksCount: Int = 0,
    val likesCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val postId: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "collection_items")
data class CollectionItemEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val postId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val telegramUsername: String? = null,
    val message: String,
    val imageUrl: String? = null,
    val status: String = "pending", // pending, in_progress, delivered, rejected
    val downloadLink: String? = null,
    val rejectionReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "RequestfromApp")
data class RequestfromAppEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val name: String = "",
    val email: String = "",
    val telegramUsername: String? = null,
    val message: String = "",
    val imageUrl: String? = null,
    val status: String = "pending",
    val downloadLink: String? = null,
    val rejectionReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val tier: String = "Free", // Free, Pro, Legendary
    val requestsCount: Int = 0,
    val isAdmin: Boolean = false,
    val status: String = "Active", // Active, Suspended
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val authorName: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val type: String, // post_created, request_submitted, request_delivered, comment_added
    val title: String,
    val body: String? = null,
    val link: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class UserPreferences(
    val themeVariant: String = "cyan",
    val isDarkMode: Boolean = true,
    val contentFilter: String = "nsfw", // sfw or nsfw
    val preferredTags: List<String> = emptyList(),
    val customWallpaperUri: String? = null,
    val wallpaperDim: Float = 0.65f
)

data class UserProfile(
    val id: String = "guest_user",
    val username: String = "Guest Fan",
    val email: String = "",
    val isSignedIn: Boolean = false,
    val authMethod: String = "guest", // "google", "passkey", "password"
    val tier: String = "Free", // Free, Pro, Legendary
    val requestsCount: Int = 0,
    val proExpiresAt: Long = 0L, // timestamp when 1-month Pro expires
    val isAdmin: Boolean = false,
    val customWallpaperUri: String? = null
) {
    // Backward compatibility getters
    val plan: String get() = tier
    val weeklyRequestsUsed: Int get() = requestsCount

    val isProActive: Boolean
        get() {
            if (tier == "Legendary" || isAdmin) return true
            if (tier == "Pro") {
                return proExpiresAt == 0L || System.currentTimeMillis() <= proExpiresAt
            }
            return false
        }

    val maxRequestsAllowed: Int
        get() = when {
            isAdmin || tier == "Legendary" -> Int.MAX_VALUE
            tier == "Pro" && isProActive -> Int.MAX_VALUE
            else -> 3 // Free tier: strictly 3 requests
        }

    val remainingRequests: Int
        get() = if (maxRequestsAllowed == Int.MAX_VALUE) 999 else (3 - requestsCount).coerceAtLeast(0)

    val canMakeRequest: Boolean
        get() = remainingRequests > 0
}
