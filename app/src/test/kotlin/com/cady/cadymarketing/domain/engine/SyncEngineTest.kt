package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.MktRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cloud, in memory. Every write is stamped by a server clock that only moves forward, like Firestore's. */
private class FakeCloud : SyncRemoteStore {
    val docs = HashMap<String, MutableMap<String, Pair<SyncRecord, Long>>>()
    val counters = HashMap<String, Long>()
    var clock = 1_000L
    var refuse: (String, SyncRecord) -> String? = { _, _ -> null }
    var brokenTable: String? = null

    fun doc(table: String, id: String): SyncRecord? = docs[table]?.get(id)?.first

    fun seed(table: String, record: SyncRecord) {
        docs.getOrPut(table) { HashMap() }[record.id] = record to ++clock
    }

    override suspend fun pull(table: String, sinceServerMs: Long): List<RemoteDoc> {
        if (table == brokenTable) throw IllegalStateException("boom")
        return docs[table].orEmpty().values.filter { it.second > sinceServerMs }.sortedBy { it.second }
            .map { RemoteDoc(it.first, it.second) }
    }

    override suspend fun get(table: String, id: String): RemoteDoc? = docs[table]?.get(id)?.let { RemoteDoc(it.first, it.second) }

    override suspend fun push(table: String, rows: List<SyncRecord>): PushResult {
        if (table == brokenTable) throw IllegalStateException("boom")
        val failed = LinkedHashMap<String, String>()
        for (row in rows) {
            val reason = refuse(table, row)
            if (reason != null) {
                failed[row.id] = reason
                continue
            }
            val old = docs.getOrPut(table) { HashMap() }[row.id]?.first
            val merged = LinkedHashMap(row.data)
            if (row.number == null && old?.number != null) merged["number"] = old.number
            docs.getValue(table)[row.id] = SyncRecord(row.id, row.updatedAt, merged) to ++clock
        }
        return PushResult(failed)
    }

    override suspend fun allocateNumbers(table: String, spec: NumberSpec, ids: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (id in ids) {
            val doc = docs[table]?.get(id)?.first ?: continue
            val existing = doc.number
            if (existing != null) {
                out[id] = existing
                continue
            }
            val next = counters[spec.counter] ?: 1L
            counters[spec.counter] = next + 1
            val number = NumberRules.format(spec.prefix, next)
            docs.getValue(table)[id] = SyncRecord(id, doc.updatedAt, doc.data + ("number" to number)) to ++clock
            out[id] = number
        }
        return out
    }
}

/** One phone: its rows, what it knows is synced, and its pull cursors. */
private class FakeDevice : SyncLocalStore {
    val tables = HashMap<String, MutableMap<String, SyncRecord>>()
    val marksByTable = HashMap<String, MutableMap<String, Long>>()
    val cursors = HashMap<String, Long>()

    fun edit(table: String, id: String, updatedAt: Long, vararg data: Pair<String, Any?>) {
        tables.getOrPut(table) { HashMap() }[id] = SyncRecord(id, updatedAt, mapOf(*data))
    }

    fun row(table: String, id: String): SyncRecord? = tables[table]?.get(id)

    override suspend fun rows(table: String): List<SyncRecord> = tables[table].orEmpty().values.sortedBy { it.id }

    override suspend fun marks(table: String): Map<String, Long> = HashMap(marksByTable[table].orEmpty())

    override suspend fun saveMarks(table: String, marks: Map<String, Long>) {
        marksByTable[table] = HashMap(marks)
    }

    override suspend fun apply(table: String, rows: List<SyncRecord>) {
        for (r in rows) {
            val old = tables.getOrPut(table) { HashMap() }[r.id]
            val data = if (r.number == null && old?.number != null) r.data + ("number" to old.number) else r.data
            tables.getValue(table)[r.id] = SyncRecord(r.id, r.updatedAt, data)
        }
    }

    override suspend fun applyNumbers(table: String, numbers: Map<String, String>) {
        for ((id, number) in numbers) {
            val old = tables[table]?.get(id) ?: continue
            if (old.number == null) tables.getValue(table)[id] = old.copy(data = old.data + ("number" to number))
        }
    }

    override suspend fun cursor(table: String): Long = cursors[table] ?: 0L

    override suspend fun saveCursor(table: String, value: Long) {
        cursors[table] = value
    }
}

