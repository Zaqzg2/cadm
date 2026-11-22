package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.IncomingStock
import com.cady.cadymarketing.domain.model.InventoryItem
import com.cady.cadymarketing.domain.model.InventoryLinks
import com.cady.cadymarketing.domain.model.InventorySnapshot

sealed interface InventoryDecode {
    /** [skipped] counts the lines the file held but that were not usable (no name, or the same line twice). */
    data class Ok(val snapshot: InventorySnapshot, val skipped: Int) : InventoryDecode

    data class Bad(val message: String) : InventoryDecode
}

/**
 * The inventory snapshot file ("inventory-snapshot v1", see docs/inventory-snapshot-contract.md) and the two documents
 * the app stores: the snapshot itself and the links. The reader accepts the usual alternative names of a field, so a
 * file from the inventory app does not have to match the contract letter for letter. Pure Kotlin.
 */
object InventoryJson {
    const val APP = "inventory-analyzer"
    const val FORMAT = 1
    private const val MAX_TIERS = 3
    private val dayPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun decode(text: String, nowMs: Long): InventoryDecode {
        val root = try {
            J.map(MiniJson.parse(text))
        } catch (e: MiniJson.ParseException) {
            return InventoryDecode.Bad("الملف ليس لقطة مخزون صالحة (تعذّرت قراءته).")
        }
        val app = J.str(root, "app")
        if (app != null && app != APP) return InventoryDecode.Bad("هذا الملف ليس لقطة من محلل المخزون.")
        if ((J.long(root, "format") ?: 1L) > FORMAT) return InventoryDecode.Bad("اللقطة بصيغة أحدث من تطبيقك. حدّث التطبيق أولًا.")
        val rawItems = root["items"] as? List<*> ?: return InventoryDecode.Bad("لا توجد أصناف في الملف.")
        val items = ArrayList<InventoryItem>()
        val seen = HashSet<String>()
        var skipped = 0
        for (raw in rawItems) {
            val item = item(J.map(raw))
            if (item == null || !seen.add(item.key)) {
                skipped++
                continue
            }
            items.add(item)
        }
        if (items.isEmpty()) return InventoryDecode.Bad("لا صنف صالح في الملف.")
        val createdAt = J.long(root, "createdAt")?.takeIf { it > 0L } ?: nowMs
        return InventoryDecode.Ok(InventorySnapshot(createdAt, J.str(root, "branch").orEmpty().trim(), items), skipped)
    }

    fun encode(snapshot: InventorySnapshot): String = MiniJson.write(
        linkedMapOf<String, Any?>(
            "app" to APP,
            "format" to FORMAT,
            "createdAt" to snapshot.createdAt,
            "branch" to snapshot.branch,
            "items" to snapshot.items.map { item ->
                linkedMapOf<String, Any?>(
                    "barcode" to item.barcode,
                    "itemNo" to item.itemNo,
                    "name" to item.name,
                    "category" to item.category,
                    "unit" to item.unit,
                    "onHand" to item.onHand,
                    "reorderAt" to item.reorderAt,
                    "goalMonth" to item.goalMonth,
                    "goalTiers" to item.goalTiers,
                    "achieved" to item.achieved,
                    "incoming" to item.incoming.map { linkedMapOf<String, Any?>("date" to it.date, "qty" to it.qty) },
                    "countedAt" to item.countedAt,
                )
            },
        ),
    )

    /** The stored snapshot document, or null when there is none or it cannot be read. */
    fun decodeStored(json: String?): InventorySnapshot? {
        if (json.isNullOrBlank()) return null
        return (decode(json, 0L) as? InventoryDecode.Ok)?.snapshot
    }

    fun encodeLinks(links: InventoryLinks): String = MiniJson.write(
        linkedMapOf<String, Any?>(
            "links" to links.byKey.map { (key, productId) -> linkedMapOf<String, Any?>("key" to key, "productId" to productId) },
        ),
    )

    fun decodeLinks(json: String?): InventoryLinks {
        val out = LinkedHashMap<String, String>()
        for (raw in J.list(J.parseMap(json)["links"])) {
            val row = J.map(raw)
            val key = J.str(row, "key")?.takeIf { it.isNotBlank() } ?: continue
            val productId = J.str(row, "productId")?.takeIf { it.isNotBlank() } ?: continue
            out[key] = productId
        }
        return InventoryLinks(out)
    }

    // ------------------------------------------------------------------ one line
    private fun item(raw: Map<String, Any?>): InventoryItem? {
        val name = text(raw, "name", "productName", "title").trim()
        if (name.isEmpty()) return null
        val barcode = text(raw, "barcode", "ean", "code").trim()
        val itemNo = text(raw, "itemNo", "itemNumber", "sku", "number").trim()
        return InventoryItem(
            key = InventoryRules.itemKey(barcode, itemNo, name),
            name = name,
            barcode = barcode,
            itemNo = itemNo,
            category = text(raw, "category").trim(),
            unit = text(raw, "unit").trim(),
            onHand = number(raw, "onHand", "qty", "quantity", "stock") ?: 0L,
            reorderAt = number(raw, "reorderAt", "reorderLevel", "minStock") ?: 0L,
            goalMonth = text(raw, "goalMonth", "month").trim(),
            goalTiers = tiers(raw["goalTiers"] ?: raw["goals"]),
            achieved = number(raw, "achieved", "sold", "done") ?: 0L,
            incoming = incoming(raw["incoming"] ?: raw["arrivals"]),
            countedAt = number(raw, "countedAt") ?: 0L,
        )
    }

    /** The first of [keys] that holds text, or a number written as text (a barcode saved as a number). */
    private fun text(raw: Map<String, Any?>, vararg keys: String): String {
        for (key in keys) {
            when (val value = raw[key]) {
                is String -> if (value.isNotBlank()) return value
                is Number -> return if (value.toDouble() % 1.0 == 0.0) value.toLong().toString() else value.toString()
            }
        }
        return ""
    }

    private fun number(raw: Map<String, Any?>, vararg keys: String): Long? {
        for (key in keys) {
            val value = J.double(raw, key) ?: continue
            return Math.round(value)
        }
        return null
    }

    private fun tiers(value: Any?): List<Long> =
        J.list(value).mapNotNull { entry ->
            when (entry) {
                is Number -> Math.round(entry.toDouble())
                is String -> entry.trim().toDoubleOrNull()?.let { Math.round(it) }
                else -> null
            }
        }.take(MAX_TIERS)

    private fun incoming(value: Any?): List<IncomingStock> =
        J.list(value).mapNotNull { entry ->
            val row = J.map(entry)
            val date = J.str(row, "date")?.trim()?.take(10)?.takeIf { dayPattern.matches(it) } ?: return@mapNotNull null
            val qty = number(row, "qty", "quantity") ?: return@mapNotNull null
            if (qty <= 0L) null else IncomingStock(date, qty)
        }.sortedBy { it.date }
}
