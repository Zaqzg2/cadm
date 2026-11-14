package com.cady.cadymarketing.core.poster

import android.content.Context
import android.graphics.Typeface

/**
 * Tajawal for the posters only (the app itself uses the system font). The two files go in
 * app/src/main/assets/fonts/ as Tajawal-Medium.ttf and Tajawal-ExtraBold.ttf. When a file is missing
 * the poster still draws, with the system font, so the build never depends on them.
 */
class PosterFonts(context: Context) {
    val medium: Typeface = load(context, "fonts/Tajawal-Medium.ttf", Typeface.NORMAL)
    val extraBold: Typeface = load(context, "fonts/Tajawal-ExtraBold.ttf", Typeface.BOLD)

    private fun load(context: Context, path: String, fallbackStyle: Int): Typeface =
        try {
            Typeface.createFromAsset(context.assets, path)
        } catch (e: Exception) {
            Typeface.create(Typeface.DEFAULT, fallbackStyle)
        }
}
