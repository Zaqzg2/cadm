package com.cady.cadymarketing.domain.model

/** One line of the products screen: the marketing view of a catalogue item, and whether Kady still sells it. */
data class ProductRow(
    val product: Product,
    val active: Boolean,
)
