package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.AttributedOrder
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.OrderOrigin
import kotlin.math.roundToLong

/**
 * Reads an order (from the website, or an invoice from Kady) that carries a campaign code. The field that holds the code
 * is chosen by the person (it is the website's / Kady's own name for it); the rest is looked up under the names it most
 * likely has, like [KadyMapper] does.
 */
object OrderMapper {
    val amountKeys = listOf("total", "grandTotal", "totalAmount", "netTotal", "totalPrice", "amount", "sum")
    val dateKeys = listOf("createdAt", "date", "orderDate", "timestamp", "created", "time")
    val channelKeys = listOf("ch", "channel", "source", "utm_source", "platform")
    val statusKeys = listOf("status", "state", "orderStatus")
    private val cancelledWords = listOf("cancel", "reject", "refund", "void", "ملغ", "مرفوض", "مسترد", "مرتجع")

    private val channelAliases: Map<String, Channel> = mapOf(
        "whatsapp" to Channel.WHATSAPP, "واتساب" to Channel.WHATSAPP, "واتس اب" to Channel.WHATSAPP,
        "status" to Channel.WHATSAPP_STATUS, "حالة واتساب" to Channel.WHATSAPP_STATUS,
        "facebook" to Channel.FACEBOOK, "فيسبوك" to Channel.FACEBOOK, "فيس بوك" to Channel.FACEBOOK,
        "instagram" to Channel.INSTAGRAM, "انستغرام" to Channel.INSTAGRAM, "إنستغرام" to Channel.INSTAGRAM,
        "tiktok" to Channel.TIKTOK, "تيك توك" to Channel.TIKTOK,
    )

    /** A campaign code as it is compared: letters and digits only, upper case ("k4f2a " and "K4F2A" are the same code). */
    fun code(value: Any?): String? {
        val text = when (value) {
            is String -> value
            is Number -> value.toLong().toString()
            else -> return null
        }
        return text.filter { it.isLetterOrDigit() }.uppercase().takeIf { it.isNotEmpty() }
    }

    fun channelOf(value: Any?): Channel? {
        val text = (value as? String)?.trim()?.lowercase() ?: return null
        return Channel.entries.firstOrNull { it.code == text } ?: channelAliases[text]
    }

    fun map(origin: OrderOrigin, docId: String, data: Map<String, Any?>, codeField: String): Mapped<AttributedOrder> {
        val code = code(data[codeField])
            ?: return Mapped.Skipped("طلب بلا رمز حملة ($docId): الحقل «$codeField» فارغ أو غير موجود")
        var channel: Channel? = null
        for (key in channelKeys) {
            channel = channelOf(data[key])
            if (channel != null) break
        }
        var amount: Long? = null
        for (key in amountKeys) {
            val number = KadyMapper.number(data[key])
            if (number != null && number >= 0.0) {
                amount = number.roundToLong()
                break
            }
        }
        var date: String? = null
        for (key in dateKeys) {
            date = KadyMapper.date(data[key])
            if (date != null) break
        }
        return Mapped.Ok(
            AttributedOrder(
                id = origin.code + ":" + docId,
                origin = origin,
                campaignCode = code,
                channel = channel,
                amount = amount,
                date = date,
                cancelled = isCancelled(data),
            ),
        )
    }

    private fun isCancelled(data: Map<String, Any?>): Boolean {
        for (key in statusKeys) {
            val status = (data[key] as? String)?.trim()?.lowercase() ?: continue
            if (cancelledWords.any { status.contains(it) }) return true
        }
        return false
    }
}
