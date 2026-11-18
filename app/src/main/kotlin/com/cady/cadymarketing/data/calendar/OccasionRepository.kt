package com.cady.cadymarketing.data.calendar

import com.cady.cadymarketing.data.local.OccasionDao
import com.cady.cadymarketing.data.local.OccasionEntity
import com.cady.cadymarketing.domain.model.Occasion
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The occasions the manager added to the calendar (the built-in ones are computed, not stored). */
@Singleton
class OccasionRepository @Inject constructor(
    private val dao: OccasionDao,
) {
    fun observeAll(): Flow<List<Occasion>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun add(title: String, date: String, yearly: Boolean) {
        val now = System.currentTimeMillis()
        dao.upsert(
            OccasionEntity(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                date = date,
                yearly = yearly,
                org = ORG,
                schemaVersion = SCHEMA_VERSION,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    private fun OccasionEntity.toDomain(): Occasion = Occasion(
        id = id,
        title = title,
        date = date,
        yearly = yearly,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val ORG = "cady"
        const val SCHEMA_VERSION = 1
    }
}
