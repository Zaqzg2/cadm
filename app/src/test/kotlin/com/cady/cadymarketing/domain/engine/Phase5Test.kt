package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.MktRole
import com.cady.cadymarketing.domain.model.SavedContent
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5Test {
    private val sanaa: TimeZone = TimeZone.getTimeZone("Asia/Aden")

    private fun post(
        id: String,
        status: ContentStatus = ContentStatus.DRAFT,
        scheduledAt: String? = null,
        updatedAt: Long = 1L,
        productName: String = "سائل جلي",
        note: String? = null,
    ) = SavedContent(
        id = id, productId = "p1", productName = productName, channel = Channel.WHATSAPP, text = "نص",
        status = status, designId = 0, picturePath = null, scheduledAt = scheduledAt, campaignId = null,
        campaignCode = null, tone = null, angle = null, createdAt = 1L, updatedAt = updatedAt, note = note,
    )

    // ---------------------------------------------------------------- who is who
    @Test
    fun aKadyManagerIsTheManager() {
        assertEquals(MktRole.MANAGER, RoleRules.resolve(mapOf("role" to "manager", "isActive" to true), null))
        assertEquals(MktRole.MANAGER, RoleRules.resolve(mapOf("role" to "Manager"), null))
    }

    @Test
    fun aDisabledManagerGetsNothing() {
        assertEquals(MktRole.NONE, RoleRules.resolve(mapOf("role" to "manager", "isActive" to false), null))
    }

    @Test
    fun aKadyRepIsNotOnTheMarketingTeamUnlessListed() {
        val rep = mapOf("role" to "rep", "isActive" to true)
        assertEquals(MktRole.NONE, RoleRules.resolve(rep, null))
        val listed = mapOf("role" to "marketer", "isActive" to true)
        assertEquals(MktRole.MARKETER, RoleRules.resolve(rep, listed))
        assertEquals(MktRole.MARKETER, RoleRules.resolve(null, listed))
    }

    @Test
    fun aTeamEntryNeedsToBeExplicitlyActive() {
        assertEquals(MktRole.NONE, RoleRules.resolve(null, mapOf("role" to "marketer", "isActive" to false)))
        assertEquals(MktRole.NONE, RoleRules.resolve(null, mapOf("role" to "marketer")))
        assertEquals(MktRole.NONE, RoleRules.resolve(null, mapOf("role" to "manager", "isActive" to true)))
        assertEquals(MktRole.NONE, RoleRules.resolve(null, null))
    }

    @Test
    fun theManagerCanEverythingAndTheMarketerNothingExtra() {
        Feature.entries.forEach {
            assertTrue(Permissions.can(MktRole.MANAGER, it))
            assertFalse(Permissions.can(MktRole.MARKETER, it))
            assertFalse(Permissions.can(MktRole.NONE, it))
        }
    }

    // ---------------------------------------------------------------- how a post moves
    @Test
    fun theMainButtonApprovesForTheManagerAndSendsForTheMarketer() {
        assertEquals(ContentStatus.APPROVED, ContentFlow.submitStatus(MktRole.MANAGER))
        assertEquals(ContentStatus.PENDING, ContentFlow.submitStatus(MktRole.MARKETER))
        assertEquals("اعتماد", ContentFlow.submitLabel(MktRole.MANAGER))
        assertEquals("أرسل", ContentFlow.submitLabel(MktRole.MARKETER))
    }

    @Test
    fun aMarketerEditsOnlyWhatIsStillHers() {
        assertTrue(ContentFlow.canEdit(MktRole.MARKETER, ContentStatus.DRAFT))
        assertTrue(ContentFlow.canEdit(MktRole.MARKETER, ContentStatus.PENDING))
        assertTrue(ContentFlow.canEdit(MktRole.MARKETER, ContentStatus.RETURNED))
        assertFalse(ContentFlow.canEdit(MktRole.MARKETER, ContentStatus.APPROVED))
        assertFalse(ContentFlow.canEdit(MktRole.MARKETER, ContentStatus.PUBLISHED))
        assertFalse(ContentFlow.canEdit(MktRole.MARKETER, ContentStatus.ARCHIVED))
        ContentStatus.entries.forEach { assertTrue(ContentFlow.canEdit(MktRole.MANAGER, it)) }
    }

    @Test
    fun aMarketerMovesPostsOnlyBetweenDraftAndWaiting() {
        assertTrue(ContentFlow.canMove(MktRole.MARKETER, ContentStatus.RETURNED, ContentStatus.DRAFT))
        assertTrue(ContentFlow.canMove(MktRole.MARKETER, ContentStatus.PENDING, ContentStatus.DRAFT))
        assertFalse(ContentFlow.canMove(MktRole.MARKETER, ContentStatus.DRAFT, ContentStatus.ARCHIVED))
        assertFalse(ContentFlow.canMove(MktRole.MARKETER, ContentStatus.DRAFT, ContentStatus.APPROVED))
        assertFalse(ContentFlow.canMove(MktRole.MARKETER, ContentStatus.APPROVED, ContentStatus.DRAFT))
        assertTrue(ContentFlow.canMove(MktRole.MANAGER, ContentStatus.APPROVED, ContentStatus.PUBLISHED))
    }

    @Test
    fun onlyTheManagerReturnsAPostThatWaits() {
        assertTrue(ContentFlow.canReturn(MktRole.MANAGER, ContentStatus.PENDING))
        assertFalse(ContentFlow.canReturn(MktRole.MANAGER, ContentStatus.DRAFT))
        assertFalse(ContentFlow.canReturn(MktRole.MARKETER, ContentStatus.PENDING))
    }

    @Test
    fun theLibraryOffersAMarketerOnlyHerMoves() {
        val manager = LibraryRules.moves(ContentStatus.APPROVED, MktRole.MANAGER).map { it.target }
        assertTrue(ContentStatus.PUBLISHED in manager)
        val marketer = LibraryRules.moves(ContentStatus.PENDING, MktRole.MARKETER).map { it.target }
        assertEquals(listOf(ContentStatus.DRAFT), marketer)
        assertTrue(LibraryRules.moves(ContentStatus.APPROVED, MktRole.MARKETER).isEmpty())
        assertTrue(LibraryRules.moves(ContentStatus.DRAFT, MktRole.MARKETER).isEmpty())
    }

    // ---------------------------------------------------------------- team accounts
    @Test
    fun aBareLoginNameGetsTheKadyDomain() {
        assertEquals("sara@kady-app.local", TeamRules.emailFor("  Sara "))
        assertEquals("a.b@example.com", TeamRules.emailFor(" a.b@example.com "))
    }

    @Test
    fun loginNamesAndPasswordsAreChecked() {
        assertNull(TeamRules.usernameProblem("sara_1"))
        assertNotNull(TeamRules.usernameProblem(""))
        assertNotNull(TeamRules.usernameProblem("sa"))
        assertNotNull(TeamRules.usernameProblem("سارة"))
        assertNotNull(TeamRules.usernameProblem("sara ali"))
        assertNull(TeamRules.passwordProblem("123456"))
        assertNotNull(TeamRules.passwordProblem("12345"))
        assertNotNull(TeamRules.nameProblem("  "))
    }

    @Test
    fun theTeamDocumentRoundTrips() {
        val doc = TeamRules.memberDoc(" سارة ", "Sara", true, 99L)
        val member = TeamRules.member("u1", doc)
        assertNotNull(member)
        assertEquals("سارة", member!!.name)
        assertEquals("sara", member.username)
        assertEquals(MktRole.MARKETER, member.role)
        assertTrue(member.active)
        assertEquals(99L, member.updatedAt)
        assertNull(TeamRules.member("u2", mapOf("role" to "manager")))
        assertEquals(MktRole.MARKETER, RoleRules.resolve(null, doc))
    }

    // ---------------------------------------------------------------- reminders
    @Test
    fun aScheduleBecomesAMomentInTheZone() {
        val at = ReminderRules.parse("2026-11-20T10:30", sanaa)
        assertNotNull(at)
        // Aden is UTC+3 all year: 10:30 there is 07:30 UTC.
        assertEquals(java.time.Instant.parse("2026-11-20T07:30:00Z").toEpochMilli(), at)
        val dayOnly = ReminderRules.parse("2026-11-20", sanaa)
        assertEquals(java.time.Instant.parse("2026-11-20T06:00:00Z").toEpochMilli(), dayOnly)
    }

    @Test
    fun schedulesThatAreNotRealAreRejected() {
        assertNull(ReminderRules.parse(null, sanaa))
        assertNull(ReminderRules.parse("", sanaa))
        assertNull(ReminderRules.parse("غدًا", sanaa))
        assertNull(ReminderRules.parse("2026-02-30T10:00", sanaa))
        assertNull(ReminderRules.parse("2026-13-01T10:00", sanaa))
        assertNull(ReminderRules.parse("2026-11-20T25:00", sanaa))
        assertNull(ReminderRules.parse("2026-11-20T10:61", sanaa))
    }

    @Test
    fun onlyApprovedPostsStillAheadGetAReminder() {
        // 06:00 in Aden: the post at 10:00 today is still ahead, the one at 10:00 yesterday is not.
        val now = java.time.Instant.parse("2026-11-19T03:00:00Z").toEpochMilli()
        val posts = listOf(
            post("a", ContentStatus.APPROVED, "2026-11-20T10:00"),
            post("b", ContentStatus.APPROVED, "2026-11-19T10:00"),
            post("past", ContentStatus.APPROVED, "2026-11-18T10:00"),
            post("draft", ContentStatus.DRAFT, "2026-11-21T10:00"),
            post("pending", ContentStatus.PENDING, "2026-11-21T10:00"),
            post("published", ContentStatus.PUBLISHED, "2026-11-21T10:00"),
            post("none", ContentStatus.APPROVED, null),
            post("bad", ContentStatus.APPROVED, "soon"),
            post("far", ContentStatus.APPROVED, "2030-01-01T10:00"),
        )
        val reminders = ReminderRules.desired(posts, now, sanaa)
        assertEquals(listOf("b", "a"), reminders.map { it.contentId })
        assertEquals("2026-11-19T10:00", reminders[0].scheduledAt)
        assertTrue(reminders[0].text.contains("سائل جلي"))
        assertEquals(0L, ReminderRules.delayMillis(reminders[0].copy(atMillis = now - 5), now))
        assertEquals(4L * 3_600_000L, ReminderRules.delayMillis(reminders[0], now))
    }

    @Test
    fun aReminderIsOnlyShownWhileThePostStillMatches() {
        val p = post("a", ContentStatus.APPROVED, "2026-11-20T10:00")
        assertTrue(ReminderRules.stillDue(p, "2026-11-20T10:00"))
        assertFalse(ReminderRules.stillDue(p, "2026-11-20T11:00"))
        assertFalse(ReminderRules.stillDue(p.copy(status = ContentStatus.PUBLISHED), "2026-11-20T10:00"))
        assertFalse(ReminderRules.stillDue(null, "2026-11-20T10:00"))
    }

    // ---------------------------------------------------------------- the periodic check's messages
    @Test
    fun theManagerIsToldAboutPostsWaitingForHim() {
        val posts = listOf(post("a", ContentStatus.PENDING, updatedAt = 5), post("b", ContentStatus.DRAFT), post("c", ContentStatus.PENDING, updatedAt = 6))
        val notice = NotificationRules.forRole(MktRole.MANAGER, posts, emptySet())
        assertNotNull(notice)
        assertEquals(2, notice!!.keys.size)
        assertTrue(notice.text.contains("2"))
        val told = NotificationRules.remember(emptyList(), notice.keys).toSet()
        assertNull(NotificationRules.forRole(MktRole.MANAGER, posts, told))
        // A post changed again counts as new.
        val changed = posts.map { if (it.id == "a") it.copy(updatedAt = 50) else it }
        val again = NotificationRules.forRole(MktRole.MANAGER, changed, told)
        assertNotNull(again)
        assertEquals(1, again!!.keys.size)
        assertTrue(again.text.contains("سائل جلي"))
    }

    @Test
    fun aMarketerIsToldAboutPostsSentBackWithTheNote() {
        val posts = listOf(post("a", ContentStatus.RETURNED, note = "غيّر السعر"), post("b", ContentStatus.PENDING))
        val notice = NotificationRules.forRole(MktRole.MARKETER, posts, emptySet())
        assertNotNull(notice)
        assertTrue(notice!!.text.contains("غيّر السعر"))
        assertEquals(1, notice.keys.size)
        assertNull(NotificationRules.forRole(MktRole.NONE, posts, emptySet()))
    }

    @Test
    fun rememberedKeysAreCappedKeepingTheLatest() {
        val many = (1..300).map { "k$it" }
        val kept = NotificationRules.remember(many, listOf("new"))
        assertEquals(NotificationRules.MAX_KEYS, kept.size)
        assertEquals("new", kept.last())
        assertEquals("k102", kept.first())
        assertEquals(listOf("a", "b", "c"), NotificationRules.remember(listOf("a", "b"), listOf("b", "c")))
    }

    // ---------------------------------------------------------------- the backup file
    private fun record(id: String, updatedAt: Long, vararg data: Pair<String, Any?>) = SyncRecord(id, updatedAt, mapOf(*data))

    @Test
    fun aBackupRoundTripsThroughItsFile() {
        val file = BackupFile(
            createdAt = 1_700_000_000_000L,
            appVersion = "0.1.9",
            tables = mapOf(
                SyncSpecs.CONTENT to listOf(record("c1", 10, "text" to "نص «عربي»\nسطر", "designId" to 2, "deletedAt" to null, "number" to "CNT-0001")),
                SyncSpecs.CAMPAIGNS to emptyList(),
            ),
        )
        val decoded = BackupJson.decode(BackupJson.encode(file))
        assertTrue(decoded is BackupDecode.Ok)
        decoded as BackupDecode.Ok
        assertEquals(0, decoded.skipped)
        assertEquals(1_700_000_000_000L, decoded.file.createdAt)
        assertEquals("0.1.9", decoded.file.appVersion)
        val row = decoded.file.tables.getValue(SyncSpecs.CONTENT).single()
        assertEquals("c1", row.id)
        assertEquals(10L, row.updatedAt)
        assertEquals("نص «عربي»\nسطر", row.data["text"])
        assertEquals(2L, J.long(row.data, "designId"))
        assertNull(row.data["deletedAt"])
        assertEquals("CNT-0001", row.number)
        assertEquals(1, decoded.file.total)
    }

    @Test
    fun filesThatAreNotOurBackupAreRefusedWithAReason() {
        assertTrue(BackupJson.decode("not json") is BackupDecode.Bad)
        assertTrue(BackupJson.decode("[1,2]") is BackupDecode.Bad)
        assertTrue(BackupJson.decode("{\"app\":\"other\",\"format\":1}") is BackupDecode.Bad)
        val newer = BackupJson.decode("{\"app\":\"cady-marketing\",\"format\":99,\"tables\":{}}")
        assertTrue(newer is BackupDecode.Bad)
        assertTrue((newer as BackupDecode.Bad).message.contains("حدّث"))
    }

    @Test
    fun unreadableRowsAreSkippedAndCounted() {
        val text = "{\"app\":\"cady-marketing\",\"format\":1,\"tables\":{\"mkt_content\":[" +
            "{\"id\":\"ok\",\"updatedAt\":5,\"data\":{}}," +
            "{\"id\":\"\",\"updatedAt\":5,\"data\":{}}," +
            "{\"id\":\"x\",\"data\":{}}," +
            "{\"id\":\"y\",\"updatedAt\":5}]}}"
        val decoded = BackupJson.decode(text) as BackupDecode.Ok
        assertEquals(3, decoded.skipped)
        assertEquals(listOf("ok"), decoded.file.tables.getValue("mkt_content").map { it.id })
    }

    @Test
    fun restoringNeverOverwritesANewerRowOrDeletesAnything() {
        val local = listOf(record("a", 100, "v" to "local-new"), record("b", 10, "v" to "local-old"), record("only-local", 1))
        val incoming = listOf(record("a", 50, "v" to "backup-old"), record("b", 20, "v" to "backup-new"), record("c", 5, "v" to "added"))
        val plan = BackupMerge.plan(local, incoming, numbered = false)
        assertEquals(listOf("b", "c"), plan.apply.map { it.id })
        assertEquals(1, plan.added)
        assertEquals(1, plan.updated)
        assertEquals(1, plan.kept)
        // Restoring the same file again changes nothing.
        val again = BackupMerge.plan(plan.apply + local.filter { it.id == "a" || it.id == "only-local" }, incoming, numbered = false)
        assertTrue(again.apply.isEmpty())
    }

    @Test
    fun aBackupGivesANumberToARowThatHasNone() {
        val local = listOf(record("a", 10, "number" to null))
        val incoming = listOf(record("a", 10, "number" to "CNT-0007"))
        val plan = BackupMerge.plan(local, incoming, numbered = true)
        assertEquals(1, plan.apply.size)
        assertTrue(BackupMerge.plan(local, incoming, numbered = false).apply.isEmpty())
    }

    // ---------------------------------------------------------------- the catalogue for the marketer
    @Test
    fun theCatalogueRoundTripsAsOneDocument() {
        val products = listOf(KadyProduct("p1", "سائل جلي", "كرتونة", 7000, true), KadyProduct("p2", "منظف", "جركن", 1800, false))
        val offers = listOf(KadyOffer("o1", "p1", 10.5, null, null, "2026-11-01", null, true), KadyOffer("o2", "p2", null, 1500, 300, null, "2026-12-01", false))
        val decoded = CatalogMirror.decode(CatalogMirror.encode(products, offers))
        assertNotNull(decoded)
        assertEquals(products, decoded!!.first)
        assertEquals(offers, decoded.second)
    }

    @Test
    fun aCatalogueWithoutProductsIsNotAcceptedAndBrokenTextIsNull() {
        assertNull(CatalogMirror.decode(null))
        assertNull(CatalogMirror.decode("{"))
        assertNull(CatalogMirror.decode(CatalogMirror.encode(emptyList(), emptyList())))
    }

    // ---------------------------------------------------------------- numbers and merging
    @Test
    fun runningNumbersAreFormattedAndRead() {
        assertEquals("CNT-0001", NumberRules.format("CNT", 1))
        assertEquals("CMP-0123", NumberRules.format("CMP", 123))
        assertEquals("CNT-12345", NumberRules.format("CNT", 12345))
        assertEquals(12L, NumberRules.parse("CNT", "CNT-0012"))
        assertNull(NumberRules.parse("CNT", "CMP-0012"))
        assertNull(NumberRules.parse("CNT", null))
        assertNull(NumberRules.parse("CNT", "CNT-x"))
    }

    @Test
    fun theNewerCopyWinsAndATieAdoptsAMissingNumber() {
        val older = record("a", 1)
        val newer = record("a", 2)
        assertEquals(MergeAction.TAKE_REMOTE, SyncMerge.decide(null, older, false))
        assertEquals(MergeAction.TAKE_REMOTE, SyncMerge.decide(older, newer, false))
        assertEquals(MergeAction.KEEP_LOCAL, SyncMerge.decide(newer, older, false))
        assertEquals(MergeAction.SAME, SyncMerge.decide(newer, newer, false))
        val numbered = record("a", 2, "number" to "CNT-0001")
        assertEquals(MergeAction.ADOPT_NUMBER, SyncMerge.decide(newer, numbered, true))
        assertEquals(MergeAction.SAME, SyncMerge.decide(newer, numbered, false))
        assertEquals(MergeAction.SAME, SyncMerge.decide(numbered, newer, true))
    }

    @Test
    fun everySyncedTableHasARuleAndTheLogIsNotBackedUp() {
        val tables = SyncSpecs.all.map { it.table }
        assertEquals(tables.size, tables.toSet().size)
        assertTrue(tables.all { it.startsWith("mkt_") })
        assertFalse(SyncSpecs.LOG in SyncSpecs.backedUp)
        assertTrue(SyncSpecs.CONTENT in SyncSpecs.backedUp)
        assertNotNull(SyncSpecs.spec(SyncSpecs.CONTENT)!!.number)
        assertEquals("CNT", SyncSpecs.contentNumber.prefix)
    }
}
