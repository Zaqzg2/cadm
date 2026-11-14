package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Product

/** Fills the {placeholders} of vocabulary lines. Unknown ones stay as they are. */
object Placeholders {
    /** Kept in the text until the post is sent to a specific customer (audience, phase 4). */
    const val CUSTOMER_NAME = "{اسم العميل}"

    private val token = Regex("\\{(.+?)\\}")

    fun fill(template: String, product: Product, brand: Brand, channel: Channel, campaignCode: String?): String {
        val values = mapOf(
            "الاسم" to product.name,
            "الوحدة" to product.unit,
            "السعر" to ArabicText.formatNumber(product.effectivePrice),
            "السعر_قبل" to ArabicText.formatNumber(product.price),
            "الفرق" to ArabicText.formatNumber(product.saving),
            "الانتهاء" to product.offerEnd,
            "الرابط" to LinkBuilder.build(brand, channel, campaignCode),
            "العلامة" to brand.name,
        )
        return token.replace(template) { match -> values[match.groupValues[1]] ?: match.value }
    }
}