class SyncEngineTest {
    private val cloud = FakeCloud()
    private val manager = FakeDevice()
    private val marketer = FakeDevice()
    private val managerEngine = SyncEngine(manager, cloud, SyncSpecs.all)
    private val marketerEngine = SyncEngine(marketer, cloud, SyncSpecs.all)

    private fun sync(engine: SyncEngine, role: MktRole) = runBlocking { engine.run(role, 0L) { it.message.orEmpty() } }

    private fun syncManager() = sync(managerEngine, MktRole.MANAGER)

    private fun syncMarketer() = sync(marketerEngine, MktRole.MARKETER)

    private fun content(device: FakeDevice, id: String, updatedAt: Long, status: String = "d", text: String = "نص $id") =
        device.edit(SyncSpecs.CONTENT, id, updatedAt, "status" to status, "text" to text, "deletedAt" to null)

    // ---------------------------------------------------------------- first sync and numbering
    @Test
    fun theFirstSyncUploadsEverythingAndGivesTheRunningNumbers() {
        content(manager, "c1", 100)
        content(manager, "c2", 200)
        manager.edit(SyncSpecs.CAMPAIGNS, "k1", 50, "name" to "رمضان", "deletedAt" to null)
        val report = syncManager()
        assertTrue(report.ok)
        assertEquals(3, report.pushed)
        assertEquals(3, report.numbered)
        assertEquals("CNT-0001", manager.row(SyncSpecs.CONTENT, "c1")!!.number)
        assertEquals("CNT-0002", manager.row(SyncSpecs.CONTENT, "c2")!!.number)
        assertEquals("CMP-0001", manager.row(SyncSpecs.CAMPAIGNS, "k1")!!.number)
        assertEquals("CNT-0001", cloud.doc(SyncSpecs.CONTENT, "c1")!!.number)
        assertEquals(3L, cloud.counters["content"])
    }

    @Test
    fun aSecondSyncWithNothingNewDoesNothing() {
        content(manager, "c1", 100)
        syncManager()
        val again = syncManager()
        assertEquals(0, again.pushed)
        assertEquals(0, again.pulled)
        assertEquals(0, again.numbered)
        assertEquals("لا جديد", again.summary)
        assertEquals(0, runBlocking { managerEngine.pendingCount(MktRole.MANAGER) })
    }

    @Test
    fun aRowIsNumberedOnceEvenIfTwoDevicesSyncItsLaterEdits() {
        content(manager, "c1", 100)
        syncManager()
        syncMarketer()
        content(marketer, "c1", 300, status = "p", text = "عدّلتها")
        syncMarketer()
        syncManager()
        assertEquals("CNT-0001", manager.row(SyncSpecs.CONTENT, "c1")!!.number)
        assertEquals("CNT-0001", marketer.row(SyncSpecs.CONTENT, "c1")!!.number)
        assertEquals("عدّلتها", manager.row(SyncSpecs.CONTENT, "c1")!!.data["text"])
        assertEquals(2L, cloud.counters["content"])
    }

    @Test
    fun postsMadeOnTwoDevicesGetDifferentNumbers() {
        content(manager, "m1", 100)
        content(marketer, "k1", 110, status = "p")
        syncManager()
        syncMarketer()
        val numbers = setOf(
            manager.row(SyncSpecs.CONTENT, "m1")!!.number,
            marketer.row(SyncSpecs.CONTENT, "k1")!!.number,
        )
        assertEquals(setOf("CNT-0001", "CNT-0002"), numbers)
    }

    @Test
    fun aNumberGivenByAnotherDeviceIsAdoptedNotReplaced() {
        content(manager, "c1", 100)
        syncManager()
        // The same post exists on the marketer's phone from an older pull with no number.
        content(marketer, "c1", 100)
        val report = syncMarketer()
        assertEquals("CNT-0001", marketer.row(SyncSpecs.CONTENT, "c1")!!.number)
        assertEquals(0, report.numbered)
        assertEquals(2L, cloud.counters["content"])
    }

    // ---------------------------------------------------------------- the newer copy wins
    @Test
    fun theLaterEditWinsOnBothPhones() {
        content(manager, "c1", 100)
        syncManager()
        syncMarketer()
        content(manager, "c1", 500, status = "a", text = "من المدير")
        content(marketer, "c1", 400, status = "p", text = "من المسوّقة")
        syncManager()
        syncMarketer()
        syncManager()
        assertEquals("من المدير", marketer.row(SyncSpecs.CONTENT, "c1")!!.data["text"])
        assertEquals("من المدير", manager.row(SyncSpecs.CONTENT, "c1")!!.data["text"])
        assertEquals("من المدير", cloud.doc(SyncSpecs.CONTENT, "c1")!!.data["text"])
    }

