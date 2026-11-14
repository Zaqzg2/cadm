package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query("SELECT * FROM content WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ContentEntity>>

    @Query("SELECT * FROM content WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): ContentEntity?

    @Upsert
    suspend fun upsert(entity: ContentEntity)

    @Query("UPDATE content SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)
}
