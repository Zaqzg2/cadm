package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocDao {
    @Query("SELECT * FROM mkt_doc WHERE docKey = :docKey")
    fun observe(docKey: String): Flow<DocEntity?>

    @Query("SELECT * FROM mkt_doc WHERE docKey = :docKey")
    suspend fun get(docKey: String): DocEntity?

    @Upsert
    suspend fun upsert(entity: DocEntity)
}
