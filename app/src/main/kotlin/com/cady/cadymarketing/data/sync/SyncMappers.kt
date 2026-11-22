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
import com.cady.cadymarketing.domain.engine.J
import com.cady.cadymarketing.domain.engine.MiniJson
import com.cady.cadymarketing.domain.engine.SyncRecord
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ContentStatus

/**
 * Rows to the plain records the sync moves, and back. Two rules hold for every table:
 *  - what belongs to one device alone (the picture files, the brand's logo file) is never sent, and a row that comes in
 *    never overwrites it: the device keeps its own;
 *  - a document from an older or newer app version never crashes the reader: missing text becomes empty, and a row that
 *    lacks what makes it meaningful (its product, its code...) is left out (the function answers null).
 */
object SyncMappers {
    /** The key of the brand document in `mkt_doc` (the same as RoomBrandRepository.KEY). Its `logoFile` stays on the device. */
    const val BRAND_KEY = "brand"

    /** A send-list line has no id of its own: its document id is the post and the customer joined by this. */
    const val MARK_SEPARATOR = "__"

    private const val ORG = "cady"
    private const val SCHEMA_VERSION = 1

    private fun fields(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

    private fun Map<String, Any?>.text(key: String): String = J.str(this, key).orEmpty()

    private fun Map<String, Any?>.orgOrDefault(): String = J.str(this, "org")?.takeIf { it.isNotBlank() } ?: ORG

    private fun Map<String, Any?>.schema(): Int = J.long(this, "schemaVersion")?.toInt() ?: SCHEMA_VERSION

    private fun LinkedHashMap<String, Any?>.withNumber(number: String?): Map<String, Any?> {
        if (number != null) this["number"] = number
        return this
    }

    fun markId(contentId: String, customerId: String): String = contentId + MARK_SEPARATOR + customerId

    // ------------------------------------------------------------------ content
    fun contentToRecord(e: ContentEntity): SyncRecord = SyncRecord(
        e.id,
        e.updatedAt,
        fields(
            "productId" to e.productId, "productName" to e.productName, "channel" to e.channel, "text" to e.text,
            "status" to e.status, "campaignId" to e.campaignId, "campaignCode" to e.campaignCode,
            "scheduledAt" to e.scheduledAt, "designId" to e.designId, "tone" to e.tone, "angle" to e.angle,
            "note" to e.note, "org" to e.org, "schemaVersion" to e.schemaVersion, "createdAt" to e.createdAt,
            "updatedAt" to e.updatedAt, "deletedAt" to e.deletedAt,
        ).withNumber(e.number),
    )

    fun contentFromRecord(r: SyncRecord, existing: ContentEntity?): ContentEntity? {
        val d = r.data
        val productId = J.str(d, "productId")?.takeIf { it.isNotBlank() } ?: return null
        return ContentEntity(
            id = r.id,
            number = J.str(d, "number") ?: existing?.number,
            productId = productId,
            productName = d.text("productName"),
            channel = Channel.fromCode(J.str(d, "channel")).code,
            text = d.text("text"),
            status = ContentStatus.fromCode(J.str(d, "status")).code,
            campaignId = J.str(d, "campaignId")?.takeIf { it.isNotBlank() },
            campaignCode = J.str(d, "campaignCode")?.takeIf { it.isNotBlank() },
            scheduledAt = J.str(d, "scheduledAt")?.takeIf { it.isNotBlank() },
            designId = J.long(d, "designId")?.toInt() ?: existing?.designId ?: 0,
            picturePath = existing?.picturePath,
            tone = J.str(d, "tone"),
            angle = J.str(d, "angle"),
            note = J.str(d, "note")?.takeIf { it.isNotBlank() },
            org = d.orgOrDefault(),
            schemaVersion = d.schema(),
            createdAt = J.long(d, "createdAt") ?: existing?.createdAt ?: r.updatedAt,
            updatedAt = r.updatedAt,
            deletedAt = J.long(d, "deletedAt"),
        )
    }

    // ------------------------------------------------------------------ campaigns
    fun campaignToRecord(e: CampaignEntity): SyncRecord = SyncRecord(
        e.id,
        e.updatedAt,
        fields(
            "code" to e.code, "name" to e.name, "goal" to e.goal, "startDate" to e.startDate, "endDate" to e.endDate,
            "status" to e.status, "org" to e.org, "schemaVersion" to e.schemaVersion, "createdAt" to e.createdAt,
            "updatedAt" to e.updatedAt, "deletedAt" to e.deletedAt,
        ).withNumber(e.number),
    )

    fun campaignFromRecord(r: SyncRecord, existing: CampaignEntity?): CampaignEntity? {
        val d = r.data
        val code = J.str(d, "code")?.takeIf { it.isNotBlank() } ?: return null
        return CampaignEntity(
            id = r.id,
            code = code,
            number = J.str(d, "number") ?: existing?.number,
            name = d.text("name"),
            goal = d.text("goal"),
            startDate = J.str(d, "startDate")?.takeIf { it.isNotBlank() },
            endDate = J.str(d, "endDate")?.takeIf { it.isNotBlank() },
            status = J.str(d, "status")?.takeIf { it.isNotBlank() } ?: "a",
            org = d.orgOrDefault(),
            schemaVersion = d.schema(),
            createdAt = J.long(d, "createdAt") ?: existing?.createdAt ?: r.updatedAt,
            updatedAt = r.updatedAt,
            deletedAt = J.long(d, "deletedAt"),
        )
    }

    // ------------------------------------------------------------------ occasions
    fun occasionToRecord(e: OccasionEntity): SyncRecord = SyncRecord(
        e.id,
        e.updatedAt,
        fields(
            "title" to e.title, "date" to e.date, "yearly" to e.yearly, "org" to e.org,
            "schemaVersion" to e.schemaVersion, "createdAt" to e.createdAt, "updatedAt" to e.updatedAt,
            "deletedAt" to e.deletedAt,
        ),
    )

    fun occasionFromRecord(r: SyncRecord, existing: OccasionEntity?): OccasionEntity? {
        val d = r.data
        val date = J.str(d, "date")?.takeIf { it.isNotBlank() } ?: return null
        return OccasionEntity(
            id = r.id,
            title = d.text("title"),
            date = date,
            yearly = J.bool(d, "yearly") ?: false,
            org = d.orgOrDefault(),
            schemaVersion = d.schema(),
            createdAt = J.long(d, "createdAt") ?: existing?.createdAt ?: r.updatedAt,
            updatedAt = r.updatedAt,
            deletedAt = J.long(d, "deletedAt"),
        )
    }

    // ------------------------------------------------------------------ consent (the id is the customer's)
    fun consentToRecord(e: ConsentEntity): SyncRecord = SyncRecord(
        e.customerId,
        e.updatedAt,
        fields(
            "status" to e.status, "note" to e.note, "org" to e.org, "schemaVersion" to e.schemaVersion,
            "createdAt" to e.createdAt, "updatedAt" to e.updatedAt, "deletedAt" to e.deletedAt,
        ),
    )

    fun consentFromRecord(r: SyncRecord, existing: ConsentEntity?): ConsentEntity? {
        val d = r.data
        val status = J.str(d, "status")?.takeIf { it.isNotBlank() } ?: return null
        return ConsentEntity(
            customerId = r.id,
            status = status,
            note = d.text("note"),
            org = d.orgOrDefault(),
            schemaVersion = d.schema(),
            createdAt = J.long(d, "createdAt") ?: existing?.createdAt ?: r.updatedAt,
            updatedAt = r.updatedAt,
            deletedAt = J.long(d, "deletedAt"),
        )
    }

    // ------------------------------------------------------------------ segments
    fun segmentToRecord(e: SegmentEntity): SyncRecord = SyncRecord(
        e.id,
        e.updatedAt,
        fields(
            "name" to e.name, "kind" to e.kind, "area" to e.area, "repName" to e.repName, "memberIds" to e.memberIds,
            "org" to e.org, "schemaVersion" to e.schemaVersion, "createdAt" to e.createdAt,
            "updatedAt" to e.updatedAt, "deletedAt" to e.deletedAt,
        ),
    )

    fun segmentFromRecord(r: SyncRecord, existing: SegmentEntity?): SegmentEntity? {
        val d = r.data
        val kind = J.str(d, "kind")?.takeIf { it.isNotBlank() } ?: return null
        return SegmentEntity(
            id = r.id,
            name = d.text("name"),
            kind = kind,
            area = d.text("area"),
            repName = d.text("repName"),
            memberIds = d.text("memberIds"),
            org = d.orgOrDefault(),
            schemaVersion = d.schema(),
            createdAt = J.long(d, "createdAt") ?: existing?.createdAt ?: r.updatedAt,
            updatedAt = r.updatedAt,
            deletedAt = J.long(d, "deletedAt"),
        )
    }

    // ------------------------------------------------------------------ send-list lines
    fun sendMarkToRecord(e: SendMarkEntity): SyncRecord = SyncRecord(
        markId(e.contentId, e.customerId),
        e.updatedAt,
        fields(
            "contentId" to e.contentId, "customerId" to e.customerId, "status" to e.status, "org" to e.org,
            "schemaVersion" to e.schemaVersion, "createdAt" to e.createdAt, "updatedAt" to e.updatedAt,
            "deletedAt" to e.deletedAt,
        ),
    )

    fun sendMarkFromRecord(r: SyncRecord, existing: SendMarkEntity?): SendMarkEntity? {
        val d = r.data
        val contentId = J.str(d, "contentId")?.takeIf { it.isNotBlank() } ?: return null
        val customerId = J.str(d, "customerId")?.takeIf { it.isNotBlank() } ?: return null
        val status = J.str(d, "status")?.takeIf { it.isNotBlank() } ?: return null
        return SendMarkEntity(
            contentId = contentId,
            customerId = customerId,
            status = status,
            org = d.orgOrDefault(),
            schemaVersion = d.schema(),
            createdAt = J.long(d, "createdAt") ?: existing?.createdAt ?: r.updatedAt,
            updatedAt = r.updatedAt,
            deletedAt = J.long(d, "deletedAt"),
        )
    }

    // ------------------------------------------------------------------ results written by hand
    fun resultToRecord(e: ResultEntity): SyncRecord = SyncRecord(
        e.id,
        e.updatedAt,
        fields(
            "campaignId" to e.campaignId, "channel" to e.channel, "metric" to e.metric, "value" to e.value,
            "date" to e.date, "note" to e.note, "org" to e.org, "schemaVersion" to e.schemaVersion,
            "createdAt" to e.createdAt, "updatedAt" to e.updatedAt, "deletedAt" to e.deletedAt,
        ),
    )

    fun resultFromRecord(r: SyncRecord, existing: ResultEntity?): ResultEntity? {
        val d = r.data
        val campaignId = J.str(d, "campaignId")?.takeIf { it.isNotBlank() } ?: return null
        val metric = J.str(d, "metric")?.takeIf { it.isNotBlank() } ?: return null
        return ResultEntity(
            id = r.id,
            campaignId = campaignId,
            channel = J.str(d, "channel")?.takeIf { it.isNotBlank() },
            metric = metric,
            value = J.long(d, "value") ?: 0L,
            date = d.text("date"),
            note = d.text("note"),
            org = d.orgOrDefault(),
            schemaVersion = d.schema(),
            createdAt = J.long(d, "createdAt") ?: existing?.createdAt ?: r.updatedAt,
            updatedAt = r.updatedAt,
            deletedAt = J.long(d, "deletedAt"),
        )
    }

    // ------------------------------------------------------------------ stored documents (brand, vocabulary, catalogue)
    fun docToRecord(e: DocEntity): SyncRecord = SyncRecord(
        e.docKey,
        e.updatedAt,
        fields("json" to publishable(e.docKey, e.json), "updatedAt" to e.updatedAt, "deletedAt" to e.deletedAt),
    )

    fun docFromRecord(r: SyncRecord, existing: DocEntity?): DocEntity? {
        val json = J.str(r.data, "json") ?: return null
        val merged = if (r.id == BRAND_KEY) keepLocalLogo(json, existing?.json) else json
        return DocEntity(r.id, merged, r.updatedAt, J.long(r.data, "deletedAt"))
    }

    /** The brand document without the logo file name: that file exists on this device only. */
    private fun publishable(key: String, json: String): String =
        if (key != BRAND_KEY) json else MiniJson.write(J.parseMap(json) - "logoFile")

    private fun keepLocalLogo(incoming: String, local: String?): String {
        val logo = J.str(J.parseMap(local), "logoFile")
        val map = LinkedHashMap(J.parseMap(incoming))
        if (logo != null) map["logoFile"] = logo else map.remove("logoFile")
        return MiniJson.write(map)
    }

    // ------------------------------------------------------------------ what the marketing app adds to a product
    fun extraToRecord(e: ProductExtraEntity): SyncRecord =
        SyncRecord(e.productId, e.updatedAt, fields("features" to e.features, "updatedAt" to e.updatedAt))

    fun extraFromRecord(r: SyncRecord, existing: ProductExtraEntity?): ProductExtraEntity =
        ProductExtraEntity(
            productId = r.id,
            picturePath = existing?.picturePath,
            features = r.data.text("features"),
            updatedAt = r.updatedAt,
        )

    // ------------------------------------------------------------------ the operations log
    fun logToRecord(e: OpLogEntity): SyncRecord = SyncRecord(
        e.id,
        e.at,
        fields(
            "at" to e.at, "actor" to e.actor, "action" to e.action, "subject" to e.subject, "detail" to e.detail,
            "org" to e.org, "schemaVersion" to e.schemaVersion, "updatedAt" to e.at,
        ),
    )

    fun logFromRecord(r: SyncRecord): OpLogEntity? {
        val d = r.data
        val action = J.str(d, "action")?.takeIf { it.isNotBlank() } ?: return null
        return OpLogEntity(
            id = r.id,
            at = J.long(d, "at") ?: r.updatedAt,
            actor = d.text("actor"),
            action = action,
            subject = d.text("subject"),
            detail = d.text("detail"),
            org = d.orgOrDefault(),
            schemaVersion = d.schema(),
        )
    }
}