    @Test
    fun anOldEditUploadedLateStillReachesTheOtherPhone() {
        content(manager, "c1", 100)
        syncManager()
        syncMarketer()
        // The marketer edits on a plane (old clock), the manager syncs meanwhile, then the marketer lands.
        content(marketer, "c2", 150, status = "p", text = "كتبتها بلا إنترنت")
        syncManager()
        syncManager()
        syncMarketer()
        syncManager()
        assertEquals("كتبتها بلا إنترنت", manager.row(SyncSpecs.CONTENT, "c2")!!.data["text"])
    }

    @Test
    fun deletedRowsTravelAsMarks() {
        content(manager, "c1", 100)
        syncManager()
        syncMarketer()
        manager.edit(SyncSpecs.CONTENT, "c1", 600, "status" to "d", "text" to "نص", "deletedAt" to 600L)
        syncManager()
        syncMarketer()
        assertEquals(600L, marketer.row(SyncSpecs.CONTENT, "c1")!!.data["deletedAt"])
    }

    // ---------------------------------------------------------------- what a marketer may do
    @Test
    fun aMarketerNeverUploadsTheManagersTables() {
        manager.edit(SyncSpecs.CAMPAIGNS, "k1", 50, "name" to "أصلية", "deletedAt" to null)
        syncManager()
        syncMarketer()
        marketer.edit(SyncSpecs.CAMPAIGNS, "k1", 900, "name" to "معدّلة", "deletedAt" to null)
        marketer.edit(SyncSpecs.OCCASIONS, "o1", 900, "title" to "مناسبة", "deletedAt" to null)
        val report = syncMarketer()
        assertEquals("أصلية", cloud.doc(SyncSpecs.CAMPAIGNS, "k1")!!.data["name"])
        assertNull(cloud.doc(SyncSpecs.OCCASIONS, "o1"))
        assertEquals(0, report.pushed)
        assertEquals(0, runBlocking { marketerEngine.pendingCount(MktRole.MARKETER) })
    }

    @Test
    fun aMarketerNeverReadsTheCustomerSide() {
        cloud.seed(SyncSpecs.CONSENT, SyncRecord("cust1", 10, mapOf("status" to "yes")))
        cloud.seed(SyncSpecs.RESULTS, SyncRecord("r1", 10, mapOf("value" to 5)))
        syncMarketer()
        assertNull(marketer.row(SyncSpecs.CONSENT, "cust1"))
        assertNull(marketer.row(SyncSpecs.RESULTS, "r1"))
        syncManager()
        assertNotNull(manager.row(SyncSpecs.CONSENT, "cust1"))
    }

    @Test
    fun aMarketerUploadsOnlyDraftsAndPostsWaitingForApproval() {
        content(marketer, "p1", 10, status = "p")
        content(marketer, "p2", 11, status = "d")
        content(marketer, "p3", 12, status = "a")
        content(marketer, "p4", 13, status = "u")
        syncMarketer()
        assertNotNull(cloud.doc(SyncSpecs.CONTENT, "p1"))
        assertNotNull(cloud.doc(SyncSpecs.CONTENT, "p2"))
        assertNull(cloud.doc(SyncSpecs.CONTENT, "p3"))
        assertNull(cloud.doc(SyncSpecs.CONTENT, "p4"))
    }

    @Test
    fun theMarketerUploadsHerLogButDoesNotReadTheOthers() {
        manager.edit(SyncSpecs.LOG, "l1", 10, "action" to "ca", "subject" to "CNT-0001")
        syncManager()
        marketer.edit(SyncSpecs.LOG, "l2", 20, "action" to "cs", "subject" to "CNT-0002")
        syncMarketer()
        assertNotNull(cloud.doc(SyncSpecs.LOG, "l2"))
        assertNull(marketer.row(SyncSpecs.LOG, "l1"))
        syncManager()
        assertNotNull(manager.row(SyncSpecs.LOG, "l2"))
    }

