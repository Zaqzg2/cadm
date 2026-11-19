package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Query("SELECT * FROM result WHERE deletedAt IS NULL ORDER BY `date` DESC, `createdAt` DESC")
    fun observeResults(): Flow<List<ResultEntity>>

    @Query("SELECT * FROM result WHERE id = :id")
    suspend fun result(id: String): ResultEntity?

    @Upsert
    suspend fun upsertResult(entity: ResultEntity)

    @Query("SELECT * FROM report_order")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Query("DELETE FROM report_order WHERE origin = :origin")
    suspend fun clearOrders(origin: String)

    @Upsert
    suspend fun upsertOrders(rows: List<OrderEntity>)
}
