package com.cady.cadymarketing.core.poster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.cady.cadymarketing.domain.engine.ArabicText
import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Product
import kotlin.math.max
import kotlin.math.min

/** Everything a poster is drawn from. */
class PosterSpec(
    val product: Product,
    val brand: Brand,
    val designId: Int,
    val picture: Bitmap?,
    val logo: Bitmap?,
)

object PosterColors {
    val INK: Int = Color.parseColor("#14212b")
    val MUTED: Int = Color.parseColor("#5d7080")
    val GRAY: Int = Color.parseColor("#555555")
    val ON_ACCENT: Int = Color.parseColor("#1a1a1a")
    val GREEN: Int = Color.parseColor("#1B8A5A")
    val WHITE_80: Int = Color.argb(0xCC, 255, 255, 255)
    val CLEAR: Int = Color.argb(0, 0, 0, 0)
    val SHADE_TOP: Int = Color.argb(0x88, 0, 0, 0)
    val SHADE_BOTTOM: Int = Color.argb(0xDD, 0, 0, 0)
}

/**
 * The drawing toolbox a design uses. Positions are fractions of the poster size, so one design draws at any
 * resolution (small for the preview, full size for sharing). The helpers are the ones of the original tool:
 * centred text, word wrapping, logo (contain), picture (cover), rounded clips.
 */
