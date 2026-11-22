package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.MktRole
import kotlin.coroutines.cancellation.CancellationException

/**
 * One document as the sync moves it: its id, the moment it last changed (device clock, epoch milliseconds) and its
 * fields as plain values (String, Number, Boolean, null, List). `number` (CNT-0001) is among the fields when it was assigned.
 */
data class SyncRecord(val id: String, val updatedAt: Long, val data: Map<String, Any?>) {
    val number: String? get() = data["number"] as? String
    val isDeleted: Boolean get() = data["deletedAt"] != null
}

/** The running number of a collection: `prefix-0001`, counted in the document `mkt_counters/{counter}`. */
data class NumberSpec(val prefix: String, val counter: String)

/**
 * One synced collection. [table] is the Firestore collection and also the key of the marks and the cursor kept here.
 * A marketer reads the collection when [marketerReads] and uploads to it when [marketerWrites] (only the rows
 * [marketerMayPush] accepts, when it is given).
 */
data class SyncTableSpec(
    val table: String,
    val number: NumberSpec? = null,
    val marketerReads: Boolean = true,
    val marketerWrites: Boolean = false,
    val marketerMayPush: ((SyncRecord) -> Boolean)? = null,
)

/** What the sync needs from the device: the rows, what was already synced, and a way to write what came in. */
interface SyncLocalStore {
    /** Every row of the table, deleted ones included. */
    suspend fun rows(table: String): List<SyncRecord>

    /** id → the `updatedAt` that the cloud is known to hold. A row changed since then is waiting to be uploaded. */
    suspend fun marks(table: String): Map<String, Long>

    suspend fun saveMarks(table: String, marks: Map<String, Long>)

    /** Writes rows that came from the cloud and won. Fields that belong to this device alone are kept as they are. */
    suspend fun apply(table: String, rows: List<SyncRecord>)

    /** Gives numbers to rows that have none (a row that already has one keeps it). */
    suspend fun applyNumbers(table: String, numbers: Map<String, String>)

    suspend fun cursor(table: String): Long

    suspend fun saveCursor(table: String, value: Long)
}

/** A cloud document with the moment (server clock, epoch milliseconds) it was last written. */
data class RemoteDoc(val record: SyncRecord, val serverMs: Long)

/** Rows the cloud refused, with the reason; everything else in the push went through. */
data class PushResult(val failed: Map<String, String> = emptyMap())

/** What the sync needs from the cloud. */
interface SyncRemoteStore {
    /** Every document written after [sinceServerMs] on the server's clock, oldest first. */
    suspend fun pull(table: String, sinceServerMs: Long): List<RemoteDoc>

    suspend fun get(table: String, id: String): RemoteDoc?

    /** Writes the rows (merging into the document, and leaving out a `number` the row does not have yet). */
    suspend fun push(table: String, rows: List<SyncRecord>): PushResult

    /** Gives the next numbers to the documents that have none yet; a document that already has one keeps it and is returned with it. */
    suspend fun allocateNumbers(table: String, spec: NumberSpec, ids: List<String>): Map<String, String>
}

enum class MergeAction { TAKE_REMOTE, KEEP_LOCAL, SAME, ADOPT_NUMBER }

/** Deciding which copy of a document wins: the one changed last (the device clock). */
object SyncMerge {
    fun decide(local: SyncRecord?, remote: SyncRecord, numbered: Boolean): MergeAction = when {
        local == null -> MergeAction.TAKE_REMOTE
        remote.updatedAt > local.updatedAt -> MergeAction.TAKE_REMOTE
        remote.updatedAt < local.updatedAt -> MergeAction.KEEP_LOCAL
        numbered && local.number == null && remote.number != null -> MergeAction.ADOPT_NUMBER
        else -> MergeAction.SAME
    }
}

/** The running numbers: CNT-0001, CMP-0001. */
object NumberRules {
    fun format(prefix: String, value: Long): String = prefix + "-" + value.toString().padStart(4, '0')

