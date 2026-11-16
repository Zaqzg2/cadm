package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Product
import kotlin.random.Random

data class IdeasResult(val ideas: List<String>, val used: List<String>)

/**
 * Short opening ideas per angle. The original tool had five angles; this has eight, and every idea can
 * be turned into a full post (idea + price line when it makes sense + a call to order).
 */
object IdeaBank {
    val angles: List<String> get() = DefaultIdeas.byAngle.keys.toList()

    fun local(
        angle: String,
        product: Product,
        brand: Brand,
        channel: Channel,
        campaignCode: String?,
        used: List<String>,
        random: Random = Random.Default,
        count: Int = 4,
        bank: Map<String, List<String>> = DefaultIdeas.byAngle,
    ): IdeasResult {
        val templates = bank[angle] ?: bank.values.firstOrNull().orEmpty()
        val picked = PhraseMemory.fresh(templates, used, random).take(count)
        val ideas = picked.map { Placeholders.fill(it, product, brand, channel, campaignCode) }
        return IdeasResult(ideas, PhraseMemory.remember(used, picked))
    }

    /** Idea to post: the idea, then (for offer-like angles) a price line, then a call to order. */
    fun toPost(
        idea: String,
        angle: String,
        vocab: ToneVocabulary,
        product: Product,
        brand: Brand,
        channel: Channel,
        campaignCode: String?,
        random: Random = Random.Default,
        withPriceLine: Set<String> = DefaultIdeas.withPriceLine,
    ): String {
        val priceLine = if (angle in withPriceLine) {
            val lines = if (product.hasOffer) vocab.offers else vocab.prices
            lines.randomOrNull(random)?.let { "\n$it" }.orEmpty()
        } else {
            ""
        }
        val call = vocab.calls.randomOrNull(random)?.let { "\n$it" }.orEmpty()
        return Placeholders.fill(idea + priceLine + call, product, brand, channel, campaignCode)
    }
}
