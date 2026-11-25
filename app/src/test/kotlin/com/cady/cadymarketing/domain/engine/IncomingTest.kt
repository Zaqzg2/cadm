package com.cady.cadymarketing.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingTest {

    // ------------------------------------------------------------------ the actions of the shortcuts and the widget

    @Test
    fun everyOwnActionOpensSomething() {
        assertEquals(AppLaunch.NewPost, LaunchActions.fromAction(LaunchActions.NEW_POST))
        assertEquals(AppLaunch.IdeaOfDay, LaunchActions.fromAction(LaunchActions.IDEA_OF_DAY))
        assertEquals(AppLaunch.OpenTab(LaunchTab.HOME), LaunchActions.fromAction(LaunchActions.OPEN_HOME))
        assertEquals(AppLaunch.OpenTab(LaunchTab.CALENDAR), LaunchActions.fromAction(LaunchActions.OPEN_CALENDAR))
        assertEquals(AppLaunch.OpenTab(LaunchTab.CAMPAIGNS), LaunchActions.fromAction(LaunchActions.OPEN_CAMPAIGNS))
        assertEquals(AppLaunch.OpenTab(LaunchTab.LIBRARY), LaunchActions.fromAction(LaunchActions.OPEN_LIBRARY))
        assertTrue(LaunchActions.all.all { LaunchActions.fromAction(it) != null })
    }

    @Test
    fun anyOtherActionIsNotOurs() {
        assertNull(LaunchActions.fromAction(null))
        assertNull(LaunchActions.fromAction("android.intent.action.MAIN"))
        assertNull(LaunchActions.fromAction("android.intent.action.SEND"))
        assertNull(LaunchActions.fromAction(""))
    }

    @Test
    fun actionNamesAreUniqueAndKeepTheirSpelling() {
        // The manifest's shortcuts file and already placed widgets refer to these names, so they never change.
        assertEquals(LaunchActions.all.size, LaunchActions.all.toSet().size)
        assertEquals("com.cady.cadymarketing.action.NEW_POST", LaunchActions.NEW_POST)
        assertEquals("com.cady.cadymarketing.action.IDEA_OF_DAY", LaunchActions.IDEA_OF_DAY)
        assertEquals("com.cady.cadymarketing.action.OPEN_CALENDAR", LaunchActions.OPEN_CALENDAR)
        assertEquals("com.cady.cadymarketing.action.OPEN_LIBRARY", LaunchActions.OPEN_LIBRARY)
    }

    // ------------------------------------------------------------------ what another app sent

    @Test
    fun aSpecificTypeFromTheSenderWinsOverTheGuess() {
        assertEquals("image/png", IncomingRules.resolveType("image/png", "image/jpeg"))
        assertEquals("application/json", IncomingRules.resolveType("application/json; charset=utf-8", null))
    }

    @Test
    fun aVagueTypeIsReplacedByTheRealOne() {
        assertEquals("image/jpeg", IncomingRules.resolveType("image/*", "image/jpeg"))
        assertEquals("image/jpeg", IncomingRules.resolveType("*/*", "IMAGE/JPEG"))
        assertEquals("image/jpeg", IncomingRules.resolveType(null, "image/jpeg"))
        assertEquals("image/*", IncomingRules.resolveType("image/*", null))
        assertNull(IncomingRules.resolveType(null, null))
        assertNull(IncomingRules.resolveType("", " "))
    }

    @Test
    fun picturesNeedTheFileItself() {
        assertEquals(IncomingKind.PICTURE, IncomingRules.classify("image/jpeg", "a.jpg", hasStream = true, hasText = false))
        assertEquals(IncomingKind.PICTURE, IncomingRules.classify("image/webp", null, hasStream = true, hasText = true))
        // A picture type with no file behind it is only a caption.
        assertEquals(IncomingKind.TEXT, IncomingRules.classify("image/jpeg", null, hasStream = false, hasText = true))
        assertEquals(IncomingKind.UNSUPPORTED, IncomingRules.classify("image/jpeg", null, hasStream = false, hasText = false))
    }

    @Test
    fun jsonFilesAreRecognisedByTypeOrByName() {
        assertEquals(IncomingKind.JSON_FILE, IncomingRules.classify("application/json", null, hasStream = true, hasText = false))
        assertEquals(IncomingKind.JSON_FILE, IncomingRules.classify("application/octet-stream", "Backup.JSON", hasStream = true, hasText = false))
        assertEquals(IncomingKind.JSON_FILE, IncomingRules.classify(null, "cady-marketing-backup-20261124.json", hasStream = true, hasText = false))
        assertEquals(IncomingKind.UNSUPPORTED, IncomingRules.classify("application/pdf", "a.pdf", hasStream = true, hasText = false))
    }

    @Test
    fun plainTextBecomesAPost() {
        assertEquals(IncomingKind.TEXT, IncomingRules.classify("text/plain", null, hasStream = false, hasText = true))
        assertEquals(IncomingKind.UNSUPPORTED, IncomingRules.classify("text/plain", null, hasStream = false, hasText = false))
    }

    @Test
    fun sharedTextIsTrimmedAndCapped() {
        assertNull(IncomingRules.cleanText(null))
        assertNull(IncomingRules.cleanText("   \n  "))
        assertEquals("عرض اليوم", IncomingRules.cleanText("  عرض اليوم \n"))
        val long = "ا".repeat(IncomingRules.MAX_TEXT + 500)
        assertEquals(IncomingRules.MAX_TEXT, IncomingRules.cleanText(long)!!.length)
    }

    @Test
    fun theNoteAboutLeftOutPicturesAppearsOnlyWhenThereAreSome() {
        assertNull(IncomingRules.extraPicturesNote(0))
        assertNull(IncomingRules.extraPicturesNote(-1))
        assertTrue(IncomingRules.extraPicturesNote(2)!!.contains("2"))
    }
}
