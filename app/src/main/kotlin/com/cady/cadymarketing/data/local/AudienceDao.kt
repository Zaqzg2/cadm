package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AudienceDao {
    // ------------------------------------------------------------------ Kady's customers (a copy)
    @Query("SELECT * FROM customer_copy ORDER BY name")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Upsert
    suspend fun upsertCustomers(rows: List<CustomerEntity>)

    @Query("DELETE FROM customer_copy")
    suspend fun clearCustomers()

    // ------------------------------------------------------------------ consent
    @Query("SELECT * FROM consent WHERE deletedAt IS NULL")
    fun observeConsents(): Flow<List<ConsentEntity>>

    @Query("SELECT * FROM consent WHERE customerId = :customerId")
    suspend fun consent(customerId: String): ConsentEntity?

    @Upsert
    suspend fun upsertConsent(entity: ConsentEntity)

    // ------------------------------------------------------------------ segments
    @Query("SELECT * FROM segment WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeSegments(): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segment WHERE id = :id")
    suspend fun segment(id: String): SegmentEntity?

    @Upsert
    suspend fun upsertSegment(entity: SegmentEntity)

    // ------------------------------------------------------------------ send lists
    @Query("SELECT * FROM send_mark WHERE contentId = :contentId AND deletedAt IS NULL")
    fun observeMarks(contentId: String): Flow<List<SendMarkEntity>>

    @Query("SELECT * FROM send_mark WHERE contentId = :contentId AND customerId = :customerId")
    suspend fun mark(contentId: String, customerId: String): SendMarkEntity?

    @Upsert
    suspend fun upsertMark(entity: SendMarkEntity)
}