    /** The number part of "CNT-0012" (12), or null when the text is not of that shape. */
    fun parse(prefix: String, text: String?): Long? {
        if (text == null || !text.startsWith("$prefix-")) return null
        return text.substring(prefix.length + 1).toLongOrNull()
    }
}

/** What one table did in a run. */
data class TableReport(
    val table: String,
    val pulled: Int = 0,
    val adopted: Int = 0,
    val pushed: Int = 0,
    val numbered: Int = 0,
    val reverted: Int = 0,
    val failed: Int = 0,
    val error: String? = null,
)

data class SyncReport(val tables: List<TableReport>, val at: Long) {
    val pulled: Int get() = tables.sumOf { it.pulled }
    val pushed: Int get() = tables.sumOf { it.pushed }
    val numbered: Int get() = tables.sumOf { it.numbered }
    val reverted: Int get() = tables.sumOf { it.reverted }
    val failed: Int get() = tables.sumOf { it.failed }
    val firstError: String? get() = tables.firstNotNullOfOrNull { it.error }
    val ok: Boolean get() = firstError == null && failed == 0

    /** "رُفع 3 · نُزّل 2" in words, or that nothing changed. */
    val summary: String
        get() {
            val parts = ArrayList<String>()
            if (pushed > 0) parts.add("رُفع $pushed")
            if (pulled > 0) parts.add("نُزّل $pulled")
            if (numbered > 0) parts.add("رُقّم $numbered")
            if (reverted > 0) parts.add("رُدّ $reverted")
            return if (parts.isEmpty()) "لا جديد" else parts.joinToString(" · ")
        }
}

/**
 * The sync itself, written against two small interfaces so it runs the same on the phone and in tests.
 * Each table goes through the same steps: take what is new in the cloud (the newer copy wins), upload what changed here
 * since, and give numbers to the rows that have none. A failure in one table never stops the others.
 */
