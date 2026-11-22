package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.InventoryItem
import com.cady.cadymarketing.domain.model.InventoryLinks
import com.cady.cadymarketing.domain.model.InventorySnapshot
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.StockInfo

/** One line of the inventory with where its link stands. */
data class ItemLinkState(
    val item: InventoryItem,
    /** The Kady product the manager confirmed, or null. */
    val productId: String?,
    /** An exact-name match waiting for the manager to confirm, or null. */
    val suggestedProductId: String?,
)

/**
 * Tying an inventory line to a Kady product. Only a link the manager confirmed is ever used. The app may suggest one,
 * and only when the names are the same after normalising and no other line or product could be meant (the inventory app's
 * own rule: never merge by similarity alone). Pure Kotlin.
 */
object InventoryLinking {
    /** The confirmed link of each product still in the catalogue, and the suggestions for the lines without one. */
    fun states(snapshot: InventorySnapshot, links: InventoryLinks, products: List<Product>): List<ItemLinkState> {
        val known = products.map { it.id }.toSet()
        val suggestions = suggestions(snapshot.items, products, links)
        return snapshot.items.map { item ->
            val linked = links.byKey[item.key]?.takeIf { it in known }
            ItemLinkState(item, linked, if (linked == null) suggestions[item.key] else null)
        }
    }

    /** line key to product id, for exact normalised-name matches that are unambiguous on both sides. */
    fun suggestions(items: List<InventoryItem>, products: List<Product>, links: InventoryLinks): Map<String, String> {
        val taken = links.byKey.values.toSet()
        val productsByName = products.groupBy { InventoryRules.normalizeName(it.name) }
        val itemsByName = items.filter { it.key !in links.byKey }.groupBy { InventoryRules.normalizeName(it.name) }
        val out = LinkedHashMap<String, String>()
        for ((name, group) in itemsByName) {
            if (name.isEmpty() || group.size != 1) continue
            val candidates = productsByName[name].orEmpty().filter { it.id !in taken }
            if (candidates.size == 1) out[group[0].key] = candidates[0].id
        }
        return out
    }

    /** What is known about each product that has a confirmed link to a line of the snapshot. */
    fun byProduct(snapshot: InventorySnapshot, links: InventoryLinks, today: String): Map<String, StockInfo> {
        val byKey = snapshot.items.associateBy { it.key }
        val out = LinkedHashMap<String, StockInfo>()
        for ((key, productId) in links.byKey) {
            val item = byKey[key] ?: continue
            if (productId !in out) out[productId] = InventoryRules.info(item, today)
        }
        return out
    }

    fun withLink(links: InventoryLinks, key: String, productId: String): InventoryLinks {
        // A product belongs to one line: linking it again moves it.
        val kept = links.byKey.filterValues { it != productId }
        return InventoryLinks(LinkedHashMap(kept).also { it[key] = productId })
    }

    fun withoutLink(links: InventoryLinks, key: String): InventoryLinks = InventoryLinks(links.byKey.filterKeys { it != key })

    fun withLinks(links: InventoryLinks, add: Map<String, String>): InventoryLinks =
        add.entries.fold(links) { acc, (key, productId) -> withLink(acc, key, productId) }
}
