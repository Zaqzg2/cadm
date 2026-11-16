package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Product
import kotlin.math.roundToLong

/** A product as Kady keeps it (the marketing app only reads it). */
data class KadyProduct(
    val id: String,
    val name: String,
    val unit: String,
    val price: Long,
    val active: Boolean,
)

/**
 * An offer as Kady keeps it. Exactly one of [offerPrice], [percent] or [amount] normally decides the new price;
 * the dates are "yyyy-MM-dd" or null when the offer is open on that side.
 */
data class KadyOffer(
    val id: String,
    val productId: String,
    val percent: Double?,
    val offerPrice: Long?,
    val amount: Long?,
    val startDate: String?,
    val endDate: String?,
    val active: Boolean,
)

/** One document read from Kady: either understood, or skipped with the reason (shown in the diagnostics). */
sealed interface Mapped<out T> {
    data class Ok<T>(val value: T) : Mapped<T>

    data class Skipped(val reason: String) : Mapped<Nothing>
}

/**
 * Reads Kady's `products` and `offers` documents. Kady's exact field names are not fixed by this app, so each
 * value is looked up under the names it is most likely to have; the first one found wins. To support another
 * spelling, add it to the matching list below: nothing else changes.
 */
object KadyMapper {
    val nameKeys = listOf("name", "title", "productName", "nameAr", "arabicName")
    val unitKeys = listOf("unit", "unitName", "packaging", "pack", "size")
    val priceKeys = listOf("price", "salePrice", "sellPrice", "sellingPrice", "unitPrice", "wholesalePrice")
    val activeKeys = listOf("isActive", "active", "enabled")
    val removedKeys = listOf("isDeleted", "deleted", "archived", "isArchived", "hidden")

    val offerProductKeys = listOf("productId", "product_id", "product", "itemId")
    val offerPercentKeys = listOf("percent", "percentage", "discountPercent", "discountPercentage", "pct")
    val offerPriceKeys = listOf("offerPrice", "newPrice", "salePrice", "specialPrice", "priceAfter", "price")
    val offerAmountKeys = listOf("discountAmount", "discountValue", "amountOff")
    val offerStartKeys = listOf("startDate", "start", "from", "fromDate", "startAt", "startsAt")
    val offerEndKeys = listOf("endDate", "end", "to", "toDate", "endAt", "expiresAt", "until")

    fun product(id: String, data: Map<String, Any?>): Mapped<KadyProduct> {
        val name = firstText(data, nameKeys)
            ?: return Mapped.Skipped("صنف بلا اسم ($id): لم أجد أيًّا من ${nameKeys.joinToString("/")}")
        val price = firstNumber(data, priceKeys)
            ?: return Mapped.Skipped("«$name» بلا سعر: لم أجد أيًّا من ${priceKeys.joinToString("/")}")
        val unit = firstText(data, unitKeys).orEmpty()
        return Mapped.Ok(KadyProduct(id, name, unit, price.roundToLong(), isActive(data)))
    }

    fun offer(id: String, data: Map<String, Any?>): Mapped<KadyOffer> {
        val productId = firstText(data, offerProductKeys)
            ?: return Mapped.Skipped("عرض بلا صنف ($id): لم أجد أيًّا من ${offerProductKeys.joinToString("/")}")
        val percent = firstNumber(data, offerPercentKeys)
        val offerPrice = firstNumber(data, offerPriceKeys)?.roundToLong()
        val amount = firstNumber(data, offerAmountKeys)?.roundToLong()
        if (percent == null && offerPrice == null && amount == null) {
            return Mapped.Skipped("عرض بلا نسبة ولا سعر ($id)")
        }
        return Mapped.Ok(
            KadyOffer(
                id = id,
                productId = productId,
                percent = percent,
                offerPrice = offerPrice,
                amount = amount,
                startDate = firstDate(data, offerStartKeys),
                endDate = firstDate(data, offerEndKeys),
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

    private fun firstText(data: Map<String, Any?>, keys: List<String>): String? {
        for (key in keys) {
            val value = data[key]
            val text = when (value) {
                is String -> value.trim()
                is Number -> value.toLong().toString()
                else -> null
            }
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    private fun firstNumber(data: Map<String, Any?>, keys: List<String>): Double? {
        for (key in keys) {
            val number = number(data[key])
            if (number != null) return number
        }
        return null
    }

    private fun firstDate(data: Map<String, Any?>, keys: List<String>): String? {
        for (key in keys) {
            val date = date(data[key])
            if (date != null) return date
        }
        return null
    }

    /** A number, or text such as "7,000" / "٧٠٠٠"; null for anything else. */
    fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> ArabicText.toWesternDigits(value).replace(",", "").replace(" ", "").toDoubleOrNull()
        else -> null
    }

    private val isoDate = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")
    private val slashDate = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$")

    /** "yyyy-MM-dd" from ISO text, "d/M/yyyy" text, or epoch milliseconds / seconds; null when unreadable. */
    fun date(value: Any?): String? {
        when (value) {
            is String -> {
                val text = ArabicText.toWesternDigits(value.trim())
                isoDate.find(text)?.let { return it.value }
                slashDate.find(text)?.let { m ->
                    val (d, mo, y) = m.destructured
                    return String.format(java.util.Locale.US, "%s-%02d-%02d", y, mo.toInt(), d.toInt())
                }
                return null
            }
            is Number -> {
                val raw = value.toLong()
                if (raw <= 0L) return null
                val millis = if (raw < 100_000_000_000L) raw * 1000L else raw
                return DateUtil.fromMillis(millis)
            }
            else -> return null
        }
    }
}

/** Puts the right offer on each product for a given day. */
object OfferResolver {
    /** The price an offer gives, or null when it is not a real discount (not below the normal price, or not above zero). */
    fun offerPriceOf(price: Long, offer: KadyOffer): Long? {
        val result = when {
            offer.offerPrice != null && offer.offerPrice > 0L -> offer.offerPrice
            offer.percent != null && offer.percent > 0.0 && offer.percent < 100.0 ->
                (price * (100.0 - offer.percent) / 100.0).roundToLong()
            offer.amount != null && offer.amount > 0L -> price - offer.amount
            else -> null
        }
        return result?.takeIf { it in 1 until price }
    }

    fun isRunning(offer: KadyOffer, today: String): Boolean =
        offer.active &&
            (offer.startDate == null || offer.startDate <= today) &&
            (offer.endDate == null || offer.endDate >= today)

    /** The best (lowest-price) running offer of a product, as the price and the end date ("" when open-ended). */
    fun best(product: KadyProduct, offers: List<KadyOffer>, today: String): Pair<Long, String>? {
        var best: Pair<Long, String>? = null
        for (offer in offers) {
            if (offer.productId != product.id || !isRunning(offer, today)) continue
            val price = offerPriceOf(product.price, offer) ?: continue
            if (best == null || price < best.first) best = price to offer.endDate.orEmpty()
        }
        return best
    }

    /** The marketing view of a catalogue item: the running offer, the marketing features and the linked picture. */
    fun toProduct(
        product: KadyProduct,
        offers: List<KadyOffer>,
        today: String,
        features: List<String>,
        picturePath: String?,
    ): Product {
        val offer = best(product, offers, today)
        return Product(
            id = product.id,
            name = product.name,
            unit = product.unit,
            price = product.price,
            offerPrice = offer?.first,
            offerEnd = offer?.second.orEmpty(),
            features = features,
            picturePath = picturePath,
        )
    }
}
