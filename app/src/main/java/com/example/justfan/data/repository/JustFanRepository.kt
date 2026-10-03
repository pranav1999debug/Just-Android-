package com.example.justfan.data.repository

import com.example.justfan.data.local.*
import com.example.justfan.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class JustFanRepository(
    private val database: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val postDao = database.postDao()
    private val favoriteDao = database.favoriteDao()
    private val collectionDao = database.collectionDao()
    private val requestDao = database.requestDao()
    private val commentDao = database.commentDao()
    private val activityDao = database.activityDao()

    private val _preferences = MutableStateFlow(
        UserPreferences(
            themeVariant = "cyan",
            isDarkMode = true,
            contentFilter = "nsfw",
            preferredTags = listOf("cosplay", "exclusive", "portrait")
        )
    )
    val preferences = _preferences.asStateFlow()

    private val _userProfile = MutableStateFlow(
        UserProfile(
            username = "CreatorFan",
            email = "fan@justfan.vip",
            plan = "Pro",
            weeklyRequestsUsed = 1,
            isAdmin = true
        )
    )
    val userProfile = _userProfile.asStateFlow()

    init {
        scope.launch {
            seedInitialDataIfNeeded()
        }
    }

    val allPosts: Flow<List<PostEntity>> = postDao.getAllPosts()
    val freePosts: Flow<List<PostEntity>> = postDao.getFreePosts()
    val trendingPosts: Flow<List<PostEntity>> = postDao.getTrendingPosts()
    val allFavorites: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    val allCollections: Flow<List<CollectionEntity>> = collectionDao.getAllCollections()
    val allRequests: Flow<List<RequestEntity>> = requestDao.getAllRequests()
    val deliveredRequests: Flow<List<RequestEntity>> = requestDao.getDeliveredRequests()
    val allActivities: Flow<List<ActivityEntity>> = activityDao.getAllActivities()

    fun getPostById(id: String): Flow<PostEntity?> = postDao.getPostById(id)
    fun isFavorite(postId: String): Flow<Boolean> = favoriteDao.isFavorite(postId)
    fun getCommentsForPost(postId: String): Flow<List<CommentEntity>> = commentDao.getCommentsForPost(postId)
    fun getItemsForCollection(colId: String): Flow<List<CollectionItemEntity>> = collectionDao.getItemsForCollection(colId)

    suspend fun incrementPostClicks(postId: String) {
        postDao.incrementClicks(postId)
    }

    suspend fun toggleFavorite(postId: String, isCurrentlyFav: Boolean) {
        if (isCurrentlyFav) {
            favoriteDao.removeFavorite(postId)
            postDao.updateLikes(postId, -1)
        } else {
            favoriteDao.addFavorite(FavoriteEntity(postId = postId))
            postDao.updateLikes(postId, 1)
        }
    }

    suspend fun addComment(postId: String, content: String, author: String = "CreatorFan") {
        val comment = CommentEntity(
            id = UUID.randomUUID().toString(),
            postId = postId,
            authorName = author,
            content = content
        )
        commentDao.insertComment(comment)
        activityDao.insertActivity(
            ActivityEntity(
                id = UUID.randomUUID().toString(),
                type = "comment_added",
                title = "New comment by $author",
                body = content,
                link = postId
            )
        )
    }

    suspend fun submitRequest(name: String, email: String, telegram: String?, message: String, imageUrl: String?) {
        val req = RequestEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            telegramUsername = telegram,
            message = message,
            imageUrl = imageUrl,
            status = "pending"
        )
        requestDao.insertRequest(req)
        activityDao.insertActivity(
            ActivityEntity(
                id = UUID.randomUUID().toString(),
                type = "request_submitted",
                title = "Request submitted for $name",
                body = message
            )
        )
        _userProfile.value = _userProfile.value.copy(
            weeklyRequestsUsed = _userProfile.value.weeklyRequestsUsed + 1
        )
    }

    suspend fun updateRequestStatus(id: String, status: String, link: String?) {
        requestDao.updateStatus(id, status, link)
        if (status == "delivered") {
            activityDao.insertActivity(
                ActivityEntity(
                    id = UUID.randomUUID().toString(),
                    type = "request_delivered",
                    title = "Request fulfilled and delivered!",
                    body = "Content is now accessible via the Gallery archive",
                    link = link
                )
            )
        }
    }

    suspend fun createCollection(name: String) {
        val col = CollectionEntity(id = UUID.randomUUID().toString(), name = name)
        collectionDao.insertCollection(col)
    }

    suspend fun deleteCollection(id: String) {
        collectionDao.deleteCollection(id)
    }

    suspend fun addPostToCollection(collectionId: String, postId: String) {
        collectionDao.insertCollectionItem(
            CollectionItemEntity(
                id = UUID.randomUUID().toString(),
                collectionId = collectionId,
                postId = postId
            )
        )
    }

    suspend fun removePostFromCollection(collectionId: String, postId: String) {
        collectionDao.removeCollectionItem(collectionId, postId)
    }

    suspend fun createPost(
        title: String,
        description: String,
        imageUrl: String,
        linkUrl: String,
        premiumLinkUrl: String?,
        tags: List<String>,
        isFree: Boolean,
        isNsfw: Boolean
    ) {
        val post = PostEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            imageUrl = imageUrl,
            linkUrl = linkUrl,
            premiumLinkUrl = premiumLinkUrl,
            tags = tags,
            isFree = isFree,
            isNsfw = isNsfw,
            clicksCount = 0,
            likesCount = 0
        )
        postDao.insertPost(post)
        activityDao.insertActivity(
            ActivityEntity(
                id = UUID.randomUUID().toString(),
                type = "post_created",
                title = "New gallery added: $title",
                body = description.take(80),
                link = post.id
            )
        )
    }

    suspend fun deletePost(id: String) {
        postDao.deletePost(id)
    }

    fun updateTheme(themeVariant: String, isDark: Boolean) {
        _preferences.value = _preferences.value.copy(
            themeVariant = themeVariant,
            isDarkMode = isDark
        )
    }

    fun updateContentFilter(filter: String) {
        _preferences.value = _preferences.value.copy(contentFilter = filter)
    }

    fun addPreferredTag(tag: String) {
        val clean = tag.trim().lowercase().removePrefix("#")
        if (clean.isNotBlank() && !_preferences.value.preferredTags.contains(clean)) {
            _preferences.value = _preferences.value.copy(
                preferredTags = _preferences.value.preferredTags + clean
            )
        }
    }

    fun removePreferredTag(tag: String) {
        _preferences.value = _preferences.value.copy(
            preferredTags = _preferences.value.preferredTags - tag
        )
    }

    fun updatePlan(newPlan: String) {
        _userProfile.value = _userProfile.value.copy(plan = newPlan)
    }

    private suspend fun seedInitialDataIfNeeded() {
        if (postDao.getPostCount() > 0) return

        val samplePosts = listOf(
            PostEntity(
                id = "post-1",
                title = "Cyberpunk Neon Empress: 4K Portrait Collection",
                description = "Futuristic aesthetic portrait set featuring stunning holographic reflections, neon lighting, and high-fashion cyber gear.",
                imageUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200&q=80",
                contentImages = listOf(
                    "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200&q=80",
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1200&q=80",
                    "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=1200&q=80"
                ),
                linkUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477",
                premiumLinkUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=3840",
                tags = listOf("cosplay", "cyberpunk", "portrait", "exclusive"),
                author = "Aria Nova",
                isFree = true,
                isNsfw = false,
                clicksCount = 1420,
                likesCount = 384
            ),
            PostEntity(
                id = "post-2",
                title = "Ethereal Golden Hour Summer Shoot",
                description = "Sunset beach photo shoot with dramatic warm sun flares, cinematic golden tones, and breezy natural styling.",
                imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1200&q=80",
                contentImages = listOf(
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1200&q=80",
                    "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=1200&q=80"
                ),
                linkUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
                premiumLinkUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=3840",
                tags = listOf("fashion", "summer", "portrait"),
                author = "Chloe Valenti",
                isFree = false,
                isNsfw = false,
                clicksCount = 980,
                likesCount = 215
            ),
            PostEntity(
                id = "post-3",
                title = "Sakura Spirit Shrine Maiden Studio Series",
                description = "Detailed Japanese fantasy costume with ornate lace, cherry blossom accents, and studio lighting setup.",
                imageUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=1200&q=80",
                contentImages = listOf(
                    "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=1200&q=80",
                    "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=1200&q=80"
                ),
                linkUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1",
                premiumLinkUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=3840",
                tags = listOf("cosplay", "anime", "exclusive"),
                author = "Yuki Rin",
                isFree = true,
                isNsfw = false,
                clicksCount = 2450,
                likesCount = 612
            ),
            PostEntity(
                id = "post-4",
                title = "Athletic Flow: Gym & Fitness Motivation Set",
                description = "High energy gym aesthetic photoshoot with dynamic motion, studio spotlights, and clean athletic fits.",
                imageUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=1200&q=80",
                contentImages = listOf(
                    "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=1200&q=80"
                ),
                linkUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9",
                tags = listOf("fitness", "athletic", "portrait"),
                author = "Sierra Brooks",
                isFree = true,
                isNsfw = false,
                clicksCount = 890,
                likesCount = 190
            ),
            PostEntity(
                id = "post-5",
                title = "Midnight Velvet: High Fashion Monolith",
                description = "Editorial noir studio session exploring monochrome silhouettes, dramatic contrast, and couture tailoring.",
                imageUrl = "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=1200&q=80",
                contentImages = listOf(
                    "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=1200&q=80"
                ),
                linkUrl = "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e",
                premiumLinkUrl = "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=3840",
                tags = listOf("fashion", "exclusive", "noir"),
                author = "Elena Vance",
                isFree = false,
                isNsfw = true,
                clicksCount = 1850,
                likesCount = 430
            ),
            PostEntity(
                id = "post-6",
                title = "Retro Wave Arcade Pop Showcase",
                description = "Vibrant 80s arcade atmosphere featuring vintage pinball machines, neon purples, and stylish throwback attire.",
                imageUrl = "https://images.unsplash.com/photo-1509967419530-da38b4704bc6?w=1200&q=80",
                contentImages = listOf(
                    "https://images.unsplash.com/photo-1509967419530-da38b4704bc6?w=1200&q=80"
                ),
                linkUrl = "https://images.unsplash.com/photo-1509967419530-da38b4704bc6",
                tags = listOf("cosplay", "retro", "portrait"),
                author = "Mia Sterling",
                isFree = true,
                isNsfw = false,
                clicksCount = 760,
                likesCount = 145
            )
        )
        postDao.insertPosts(samplePosts)

        val sampleRequests = listOf(
            RequestEntity(
                id = "req-1",
                name = "Aria Nova Cyberpunk V2",
                email = "user1@demo.com",
                telegramUsername = "@arianovafan",
                message = "Would love to see more cyberpunk themed high res wallpapers from Aria!",
                imageUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&q=80",
                status = "delivered",
                downloadLink = "https://images.unsplash.com/photo-1578632767115-351597cf2477"
            ),
            RequestEntity(
                id = "req-2",
                name = "Chloe Valenti Malibu Set",
                email = "user2@demo.com",
                telegramUsername = "@chloefan",
                message = "The golden hour set was amazing, please fulfill the remaining 20 photos.",
                imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&q=80",
                status = "delivered",
                downloadLink = "https://images.unsplash.com/photo-1534528741775-53994a69daeb"
            ),
            RequestEntity(
                id = "req-3",
                name = "Yuki Rin Shrine Series Part 2",
                email = "user3@demo.com",
                telegramUsername = "@yukilover",
                message = "Please upload full 4K ZIP pack of the shrine maiden costume shoot.",
                imageUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=600&q=80",
                status = "in_progress"
            )
        )
        requestDao.insertRequests(sampleRequests)

        val sampleActivities = listOf(
            ActivityEntity(
                id = "act-1",
                type = "post_created",
                title = "New gallery: Cyberpunk Neon Empress",
                body = "4K Portrait Collection by Aria Nova now live!",
                link = "post-1"
            ),
            ActivityEntity(
                id = "act-2",
                type = "request_delivered",
                title = "Community Request Delivered: Aria Nova V2",
                body = "Fulfilled community request is available now in Gallery.",
                link = "req-1"
            ),
            ActivityEntity(
                id = "act-3",
                type = "post_created",
                title = "New gallery: Sakura Spirit Shrine Maiden",
                body = "Exclusive Japanese fantasy shoot by Yuki Rin added.",
                link = "post-3"
            )
        )
        activityDao.insertActivities(sampleActivities)

        val sampleCollections = listOf(
            CollectionEntity(id = "col-1", name = "My Best Creator Picks"),
            CollectionEntity(id = "col-2", name = "Cyberpunk & Cosplay")
        )
        sampleCollections.forEach { collectionDao.insertCollection(it) }
        collectionDao.insertCollectionItem(
            CollectionItemEntity(
                id = UUID.randomUUID().toString(),
                collectionId = "col-1",
                postId = "post-1"
            )
        )
        collectionDao.insertCollectionItem(
            CollectionItemEntity(
                id = UUID.randomUUID().toString(),
                collectionId = "col-2",
                postId = "post-3"
            )
        )
        favoriteDao.addFavorite(FavoriteEntity("post-1"))
        favoriteDao.addFavorite(FavoriteEntity("post-3"))
    }
}
