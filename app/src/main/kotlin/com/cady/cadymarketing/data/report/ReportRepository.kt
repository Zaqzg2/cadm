package com.cady.cadymarketing.data.report

import androidx.room.withTransaction
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.cloud.OrderSource
import com.cady.cadymarketing.data.cloud.describe
import com.cady.cadymarketing.data.local.CadyMarketingDatabase
import com.cady.cadymarketing.data.local.OrderEntity
import com.cady.cadymarketing.data.local.ReportDao
import com.cady.cadymarketing.data.local.ResultEntity
import com.cady.cadymarketing.data.local.SyncMetaDao
import com.cady.cadymarketing.data.local.SyncMetaEntity
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.domain.engine.Mapped
import com.cady.cadymarketing.domain.engine.OrderMapper
import com.cady.cadymarketing.domain.model.AttributedOrder
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ManualResult
import com.cady.cadymarketing.domain.model.OrderOrigin
import com.cady.cadymarketing.domain.model.ResultMetric
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What one source (the website's orders, or Kady's invoices) brought on the last read. */
data class SourceReport(
    val origin: OrderOrigin,
    val collection: String,
    val codeField: String,
    /** How many orders carrying a code were understood. */
    val read: Int,
    val skipped: List<String>,
    /** How many documents the collection has in all (null when it could not be counted). */
    val total: Long?,
    val sample: String,
)

data class OrdersRefreshReport(
    val sources: List<SourceReport>,
    /** One sentence per source that could not be read (the other sources were still read). */
    val failures: List<String>,
    val at: Long,
)

/** A read that brought nothing at all. The message says why and what to check. */
class ReportException(message: String) : Exception(message)

/**
 * The raw material of the reports: the orders that carry a campaign code (copied from the website or from Kady, read
 * only) and the results written by hand. The numbers themselves are worked out by ReportEngine.
 */
@Singleton
class ReportRepository @Inject constructor(
    private val database: CadyMarketingDatabase,
    private val dao: ReportDao,
    private val syncMeta: SyncMetaDao,
    private val source: OrderSource,
    private val settings: SettingsStore,
    private val auth: AuthRepository,
) {
    val isPreview: Boolean get() = !auth.isConfigured

    fun observeOrders(): Flow<List<AttributedOrder>> = dao.observeOrders().map { rows -> rows.map { it.toDomain() } }

    fun observeResults(): Flow<List<ManualResult>> = dao.observeResults().map { rows -> rows.map { it.toDomain() } }

    fun observeLastRefresh(): Flow<Long?> = syncMeta.observe(SYNC_KEY).map { it?.lastSyncAt }

    /**
     * Reads every switched-on source. A source that fails does not stop the others (its sentence is in the report's
     * failures). Throws [ReportException] only when nothing could be read at all.
     */
    suspend fun refreshOrders(): OrdersRefreshReport {
        val specs = settings.reportSources.first().enabled()
        if (specs.isEmpty()) throw ReportException("لا يوجد مصدر طلبات مفعّل. حدّد اسم المجموعة والحقل من إعدادات التقارير.")
        val now = System.currentTimeMillis()
        val reports = ArrayList<SourceReport>()
        val failures = ArrayList<String>()
        for (spec in specs) {
            try {
                val raw = source.fetch(spec.collection, spec.codeField, MAX_ORDERS)
                val skipped = ArrayList<String>()
                val orders = ArrayList<AttributedOrder>()
                for (doc in raw.attributed) {
                    when (val mapped = OrderMapper.map(spec.origin, doc.id, doc.data, spec.codeField)) {
                        is Mapped.Ok -> orders.add(mapped.value)
                        is Mapped.Skipped -> skipped.add(mapped.reason)
                    }
                }
                database.withTransaction {
                    dao.clearOrders(spec.origin.code)
                    dao.upsertOrders(orders.map { it.toEntity(now) })
                }
                reports.add(
                    SourceReport(
                        origin = spec.origin,
                        collection = spec.collection,
                        codeField = spec.codeField,
                        read = orders.size,
                        skipped = skipped,
                        total = raw.total,
                        sample = raw.sample?.describe() ?: "المجموعة فارغة أو لا يمكن قراءتها",
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.add(spec.origin.label + ": " + failureText(e, spec.collection))
            }
        }
        if (reports.isEmpty()) throw ReportException(failures.joinToString("\n"))
        syncMeta.upsert(SyncMetaEntity(SYNC_KEY, now))
        return OrdersRefreshReport(reports, failures, now)
    }

    /** Refreshes quietly when the orders on the device are older than [maxAgeMs]. Returns true only when it worked. */
    suspend fun refreshIfStale(maxAgeMs: Long): Boolean {
        if (isPreview) return false
        val last = syncMeta.get(SYNC_KEY)?.lastSyncAt
        if (last != null && System.currentTimeMillis() - last < maxAgeMs) return false
        return try {
            refreshOrders()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun addResult(
        campaignId: String,
        channel: Channel?,
        metric: ResultMetric,
        value: Long,
        date: String,
        note: String,
    ): ManualResult {
        val now = System.currentTimeMillis()
        val entity = ResultEntity(
            id = UUID.randomUUID().toString(),
            campaignId = campaignId,
            channel = channel?.code,
            metric = metric.code,
            value = value,
            date = date,
            note = note.trim(),
            org = ORG,
            schemaVersion = SCHEMA_VERSION,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        dao.upsertResult(entity)
        return entity.toDomain()
    }

    /** A soft delete, so the removal can sync. */
    suspend fun deleteResult(id: String) {
        val existing = dao.result(id) ?: return
        val now = System.currentTimeMillis()
        dao.upsertResult(existing.copy(deletedAt = now, updatedAt = now))
    }

    private fun failureText(error: Throwable, collection: String): String =
        if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "لا تملك صلاحية قراءة «$collection»: قواعد Firestore تمنع حسابك. أضف قاعدة قراءة لهذه المجموعة."
        } else {
            CloudErrors.message(error)
        }

    private fun OrderEntity.toDomain() = AttributedOrder(
        id = id,
        origin = OrderOrigin.fromCode(origin),
        campaignCode = campaignCode,
        channel = channel?.let { code -> Channel.entries.firstOrNull { it.code == code } },
        amount = amount,
        date = date,
        cancelled = cancelled,
    )

    private fun AttributedOrder.toEntity(now: Long) =
        OrderEntity(id, origin.code, campaignCode, channel?.code, amount, date, cancelled, now)

    private fun ResultEntity.toDomain() = ManualResult(
        id = id,
        campaignId = campaignId,
        channel = channel?.let { code -> Channel.entries.firstOrNull { it.code == code } },
        metric = ResultMetric.fromCode(metric),
        value = value,
        date = date,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        const val SYNC_KEY = "orders:pull"
        private const val MAX_ORDERS = 2000
        private const val ORG = "cady"
        private const val SCHEMA_VERSION = 1
    }
}
