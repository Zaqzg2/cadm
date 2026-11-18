package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaign WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaign WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<CampaignEntity?>

    /** Also finds a deleted campaign: the sync needs it, and a code must never be reused. */
    @Query("SELECT * FROM campaign WHERE id = :id")
    suspend fun get(id: String): CampaignEntity?

    /** Every code ever given, deleted campaigns included, so a new code can never repeat an old one. */
    @Query("SELECT code FROM campaign")
    suspend fun allCodes(): List<String>

    @Upsert
    suspend fun upsert(entity: CampaignEntity)
}
