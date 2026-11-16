package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE collection = :collection")
    suspend fun get(collection: String): SyncMetaEntity?

    @Query("SELECT * FROM sync_meta WHERE collection = :collection")
    fun observe(collection: String): Flow<SyncMetaEntity?>

    @Upsert
    suspend fun upsert(entity: SyncMetaEntity)
}