class PosterPainter(
    val canvas: Canvas,
    val w: Float,
    val h: Float,
    spec: PosterSpec,
    private val fonts: PosterFonts,
) {
    /** Horizontal centre. */
    val m: Float = w / 2f

    /** Scale for values that were absolute pixels on the 1080 px wide original (corner radii). */
    val u: Float = w / 1080f

    val product: Product = spec.product
    val brand: Brand = spec.brand
    val picture: Bitmap? = spec.picture
    private val logoBitmap: Bitmap? = spec.logo

    val primary: Int = parse(brand.primaryHex, 0xFFC8102E.toInt())
    val dark: Int = parse(brand.darkHex, 0xFF3A0710.toInt())
    val accent: Int = parse(brand.accentHex, 0xFFFFC233.toInt())

    val priceLabel: String = ArabicText.formatNumber(product.effectivePrice) + " ريال"
    private val oldPriceLabel: String = "بدل " + ArabicText.formatNumber(product.price) + " ريال"
    val phoneLine: String = "للطلب واتساب: " + brand.whatsapp

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // ------------------------------------------------------------------ shapes

    private fun solid(color: Int): Paint {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        return paint
    }

    fun fillBackground(color: Int) {
        canvas.drawRect(0f, 0f, w, h, solid(color))
    }

    fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        canvas.drawRect(left, top, right, bottom, solid(color))
    }

    fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, solid(color))
    }

    fun fillCircle(cx: Float, cy: Float, radius: Float, color: Int) {
        canvas.drawCircle(cx, cy, radius, solid(color))
    }

    fun strokeCircle(cx: Float, cy: Float, radius: Float, strokeWidth: Float, color: Int) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = color
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.FILL
    }

    /** A vertical gradient band across the whole width. */
    fun fillGradient(top: Float, bottom: Float, from: Int, to: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, top, 0f, bottom, from, to, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, top, w, bottom, paint)
        paint.shader = null
    }

    fun fillQuad(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, color: Int) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x4, y4)
        path.close()
        canvas.drawPath(path, solid(color))
    }

    fun withRoundClip(left: Float, top: Float, right: Float, bottom: Float, radius: Float, block: () -> Unit) {
        canvas.save()
        val path = Path()
        path.addRoundRect(RectF(left, top, right, bottom), radius, radius, Path.Direction.CW)
        canvas.clipPath(path)
        block()
        canvas.restore()
    }

    fun withCircleClip(cx: Float, cy: Float, radius: Float, block: () -> Unit) {
        canvas.save()
        val path = Path()
        path.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(path)
        block()
        canvas.restore()
    }

    // ------------------------------------------------------------------ pictures

    /** Fills the rectangle with the picture, centred and cropped (cover). The rectangle itself clips it. */
    fun coverBitmap(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float) {
        val boxW = right - left
        val boxH = bottom - top
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val scale = max(boxW / bw, boxH / bh)
        val drawW = bw * scale
        val drawH = bh * scale
        val dx = left + (boxW - drawW) / 2f
        val dy = top + (boxH - drawH) / 2f
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + drawW, dy + drawH), bitmapPaint)
        canvas.restore()
    }

    /** Fits the picture inside the box, centred at (cx, cy) (contain). */
    fun containBitmap(bitmap: Bitmap, cx: Float, cy: Float, maxW: Float, maxH: Float) {
        val scale = min(maxW / bitmap.width, maxH / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(cx - drawW / 2f, cy - drawH / 2f, cx + drawW / 2f, cy + drawH / 2f),
            bitmapPaint,
        )
    }

    /** The logo centred at (m, y) inside the box, or the brand name when there is no logo. */
    fun logo(y: Float, maxW: Float, maxH: Float, color: Int) {
        val bitmap = logoBitmap
        if (bitmap != null) {
            containBitmap(bitmap, m, y, maxW, maxH)
        } else {
            text(brand.name, y + maxH / 3f, .08f, true, color)
        }
    }

    fun hasLogo(): Boolean = logoBitmap != null

    // ------------------------------------------------------------------ text

    private fun prepareText(size: Float, extraBold: Boolean, color: Int, align: Paint.Align) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.typeface = if (extraBold) fonts.extraBold else fonts.medium
        paint.textSize = w * size
        paint.textAlign = align
    }

    /** One line centred on the poster. [size] is a fraction of the poster width; [y] is the baseline. */
    fun text(s: String, y: Float, size: Float, extraBold: Boolean, color: Int) {
        prepareText(size, extraBold, color, Paint.Align.CENTER)
        canvas.drawText(s, m, y, paint)
    }

    private fun breakLines(s: String, maxWidth: Float): List<String> {
        val lines = ArrayList<String>()
        var line = ""
        for (word in s.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                lines.add(line)
                line = word
            } else {
                line = candidate
            }
        }
        lines.add(line)
        return lines
    }

    /** Centred, wrapped at word boundaries. Returns the number of lines drawn. */
    fun wrappedText(
        s: String,
        y: Float,
        maxWidth: Float,
        lineHeight: Float,
        size: Float,
        extraBold: Boolean,
        color: Int,
    ): Int {
        prepareText(size, extraBold, color, Paint.Align.CENTER)
        val lines = breakLines(s, maxWidth)
        lines.forEachIndexed { index, line -> canvas.drawText(line, m, y + index * lineHeight, paint) }
        return lines.size
    }

    /** Wrapped text whose lines end at [rightX] (Arabic reads right to left). Returns the number of lines. */
    fun wrappedRight(
        s: String,
        rightX: Float,
        y: Float,
        maxWidth: Float,
        lineHeight: Float,
        size: Float,
        color: Int,
    ): Int {
        prepareText(size, false, color, Paint.Align.RIGHT)
        val lines = breakLines(s, maxWidth)
        lines.forEachIndexed { index, line -> canvas.drawText(line, rightX, y + index * lineHeight, paint) }
        return lines.size
    }

    /** The product name, wrapped to 86% of the width. */
    fun productName(y: Float, color: Int, size: Float): Int =
        wrappedText(product.name, y, w * .86f, w * size * 1.2f, size, true, color)

    /** "بدل 7,000 ريال", only when there is an offer. */
    fun oldPrice(y: Float, color: Int) {
        if (product.hasOffer) text(oldPriceLabel, y, .036f, false, color)
    }

    fun checkBullet(cx: Float, cy: Float, radius: Float, color: Int) {
        fillCircle(cx, cy, radius, color)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = radius * .28f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val tick = Path()
        tick.moveTo(cx - radius * .42f, cy + radius * .02f)
        tick.lineTo(cx - radius * .10f, cy + radius * .34f)
        tick.lineTo(cx + radius * .45f, cy - radius * .30f)
        canvas.drawPath(tick, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
    }

    private fun parse(hex: String, fallback: Int): Int =
        try {
            Color.parseColor(hex.trim())
        } catch (e: IllegalArgumentException) {
            fallback
        }
}