class SyncEngine(
    private val local: SyncLocalStore,
    private val remote: SyncRemoteStore,
    private val tables: List<SyncTableSpec>,
) {
    suspend fun run(role: MktRole, now: Long, describe: (Throwable) -> String): SyncReport {
        if (role == MktRole.NONE) return SyncReport(emptyList(), now)
        val reports = ArrayList<TableReport>()
        for (spec in tables) {
            val canRead = role == MktRole.MANAGER || spec.marketerReads
            val canWrite = role == MktRole.MANAGER || spec.marketerWrites
            if (!canRead && !canWrite) continue
            reports.add(syncTable(spec, role, canRead, canWrite, describe))
        }
        return SyncReport(reports, now)
    }

    /** How many rows of the tables this role uploads are waiting to be uploaded. */
    suspend fun pendingCount(role: MktRole): Int {
        if (role == MktRole.NONE) return 0
        var total = 0
        for (spec in tables) {
            if (role == MktRole.MARKETER && !spec.marketerWrites) continue
            val marks = local.marks(spec.table)
            total += local.rows(spec.table).count { isDirty(it, marks) && mayPush(spec, role, it) }
        }
        return total
    }

    private suspend fun syncTable(
        spec: SyncTableSpec,
        role: MktRole,
        canRead: Boolean,
        canWrite: Boolean,
        describe: (Throwable) -> String,
    ): TableReport {
        var report = TableReport(spec.table)
        val marks = HashMap(local.marks(spec.table))
        try {
            // ---- 1. take what is new in the cloud (a table this role may only write, such as the log, is not read)
            val rows = if (canRead) local.rows(spec.table) else emptyList()
            val byId = rows.associateBy { it.id }
            val oldCursor = local.cursor(spec.table)
            val docs = if (canRead) remote.pull(spec.table, (oldCursor - OVERLAP_MS).coerceAtLeast(0L)) else emptyList()
            val take = ArrayList<SyncRecord>()
            val adopt = HashMap<String, String>()
            var cursor = oldCursor
            for (doc in docs) {
                cursor = maxOf(cursor, doc.serverMs)
                val theirs = doc.record
                val mine = byId[theirs.id]
                when (SyncMerge.decide(mine, theirs, spec.number != null)) {
                    MergeAction.TAKE_REMOTE -> {
                        take.add(theirs)
                        marks[theirs.id] = theirs.updatedAt
                    }
                    MergeAction.ADOPT_NUMBER -> {
                        theirs.number?.let { adopt[theirs.id] = it }
                        marks[theirs.id] = theirs.updatedAt
                    }
                    MergeAction.SAME -> marks[theirs.id] = theirs.updatedAt
                    MergeAction.KEEP_LOCAL -> Unit
                }
            }
            if (take.isNotEmpty()) local.apply(spec.table, take)
            if (adopt.isNotEmpty()) local.applyNumbers(spec.table, adopt)
            local.saveCursor(spec.table, cursor)
            local.saveMarks(spec.table, marks)
            report = report.copy(pulled = take.size, adopted = adopt.size)

            if (!canWrite) return report

            // ---- 2. upload what changed here
            val current = local.rows(spec.table)
            val dirty = current.filter { isDirty(it, marks) && mayPush(spec, role, it) }
            if (dirty.isNotEmpty()) {
                val result = remote.push(spec.table, dirty)
                var pushed = 0
                for (row in dirty) {
                    if (row.id !in result.failed) {
                        marks[row.id] = row.updatedAt
                        pushed++
                    }
                }
                report = report.copy(pushed = pushed)
                if (result.failed.isNotEmpty()) report = handleRefused(spec, role, result, marks, report)
                local.saveMarks(spec.table, marks)
            }

            // ---- 3. numbers
            val numberSpec = spec.number
            if (numberSpec != null) {
                val need = local.rows(spec.table)
                    .filter { it.number == null && !it.isDeleted && (marks[it.id] ?: -1L) >= it.updatedAt }
                    .map { it.id }
                if (need.isNotEmpty()) {
                    val numbers = remote.allocateNumbers(spec.table, numberSpec, need)
                    if (numbers.isNotEmpty()) {
                        local.applyNumbers(spec.table, numbers)
                        report = report.copy(numbered = numbers.size)
                    }
                }
            }
            return report
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return report.copy(error = describe(e))
        }
    }

    /**
     * The cloud refused some rows. A marketer's refused row is replaced by the cloud's copy (it was changed by the manager
     * in the meantime), so the two never stay apart. The manager's refused rows stay as they are and are reported, because a
     * refusal for the manager means the rules are wrong, and discarding work because of that would be worse than reporting it.
     */
    private suspend fun handleRefused(
        spec: SyncTableSpec,
        role: MktRole,
        result: PushResult,
        marks: MutableMap<String, Long>,
        before: TableReport,
    ): TableReport {
        var reverted = 0
        var stuck = 0
        var firstReason: String? = null
        for ((id, reason) in result.failed) {
            val theirs = if (role == MktRole.MARKETER) remote.get(spec.table, id) else null
            if (theirs != null) {
                local.apply(spec.table, listOf(theirs.record))
                marks[id] = theirs.record.updatedAt
                reverted++
            } else {
                stuck++
                if (firstReason == null) firstReason = reason
            }
        }
        return before.copy(reverted = reverted, failed = stuck, error = firstReason)
    }

    private fun isDirty(row: SyncRecord, marks: Map<String, Long>): Boolean = row.updatedAt > (marks[row.id] ?: -1L)

    private fun mayPush(spec: SyncTableSpec, role: MktRole, row: SyncRecord): Boolean {
        if (role == MktRole.MANAGER) return true
        if (!spec.marketerWrites) return false
        return spec.marketerMayPush?.invoke(row) ?: true
    }

    companion object {
        /** The cursor is moved back a little on every pull; a document seen twice is harmless (it is the same copy). */
        const val OVERLAP_MS = 2_000L
    }
}
