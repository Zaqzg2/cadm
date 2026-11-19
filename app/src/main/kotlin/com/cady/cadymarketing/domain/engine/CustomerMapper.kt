package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Customer

/**
 * Reads Kady's `customers` documents. Like [KadyMapper], it looks each value up under the names it most likely has;
 * the first one found wins, and a name that is missing from the list is a one-line addition here.
 */
object CustomerMapper {
    val nameKeys = listOf("name", "customerName", "fullName", "title", "nameAr", "displayName")
    val phoneKeys = listOf("phone", "mobile", "phoneNumber", "whatsapp", "tel", "mobileNumber", "contactNumber")
    val areaKeys = listOf("area", "city", "region", "district", "zone", "address", "location")
    val repKeys = listOf("repName", "ownerName", "rep", "salesRep", "assignedRepName", "createdByName")
    private val activeKeys = listOf("isActive", "active", "enabled")
    private val removedKeys = listOf("isDeleted", "deleted", "archived", "isArchived", "hidden")

    fun customer(id: String, data: Map<String, Any?>): Mapped<Customer> {
        val name = text(data, nameKeys)
            ?: return Mapped.Skipped("عميل بلا اسم ($id): لم أجد أيًّا من ${nameKeys.joinToString("/")}")
        val raw = text(data, phoneKeys).orEmpty()
        return Mapped.Ok(
            Customer(
                id = id,
                name = name,
                phone = PhoneRules.normalize(raw),
                rawPhone = raw,
                area = text(data, areaKeys).orEmpty(),
                repName = text(data, repKeys).orEmpty(),
                active = isActive(data),
            ),
        )
    }

    private fun isActive(data: Map<String, Any?>): Boolean {
        for (key in removedKeys) if (J.bool(data, key) == true) return false
        for (key in activeKeys) {
            val flag = J.bool(data, key)
            if (flag != null) return flag
        }
        return true
    }

    private fun text(data: Map<String, Any?>, keys: List<String>): String? {
        for (key in keys) {
            val text = when (val value = data[key]) {
                is String -> value.trim()
                is Number -> value.toLong().toString()
                else -> null
            }
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }
}
