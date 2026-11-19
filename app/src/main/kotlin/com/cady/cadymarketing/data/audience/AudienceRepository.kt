package com.cady.cadymarketing.data.audience

import androidx.room.withTransaction
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.cloud.CustomerSource
import com.cady.cadymarketing.data.cloud.describe
import com.cady.cadymarketing.data.local.AudienceDao
import com.cady.cadymarketing.data.local.CadyMarketingDatabase
import com.cady.cadymarketing.data.local.ConsentEntity
import com.cady.cadymarketing.data.local.CustomerEntity
import com.cady.cadymarketing.data.local.SegmentEntity
import com.cady.cadymarketing.data.local.SendMarkEntity
import com.cady.cadymarketing.data.local.SyncMetaDao
import com.cady.cadymarketing.data.local.SyncMetaEntity
import com.cady.cadymarketing.domain.engine.CustomerMapper
import com.cady.cadymarketing.domain.engine.Mapped
import com.cady.cadymarketing.domain.model.Consent
import com.cady.cadymarketing.domain.model.ConsentStatus
import com.cady.cadymarketing.domain.model.Customer
import com.cady.cadymarketing.domain.model.Segment
import com.cady.cadymarketing.domain.model.SegmentKind
import com.cady.cadymarketing.domain.model.SendStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the last read of Kady's customers brought. Shown in the diagnostics dialog so a field-name mismatch is easy to see. */
data class CustomerRefreshReport(
    val customers: Int,
    val withoutPhone: Int,
    val skipped: List<String>,
    val sample: String,
    val at: Long,
)

/** A read that brought nothing usable. The message says why. The customers already on the device are left untouched. */
class AudienceException(message: String) : Exception(message)

/**
 * The audience of the marketing app: Kady's customers copied to this device (read only), the consent of each one
 * (kept here, never in Kady), the segments, and what was sent to whom. Everything works offline from the copy.
 */
