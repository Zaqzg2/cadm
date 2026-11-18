package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OccasionDao {
    @Query("SELECT * FROM occasion WHERE deletedAt IS NULL ORDER BY date")
    fun observeAll(): Flow<List<OccasionEntity>>

    @Query("SELECT * FROM occasion WHERE id = :id")
    suspend fun get(id: String): OccasionEntity?

    @Upsert
    suspend fun upsert(entity: OccasionEntity)

    @Query("UPDATE occasion SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)
}
