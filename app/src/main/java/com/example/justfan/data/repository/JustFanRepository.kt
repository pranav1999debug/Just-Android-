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
    private val context: android.content.Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val postDao = database.postDao()
    private val favoriteDao = database.favoriteDao()
    private val collectionDao = database.collectionDao()
    private val requestDao = database.requestDao()
    private val commentDao = database.commentDao()
    private val activityDao = database.activityDao()
    private val userDao = database.userDao()
    private val postClickDao = database.postClickDao()

    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    private val _preferences = MutableStateFlow(
        UserPreferences(
            themeVariant = "cyan",
            isDarkMode = true,
            contentFilter = "nsfw",
            preferredTags = listOf("cosplay", "exclusive", "portrait")
        )
    )
    val preferences = _preferences.asStateFlow()

    private fun loadInitialProfile(): UserProfile {
        try {
            val sp = context?.getSharedPreferences("justfan_auth_prefs", android.content.Context.MODE_PRIVATE)
            if (sp != null && sp.getBoolean("is_signed_in", false)) {
                val email = sp.getString("email", "") ?: ""
                val isRey = email.equals("reytherapper12@gmail.com", ignoreCase = true)
                return UserProfile(
                    id = sp.getString("id", if (isRey) "admin_rey" else UUID.randomUUID().toString()) ?: "guest_user",
                    username = sp.getString("username", if (isRey) "reytherapper12" else "User") ?: "User",
                    email = email,
                    isSignedIn = true,
                    authMethod = sp.getString("auth_method", "google") ?: "google",
                    tier = sp.getString("tier", if (isRey) "Legendary" else "Free") ?: "Free",
                    isAdmin = isRey,
                    proExpiresAt = sp.getLong("pro_expires_at", if (isRey) Long.MAX_VALUE else 0L),
                    customWallpaperUri = sp.getString("custom_wallpaper_uri", null),
                    accessToken = sp.getString("access_token", null),
                    refreshToken = sp.getString("refresh_token", null)
                )
            }
        } catch (_: Exception) {}
        return UserProfile(
            id = "guest_user",
            username = "Guest Fan",
            email = "",
            isSignedIn = false,
            authMethod = "guest",
            tier = "Free",
            isAdmin = false,
            proExpiresAt = 0L,
            accessToken = null,
            refreshToken = null
        )
    }

    private fun saveProfileToPrefs(profile: UserProfile) {
        try {
            val sp = context?.getSharedPreferences("justfan_auth_prefs", android.content.Context.MODE_PRIVATE) ?: return
            val isStrictAdmin = profile.email.equals("reytherapper12@gmail.com", ignoreCase = true)
            sp.edit().apply {
                putBoolean("is_signed_in", profile.isSignedIn)
                putString("id", profile.id)
                putString("username", profile.username)
                putString("email", profile.email)
                putString("auth_method", profile.authMethod)
                putString("tier", if (isStrictAdmin) "Legendary" else profile.tier)
                putBoolean("is_admin", isStrictAdmin)
                putLong("pro_expires_at", profile.proExpiresAt)
                putString("custom_wallpaper_uri", profile.customWallpaperUri)
                putString("access_token", profile.accessToken)
                putString("refresh_token", profile.refreshToken)
                apply()
            }
        } catch (_: Exception) {}
    }

    private val _userProfile = MutableStateFlow(loadInitialProfile())
    val userProfile = _userProfile.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    init {
        scope.launch {
            cleanDummyData()
            syncFromSupabase()
        }
    }

    suspend fun syncFromSupabase() {
        _isSyncing.value = true
        try {
            cleanDummyData()

            // 1. Sync Real User Profiles from Supabase
            val profilesResult = com.example.justfan.data.remote.SupabaseClient.fetchProfiles(limit = 500)
            val profilesMap = mutableMapOf<String, String>()
            if (profilesResult.isSuccess) {
                val remoteUsers = profilesResult.getOrNull().orEmpty()
                if (remoteUsers.isNotEmpty()) {
                    for (u in remoteUsers) {
                        profilesMap[u.id] = u.username
                    }
                    val existingUsers = userDao.getAllUsersList().associateBy { it.id }
                    val mergedUsers = remoteUsers.map { remote ->
                        val local = existingUsers[remote.id]
                        if (local != null) {
                            remote.copy(tier = local.tier, requestsCount = local.requestsCount, status = local.status)
                        } else {
                            remote
                        }
                    }
                    userDao.insertUsers(mergedUsers)
                    val remoteIds = remoteUsers.map { it.id }.toSet()
                    for (u in existingUsers.values) {
                        if (u.id !in remoteIds) {
                            userDao.deleteUser(u.id)
                        }
                    }
                }
            }

            // 2. Sync Clicks, Likes (Favorites), and Shares from Supabase
            val clicksSummary = com.example.justfan.data.remote.SupabaseClient.fetchPostClicksSummary().getOrDefault(emptyMap())
            val localClicksSummary = postClickDao.getClicksSummary().associate { it.postId to it.clickCount }
            val favoritesResult = com.example.justfan.data.remote.SupabaseClient.fetchFavoritesSummary().getOrNull()
            val likesSummary = favoritesResult?.first ?: emptyMap()
            val userFavPostIds = favoritesResult?.second ?: emptySet()

            // Update user favorites in local DB
            for (favPostId in userFavPostIds) {
                favoriteDao.addFavorite(FavoriteEntity(postId = favPostId))
            }

            // 3. Sync Posts from Supabase with real clicks and likes
            val postsResult = com.example.justfan.data.remote.SupabaseClient.fetchPosts(limit = 1500)
            if (postsResult.isSuccess) {
                val remotePosts = postsResult.getOrNull().orEmpty()
                if (remotePosts.isNotEmpty()) {
                    val existingPosts = postDao.getAllPostsList().associateBy { it.id }
                    val mergedPosts = remotePosts.map { remote ->
                        val local = existingPosts[remote.id]
                        val remoteClicks = clicksSummary[remote.id] ?: 0
                        val localClicks = localClicksSummary[remote.id] ?: 0
                        val totalClicks = maxOf(remote.clicksCount, remoteClicks, (local?.clicksCount ?: 0), localClicks)
                        val totalLikes = maxOf(remote.likesCount, (likesSummary[remote.id] ?: 0), (local?.likesCount ?: 0))
                        remote.copy(clicksCount = totalClicks, likesCount = totalLikes)
                    }
                    postDao.insertPosts(mergedPosts)
                    val remoteIds = remotePosts.map { it.id }.toSet()
                    for (p in existingPosts.values) {
                        if (p.id !in remoteIds) {
                            postDao.deletePost(p.id)
                        }
                    }
                }
            }

            // 4. Sync Real Comments from Supabase post_comments
            val commentsResult = com.example.justfan.data.remote.SupabaseClient.fetchPostComments()
            if (commentsResult.isSuccess) {
                val remoteComments = commentsResult.getOrNull().orEmpty()
                if (remoteComments.isNotEmpty()) {
                    val mappedComments = remoteComments.map { rc ->
                        val resolvedAuthor = profilesMap[rc.authorName] ?: "CommunityFan"
                        rc.copy(authorName = resolvedAuthor)
                    }
                    commentDao.insertComments(mappedComments)
                }
            }

            // 5. Sync Requests from Supabase
            val reqResult = com.example.justfan.data.remote.SupabaseClient.fetchRequests()
            if (reqResult.isSuccess) {
                val remoteRequests = reqResult.getOrNull().orEmpty()
                val existingRequests = requestDao.getAllRequestsList()
                val remoteReqIds = remoteRequests.map { it.id }.toSet()
                for (r in existingRequests) {
                    if (r.id !in remoteReqIds) {
                        requestDao.deleteRequest(r.id)
                    }
                }
                if (remoteRequests.isNotEmpty()) {
                    requestDao.insertRequests(remoteRequests)
                }
            }

            // 6. Sync Collections from Supabase
            val colResult = com.example.justfan.data.remote.SupabaseClient.fetchCollections()
            if (colResult.isSuccess) {
                val remoteCollections = colResult.getOrNull().orEmpty()
                val existingCols = collectionDao.getAllCollectionsList()
                val remoteColIds = remoteCollections.map { it.id }.toSet()
                for (c in existingCols) {
                    if (c.id !in remoteColIds) {
                        collectionDao.deleteCollection(c.id)
                    }
                }
                for (col in remoteCollections) {
                    collectionDao.insertCollection(col)
                }
            }

            // 7. Fetch Active User Profile (only if signed in, strictly refresh the logged-in user's profile)
            val currentProfile = _userProfile.value
            val currentEmail = currentProfile.email
            if (currentProfile.isSignedIn && currentEmail.isNotBlank()) {
                val profileResult = com.example.justfan.data.remote.SupabaseClient.fetchProfile(currentEmail)
                if (profileResult.isSuccess) {
                    profileResult.getOrNull()?.let { p ->
                        val isStrictAdmin = p.email.equals("reytherapper12@gmail.com", ignoreCase = true)
                        val updated = p.copy(
                            // Profile rows do not contain auth session tokens. Never overwrite
                            // the active Supabase session while refreshing profile data.
                            id = if (p.id.isBlank()) currentProfile.id else p.id,
                            isSignedIn = true,
                            isAdmin = isStrictAdmin,
                            tier = if (isStrictAdmin) "Legendary" else p.tier,
                            accessToken = currentProfile.accessToken,
                            refreshToken = currentProfile.refreshToken
                        )
                        _userProfile.value = updated
                        saveProfileToPrefs(updated)
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to Room cached database
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun refreshPosts() {
        syncFromSupabase()
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

    private fun getValidUuidOrNull(userId: String?): String? {
        if (userId.isNullOrBlank()) return null
        return if (userId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))) {
            userId
        } else if (userId == "admin_rey" || _userProfile.value.email.equals("reytherapper12@gmail.com", ignoreCase = true)) {
            "fe335770-80a9-4125-9c15-d47f385579fb"
        } else {
            null
        }
    }

    suspend fun incrementPostClicks(postId: String) {
        postDao.incrementClicks(postId)
        postClickDao.insertClick(
            PostClickEntity(
                postId = postId,
                userId = getValidUuidOrNull(_userProfile.value.id),
                downloadType = "view"
            )
        )
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.recordPostClick(
                postId = postId,
                userId = getValidUuidOrNull(_userProfile.value.id),
                clickType = "view"
            )
        }
    }

    suspend fun recordDownloadClick(postId: String, downloadType: String = "download") {
        postDao.incrementClicks(postId)
        postClickDao.insertClick(
            PostClickEntity(
                postId = postId,
                userId = getValidUuidOrNull(_userProfile.value.id),
                downloadType = downloadType
            )
        )
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.recordPostClick(
                postId = postId,
                userId = getValidUuidOrNull(_userProfile.value.id),
                clickType = downloadType
            )
        }
    }

    suspend fun toggleFavorite(postId: String, isCurrentlyFav: Boolean) {
        if (isCurrentlyFav) {
            favoriteDao.removeFavorite(postId)
            postDao.updateLikes(postId, -1)
            scope.launch {
                com.example.justfan.data.remote.SupabaseClient.toggleFavorite(postId, _userProfile.value.id, false)
            }
        } else {
            favoriteDao.addFavorite(FavoriteEntity(postId = postId))
            postDao.updateLikes(postId, 1)
            scope.launch {
                com.example.justfan.data.remote.SupabaseClient.toggleFavorite(postId, _userProfile.value.id, true)
            }
        }
    }

    suspend fun recordShare(postId: String) {
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.recordPostShare(postId, _userProfile.value.id)
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
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.insertPostComment(postId, _userProfile.value.id, content)
        }
    }

    suspend fun syncRequestsFromSupabase(): Result<List<RequestEntity>> {
        val reqResult = com.example.justfan.data.remote.SupabaseClient.fetchRequests()
        if (reqResult.isSuccess) {
            val remoteRequests = reqResult.getOrNull().orEmpty()
            val existingRequests = requestDao.getAllRequestsList()
            val remoteReqIds = remoteRequests.map { it.id }.toSet()
            for (r in existingRequests) {
                if (r.id !in remoteReqIds) {
                    requestDao.deleteRequest(r.id)
                }
            }
            if (remoteRequests.isNotEmpty()) {
                requestDao.insertRequests(remoteRequests)
            }
        }
        return reqResult
    }

    suspend fun submitRequest(
        name: String,
        email: String,
        telegram: String?,
        message: String,
        imageUrl: String?
    ): Result<Boolean> {
        var currentProfile = _userProfile.value
        if (!currentProfile.isSignedIn || currentProfile.id == "guest_user") {
            return Result.failure(Exception("SIGN_IN_REQUIRED"))
        }

        // Access tokens are short-lived. A locally signed-in profile can still
        // exist after its access token expires, so refresh the Supabase session
        // before the insert instead of sending the user back to the login UI.
        if (currentProfile.accessToken.isNullOrBlank()) {
            currentProfile = refreshStoredSession() ?: return Result.failure(Exception("SIGN_IN_REQUIRED"))
        }

        val deviceId = context?.let { com.example.justfan.util.getDeviceId(it) } ?: "unknown_device"

        // Push directly to live Supabase requests table using real user session
        var remoteResult = com.example.justfan.data.remote.SupabaseClient.submitRequest(
            name = name,
            email = email,
            telegram = telegram,
            message = message,
            imageUrl = imageUrl,
            userId = currentProfile.id,
            deviceId = deviceId,
            accessToken = currentProfile.accessToken!!
        )

        // A 401 means the token expired between startup and submit. Refresh
        // once and retry the exact request with the new access token.
        if (remoteResult.isFailure && remoteResult.exceptionOrNull()?.message == "SIGN_IN_REQUIRED") {
            val refreshed = refreshStoredSession()
            if (refreshed != null) {
                remoteResult = com.example.justfan.data.remote.SupabaseClient.submitRequest(
                    name = name,
                    email = email,
                    telegram = telegram,
                    message = message,
                    imageUrl = imageUrl,
                    userId = refreshed.id,
                    deviceId = deviceId,
                    accessToken = refreshed.accessToken!!
                )
            }
        }

        if (remoteResult.isSuccess) {
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
            // Refresh live requests from Supabase
            syncRequestsFromSupabase()
        }
        return remoteResult
    }

    private suspend fun refreshStoredSession(): UserProfile? {
        val current = _userProfile.value
        val refreshToken = current.refreshToken ?: return null
        val result = com.example.justfan.data.remote.SupabaseClient.refreshSession(refreshToken)
        if (result.isFailure) return null
        val session = result.getOrNull() ?: return null
        signInWithRefreshedSession(session)
        return _userProfile.value
    }

    suspend fun updateRequestStatus(id: String, status: String, link: String?) {
        requestDao.updateStatus(id, status, link)
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.updateRequestStatusInSupabase(id, status)
        }
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
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.createCollection(name)
        }
    }

    suspend fun deleteCollection(id: String) {
        collectionDao.deleteCollection(id)
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.deleteCollection(id)
        }
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
        contentImages: List<String> = emptyList(),
        linkUrl: String,
        premiumLinkUrl: String?,
        tags: List<String>,
        isFree: Boolean,
        isNsfw: Boolean
    ): Result<Boolean> {
        val post = PostEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            imageUrl = imageUrl,
            contentImages = contentImages,
            linkUrl = linkUrl,
            premiumLinkUrl = premiumLinkUrl,
            tags = tags,
            isFree = isFree,
            isNsfw = isNsfw,
            clicksCount = 0,
            likesCount = 0
        )
        var profile = _userProfile.value
        if (!profile.isSignedIn || !profile.isAdmin) {
            return Result.failure(Exception("ADMIN_AUTH_REQUIRED"))
        }
        if (profile.accessToken.isNullOrBlank()) {
            profile = refreshStoredSession() ?: return Result.failure(Exception("SIGN_IN_REQUIRED"))
        }

        var remoteResult = com.example.justfan.data.remote.SupabaseClient.insertPost(post, profile.accessToken!!)
        if (remoteResult.isFailure && remoteResult.exceptionOrNull()?.message?.contains("401") == true) {
            val refreshed = refreshStoredSession()
            if (refreshed != null) {
                remoteResult = com.example.justfan.data.remote.SupabaseClient.insertPost(post, refreshed.accessToken!!)
            }
        }
        if (remoteResult.isFailure) return remoteResult

        // Only cache the post locally after Supabase confirms the insert.
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
        return Result.success(true)
    }

    suspend fun deletePost(id: String) {
        postDao.deletePost(id)
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.deletePost(id)
        }
    }

    suspend fun updatePost(post: PostEntity) {
        postDao.updatePost(post)
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.updatePost(post)
        }
    }

    suspend fun updateUserTier(userId: String, newTier: String) {
        userDao.updateUserTier(userId, newTier)
        if (_userProfile.value.id == userId || _userProfile.value.email.equals(userId, ignoreCase = true)) {
            updateTier(newTier)
        }
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.updateUserTier(userId, newTier)
        }
    }

    suspend fun updateUserRequestsCount(userId: String, count: Int) {
        userDao.updateUserRequestsCount(userId, count)
    }

    suspend fun fetchRequestQuota(): Result<com.example.justfan.data.model.RequestQuota> {
        val current = _userProfile.value
        if (!current.isSignedIn || current.id == "guest_user") {
            return Result.success(com.example.justfan.data.model.RequestQuota())
        }
        val deviceId = context?.let { com.example.justfan.util.getDeviceId(it) } ?: "unknown_device"
        return com.example.justfan.data.remote.SupabaseClient.fetchRequestQuota(current.id, deviceId, current.accessToken)
    }

    suspend fun updateUserStatus(userId: String, status: String) {
        userDao.updateUserStatus(userId, status)
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.updateUserStatus(userId, status)
        }
    }

    suspend fun deleteUser(userId: String) {
        userDao.deleteUser(userId)
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.deleteUser(userId)
        }
    }

    suspend fun rejectRequest(id: String, reason: String) {
        requestDao.updateStatusWithReason(id, "rejected", null, reason)
        activityDao.insertActivity(
            ActivityEntity(
                id = UUID.randomUUID().toString(),
                type = "request_rejected",
                title = "Request Rejected",
                body = "Reason: $reason"
            )
        )
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.rejectRequestInSupabase(id, reason)
        }
    }

    suspend fun fulfillRequest(id: String, downloadLink: String) {
        requestDao.updateStatusWithReason(id, "delivered", downloadLink, null)
        activityDao.insertActivity(
            ActivityEntity(
                id = UUID.randomUUID().toString(),
                type = "request_delivered",
                title = "Request Fulfilled & Uploaded",
                body = "Fulfillment link: $downloadLink",
                link = downloadLink
            )
        )
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.fulfillRequestInSupabase(id, downloadLink)
        }
    }

    suspend fun deleteRequest(id: String) {
        requestDao.deleteRequest(id)
        scope.launch {
            com.example.justfan.data.remote.SupabaseClient.deleteRequestFromSupabase(id)
        }
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
        updateTier(newPlan)
    }

    fun updateTier(newTier: String) {
        val isRey = _userProfile.value.email.equals("reytherapper12@gmail.com", ignoreCase = true)
        val expiresAt = when (newTier) {
            "Pro" -> System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L) // 1 month
            "Legendary" -> Long.MAX_VALUE // Unlimited time
            else -> 0L
        }
        val updated = _userProfile.value.copy(
            tier = newTier,
            isAdmin = isRey,
            proExpiresAt = expiresAt
        )
        _userProfile.value = updated
        saveProfileToPrefs(updated)
    }

    fun signInWithEmail(email: String, password: String): Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        val isAdminUser = cleanEmail == "reytherapper12@gmail.com"

        // Try Supabase Auth API
        val supaResult = runCatching {
            kotlinx.coroutines.runBlocking {
                com.example.justfan.data.remote.SupabaseClient.signInWithEmailPassword(cleanEmail, password)
            }
        }.getOrNull()

        if (supaResult != null && supaResult.isSuccess) {
            val session = supaResult.getOrThrow()
            val user = UserProfile(
                id = session.userId,
                username = cleanEmail.substringBefore("@"),
                email = cleanEmail,
                isSignedIn = true,
                authMethod = "password",
                tier = if (isAdminUser) "Legendary" else "Free",
                isAdmin = isAdminUser,
                proExpiresAt = if (isAdminUser) Long.MAX_VALUE else 0L,
                accessToken = session.accessToken,
                refreshToken = session.refreshToken
            )
            _userProfile.value = user
            saveProfileToPrefs(user)
            return Result.success(user)
        }

        return Result.failure<UserProfile>(
            IllegalArgumentException(
                supaResult?.exceptionOrNull()?.message
                    ?: "Supabase sign-in failed. Check your email and password, then try again."
            )
        )
    }

    fun signInWithGoogle(
        email: String,
        name: String = "Google User",
        accessToken: String? = null,
        userId: String? = null,
        refreshToken: String? = null
    ): Result<UserProfile> {
        if (accessToken.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Complete Google OAuth sign-in through Supabase first."))
        }
        val cleanEmail = email.trim().lowercase()
        val isAdminUser = cleanEmail == "reytherapper12@gmail.com"
        val effectiveId = when {
            !userId.isNullOrBlank() -> userId
            isAdminUser -> "admin_rey"
            else -> UUID.randomUUID().toString()
        }
        val profile = UserProfile(
            id = effectiveId,
            username = if (isAdminUser) "reytherapper12 (Admin)" else name,
            email = cleanEmail,
            isSignedIn = true,
            authMethod = "google",
            tier = if (isAdminUser) "Legendary" else "Free",
            isAdmin = isAdminUser,
            proExpiresAt = if (isAdminUser) Long.MAX_VALUE else 0L,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
        _userProfile.value = profile
        saveProfileToPrefs(profile)
        // Background sync user's specific tier if they exist on Supabase
        scope.launch {
            try {
                val res = com.example.justfan.data.remote.SupabaseClient.fetchProfile(cleanEmail)
                if (res.isSuccess) {
                    res.getOrNull()?.let { remoteProfile ->
                        val updated = remoteProfile.copy(
                            id = effectiveId,
                            isAdmin = isAdminUser,
                            tier = if (isAdminUser) "Legendary" else remoteProfile.tier,
                            accessToken = accessToken,
                            refreshToken = refreshToken
                        )
                        _userProfile.value = updated
                        saveProfileToPrefs(updated)
                    }
                }
            } catch (_: Exception) {}
        }
        return Result.success(profile)
    }

    fun signInWithRefreshedSession(
        session: com.example.justfan.data.remote.SupabaseClient.AuthSession
    ): Result<UserProfile> {
        val cleanEmail = session.email.trim().lowercase()
        val isAdminUser = cleanEmail == "reytherapper12@gmail.com"
        val effectiveId = if (session.userId.isNotBlank()) session.userId else if (isAdminUser) "admin_rey" else UUID.randomUUID().toString()
        val profile = UserProfile(
            id = effectiveId,
            username = if (isAdminUser) "reytherapper12 (Admin)" else if (cleanEmail.contains("@")) cleanEmail.substringBefore("@") else "User",
            email = cleanEmail,
            isSignedIn = true,
            authMethod = "biometric",
            tier = if (isAdminUser) "Legendary" else "Free",
            isAdmin = isAdminUser,
            proExpiresAt = if (isAdminUser) Long.MAX_VALUE else 0L,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken
        )
        _userProfile.value = profile
        saveProfileToPrefs(profile)
        // Background sync user's specific tier if they exist on Supabase
        scope.launch {
            try {
                val res = com.example.justfan.data.remote.SupabaseClient.fetchProfile(cleanEmail)
                if (res.isSuccess) {
                    res.getOrNull()?.let { remoteProfile ->
                        val updated = remoteProfile.copy(
                            id = effectiveId,
                            isAdmin = isAdminUser,
                            tier = if (isAdminUser) "Legendary" else remoteProfile.tier,
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken
                        )
                        _userProfile.value = updated
                        saveProfileToPrefs(updated)
                    }
                }
            } catch (_: Exception) {}
        }
        return Result.success(profile)
    }

    fun signInWithPasskey(deviceCredentialName: String = "Device Passkey"): Result<UserProfile> {
        val current = _userProfile.value
        val isRey = current.email.equals("reytherapper12@gmail.com", ignoreCase = true)
        val profile = UserProfile(
            id = if (isRey) "admin_rey" else "passkey_${UUID.randomUUID().toString().take(8)}",
            username = if (isRey) "reytherapper12 (Admin)" else deviceCredentialName,
            email = if (isRey) "reytherapper12@gmail.com" else "passkey@device.local",
            isSignedIn = true,
            authMethod = "passkey",
            tier = if (isRey) "Legendary" else "Free",
            isAdmin = isRey,
            proExpiresAt = if (isRey) Long.MAX_VALUE else 0L,
            accessToken = null,
            refreshToken = null
        )
        _userProfile.value = profile
        saveProfileToPrefs(profile)
        return Result.success(profile)
    }

    fun signOut(clearBiometric: Boolean = true) {
        if (clearBiometric) {
            context?.let {
                com.example.justfan.util.BiometricAuthManager.clearBiometricData(it)
            }
        }
        val guest = UserProfile(
            id = "guest_user",
            username = "Guest Fan",
            email = "",
            isSignedIn = false,
            authMethod = "guest",
            tier = "Free",
            isAdmin = false,
            proExpiresAt = 0L,
            accessToken = null,
            refreshToken = null
        )
        _userProfile.value = guest
        saveProfileToPrefs(guest)
    }

    fun updateWallpaper(wallpaperUri: String?, dim: Float = 0.65f) {
        _preferences.value = _preferences.value.copy(
            customWallpaperUri = wallpaperUri,
            wallpaperDim = dim
        )
        _userProfile.value = _userProfile.value.copy(
            customWallpaperUri = wallpaperUri
        )
    }

    private suspend fun cleanDummyData() {
        postDao.clearDummyPosts()
        requestDao.clearDummyRequests()
        activityDao.clearDummyActivities()
        collectionDao.clearDummyCollections()
        userDao.clearDummyUsers()
        favoriteDao.removeFavorite("post-1")
        favoriteDao.removeFavorite("post-2")
        favoriteDao.removeFavorite("post-3")
        favoriteDao.removeFavorite("post-4")
        favoriteDao.removeFavorite("post-5")
        favoriteDao.removeFavorite("post-6")
        collectionDao.deleteCollection("col-1")
        collectionDao.deleteCollection("col-2")
        userDao.deleteUser("user_alex")
        userDao.deleteUser("user_elena")
        userDao.deleteUser("user_liam")
        userDao.deleteUser("user_sara")
    }
}
