package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.OrderOrigin

/** One place orders are read from: the collection and the name of the field that holds the campaign code. */
data class OrderSpec(val origin: OrderOrigin, val collection: String, val codeField: String)

/**
 * Where the reports read orders from. The names are the website's and Kady's own, so they are set by the person:
 * the website must save the code it receives in `?c=` into [siteField] of every order, and Kady's invoices (for WhatsApp
 * orders) need a field for it before [kadyEnabled] means anything.
 */
data class ReportSources(
    val siteCollection: String = "orders",
    val siteField: String = "campaignCode",
    val kadyEnabled: Boolean = false,
    val kadyCollection: String = "invoices",
    val kadyField: String = "campaignCode",
) {
    /** The sources that are switched on and fully named. */
    fun enabled(): List<OrderSpec> {
        val list = ArrayList<OrderSpec>()
        if (siteCollection.isNotBlank() && siteField.isNotBlank()) {
            list.add(OrderSpec(OrderOrigin.SITE, siteCollection.trim(), siteField.trim()))
        }
        if (kadyEnabled && kadyCollection.isNotBlank() && kadyField.isNotBlank()) {
            list.add(OrderSpec(OrderOrigin.KADY, kadyCollection.trim(), kadyField.trim()))
        }
        return list
    }

    /** True for a name Firestore accepts as a collection or field here: no slash, no spaces, not empty. */
    companion object {
        fun isValidName(name: String): Boolean = name.isNotBlank() && name.none { it == '/' || it.isWhitespace() }
    }
}
