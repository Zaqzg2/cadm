package com.cady.cadymarketing.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStyleTest {
    @Test
    fun everyStyleComesBackFromItsOwnId() {
        for (style in AppStyle.entries) assertEquals(style, AppStyle.fromId(style.id))
    }

    @Test
    fun nothingSavedOrAnUnknownIdIsTheGlassLook() {
        // Phase 8: glass is what the app opens with until a style is picked.
        assertEquals(AppStyle.GLASS, AppStyle.DEFAULT)
        assertEquals(AppStyle.GLASS, AppStyle.fromId(null))
        assertEquals(AppStyle.GLASS, AppStyle.fromId(""))
        assertEquals(AppStyle.GLASS, AppStyle.fromId("a-style-from-a-later-version"))
    }

    @Test
    fun aStyleThatWasPickedIsKeptEvenWhenItIsNotTheDefault() {
        // A phone that saved "classic" in phase 7 must stay classic.
        assertEquals(AppStyle.CLASSIC, AppStyle.fromId("classic"))
    }

    @Test
    fun savedIdsAreUniqueAndLowerCase() {
        // The id is what is kept on the phone, so two styles must never share one.
        val ids = AppStyle.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() && it == it.lowercase() })
    }

    @Test
    fun theSixStylesKeepTheirSavedIds() {
        // Changing one of these would silently reset the look of every phone that saved it.
        assertEquals(
            listOf("classic", "glass", "soft", "minimal", "gradient", "bold"),
            AppStyle.entries.map { it.id },
        )
    }

    @Test
    fun everyStyleHasANameAndADescription() {
        assertTrue(AppStyle.entries.all { it.label.isNotBlank() && it.blurb.isNotBlank() })
    }

    @Test
    fun modeFallsBackToFollowingThePhone() {
        for (mode in ThemeMode.entries) assertEquals(mode, ThemeMode.fromId(mode.id))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId("sepia"))
    }

    @Test
    fun theDefaultAppearanceIsTheGlassLookFollowingThePhone() {
        val default = Appearance()
        assertEquals(AppStyle.GLASS, default.style)
        assertEquals(ThemeMode.SYSTEM, default.mode)
    }
}
