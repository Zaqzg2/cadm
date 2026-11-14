package com.cady.cadymarketing.core.poster

import android.graphics.Color
import com.cady.cadymarketing.domain.engine.ArabicText

/**
 * A poster design. Adding one = writing one object and adding it to [PosterDesigns.all]; nothing else
 * changes. The ids are stored with saved content, so an id is never reused.
 * Ids 0-4 are the five designs of the original tool; 5 and 6 are new.
 */
interface PosterDesign {
    val id: Int
    val label: String

    /** True when the design shows the picture chosen for the post. */
    val usesPicture: Boolean

    fun draw(p: PosterPainter)
}

object PosterDesigns {
    val all: List<PosterDesign> = listOf(
        GradientDesign,
        CleanDesign,
        FullPhotoDesign,
        OfferBadgeDesign,
        SplitDesign,
        FeaturesDesign,
        UrgentDesign,
    )

    fun byId(id: Int): PosterDesign = all.firstOrNull { it.id == id } ?: GradientDesign
}

/** 0: brand gradient, white price panel. */
object GradientDesign : PosterDesign {
    override val id = 0
    override val label = "تدرّج"
    override val usesPicture = false

    override fun draw(p: PosterPainter) {
        with(p) {
            fillGradient(0f, h, primary, dark)
            if (hasLogo()) logo(h * .12f, w * .5f, h * .12f, Color.WHITE) else text(brand.name, h * .12f, .09f, true, Color.WHITE)
            wrappedText(product.name, h * .28f, w * .84f, w * .09f, .07f, true, Color.WHITE)
            text(product.unit, h * .5f, .04f, false, Color.WHITE)
            fillRoundRect(w * .1f, h * .56f, w * .9f, h * .77f, 36f * u, Color.WHITE)
            text(priceLabel, h * .66f, .12f, true, primary)
            if (product.hasOffer) {
                text("بدل " + ArabicText.formatNumber(product.price) + " ريال", h * .72f, .04f, false, PosterColors.GRAY)
            }
            text(phoneLine, h * .9f, .05f, true, Color.WHITE)
        }
    }
}

/** 1: clean white, picture in a rounded frame, price pill. */
object CleanDesign : PosterDesign {
    override val id = 1
    override val label = "ناصع"
    override val usesPicture = true

    override fun draw(p: PosterPainter) {
        with(p) {
            fillBackground(Color.WHITE)
            fillRect(0f, 0f, w, h * .025f, primary)
            logo(h * .1f, w * .42f, h * .09f, primary)
            val frameRadius = 44f * u
            withRoundClip(w * .1f, h * .19f, w * .9f, h * .52f, frameRadius) {
                val pic = picture
                if (pic != null) {
                    coverBitmap(pic, w * .1f, h * .19f, w * .9f, h * .52f)
                } else {
                    fillRect(w * .1f, h * .19f, w * .9f, h * .52f, withAlpha(primary, 0x22))
                }
            }
            productName(h * .6f, PosterColors.INK, .065f)
            text(product.unit, h * .72f, .038f, false, PosterColors.MUTED)
            fillRoundRect(w * .14f, h * .755f, w * .86f, h * .86f, 60f * u, primary)
            text(priceLabel, h * .83f, .08f, true, Color.WHITE)
            oldPrice(h * .9f, PosterColors.MUTED)
            text(phoneLine, h * .955f, .042f, true, primary)
        }
    }
}

/** 2: the picture fills the poster, text over a dark fade. */
object FullPhotoDesign : PosterDesign {
    override val id = 2
    override val label = "صورة كاملة"
    override val usesPicture = true

    override fun draw(p: PosterPainter) {
        with(p) {
            val pic = picture
            if (pic != null) coverBitmap(pic, 0f, 0f, w, h) else fillGradient(0f, h, primary, dark)
            fillGradient(h * .38f, h, PosterColors.CLEAR, PosterColors.SHADE_BOTTOM)
            fillGradient(0f, h * .2f, PosterColors.SHADE_TOP, PosterColors.CLEAR)
            logo(h * .08f, w * .42f, h * .08f, Color.WHITE)
            productName(h * .66f, Color.WHITE, .07f)
            text(product.unit, h * .77f, .038f, false, PosterColors.WHITE_80)
            fillRoundRect(w * .2f, h * .8f, w * .8f, h * .895f, 60f * u, accent)
            text(priceLabel, h * .867f, .075f, true, PosterColors.ON_ACCENT)
            oldPrice(h * .925f, PosterColors.WHITE_80)
            text(phoneLine, h * .97f, .04f, true, Color.WHITE)
        }
    }
}

/** 3: gradient with stripes, round picture, "save" badge. */
object OfferBadgeDesign : PosterDesign {
    override val id = 3
    override val label = "شارة عرض"
    override val usesPicture = true

