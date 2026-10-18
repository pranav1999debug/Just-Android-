package com.example.justfan.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import com.example.justfan.data.model.FavoriteEntity
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.StringListConverter
import com.example.justfan.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun getAllPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    suspend fun getAllPostsList(): List<PostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    @Update
    suspend fun updatePost(post: PostEntity)

    @Query("DELETE FROM posts WHERE id = :id")
    suspend fun deletePost(id: String)

    @Query("UPDATE posts SET clicksCount = clicksCount + 1 WHERE id = :id")
    suspend fun incrementClicks(id: String)

    @Query("UPDATE posts SET likesCount = likesCount + 1 WHERE id = :id")
    suspend fun incrementLikes(id: String)
}

@Dao
interface RequestDao {
    @Query("SELECT * FROM requests ORDER BY createdAt DESC")
    fun getAllRequests(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests ORDER BY createdAt DESC")
    suspend fun getAllRequestsList(): List<RequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<RequestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: RequestEntity)

    @Query("UPDATE requests SET status = :status, downloadLink = :link, rejectionReason = :reason WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, link: String?, reason: String? = null)

    @Query("DELETE FROM requests WHERE id = :id")
    suspend fun deleteRequest(id: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY joinedAt DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY joinedAt DESC")
    suspend fun getAllUsersList(): List<UserEntity>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findUserById(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("UPDATE users SET tier = :tier WHERE id = :id OR email = :id")
    suspend fun updateUserTier(id: String, tier: String)

    @Query("UPDATE users SET requestsCount = :count WHERE id = :id")
    suspend fun updateUserRequestsCount(id: String, count: Int)

    @Query("UPDATE users SET status = :status WHERE id = :id")
    suspend fun updateUserStatus(id: String, status: String)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE postId = :postId")
    suspend fun removeFavorite(postId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE postId = :postId)")
    suspend fun isFavorite(postId: String): Boolean
}

@Database(
    entities = [PostEntity::class, RequestEntity::class, UserEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun requestDao(): RequestDao
    abstract fun userDao(): UserDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "justfan_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
