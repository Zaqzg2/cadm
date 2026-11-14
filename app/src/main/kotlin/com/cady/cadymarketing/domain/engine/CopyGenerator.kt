package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Formula
import com.cady.cadymarketing.domain.model.Product
import kotlin.random.Random

data class GenerationRequest(
    val product: Product,
    val brand: Brand,
    val channel: Channel,
    val tone: String,
    val campaignCode: String? = null,
    val bank: Map<String, ToneVocabulary> = DefaultVocabulary.tones,
    val used: List<String> = emptyList(),
)

data class GenerationResult(val formulas: List<Formula>, val used: List<String>)

/**
 * Builds the text formulas of a post from the vocabulary bank.
 *
 * Compared with the original tool: five different SHAPES (not one fixed order), channel-aware
 * (short for status and TikTok, hashtags for Instagram and TikTok), questions and urgency lines,
 * and a memory of the phrases already used so nothing repeats until the bank is exhausted.
 */
object CopyGenerator {
    private enum class Shape(val label: String) {
        CLASSIC("كلاسيكية"),
        PRICE_FIRST("السعر أولًا"),
        QUESTION("سؤال"),
        URGENT("عاجلة"),
        COMPACT("مختصرة"),
    }

    private class Composed(val text: String, val raw: List<String>)

    /** The plain "direct" formula: the facts in a fixed layout, identical to the original tool. */
    fun direct(product: Product, brand: Brand, channel: Channel, campaignCode: String?): Formula {
        val link = LinkBuilder.build(brand, channel, campaignCode)
        val price = if (product.hasOffer) {
            ArabicText.formatNumber(product.effectivePrice) + " ريال بدل " + ArabicText.formatNumber(product.price)
        } else {
            ArabicText.formatNumber(product.price) + " ريال"
        }
        val end = if (product.offerEnd.isNotEmpty()) "\nينتهي العرض: " + product.offerEnd else ""
        val text = brand.name + " | " + product.name + "\n" + product.unit + "\nالسعر: " + price + end + "\nللطلب: " + link
        return Formula("مباشر", text)
    }

    /** A built-in template filled with the product, as the original tool did with its saved templates. */
    fun fromTemplate(template: TextTemplate, product: Product, brand: Brand, channel: Channel, campaignCode: String?): Formula =
        Formula(template.name, Placeholders.fill(template.text, product, brand, channel, campaignCode))

    fun generate(request: GenerationRequest, random: Random = Random.Default): GenerationResult {
        val product = request.product
        val brand = request.brand
        val channel = request.channel
        val vocab = request.bank[request.tone]
            ?: request.bank.values.firstOrNull()
            ?: ToneVocabulary(emptyList(), emptyList(), emptyList(), emptyList())
        val used = request.used

        val hooks = PhraseMemory.fresh(vocab.hooks, used, random)
        val priceLines = PhraseMemory.fresh(if (product.hasOffer) vocab.offers else vocab.prices, used, random)
        val calls = PhraseMemory.fresh(vocab.calls, used, random)
        val questions = PhraseMemory.fresh(vocab.questions.ifEmpty { vocab.hooks }, used, random)
        val urgency = PhraseMemory.fresh(vocab.urgency, used, random)
        val features = product.features
            .filter { ClaimChecker.isSafeSnippet(it, product, brand) }
            .shuffled(random)
        val hasEnd = product.offerEnd.isNotEmpty()

        fun compose(shape: Shape, n: Int): Composed {
            val hook = hooks.at(n)
            val price = priceLines.at(n)
            val call = calls.at(n)
            val question = questions.at(n)
            val endLine = if (hasEnd) urgency.at(n) else null
            val feature = features.at(n)
            val lines: List<String?> = when (shape) {
                Shape.CLASSIC -> listOf(hook, feature, price, endLine, call)
                Shape.PRICE_FIRST -> listOf(price, hook, endLine, call)
                Shape.QUESTION -> listOf(question, feature, price, endLine, call)
                Shape.URGENT -> listOf(hook, price, endLine, call)
                Shape.COMPACT -> listOf(joinCompact(hook, price), call)
            }
            val filled = lines.filterNotNull().filter { it.isNotBlank() }
            val text = Placeholders.fill(filled.joinToString("\n"), product, brand, channel, request.campaignCode)
            val raw = listOfNotNull(hook, price, call, endLine).plus(if (shape == Shape.QUESTION) listOfNotNull(question) else emptyList())
            return Composed(withHashtags(text, brand, channel), raw)
        }

        val order = when (channel) {
            Channel.WHATSAPP_STATUS -> listOf(Shape.COMPACT, Shape.PRICE_FIRST, Shape.QUESTION, Shape.URGENT, Shape.CLASSIC)
            Channel.TIKTOK -> listOf(Shape.COMPACT, Shape.QUESTION, Shape.PRICE_FIRST, Shape.URGENT, Shape.CLASSIC)
            else -> listOf(Shape.CLASSIC, Shape.PRICE_FIRST, Shape.QUESTION, Shape.URGENT, Shape.COMPACT)
        }

        val formulas = ArrayList<Formula>()
        val seen = HashSet<String>()
        val usedNow = ArrayList<String>()
        order.forEachIndexed { index, shape ->
            var composed = compose(shape, index)
            var attempt = 1
            while (!seen.add(composed.text) && attempt <= 6) {
                composed = compose(shape, index + attempt)
                attempt++
            }
            val label = if (shape == Shape.URGENT && !hasEnd) "مباشرة" else shape.label
            formulas.add(Formula(label, composed.text))
            usedNow.addAll(composed.raw)
        }
        return GenerationResult(formulas, PhraseMemory.remember(used, usedNow))
    }

    private fun joinCompact(hook: String?, price: String?): String? = when {
        hook != null && price != null -> "$hook — $price"
        hook != null -> hook
        else -> price
    }

    private fun withHashtags(text: String, brand: Brand, channel: Channel): String {
        val wantsTags = channel == Channel.INSTAGRAM || channel == Channel.TIKTOK
        val tags = brand.hashtags.take(3)
        return if (wantsTags && tags.isNotEmpty()) text + "\n" + tags.joinToString(" ") else text
    }

    /** Cycles through a list; null when it is empty. */
    private fun <T> List<T>.at(index: Int): T? = if (isEmpty()) null else this[index % size]
}
