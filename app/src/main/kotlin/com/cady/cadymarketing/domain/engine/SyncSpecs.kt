package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.MktRole

/**
 * The collections the marketing app keeps in Firestore, all under the `mkt_` prefix so they never meet Kady's own.
 * Kady's collections (products, offers, customers, orders) are only ever read, never written.
 */
object SyncSpecs {
    const val CONTENT = "mkt_content"
    const val CAMPAIGNS = "mkt_campaigns"
    const val OCCASIONS = "mkt_occasions"
    const val CONSENT = "mkt_consent"
    const val SEGMENTS = "mkt_segments"
    const val SEND_MARKS = "mkt_sendmarks"
    const val RESULTS = "mkt_results"
    const val DOCS = "mkt_docs"
    const val PRODUCT_EXTRA = "mkt_product_extra"
    const val LOG = "mkt_log"

    /** Not synced like the others: the counters of the running numbers, and the marketing team. */
    const val COUNTERS = "mkt_counters"
    const val TEAM = "mkt_team"

    val contentNumber = NumberSpec("CNT", "content")
    val campaignNumber = NumberSpec("CMP", "campaign")

    /**
     * What the marketer does with each collection: she reads and writes posts (only drafts and posts waiting for approval),
     * reads the campaigns, occasions, product extras and documents (brand, vocabulary, catalogue), writes the log, and
     * never touches the customer side (consent, segments, send lists, results).
     */
    val all: List<SyncTableSpec> = listOf(
        SyncTableSpec(DOCS),
        SyncTableSpec(PRODUCT_EXTRA),
        SyncTableSpec(CAMPAIGNS, number = campaignNumber),
        SyncTableSpec(OCCASIONS),
        SyncTableSpec(
            CONTENT,
            number = contentNumber,
            marketerWrites = true,
            marketerMayPush = { row -> ContentFlow.canPush(MktRole.MARKETER, ContentStatus.fromCode(row.data["status"] as? String)) },
        ),
        SyncTableSpec(CONSENT, marketerReads = false),
        SyncTableSpec(SEGMENTS, marketerReads = false),
        SyncTableSpec(SEND_MARKS, marketerReads = false),
        SyncTableSpec(RESULTS, marketerReads = false),
        SyncTableSpec(LOG, marketerReads = false, marketerWrites = true),
    )

    /** The tables a backup holds: everything but the log (a record of what was done, not content). */
    val backedUp: List<String> = all.map { it.table }.filter { it != LOG }

    fun spec(table: String): SyncTableSpec? = all.firstOrNull { it.table == table }
}
