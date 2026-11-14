package com.cady.cadymarketing.core.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import com.cady.cadymarketing.data.media.PictureStore
import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Product
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws posters. Small renders (the live preview and the design thumbnails) are cached by everything the
 * picture depends on, so typing in the text field never redraws it: only a change of product, channel, design,
 * picture or brand does. Call it off the main thread.
 */
@Singleton
class PosterRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pictures: PictureStore,
) {
    private val fonts by lazy { PosterFonts(context) }

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    val designs: List<PosterDesign> get() = PosterDesigns.all

    fun render(
        product: Product,
        brand: Brand,
        channel: Channel,
        designId: Int,
        pictureFile: String?,
        widthPx: Int,
        cached: Boolean = true,
    ): Bitmap {
        val key = listOf(
            product.id, product.name, product.unit, product.price, product.offerPrice, product.offerEnd,
            product.features.joinToString("|"), channel.code, designId, pictureFile,
            brand.name, brand.primaryHex, brand.darkHex, brand.accentHex, brand.whatsapp, widthPx,
        ).joinToString("#")
        if (cached) cache.get(key)?.let { return it }

        val height = (widthPx * channel.height.toFloat() / channel.width.toFloat()).roundToInt()
        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val picture = pictureFile?.let { pictures.load(it, max(widthPx, height)) }
        val spec = PosterSpec(product, brand, designId, picture, null)
        PosterDesigns.byId(designId).draw(PosterPainter(Canvas(bitmap), widthPx.toFloat(), height.toFloat(), spec, fonts))
        if (cached) cache.put(key, bitmap)
        return bitmap
    }

    /** The full-size poster of the channel (1080 px wide), never cached. */
    fun renderFull(product: Product, brand: Brand, channel: Channel, designId: Int, pictureFile: String?): Bitmap =
        render(product, brand, channel, designId, pictureFile, channel.width, cached = false)

    private companion object {
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
