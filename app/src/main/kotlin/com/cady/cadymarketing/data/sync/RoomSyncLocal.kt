package com.cady.cadymarketing.data.sync

import androidx.room.withTransaction
import com.cady.cadymarketing.data.local.CadyMarketingDatabase
import com.cady.cadymarketing.data.local.SyncDao
import com.cady.cadymarketing.data.local.SyncMetaDao
import com.cady.cadymarketing.data.local.SyncMetaEntity
import com.cady.cadymarketing.data.local.SyncStateEntity
import com.cady.cadymarketing.domain.engine.SyncLocalStore
import com.cady.cadymarketing.domain.engine.SyncRecord
import com.cady.cadymarketing.domain.engine.SyncSpecs
import javax.inject.Inject
import javax.inject.Singleton

/** The sync's view of the device: Room's tables as plain records (see [SyncMappers]), and the marks of what the cloud holds. */
@Singleton
class RoomSyncLocal @Inject constructor(
    private val database: CadyMarketingDatabase,
    private val dao: SyncDao,
    private val meta: SyncMetaDao,
) : SyncLocalStore {

    override suspend fun rows(table: String): List<SyncRecord> = when (table) {
        SyncSpecs.CONTENT -> dao.allContent().map { SyncMappers.contentToRecord(it) }
        SyncSpecs.CAMPAIGNS -> dao.allCampaigns().map { SyncMappers.campaignToRecord(it) }
        SyncSpecs.OCCASIONS -> dao.allOccasions().map { SyncMappers.occasionToRecord(it) }
        SyncSpecs.CONSENT -> dao.allConsents().map { SyncMappers.consentToRecord(it) }
        SyncSpecs.SEGMENTS -> dao.allSegments().map { SyncMappers.segmentToRecord(it) }
        SyncSpecs.SEND_MARKS -> dao.allMarks().map { SyncMappers.sendMarkToRecord(it) }
        SyncSpecs.RESULTS -> dao.allResults().map { SyncMappers.resultToRecord(it) }
        SyncSpecs.DOCS -> dao.allDocs().map { SyncMappers.docToRecord(it) }
        SyncSpecs.PRODUCT_EXTRA -> dao.allExtras().map { SyncMappers.extraToRecord(it) }
        SyncSpecs.LOG -> dao.allLog().map { SyncMappers.logToRecord(it) }
        else -> emptyList()
    }

    override suspend fun apply(table: String, rows: List<SyncRecord>) {
        if (rows.isEmpty()) return
        database.withTransaction {
            when (table) {
                SyncSpecs.CONTENT -> {
                    val have = dao.allContent().associateBy { it.id }
                    dao.upsertContent(rows.mapNotNull { SyncMappers.contentFromRecord(it, have[it.id]) })
                }
                SyncSpecs.CAMPAIGNS -> {
                    val have = dao.allCampaigns().associateBy { it.id }
                    dao.upsertCampaigns(rows.mapNotNull { SyncMappers.campaignFromRecord(it, have[it.id]) })
                }
                SyncSpecs.OCCASIONS -> {
                    val have = dao.allOccasions().associateBy { it.id }
                    dao.upsertOccasions(rows.mapNotNull { SyncMappers.occasionFromRecord(it, have[it.id]) })
                }
                SyncSpecs.CONSENT -> {
                    val have = dao.allConsents().associateBy { it.customerId }
                    dao.upsertConsents(rows.mapNotNull { SyncMappers.consentFromRecord(it, have[it.id]) })
                }
                SyncSpecs.SEGMENTS -> {
                    val have = dao.allSegments().associateBy { it.id }
                    dao.upsertSegments(rows.mapNotNull { SyncMappers.segmentFromRecord(it, have[it.id]) })
                }
                SyncSpecs.SEND_MARKS -> {
                    val have = dao.allMarks().associateBy { SyncMappers.markId(it.contentId, it.customerId) }
                    dao.upsertMarks(rows.mapNotNull { SyncMappers.sendMarkFromRecord(it, have[it.id]) })
                }
                SyncSpecs.RESULTS -> {
                    val have = dao.allResults().associateBy { it.id }
                    dao.upsertResults(rows.mapNotNull { SyncMappers.resultFromRecord(it, have[it.id]) })
                }
                SyncSpecs.DOCS -> {
                    val have = dao.allDocs().associateBy { it.docKey }
                    dao.upsertDocs(rows.mapNotNull { SyncMappers.docFromRecord(it, have[it.id]) })
                }
                SyncSpecs.PRODUCT_EXTRA -> {
                    val have = dao.allExtras().associateBy { it.productId }
                    dao.upsertExtras(rows.map { SyncMappers.extraFromRecord(it, have[it.id]) })
                }
                SyncSpecs.LOG -> dao.upsertLog(rows.mapNotNull { SyncMappers.logFromRecord(it) })
                else -> Unit
            }
        }
    }

    override suspend fun applyNumbers(table: String, numbers: Map<String, String>) {
        if (numbers.isEmpty()) return
        database.withTransaction {
            for ((id, number) in numbers) {
                when (table) {
                    SyncSpecs.CONTENT -> dao.setContentNumber(id, number)
                    SyncSpecs.CAMPAIGNS -> dao.setCampaignNumber(id, number)
                    else -> Unit
                }
            }
        }
    }

    override suspend fun marks(table: String): Map<String, Long> =
        dao.states(table).associate { it.docId to it.syncedUpdatedAt }

    override suspend fun saveMarks(table: String, marks: Map<String, Long>) {
        val known = dao.states(table).associate { it.docId to it.syncedUpdatedAt }
        val changed = marks.filter { (id, at) -> known[id] != at }.map { (id, at) -> SyncStateEntity(table, id, at) }
        if (changed.isNotEmpty()) dao.upsertStates(changed)
    }

    override suspend fun cursor(table: String): Long = meta.get(PULL_PREFIX + table)?.lastSyncAt ?: 0L

    override suspend fun saveCursor(table: String, value: Long) {
        meta.upsert(SyncMetaEntity(PULL_PREFIX + table, value))
    }

    private companion object {
        const val PULL_PREFIX = "pull:"
    }
}
