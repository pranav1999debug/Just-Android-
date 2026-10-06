package com.example.justfan.data.local

import androidx.room.*
import com.example.justfan.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun getAllPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE id = :id LIMIT 1")
    fun getPostById(id: String): Flow<PostEntity?>

    @Query("SELECT * FROM posts WHERE isFree = 1 ORDER BY createdAt DESC")
    fun getFreePosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts ORDER BY clicksCount DESC, createdAt DESC")
    fun getTrendingPosts(): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    @Query("UPDATE posts SET clicksCount = clicksCount + 1 WHERE id = :id")
    suspend fun incrementClicks(id: String)

    @Query("UPDATE posts SET likesCount = likesCount + :delta WHERE id = :id")
    suspend fun updateLikes(id: String, delta: Int)

    @Query("DELETE FROM posts WHERE id = :id")
    suspend fun deletePost(id: String)

    @Update
    suspend fun updatePost(post: PostEntity)

    @Query("SELECT COUNT(*) FROM posts")
    suspend fun getPostCount(): Int

    @Query("SELECT * FROM posts")
    suspend fun getAllPostsList(): List<PostEntity>

    @Query("DELETE FROM posts WHERE id LIKE 'post-%' OR id LIKE 'sample-%'")
    suspend fun clearDummyPosts()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE postId = :postId)")
    fun isFavorite(postId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE postId = :postId")
    suspend fun removeFavorite(postId: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(col: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("SELECT * FROM collections")
    suspend fun getAllCollectionsList(): List<CollectionEntity>

    @Query("DELETE FROM collections WHERE id LIKE 'col-%' OR id LIKE 'sample-%'")
    suspend fun clearDummyCollections()

    @Query("SELECT * FROM collection_items WHERE collectionId = :colId ORDER BY addedAt DESC")
    fun getItemsForCollection(colId: String): Flow<List<CollectionItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItem(item: CollectionItemEntity)

    @Query("DELETE FROM collection_items WHERE collectionId = :colId AND postId = :postId")
    suspend fun removeCollectionItem(colId: String, postId: String)
}

@Dao
interface RequestDao {
    @Query("SELECT * FROM requests ORDER BY createdAt DESC")
    fun getAllRequests(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE status = 'delivered' ORDER BY createdAt DESC")
    fun getDeliveredRequests(): Flow<List<RequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(req: RequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(reqs: List<RequestEntity>)

    @Query("UPDATE requests SET status = :status, downloadLink = :link WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, link: String?)

    @Query("UPDATE requests SET status = :status, downloadLink = :link, rejectionReason = :reason WHERE id = :id")
    suspend fun updateStatusWithReason(id: String, status: String, link: String?, reason: String? = null)

    @Query("DELETE FROM requests WHERE id = :id")
    suspend fun deleteRequest(id: String)

    @Query("DELETE FROM requests WHERE id LIKE 'req-%' OR id LIKE 'sample-%'")
    suspend fun clearDummyRequests()

    @Query("SELECT * FROM requests")
    suspend fun getAllRequestsList(): List<RequestEntity>
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY joinedAt DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    suspend fun getAllUsersList(): List<UserEntity>

    @Query("DELETE FROM users WHERE id LIKE 'user_%' OR id LIKE 'sample-%'")
    suspend fun clearDummyUsers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("UPDATE users SET tier = :tier WHERE id = :id")
    suspend fun updateUserTier(id: String, tier: String)

    @Query("UPDATE users SET requestsCount = :count WHERE id = :id")
    suspend fun updateUserRequestsCount(id: String, count: Int)

    @Query("UPDATE users SET status = :status WHERE id = :id")
    suspend fun updateUserStatus(id: String, status: String)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: String)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY createdAt ASC")
    fun getCommentsForPost(postId: String): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY createdAt DESC LIMIT 50")
    fun getAllActivities(): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: ActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<ActivityEntity>)

    @Query("DELETE FROM activities WHERE id LIKE 'act-%'")
    suspend fun clearDummyActivities()
}
