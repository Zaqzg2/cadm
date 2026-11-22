package com.cady.cadymarketing.data.campaign

import com.cady.cadymarketing.data.local.CampaignDao
import com.cady.cadymarketing.data.local.CampaignEntity
import com.cady.cadymarketing.data.oplog.OpLogRepository
import com.cady.cadymarketing.domain.engine.CampaignRules
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.OpAction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The campaigns of the marketing app. Every campaign has a short code that its links carry. */
@Singleton
class CampaignRepository @Inject constructor(
    private val dao: CampaignDao,
    private val opLog: OpLogRepository,
) {
    fun observeAll(): Flow<List<Campaign>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observe(id: String): Flow<Campaign?> = dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: String): Campaign? = dao.get(id)?.takeIf { it.deletedAt == null }?.toDomain()

    /** Makes a campaign with a fresh code that no campaign (deleted ones included) ever had. */
    suspend fun create(name: String, goal: String, startDate: String?, endDate: String?): Campaign {
        val now = System.currentTimeMillis()
        val code = CampaignRules.newCode(dao.allCodes().toSet())
        val entity = CampaignEntity(
            id = UUID.randomUUID().toString(),
            code = code,
            number = null,
            name = name.trim(),
            goal = goal.trim(),
            startDate = startDate,
            endDate = endDate,
            status = CampaignStatus.ACTIVE.code,
            org = ORG,
            schemaVersion = SCHEMA_VERSION,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        dao.upsert(entity)
        opLog.record(OpAction.CAMPAIGN_CREATED, entity.name, entity.code)
        return entity.toDomain()
    }

    /** Saves the edited name, goal and dates. The code and the number never change. */
    suspend fun update(id: String, name: String, goal: String, startDate: String?, endDate: String?) {
        val existing = dao.get(id) ?: return
        dao.upsert(
            existing.copy(
                name = name.trim(),
                goal = goal.trim(),
                startDate = startDate,
                endDate = endDate,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setStatus(id: String, status: CampaignStatus) {
        val existing = dao.get(id) ?: return
        dao.upsert(existing.copy(status = status.code, updatedAt = System.currentTimeMillis()))
        if (existing.status != status.code) opLog.record(OpAction.CAMPAIGN_STATUS, existing.name, status.label)
    }

    /** A soft delete: the row stays (with its code) so the links already shared are never reused. */
    suspend fun delete(id: String) {
        val existing = dao.get(id) ?: return
        val now = System.currentTimeMillis()
        dao.upsert(existing.copy(deletedAt = now, updatedAt = now))
        opLog.record(OpAction.CAMPAIGN_DELETED, existing.name, existing.code)
    }

    private fun CampaignEntity.toDomain(): Campaign = Campaign(
        id = id,
        code = code,
        number = number,
        name = name,
        goal = goal,
        startDate = startDate,
        endDate = endDate,
        status = CampaignStatus.fromCode(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val ORG = "cady"
        const val SCHEMA_VERSION = 1
    }
}
