package com.cady.cadymarketing.core.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the system share sheet with a picture (and optionally a text) that this app wrote to its share folder. */
object ShareImage {
    /** Returns false when no app could take it. [mime] is the file's type (a picture by default; the backup is JSON). */
    fun send(context: Context, uri: Uri, text: String? = null, title: String = "مشاركة", mime: String = "image/png"): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, title))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
