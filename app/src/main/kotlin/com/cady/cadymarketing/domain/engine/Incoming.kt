package com.cady.cadymarketing.domain.engine

/** The tabs and screens a shortcut or the widget can open directly. */
enum class LaunchTab { HOME, CALENDAR, CAMPAIGNS, LIBRARY }

/**
 * What the app is asked to do when it is opened from outside: a long-press shortcut on the launcher icon,
 * a button of the home-screen widget, or another app's Share / "Open with". Pure Kotlin: the Android side
 * (reading the intent, copying the shared file) lives in `LaunchInbox`.
 */
sealed interface AppLaunch {
    /** The Studio, on a fresh post. */
    data object NewPost : AppLaunch

    /** The Studio, started on the idea of the day. */
    data object IdeaOfDay : AppLaunch

    data class OpenTab(val tab: LaunchTab) : AppLaunch

    /**
     * A picture another app shared (or opened with this app), already copied into the app's own storage as [fileName].
     * [extraPictures] counts the ones that were not used (only the first is), [text] is a caption that came with it.
     */
    data class SharedPicture(val fileName: String, val extraPictures: Int = 0, val text: String? = null) : AppLaunch

    /** Text another app shared: it becomes the text of a new post. */
    data class SharedText(val text: String) : AppLaunch

    /** A JSON file (a backup, or the inventory snapshot), copied to the app's cache at [path]. */
    data class SharedFile(val path: String, val name: String?) : AppLaunch
}

/** The actions the shortcuts and the widget buttons send to the main screen. They never change once shipped. */
object LaunchActions {
    const val NEW_POST = "com.cady.cadymarketing.action.NEW_POST"
    const val IDEA_OF_DAY = "com.cady.cadymarketing.action.IDEA_OF_DAY"
    const val OPEN_HOME = "com.cady.cadymarketing.action.OPEN_HOME"
    const val OPEN_CALENDAR = "com.cady.cadymarketing.action.OPEN_CALENDAR"
    const val OPEN_CAMPAIGNS = "com.cady.cadymarketing.action.OPEN_CAMPAIGNS"
    const val OPEN_LIBRARY = "com.cady.cadymarketing.action.OPEN_LIBRARY"

    val all: List<String> = listOf(NEW_POST, IDEA_OF_DAY, OPEN_HOME, OPEN_CALENDAR, OPEN_CAMPAIGNS, OPEN_LIBRARY)

    /** The request behind one of our own actions, or null for any other action (the normal launcher start included). */
    fun fromAction(action: String?): AppLaunch? = when (action) {
        NEW_POST -> AppLaunch.NewPost
        IDEA_OF_DAY -> AppLaunch.IdeaOfDay
        OPEN_HOME -> AppLaunch.OpenTab(LaunchTab.HOME)
        OPEN_CALENDAR -> AppLaunch.OpenTab(LaunchTab.CALENDAR)
        OPEN_CAMPAIGNS -> AppLaunch.OpenTab(LaunchTab.CAMPAIGNS)
        OPEN_LIBRARY -> AppLaunch.OpenTab(LaunchTab.LIBRARY)
        else -> null
    }
}

/** What an incoming Share / "Open with" holds, as far as this app can use it. */
enum class IncomingKind { PICTURE, JSON_FILE, TEXT, UNSUPPORTED }

/** Reading what another app sent. Pure Kotlin, so every rule is tested without a phone. */
object IncomingRules {
    /** A post is never longer than this; a longer shared text is cut (a whole web page would only get in the way). */
    const val MAX_TEXT = 4000

    const val UNSUPPORTED_MESSAGE = "هذا النوع من المحتوى غير مدعوم: يقبل التطبيق الصور والنصوص وملفات JSON (النسخ الاحتياطي ولقطة المخزون)."
    const val UNREADABLE_MESSAGE = "تعذّرت قراءة ما أُرسل إلى التطبيق."

    /**
     * Another app often names only a family of types (every image, say) or nothing at all; the real type, asked from
     * the file's owner, is the better one. A specific type given with the intent wins over a guess.
     */
    fun resolveType(intentType: String?, resolverType: String?): String? {
        val given = normalize(intentType)
        val asked = normalize(resolverType)
        return when {
            given == null || given == "*/*" || given.endsWith("/*") -> asked ?: given
            else -> given
        }
    }

    /** What the incoming thing is. A picture or a JSON file needs the file itself; text needs only the text. */
    fun classify(type: String?, fileName: String?, hasStream: Boolean, hasText: Boolean): IncomingKind {
        val kind = normalize(type).orEmpty()
        val name = fileName?.lowercase().orEmpty()
        if (hasStream) {
            if (kind.startsWith("image/")) return IncomingKind.PICTURE
            if (kind == "application/json" || kind == "text/json" || name.endsWith(".json")) return IncomingKind.JSON_FILE
        }
        if (hasText) return IncomingKind.TEXT
        return IncomingKind.UNSUPPORTED
    }

    /** The text of a share trimmed and capped; null when there is nothing to write about. */
    fun cleanText(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return if (text.length > MAX_TEXT) text.take(MAX_TEXT).trimEnd() else text
    }

    /** What the Studio says after a picture arrives: nothing for one picture, a note when the others were left out. */
    fun extraPicturesNote(extra: Int): String? =
        if (extra <= 0) null else "استُخدمت أول صورة فقط، وتُركت $extra أخرى."

    private fun normalize(type: String?): String? {
        val clean = type?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return clean.ifEmpty { null }
    }
}
