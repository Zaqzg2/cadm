package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * What the sync, the backup and the operations log need: every row of every synced table (deleted ones included),
 * writing them back, the marks of what is already in the cloud, and one number that changes whenever any of them changes.
 */
@Dao
interface SyncDao {
    // ------------------------------------------------------------------ every row, deleted included
    @Query("SELECT * FROM content")
    suspend fun allContent(): List<ContentEntity>

    @Query("SELECT * FROM campaign")
    suspend fun allCampaigns(): List<CampaignEntity>

    @Query("SELECT * FROM occasion")
    suspend fun allOccasions(): List<OccasionEntity>

    @Query("SELECT * FROM consent")
    suspend fun allConsents(): List<ConsentEntity>

    @Query("SELECT * FROM segment")
    suspend fun allSegments(): List<SegmentEntity>

    @Query("SELECT * FROM send_mark")
    suspend fun allMarks(): List<SendMarkEntity>

    @Query("SELECT * FROM result")
    suspend fun allResults(): List<ResultEntity>

    @Query("SELECT * FROM mkt_doc")
    suspend fun allDocs(): List<DocEntity>

    @Query("SELECT * FROM product_extra")
    suspend fun allExtras(): List<ProductExtraEntity>

    @Query("SELECT * FROM op_log")
    suspend fun allLog(): List<OpLogEntity>

    // ------------------------------------------------------------------ writing rows that came from the cloud or a backup
    @Upsert
    suspend fun upsertContent(rows: List<ContentEntity>)

    @Upsert
    suspend fun upsertCampaigns(rows: List<CampaignEntity>)

    @Upsert
    suspend fun upsertOccasions(rows: List<OccasionEntity>)

    @Upsert
    suspend fun upsertConsents(rows: List<ConsentEntity>)

    @Upsert
    suspend fun upsertSegments(rows: List<SegmentEntity>)

    @Upsert
    suspend fun upsertMarks(rows: List<SendMarkEntity>)

    @Upsert
    suspend fun upsertResults(rows: List<ResultEntity>)

    @Upsert
    suspend fun upsertDocs(rows: List<DocEntity>)

    @Upsert
    suspend fun upsertExtras(rows: List<ProductExtraEntity>)

    @Upsert
    suspend fun upsertLog(rows: List<OpLogEntity>)

    // ------------------------------------------------------------------ the running numbers (a row that has one keeps it)
    @Query("UPDATE content SET number = :number WHERE id = :id AND number IS NULL")
    suspend fun setContentNumber(id: String, number: String)

    @Query("UPDATE campaign SET number = :number WHERE id = :id AND number IS NULL")
    suspend fun setCampaignNumber(id: String, number: String)

    // ------------------------------------------------------------------ what the cloud is known to hold
    @Query("SELECT * FROM sync_state WHERE collection = :collection")
    suspend fun states(collection: String): List<SyncStateEntity>

    @Upsert
    suspend fun upsertStates(rows: List<SyncStateEntity>)

    // ------------------------------------------------------------------ one number that moves when any synced table changes
    @Query(
        "SELECT MAX(m) FROM (" +
            "SELECT MAX(updatedAt) AS m FROM content UNION ALL " +
            "SELECT MAX(updatedAt) FROM campaign UNION ALL " +
            "SELECT MAX(updatedAt) FROM occasion UNION ALL " +
            "SELECT MAX(updatedAt) FROM consent UNION ALL " +
            "SELECT MAX(updatedAt) FROM segment UNION ALL " +
            "SELECT MAX(updatedAt) FROM send_mark UNION ALL " +
            "SELECT MAX(updatedAt) FROM result UNION ALL " +
            "SELECT MAX(updatedAt) FROM mkt_doc UNION ALL " +
            "SELECT MAX(updatedAt) FROM product_extra UNION ALL " +
            "SELECT MAX(`at`) FROM op_log)",
    )
    fun observeChangeMarker(): Flow<Long?>

    // ------------------------------------------------------------------ the operations log
    @Query("SELECT * FROM op_log ORDER BY `at` DESC LIMIT :limit")
    fun observeLog(limit: Int): Flow<List<OpLogEntity>>

    @Insert
    suspend fun insertLog(row: OpLogEntity)

    @Query("DELETE FROM op_log WHERE id NOT IN (SELECT id FROM op_log ORDER BY `at` DESC LIMIT :keep)")
    suspend fun pruneLog(keep: Int)
}
