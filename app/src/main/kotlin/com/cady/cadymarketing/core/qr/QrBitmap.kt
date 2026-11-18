package com.cady.cadymarketing.core.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR codes made on the device (ZXing's encoder, no network): the image of a campaign link. */
object QrBitmap {
    private const val PADDING = 48
    private const val CAPTION_HEIGHT = 150

    /** The QR of [text] as a square black-on-white bitmap with its quiet zone, or null when the text cannot be encoded. */
    fun create(text: String, sizePx: Int = 720): Bitmap? = try {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }

    /** A card ready to print or share: the QR with a title and a smaller line under it. */
    fun card(qr: Bitmap, title: String, subtitle: String): Bitmap {
        val width = qr.width + PADDING * 2
        val height = qr.height + PADDING + CAPTION_HEIGHT
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(qr, PADDING.toFloat(), PADDING.toFloat(), null)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 54f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        val maxWidth = (width - PADDING * 2).toFloat()
        fit(titlePaint, title, maxWidth)
        fit(subtitlePaint, subtitle, maxWidth)
        val top = qr.height + PADDING
        canvas.drawText(title, width / 2f, top + 70f, titlePaint)
        canvas.drawText(subtitle, width / 2f, top + 125f, subtitlePaint)
        return out
    }

    /** Shrinks the text size until [text] fits in [maxWidth] (down to a floor, so a very long name is still drawn). */
    private fun fit(paint: Paint, text: String, maxWidth: Float) {
        while (paint.measureText(text) > maxWidth && paint.textSize > MIN_TEXT_SIZE) {
            paint.textSize -= 2f
        }
    }

    private const val MIN_TEXT_SIZE = 20f
}
