package com.example.justfan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.justfan.data.model.RequestfromAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequestfromAppDao {
    @Query("SELECT * FROM RequestfromApp ORDER BY createdAt DESC")
    fun getAllRequests(): Flow<List<RequestfromAppEntity>>

    @Query("SELECT * FROM RequestfromApp WHERE status = 'delivered' OR status = 'replied' ORDER BY createdAt DESC")
    fun getDeliveredRequests(): Flow<List<RequestfromAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(req: RequestfromAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(reqs: List<RequestfromAppEntity>)

    @Query("UPDATE RequestfromApp SET status = :status, downloadLink = :link WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, link: String?)

    @Query("UPDATE RequestfromApp SET status = :status, downloadLink = :link, rejectionReason = :reason WHERE id = :id")
    suspend fun updateStatusWithReason(id: String, status: String, link: String?, reason: String? = null)

    @Query("DELETE FROM RequestfromApp WHERE id = :id")
    suspend fun deleteRequest(id: String)

    @Query("SELECT * FROM RequestfromApp")
    suspend fun getAllRequestsList(): List<RequestfromAppEntity>

    @Query("SELECT COUNT(*) FROM RequestfromApp")
    suspend fun getCount(): Int
}
