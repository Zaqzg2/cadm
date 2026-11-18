package com.cady.cadymarketing.data.content

import com.cady.cadymarketing.data.local.ContentDao
import com.cady.cadymarketing.data.local.ContentEntity
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.SavedContent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ContentRepository @Inject constructor(
    private val dao: ContentDao,
) {
    fun observeRecent(limit: Int = 30): Flow<List<SavedContent>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    /** Every saved post that is not deleted, newest change first (the library). */
    fun observeAll(): Flow<List<SavedContent>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeByCampaign(campaignId: String): Flow<List<SavedContent>> =
        dao.observeByCampaign(campaignId).map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: String): SavedContent? = dao.get(id)?.toDomain()

    /** Moves a post to another status (publish, archive, return to draft...). */
    suspend fun setStatus(id: String, status: ContentStatus) {
        dao.setStatus(id, status.code, System.currentTimeMillis())
    }

    /**
     * A copy of a post as a new draft: same text, channel, design, picture, campaign and schedule, new identity.
     * Returns the new id, or null when the original no longer exists.
     */
    suspend fun duplicate(id: String): String? {
        val original = dao.get(id) ?: return null
        val now = System.currentTimeMillis()
        val copyId = UUID.randomUUID().toString()
        dao.upsert(
            original.copy(
                id = copyId,
                number = null,
                status = ContentStatus.DRAFT.code,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        return copyId
    }

    /** Inserts, or updates the row with the same id (keeping the fields the Studio does not edit). */
    suspend fun save(content: SavedContent) {
        val existing = dao.get(content.id)
        dao.upsert(
            ContentEntity(
                id = content.id,
                number = existing?.number,
                productId = content.productId,
                productName = content.productName,
                channel = content.channel.code,
                text = content.text,
                status = content.status.code,
                campaignId = content.campaignId,
                campaignCode = content.campaignCode,
                scheduledAt = content.scheduledAt,
                designId = content.designId,
                picturePath = content.picturePath,
                tone = content.tone,
                angle = content.angle,
                note = existing?.note,
                org = existing?.org ?: ORG,
                schemaVersion = SCHEMA_VERSION,
                createdAt = existing?.createdAt ?: content.createdAt,
                updatedAt = content.updatedAt,
                deletedAt = null,
            ),
        )
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    private fun ContentEntity.toDomain(): SavedContent = SavedContent(
        id = id,
        productId = productId,
        productName = productName,
        channel = Channel.fromCode(channel),
        text = text,
        status = ContentStatus.fromCode(status),
        designId = designId,
        picturePath = picturePath,
        scheduledAt = scheduledAt,
        campaignId = campaignId,
        campaignCode = campaignCode,
        tone = tone,
        angle = angle,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val ORG = "cady"
        const val SCHEMA_VERSION = 1
    }
}
