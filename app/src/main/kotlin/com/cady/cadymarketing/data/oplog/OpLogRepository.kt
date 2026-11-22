package com.cady.cadymarketing.data.oplog

import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.local.OpLogEntity
import com.cady.cadymarketing.data.local.SyncDao
import com.cady.cadymarketing.domain.model.OpAction
import com.cady.cadymarketing.domain.model.OpLogEntry
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The operations log: who did what to which post or campaign, and when. Lines are only ever added. They sync like the
 * rest (the manager sees every line, a marketer only adds hers) and the device keeps the latest [KEEP].
 */
@Singleton
class OpLogRepository @Inject constructor(
    private val dao: SyncDao,
    private val auth: AuthRepository,
) {
    fun observe(limit: Int = SHOWN): Flow<List<OpLogEntry>> =
        dao.observeLog(limit).map { rows -> rows.map { it.toDomain() } }

    /** Adds a line. A failure to write the log never stops the work it describes. */
    suspend fun record(action: OpAction, subject: String, detail: String = "") {
        try {
            dao.insertLog(
                OpLogEntity(
                    id = UUID.randomUUID().toString(),
                    at = System.currentTimeMillis(),
                    actor = auth.currentEmail() ?: PREVIEW_ACTOR,
                    action = action.code,
                    subject = subject.take(MAX_SUBJECT),
                    detail = detail.take(MAX_DETAIL),
                    org = ORG,
                    schemaVersion = SCHEMA_VERSION,
                ),
            )
            dao.pruneLog(KEEP)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The log is a record of the work, not part of it.
        }
    }

    private fun OpLogEntity.toDomain(): OpLogEntry = OpLogEntry(
        id = id,
        at = at,
        actor = actor,
        action = OpAction.fromCode(action),
        actionCode = action,
        subject = subject,
        detail = detail,
    )

    companion object {
        const val SHOWN = 300
        private const val KEEP = 600
        private const val MAX_SUBJECT = 200
        private const val MAX_DETAIL = 400
        private const val PREVIEW_ACTOR = "معاينة"
        private const val ORG = "cady"
        private const val SCHEMA_VERSION = 1
    }
}
