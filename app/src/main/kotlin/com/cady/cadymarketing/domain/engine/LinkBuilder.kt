package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel

/**
 * The order link of a post. WhatsApp channels open a chat with a prefilled message; the other channels
 * open the website with the campaign code and channel, so orders can be traced back to the post.
 */
object LinkBuilder {
    fun build(brand: Brand, channel: Channel, campaignCode: String?): String {
        val digits = brand.whatsapp.filter { it in '0'..'9' }
        val phone = if (digits.length == 9) "967$digits" else digits
        val code = campaignCode.orEmpty()
        return if (channel.isWhatsApp) {
            val message = "أريد الطلب" + if (code.isNotEmpty()) " (حملة $code)" else ""
            "https://wa.me/$phone?text=" + ArabicText.encodeUriComponent(message)
        } else {
            brand.siteUrl.removeSuffix("/") + "/?c=" + code.ifEmpty { "-" } + "&ch=" + channel.code
        }
    }
}
