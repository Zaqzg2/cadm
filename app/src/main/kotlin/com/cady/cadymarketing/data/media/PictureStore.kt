package com.cady.cadymarketing.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the pictures chosen for posters inside the app's own storage. A picked picture is copied, turned
 * upright (camera rotation applied) and reduced to 1200 px, so a saved post can always be redrawn, even
 * after the gallery picture is moved or the temporary permission is gone.
 */
@Singleton
class PictureStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory = File(context.filesDir, "pictures")
    private val decoded = LruCache<String, Bitmap>(4)

    /** Copies a picked picture into the app. Returns its file name, or null when it cannot be read. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeUri(uri) ?: return@withContext null
            directory.mkdirs()
            val name = UUID.randomUUID().toString() + ".jpg"
            val flat = if (bitmap.hasAlpha()) flattenOnWhite(bitmap) else bitmap
            FileOutputStream(File(directory, name)).use { out ->
                flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    /** Copies a picked logo into the app, keeping its transparency (a PNG of at most 800 px). Returns its file name, or null. */
    suspend fun importLogo(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeUri(uri, LOGO_MAX_SIDE) ?: return@withContext null
            directory.mkdirs()
            val name = "logo-" + UUID.randomUUID().toString() + ".png"
            FileOutputStream(File(directory, name)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    /** Loads a stored picture, reduced so that its longest side is not much more than [maxSide]. */
    fun load(fileName: String, maxSide: Int): Bitmap? {
        val file = File(directory, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val key = "$fileName|$sample"
        decoded.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        decoded.put(key, bitmap)
        return bitmap
    }

    fun delete(fileName: String) {
        File(directory, fileName).delete()
    }

    private fun decodeUri(uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val upright = rotate(raw, exifDegrees(uri))
        return scaleDown(upright, maxSide)
    }

    private fun exifDegrees(uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return flat
    }

    private companion object {
        const val MAX_SIDE = 1200
        const val LOGO_MAX_SIDE = 800
        const val JPEG_QUALITY = 85
    }
}
