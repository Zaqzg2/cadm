package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query("SELECT * FROM content WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ContentEntity>>

    @Query("SELECT * FROM content WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM content WHERE deletedAt IS NULL AND campaignId = :campaignId ORDER BY updatedAt DESC")
    fun observeByCampaign(campaignId: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM content WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): ContentEntity?

    @Query("UPDATE content SET status = :status, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun setStatus(id: String, status: String, at: Long)

    @Upsert
    suspend fun upsert(entity: ContentEntity)

    @Query("UPDATE content SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)
}
