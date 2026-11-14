package com.cady.cadymarketing.domain.model

/**
 * A catalogue item as the marketing tools need it. Prices are whole riyals.
 * [offerPrice] is null (or 0) when there is no offer; [offerEnd] is "yyyy-MM-dd" or empty.
 */
data class Product(
    val id: String,
    val name: String,
    val unit: String,
    val price: Long,
    val offerPrice: Long? = null,
    val offerEnd: String = "",
    val features: List<String> = emptyList(),
) {
    val hasOffer: Boolean get() = (offerPrice ?: 0L) > 0L

    /** The price a customer pays: the offer price when there is an offer. */
    val effectivePrice: Long get() = if (hasOffer) (offerPrice ?: 0L) else price

    val saving: Long get() = if (hasOffer) price - (offerPrice ?: 0L) else 0L
}
