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
    val status: String = "pending", // pending, in_progress, delivered
    val downloadLink: String? = null,
    val createdAt: Long = System.currentTimeMillis()
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
    val preferredTags: List<String> = emptyList()
)

data class UserProfile(
    val id: String = "user_demo",
    val username: String = "CreatorFan",
    val email: String = "fan@justfan.vip",
    val plan: String = "Free", // Free, Pro, Legendary
    val weeklyRequestsUsed: Int = 1,
    val isAdmin: Boolean = true
)