@Singleton
class AudienceRepository @Inject constructor(
    private val database: CadyMarketingDatabase,
    private val dao: AudienceDao,
    private val syncMeta: SyncMetaDao,
    private val source: CustomerSource,
    private val auth: AuthRepository,
) {
    val isPreview: Boolean get() = !auth.isConfigured

    fun observeCustomers(): Flow<List<Customer>> = dao.observeCustomers().map { rows -> rows.map { it.toDomain() } }

    fun observeConsents(): Flow<Map<String, Consent>> =
        dao.observeConsents().map { rows -> rows.associate { it.customerId to it.toDomain() } }

    fun observeSegments(): Flow<List<Segment>> = dao.observeSegments().map { rows -> rows.map { it.toDomain() } }

    /** What was done for each customer in the send list of one post (customers with no entry are still to do). */
    fun observeMarks(contentId: String): Flow<Map<String, SendStatus>> = dao.observeMarks(contentId).map { rows ->
        rows.mapNotNull { row -> SendStatus.fromCode(row.status)?.let { row.customerId to it } }.toMap()
    }

    fun observeLastRefresh(): Flow<Long?> = syncMeta.observe(SYNC_KEY).map { it?.lastSyncAt }

    /** Reads Kady's customers and replaces the copy on this device. Throws the cloud's exception, or [AudienceException]. */
    suspend fun refreshCustomers(): CustomerRefreshReport {
        val raw = source.fetch()
        if (raw.isEmpty()) {
            throw AudienceException("لم يرجع كادي أي عميل. تحقق من وجود عملاء في كادي ومن صلاحية حسابك.")
        }
        val skipped = ArrayList<String>()
        val customers = ArrayList<Customer>()
        for (doc in raw) {
            when (val mapped = CustomerMapper.customer(doc.id, doc.data)) {
                is Mapped.Ok -> customers.add(mapped.value)
                is Mapped.Skipped -> skipped.add(mapped.reason)
            }
        }
        if (customers.isEmpty()) {
            throw AudienceException("قرأت ${raw.size} مستندًا من كادي لكن لم أفهم أيًّا منها: ${skipped.firstOrNull().orEmpty()}")
        }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.clearCustomers()
            dao.upsertCustomers(customers.map { it.toEntity(now) })
        }
        syncMeta.upsert(SyncMetaEntity(SYNC_KEY, now))
        return CustomerRefreshReport(
            customers = customers.size,
            withoutPhone = customers.count { it.phone == null },
            skipped = skipped,
            sample = raw.first().describe(),
            at = now,
        )
    }

    /** Refreshes quietly when the copy is older than [maxAgeMs]. Returns true only when a refresh happened and worked. */
    suspend fun refreshIfStale(maxAgeMs: Long): Boolean {
        if (isPreview) return false
        val last = syncMeta.get(SYNC_KEY)?.lastSyncAt
        if (last != null && System.currentTimeMillis() - last < maxAgeMs) return false
        return try {
            refreshCustomers()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Records what a customer agreed to. "No consent recorded" is not a stored answer: it removes the record (softly,
     * so the removal can sync), and the customer is then treated as not having agreed.
     */
    suspend fun setConsent(customerId: String, status: ConsentStatus, note: String) {
        val now = System.currentTimeMillis()
        val existing = dao.consent(customerId)
        if (status == ConsentStatus.UNKNOWN) {
            if (existing != null && existing.deletedAt == null) dao.upsertConsent(existing.copy(deletedAt = now, updatedAt = now))
            return
        }
        dao.upsertConsent(
            ConsentEntity(
                customerId = customerId,
                status = status.code,
                note = note.trim(),
                org = existing?.org ?: ORG,
                schemaVersion = SCHEMA_VERSION,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    /** Makes a segment ([id] null) or saves the changes of an existing one. */
    suspend fun saveSegment(
        id: String?,
        name: String,
        kind: SegmentKind,
        area: String,
        repName: String,
        memberIds: List<String>,
    ): Segment {
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.segment(it) }
        val entity = SegmentEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            kind = kind.code,
            area = if (kind == SegmentKind.FILTER) area.trim() else "",
            repName = if (kind == SegmentKind.FILTER) repName.trim() else "",
            memberIds = if (kind == SegmentKind.MANUAL) memberIds.distinct().joinToString("\n") else "",
            org = existing?.org ?: ORG,
            schemaVersion = SCHEMA_VERSION,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
        )
        dao.upsertSegment(entity)
        return entity.toDomain()
    }

    suspend fun deleteSegment(id: String) {
        val existing = dao.segment(id) ?: return
        val now = System.currentTimeMillis()
        dao.upsertSegment(existing.copy(deletedAt = now, updatedAt = now))
    }

    /** Writes what happened to one customer in the send list of one post; null clears it (the customer is to do again). */
    suspend fun setMark(contentId: String, customerId: String, status: SendStatus?) {
        val now = System.currentTimeMillis()
        val existing = dao.mark(contentId, customerId)
        if (status == null) {
            if (existing != null && existing.deletedAt == null) dao.upsertMark(existing.copy(deletedAt = now, updatedAt = now))
            return
        }
        dao.upsertMark(
            SendMarkEntity(
                contentId = contentId,
                customerId = customerId,
                status = status.code,
                org = existing?.org ?: ORG,
                schemaVersion = SCHEMA_VERSION,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    private fun CustomerEntity.toDomain() = Customer(id, name, phone, rawPhone, area, repName, active)

    private fun Customer.toEntity(now: Long) = CustomerEntity(id, name, phone, rawPhone, area, repName, active, now)

    private fun ConsentEntity.toDomain() = Consent(customerId, ConsentStatus.fromCode(status), note, updatedAt)

    private fun SegmentEntity.toDomain() = Segment(
        id = id,
        name = name,
        kind = SegmentKind.fromCode(kind),
        area = area,
        repName = repName,
        memberIds = memberIds.split('\n').filter { it.isNotBlank() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        const val SYNC_KEY = "customers:pull"
        private const val ORG = "cady"
        private const val SCHEMA_VERSION = 1
    }
}