    @Test
    fun aRowTheServerRefusesIsReplacedByTheServersCopyForAMarketer() {
        // The manager approved the post a moment ago; the marketer's newer-clocked edit is refused by the rules.
        cloud.seed(SyncSpecs.CONTENT, SyncRecord("c1", 300, mapOf("status" to "a", "text" to "المعتمد", "deletedAt" to null)))
        cloud.refuse = { _, row -> if (row.id == "c1") "PERMISSION_DENIED" else null }
        marketer.edit(SyncSpecs.CONTENT, "c1", 400, "status" to "p", "text" to "تعديلي", "deletedAt" to null)
        marketer.saveMarksBlocking(SyncSpecs.CONTENT, mapOf("c1" to 100L))
        marketer.cursors[SyncSpecs.CONTENT] = 1_000_000L
        val report = syncMarketer()
        assertEquals(1, report.reverted)
        assertEquals("a", marketer.row(SyncSpecs.CONTENT, "c1")!!.data["status"])
        assertEquals("المعتمد", marketer.row(SyncSpecs.CONTENT, "c1")!!.data["text"])
        assertTrue(report.ok)
    }

    @Test
    fun aRowTheServerRefusesStaysForTheManagerAndIsReported() {
        cloud.refuse = { _, _ -> "PERMISSION_DENIED" }
        content(manager, "c1", 100, text = "عملي")
        val report = syncManager()
        assertFalse(report.ok)
        assertTrue(report.failed >= 1)
        assertEquals("PERMISSION_DENIED", report.firstError)
        assertEquals("عملي", manager.row(SyncSpecs.CONTENT, "c1")!!.data["text"])
        assertTrue(runBlocking { managerEngine.pendingCount(MktRole.MANAGER) } >= 1)
    }

    // ---------------------------------------------------------------- failures and roles
    @Test
    fun oneBrokenTableDoesNotStopTheOthers() {
        cloud.brokenTable = SyncSpecs.CAMPAIGNS
        manager.edit(SyncSpecs.CAMPAIGNS, "k1", 50, "name" to "رمضان", "deletedAt" to null)
        content(manager, "c1", 100)
        val report = syncManager()
        assertFalse(report.ok)
        assertEquals("boom", report.firstError)
        assertNotNull(cloud.doc(SyncSpecs.CONTENT, "c1"))
        assertEquals("CNT-0001", manager.row(SyncSpecs.CONTENT, "c1")!!.number)
    }

    @Test
    fun aPersonWithNoRoleSyncsNothing() {
        content(manager, "c1", 100)
        val report = sync(managerEngine, MktRole.NONE)
        assertTrue(report.tables.isEmpty())
        assertNull(cloud.doc(SyncSpecs.CONTENT, "c1"))
        assertEquals(0, runBlocking { managerEngine.pendingCount(MktRole.NONE) })
    }

    @Test
    fun pendingCountFollowsLocalEditsUntilTheyAreSynced() {
        content(manager, "c1", 100)
        content(manager, "c2", 110)
        assertEquals(2, runBlocking { managerEngine.pendingCount(MktRole.MANAGER) })
        syncManager()
        assertEquals(0, runBlocking { managerEngine.pendingCount(MktRole.MANAGER) })
        content(manager, "c1", 900, text = "تعديل")
        assertEquals(1, runBlocking { managerEngine.pendingCount(MktRole.MANAGER) })
    }

    @Test
    fun aFreshPhoneGetsEverythingBack() {
        content(manager, "c1", 100)
        manager.edit(SyncSpecs.DOCS, "brand", 40, "json" to "{}")
        manager.edit(SyncSpecs.CONSENT, "cust1", 60, "status" to "yes")
        syncManager()
        val fresh = FakeDevice()
        val report = sync(SyncEngine(fresh, cloud, SyncSpecs.all), MktRole.MANAGER)
        assertTrue(report.ok)
        assertEquals("CNT-0001", fresh.row(SyncSpecs.CONTENT, "c1")!!.number)
        assertNotNull(fresh.row(SyncSpecs.DOCS, "brand"))
        assertNotNull(fresh.row(SyncSpecs.CONSENT, "cust1"))
    }

    @Test
    fun aRestoredOlderRowIsNeverPushedOverANewerCloudCopy() {
        content(manager, "c1", 500, text = "الحديث")
        syncManager()
        // A backup restore put an older copy back with an old time: it is not newer than what the cloud holds.
        content(manager, "c1", 100, text = "القديم")
        val report = syncManager()
        assertEquals(0, report.pushed)
        assertEquals("الحديث", cloud.doc(SyncSpecs.CONTENT, "c1")!!.data["text"])
    }
}

private fun FakeDevice.saveMarksBlocking(table: String, marks: Map<String, Long>) {
    marksByTable[table] = HashMap(marks)
}
