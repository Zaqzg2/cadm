package com.cady.cadymarketing.domain.engine

/** A whole backup: every synced table as it was on the device at [createdAt]. */
data class BackupFile(
    val createdAt: Long,
    val appVersion: String,
    val tables: Map<String, List<SyncRecord>>,
) {
    val total: Int get() = tables.values.sumOf { it.size }
}

sealed interface BackupDecode {
    /** [skipped] counts the rows the file held but that were not readable (they are left out). */
    data class Ok(val file: BackupFile, val skipped: Int) : BackupDecode

    data class Bad(val message: String) : BackupDecode
}

/** The backup file: one JSON document, readable by hand. Pictures are not part of it (each device keeps its own). Pure Kotlin. */
object BackupJson {
    const val APP = "cady-marketing"
    const val FORMAT = 1

    fun encode(file: BackupFile): String {
        val tables = LinkedHashMap<String, Any?>()
        for ((table, rows) in file.tables) {
            tables[table] = rows.map { row ->
                linkedMapOf<String, Any?>("id" to row.id, "updatedAt" to row.updatedAt, "data" to row.data)
            }
        }
        return MiniJson.write(
            linkedMapOf<String, Any?>(
                "app" to APP,
                "format" to FORMAT,
                "createdAt" to file.createdAt,
                "appVersion" to file.appVersion,
                "tables" to tables,
            ),
        )
    }

    fun decode(text: String): BackupDecode {
        val root = try {
            MiniJson.parse(text)
        } catch (e: MiniJson.ParseException) {
            return BackupDecode.Bad("الملف ليس نسخة احتياطية صالحة (تعذّرت قراءته).")
        }
        val map = J.map(root)
        if (J.str(map, "app") != APP) return BackupDecode.Bad("هذا الملف ليس نسخة احتياطية من كادي للتسويق.")
        val format = J.long(map, "format") ?: 0L
        if (format < 1L) return BackupDecode.Bad("نسخة احتياطية غير مفهومة.")
        if (format > FORMAT) return BackupDecode.Bad("النسخة أحدث من تطبيقك. حدّث التطبيق أولًا ثم أعد الاستعادة.")
        val tables = LinkedHashMap<String, List<SyncRecord>>()
        var skipped = 0
        for ((table, value) in J.map(map["tables"])) {
            val rows = ArrayList<SyncRecord>()
            for (item in J.list(value)) {
                val row = J.map(item)
                val id = J.str(row, "id")?.takeIf { it.isNotBlank() }
                val updatedAt = J.long(row, "updatedAt")
                if (id == null || updatedAt == null || row["data"] !is Map<*, *>) {
                    skipped++
                    continue
                }
                rows.add(SyncRecord(id, updatedAt, J.map(row["data"])))
            }
            tables[table] = rows
        }
        return BackupDecode.Ok(
            BackupFile(
                createdAt = J.long(map, "createdAt") ?: 0L,
                appVersion = J.str(map, "appVersion").orEmpty(),
                tables = tables,
            ),
            skipped,
        )
    }
}

/** What restoring a table of a backup would do. */
data class RestorePlan(val apply: List<SyncRecord>, val added: Int, val updated: Int, val kept: Int)

/** Restoring never deletes and never overwrites a newer row: the same rule as the sync, so a backup is safe to restore twice. */
object BackupMerge {
    fun plan(local: List<SyncRecord>, incoming: List<SyncRecord>, numbered: Boolean): RestorePlan {
        val byId = local.associateBy { it.id }
        val apply = ArrayList<SyncRecord>()
        var added = 0
        var updated = 0
        var kept = 0
        for (row in incoming) {
            val mine = byId[row.id]
            when (SyncMerge.decide(mine, row, numbered)) {
                MergeAction.TAKE_REMOTE -> {
                    apply.add(row)
                    if (mine == null) added++ else updated++
                }
                MergeAction.ADOPT_NUMBER -> {
                    apply.add(row)
                    updated++
                }
                MergeAction.KEEP_LOCAL, MergeAction.SAME -> kept++
            }
        }
        return RestorePlan(apply, added, updated, kept)
    }
}

/** Kady's catalogue as one stored document, so a marketer (who may not read Kady's collections) gets the products from the manager. */
object CatalogMirror {
    const val DOC_KEY = "catalog"

    fun encode(products: List<KadyProduct>, offers: List<KadyOffer>): String = MiniJson.write(
        linkedMapOf<String, Any?>(
            "products" to products.map {
                linkedMapOf<String, Any?>(
                    "id" to it.id, "name" to it.name, "unit" to it.unit, "price" to it.price, "active" to it.active,
                )
            },
            "offers" to offers.map {
                linkedMapOf<String, Any?>(
                    "id" to it.id,
                    "productId" to it.productId,
                    "percent" to it.percent,
                    "offerPrice" to it.offerPrice,
                    "amount" to it.amount,
                    "startDate" to it.startDate,
                    "endDate" to it.endDate,
                    "active" to it.active,
                )
            },
        ),
    )

    /** The catalogue from its document, or null when the text is not one (a catalogue with no products is also null). */
    fun decode(json: String?): Pair<List<KadyProduct>, List<KadyOffer>>? {
        val map = J.parseMap(json)
        val products = J.list(map["products"]).mapNotNull { item ->
            val row = J.map(item)
            val id = J.str(row, "id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            KadyProduct(
                id = id,
                name = J.str(row, "name").orEmpty(),
                unit = J.str(row, "unit").orEmpty(),
                price = J.long(row, "price") ?: 0L,
                active = J.bool(row, "active") ?: true,
            )
        }
        if (products.isEmpty()) return null
        val offers = J.list(map["offers"]).mapNotNull { item ->
            val row = J.map(item)
            val id = J.str(row, "id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val productId = J.str(row, "productId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            KadyOffer(
                id = id,
                productId = productId,
                percent = J.double(row, "percent"),
                offerPrice = J.long(row, "offerPrice"),
                amount = J.long(row, "amount"),
                startDate = J.str(row, "startDate")?.takeIf { it.isNotBlank() },
                endDate = J.str(row, "endDate")?.takeIf { it.isNotBlank() },
                active = J.bool(row, "active") ?: true,
            )
        }
        return products to offers
    }
}
