package com.cady.cadymarketing.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gets a finished poster out of the app: into the shared Downloads/csdy folder, or to another app. */
@Singleton
class ImageExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Saves the poster as a PNG in Download/csdy and returns the folder text to show. Throws IOException. */
    suspend fun saveToDownloads(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val name = fileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("تعذّر إنشاء الملف")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IOException("تعذّر كتابة الملف")
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            if (!folder.exists() && !folder.mkdirs()) throw IOException("تعذّر إنشاء المجلد، امنح التطبيق صلاحية التخزين")
            FileOutputStream(File(folder, name)).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
        "Download/$FOLDER/$name"
    }

    /** Writes the poster to the private share folder and returns a content:// address other apps may read. */
    suspend fun shareUri(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, SHARE_FOLDER)
        folder.mkdirs()
        val old = System.currentTimeMillis() - ONE_DAY_MS
        folder.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }
        val file = File(folder, fileName())
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    private fun fileName(): String =
        "kady-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"

    private companion object {
        const val FOLDER = "csdy"
        const val SHARE_FOLDER = "share"
        const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}