    override fun draw(p: PosterPainter) {
        with(p) {
            fillGradient(0f, h, primary, dark)
            val stripe = Color.argb(20, 255, 255, 255)
            var x = -h
            while (x < w) {
                fillQuad(x, 0f, x + w * .07f, 0f, x + w * .07f + h * .4f, h, x + h * .4f, h, stripe)
                x += w * .14f
            }
            logo(h * .08f, w * .42f, h * .08f, Color.WHITE)
            val pic = picture
            if (pic != null) {
                val radius = w * .27f
                withCircleClip(m, h * .36f, radius) { coverBitmap(pic, m - radius, h * .36f - radius, m + radius, h * .36f + radius) }
                strokeCircle(m, h * .36f, radius, w * .01f, Color.WHITE)
            }
            if (product.hasOffer) {
                fillCircle(w * .82f, h * .25f, w * .13f, accent)
                text("وفّر", h * .24f, .04f, true, PosterColors.ON_ACCENT)
                text(ArabicText.formatNumber(product.saving), h * .285f, .06f, true, PosterColors.ON_ACCENT)
            }
            productName(h * .68f, Color.WHITE, .065f)
            text(product.unit, h * .78f, .038f, false, PosterColors.WHITE_80)
            text(priceLabel, h * .87f, .1f, true, accent)
            oldPrice(h * .915f, PosterColors.WHITE_80)
            text(phoneLine, h * .965f, .04f, true, Color.WHITE)
        }
    }
}

/** 4: picture on top, white label with the logo, price below, order bar. */
object SplitDesign : PosterDesign {
    override val id = 4
    override val label = "تقسيم"
    override val usesPicture = true

    override fun draw(p: PosterPainter) {
        with(p) {
            fillBackground(Color.WHITE)
            val pic = picture
            if (pic != null) {
                coverBitmap(pic, 0f, 0f, w, h * .5f)
            } else {
                fillGradient(0f, h * .5f, primary, dark)
                productName(h * .22f, Color.WHITE, .075f)
            }
            fillRoundRect(w * .26f, h * .455f, w * .74f, h * .545f, 50f * u, Color.WHITE)
            logo(h * .5f, w * .4f, h * .065f, primary)
            if (pic != null) productName(h * .62f, PosterColors.INK, .058f)
            text(product.unit, h * .7f, .038f, false, PosterColors.MUTED)
            text(priceLabel, h * .8f, .11f, true, primary)
            oldPrice(h * .85f, PosterColors.MUTED)
            fillRect(0f, h * .91f, w, h, primary)
            text(phoneLine, h * .965f, .045f, true, Color.WHITE)
        }
    }
}

/** 5 (new): the product's own selling facts in a white card, with check marks. */
object FeaturesDesign : PosterDesign {
    override val id = 5
    override val label = "المزايا"
    override val usesPicture = false

    override fun draw(p: PosterPainter) {
        with(p) {
            fillGradient(0f, h, primary, dark)
            logo(h * .08f, w * .42f, h * .08f, Color.WHITE)
            productName(h * .2f, Color.WHITE, .065f)

            val cardRight = w * .92f
            val cardTop = h * .3f
            val cardBottom = h * .7f
            fillRoundRect(w * .08f, cardTop, cardRight, cardBottom, 40f * u, Color.WHITE)

            val facts = product.features.filter { it.isNotBlank() }.take(4)
            if (facts.isEmpty()) {
                wrappedText(product.unit, cardTop + (cardBottom - cardTop) * .5f, w * .7f, w * .06f, .05f, true, PosterColors.INK)
            } else {
                val lineHeight = w * .056f
                var y = cardTop + h * .065f
                for (fact in facts) {
                    if (y > cardBottom - h * .03f) break
                    checkBullet(cardRight - w * .07f, y - w * .012f, w * .022f, PosterColors.GREEN)
                    val lines = wrappedRight(fact, cardRight - w * .13f, y, w * .62f, lineHeight, .04f, PosterColors.INK)
                    y += lines * lineHeight + h * .035f
                }
            }

            fillRoundRect(w * .18f, h * .74f, w * .82f, h * .84f, 60f * u, accent)
            text(priceLabel, h * .805f, .075f, true, PosterColors.ON_ACCENT)
            oldPrice(h * .88f, PosterColors.WHITE_80)
            text(phoneLine, h * .95f, .04f, true, Color.WHITE)
        }
    }
}

/** 6 (new): urgency banner with the last date of the offer, big price. */
object UrgentDesign : PosterDesign {
    override val id = 6
    override val label = "عاجل"
    override val usesPicture = true

    override fun draw(p: PosterPainter) {
        with(p) {
            fillGradient(0f, h, dark, primary)

            fillRect(0f, h * .05f, w, h * .155f, accent)
            val headline = when {
                product.hasOffer && product.offerEnd.isNotEmpty() -> "عرض لفترة محدودة"
                product.hasOffer -> "عرض خاص"
                else -> "اطلب الآن"
            }
            if (product.offerEnd.isNotEmpty()) {
                text(headline, h * .1f, .055f, true, PosterColors.ON_ACCENT)
                text("آخر موعد: " + product.offerEnd, h * .14f, .036f, false, PosterColors.ON_ACCENT)
            } else {
                text(headline, h * .12f, .06f, true, PosterColors.ON_ACCENT)
            }

            logo(h * .235f, w * .42f, h * .07f, Color.WHITE)

            val pic = picture
            if (pic != null) {
                withRoundClip(w * .12f, h * .29f, w * .88f, h * .52f, 44f * u) {
                    coverBitmap(pic, w * .12f, h * .29f, w * .88f, h * .52f)
                }
                productName(h * .585f, Color.WHITE, .06f)
                text(product.unit, h * .69f, .036f, false, PosterColors.WHITE_80)
            } else {
                productName(h * .43f, Color.WHITE, .075f)
                text(product.unit, h * .58f, .04f, false, PosterColors.WHITE_80)
            }

            text(priceLabel, h * .8f, .11f, true, accent)
            oldPrice(h * .85f, PosterColors.WHITE_80)
            text(phoneLine, h * .95f, .04f, true, Color.WHITE)
        }
    }
}

/** The same colour with another alpha (0-255). */
private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
