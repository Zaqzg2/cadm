package com.cady.cadymarketing.data.sync

import com.cady.cadymarketing.data.local.CampaignEntity
import com.cady.cadymarketing.data.local.ConsentEntity
import com.cady.cadymarketing.data.local.ContentEntity
import com.cady.cadymarketing.data.local.DocEntity
import com.cady.cadymarketing.data.local.OccasionEntity
import com.cady.cadymarketing.data.local.OpLogEntity
import com.cady.cadymarketing.data.local.ProductExtraEntity
import com.cady.cadymarketing.data.local.ResultEntity
import com.cady.cadymarketing.data.local.SegmentEntity
import com.cady.cadymarketing.data.local.SendMarkEntity
import com.cady.cadymarketing.domain.engine.BackupDecode
import com.cady.cadymarketing.domain.engine.BackupFile
import com.cady.cadymarketing.domain.engine.BackupJson
import com.cady.cadymarketing.domain.engine.J
import com.cady.cadymarketing.domain.engine.SyncRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMappersTest {
    private val content = ContentEntity(
        id = "c1", number = "CNT-0003", productId = "p1", productName = "سائل جلي", channel = "ig", text = "نص\nسطرين",
        status = "p", campaignId = "k1", campaignCode = "K4F2A", scheduledAt = "2026-11-20T10:00", designId = 3,
        picturePath = "pic-1.png", tone = "تجاري", angle = "عرض", note = "غيّر السعر", org = "cady", schemaVersion = 1,
        createdAt = 10L, updatedAt = 20L, deletedAt = null,
    )

    @Test
    fun aPostRoundTripsAndNeverSendsItsPictureFile() {
        val record = SyncMappers.contentToRecord(content)
        assertEquals("c1", record.id)
        assertEquals(20L, record.updatedAt)
        assertFalse(record.data.containsKey("picturePath"))
        assertEquals("CNT-0003", record.number)
        val back = SyncMappers.contentFromRecord(record, null)!!
        assertEquals(content.copy(picturePath = null), back)
    }

    @Test
    fun aPostComingInKeepsThePictureThisDeviceAlreadyHas() {
        val remote = SyncMappers.contentToRecord(content.copy(text = "نص جديد", updatedAt = 30L, picturePath = "other-phone.png"))
        val back = SyncMappers.contentFromRecord(remote, content)!!
        assertEquals("pic-1.png", back.picturePath)
        assertEquals("نص جديد", back.text)
        assertEquals(30L, back.updatedAt)
    }

    @Test
    fun aPostWithoutANumberKeepsTheNumberItHas() {
        val remote = SyncMappers.contentToRecord(content.copy(number = null, updatedAt = 30L))
        assertFalse(remote.data.containsKey("number"))
        assertEquals("CNT-0003", SyncMappers.contentFromRecord(remote, content)!!.number)
        assertNull(SyncMappers.contentFromRecord(remote, content.copy(number = null))!!.number)
    }

    @Test
    fun aPostWithoutItsProductIsLeftOutAndOldDocumentsGetDefaults() {
        assertNull(SyncMappers.contentFromRecord(SyncRecord("x", 5, mapOf("text" to "بلا صنف")), null))
        val old = SyncMappers.contentFromRecord(SyncRecord("x", 5, mapOf("productId" to "p9")), null)!!
        assertEquals("", old.text)
        assertEquals("d", old.status)
        assertEquals("wa", old.channel)
        assertEquals(0, old.designId)
        assertEquals(5L, old.createdAt)
        assertEquals("cady", old.org)
        assertNull(old.deletedAt)
    }

    @Test
    fun aDeletedPostKeepsItsDeletionMark() {
        val gone = content.copy(deletedAt = 99L, updatedAt = 99L)
        val back = SyncMappers.contentFromRecord(SyncMappers.contentToRecord(gone), null)!!
        assertEquals(99L, back.deletedAt)
    }

    @Test
    fun campaignsKeepTheirCodeAndNumber() {
        val e = CampaignEntity("k1", "K4F2A", "CMP-0001", "رمضان", "هدف", "2026-11-01", null, "a", "cady", 1, 5L, 6L, null)
        val record = SyncMappers.campaignToRecord(e)
        assertEquals("K4F2A", record.data["code"])
        assertEquals(e, SyncMappers.campaignFromRecord(record, null))
        assertNull(SyncMappers.campaignFromRecord(SyncRecord("k", 1, mapOf("name" to "بلا رمز")), null))
    }

    @Test
    fun occasionsConsentSegmentsMarksAndResultsRoundTrip() {
        val occasion = OccasionEntity("o1", "عيد", "2026-12-01", true, "cady", 1, 1L, 2L, null)
        assertEquals(occasion, SyncMappers.occasionFromRecord(SyncMappers.occasionToRecord(occasion), null))

        val consent = ConsentEntity("cust9", "yes", "وافق هاتفيًا", "cady", 1, 1L, 2L, null)
        val consentRecord = SyncMappers.consentToRecord(consent)
        assertEquals("cust9", consentRecord.id)
        assertEquals(consent, SyncMappers.consentFromRecord(consentRecord, null))

        val segment = SegmentEntity("s1", "الحديدة", "filter", "الحديدة", "", "", "cady", 1, 1L, 2L, null)
        assertEquals(segment, SyncMappers.segmentFromRecord(SyncMappers.segmentToRecord(segment), null))

        val mark = SendMarkEntity("c1", "cust9", "sent", "cady", 1, 1L, 2L, null)
        val markRecord = SyncMappers.sendMarkToRecord(mark)
        assertEquals("c1__cust9", markRecord.id)
        assertEquals(mark, SyncMappers.sendMarkFromRecord(markRecord, null))

        val result = ResultEntity("r1", "k1", "wa", "orders", 12L, "2026-11-10", "ملاحظة", "cady", 1, 1L, 2L, null)
        assertEquals(result, SyncMappers.resultFromRecord(SyncMappers.resultToRecord(result), null))
        val noChannel = result.copy(channel = null)
        assertEquals(noChannel, SyncMappers.resultFromRecord(SyncMappers.resultToRecord(noChannel), null))
    }

    @Test
    fun theBrandNeverSendsItsLogoFileAndKeepsThisDevicesOwn() {
        val local = DocEntity("brand", "{\"name\":\"كادي\",\"primaryHex\":\"#c8102e\",\"logoFile\":\"logo-mine.png\"}", 10L, null)
        val record = SyncMappers.docToRecord(local)
        assertFalse((record.data["json"] as String).contains("logoFile"))
        assertTrue((record.data["json"] as String).contains("c8102e"))

        val remote = SyncRecord("brand", 20L, mapOf("json" to "{\"name\":\"كادي الجديد\",\"logoFile\":\"logo-theirs.png\"}", "deletedAt" to null))
        val merged = SyncMappers.docFromRecord(remote, local)!!
        assertEquals(20L, merged.updatedAt)
        assertEquals("logo-mine.png", J.str(J.parseMap(merged.json), "logoFile"))
        assertEquals("كادي الجديد", J.str(J.parseMap(merged.json), "name"))

        // With no logo here, the other phone's logo name is not adopted (the file does not exist on this one).
        val noLogo = SyncMappers.docFromRecord(remote, local.copy(json = "{\"name\":\"كادي\"}"))!!
        assertNull(J.str(J.parseMap(noLogo.json), "logoFile"))
    }

    @Test
    fun otherDocumentsTravelAsTheyAre() {
        val vocabulary = DocEntity("vocabulary", "{\"updatedAt\":5,\"tones\":{}}", 5L, null)
        val record = SyncMappers.docToRecord(vocabulary)
        assertEquals(vocabulary.json, record.data["json"])
        assertEquals(vocabulary, SyncMappers.docFromRecord(record, null))
        assertNull(SyncMappers.docFromRecord(SyncRecord("x", 1, emptyMap()), null))
    }

    @Test
    fun aProductsFeaturesTravelButItsPictureDoesNot() {
        val extra = ProductExtraEntity("p1", "pic.png", "ميزة 1\nميزة 2", 7L)
        val record = SyncMappers.extraToRecord(extra)
        assertFalse(record.data.containsKey("picturePath"))
        val back = SyncMappers.extraFromRecord(record.copy(updatedAt = 9L, data = record.data + ("features" to "ميزة جديدة")), extra)
        assertEquals("pic.png", back.picturePath)
        assertEquals("ميزة جديدة", back.features)
        assertEquals(9L, back.updatedAt)
        assertNull(SyncMappers.extraFromRecord(record, null).picturePath)
    }

    @Test
    fun theLogRoundTrips() {
        val line = OpLogEntity("l1", 123L, "manager@x", "ca", "CNT-0001 · سائل جلي", "", "cady", 1)
        val record = SyncMappers.logToRecord(line)
        assertEquals(123L, record.updatedAt)
        assertEquals(line, SyncMappers.logFromRecord(record))
        assertNull(SyncMappers.logFromRecord(SyncRecord("x", 1, mapOf("actor" to "a"))))
    }

    @Test
    fun aRowStillMapsAfterGoingThroughABackupFile() {
        // The backup file reads every number back as a decimal; the mappers must not care.
        val record = SyncMappers.contentToRecord(content)
        val file = BackupFile(1L, "v", mapOf("mkt_content" to listOf(record)))
        val decoded = (BackupJson.decode(BackupJson.encode(file)) as BackupDecode.Ok).file
        val back = SyncMappers.contentFromRecord(decoded.tables.getValue("mkt_content").single(), null)!!
        assertEquals(content.copy(picturePath = null), back)
    }
}
