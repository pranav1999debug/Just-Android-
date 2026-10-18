package com.example.justfan.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import org.json.JSONArray
import java.util.UUID

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val linkUrl: String,
    val directLinkUrl: String? = null,
    val premiumLinkUrl: String? = null,
    val contentImages: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val section: String = "home",
    val author: String = "JustFan Creator",
    val isNsfw: Boolean = false,
    val isFree: Boolean = false,
    val clicksCount: Int = 0,
    val likesCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val name: String? = null,
    val email: String,
    val telegramUsername: String? = null,
    val message: String,
    val imageUrl: String? = null,
    val status: String = "pending", // pending, approved, fulfilled, rejected
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
    val status: String = "Active",
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val postId: String,
    val savedAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val id: String = "guest_user",
    val username: String = "Guest Fan",
    val email: String = "",
    val isSignedIn: Boolean = false,
    val authMethod: String = "guest",
    val tier: String = "Free", // Free, Pro, Legendary
    val isAdmin: Boolean = false,
    val proExpiresAt: Long = 0L,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val customWallpaperUri: String? = null
) {
    val isProActive: Boolean
        get() = tier.equals("Pro", ignoreCase = true) || tier.equals("Legendary", ignoreCase = true)
}

data class UserPreferencesState(
    val themeVariant: String = "cyan",
    val isDarkMode: Boolean = true,
    val customWallpaperUri: String? = null,
    val wallpaperDim: Float = 0.65f
)

data class RemoteUserPreferences(
    val theme: String?,
    val wallpaperUrl: String?
)

data class RequestQuota(
    val unlimited: Boolean = false,
    val limit: Int = 3,
    val used: Int = 0,
    val remaining: Int = 3,
    val resetsAt: String? = null
)

data class ActivityEntity(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "general",
    val title: String = "",
    val body: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class CommentEntity(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val authorName: String = "Fan Member",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>?): String {
        if (list == null) return "[]"
        val array = JSONArray()
        for (item in list) {
            array.put(item)
        }
        return array.toString()
    }

    @TypeConverter
    fun toList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(data)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }
}
