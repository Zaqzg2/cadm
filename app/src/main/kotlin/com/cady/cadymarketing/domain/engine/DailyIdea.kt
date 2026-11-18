package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Product
import kotlin.random.Random

/** The idea the home screen suggests for a day: one product, one angle, one opening line. */
data class DailyIdea(val product: Product, val angle: String, val idea: String)

/**
 * Picks the idea of the day. The pick depends only on the date and on what is passed in, so the home screen and the
 * Studio (which opens on the same idea) always agree, with nothing to carry between them.
 */
object DailyIdeas {
    fun pick(date: String, products: List<Product>, bank: Map<String, List<String>>, brand: Brand): DailyIdea? {
        if (products.isEmpty() || bank.isEmpty()) return null
        val seed = CalendarMath.dayNumber(date) ?: return null
        val random = Random(seed)
        val product = products[random.nextInt(products.size)]
        val angles = bank.keys.toList()
        val angle = angles[random.nextInt(angles.size)]
        val ideas = IdeaBank.local(angle, product, brand, Channel.WHATSAPP, null, emptyList(), Random(seed), 1, bank).ideas
        val idea = ideas.firstOrNull() ?: return null
        return DailyIdea(product, angle, idea)
    }
}
