package com.cady.cadymarketing.domain.model

/**
 * Brand identity used by the engines and the poster. The Identity screen edits it; until something is
 * saved the defaults are the values of the original marketing tool.
 * [logoFile] is the name of a file in the app's own storage (never synced: each device keeps its logo).
 */
data class Brand(
    val name: String = "كادي",
    val primaryHex: String = DEFAULT_PRIMARY_HEX,
    val darkHex: String = "#3a0710",
    val accentHex: String = "#ffc233",
    val tone: String = "يمنية خفيفة مفهومة",
    val bannedPhrases: String = "يقتل,جراثيم,يعالج,علاج,شفاء,أفضل من,100%",
    val whatsapp: String = "730487946",
    val siteUrl: String = "https://cadyco.web.app",
    val hashtags: List<String> = listOf("#كادي", "#منظفات", "#اليمن"),
    val logoFile: String? = null,
) {
    /** The banned phrases as a clean list (the field is split on "," and the Arabic comma). */
    val bannedList: List<String>
        get() = bannedPhrases.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val DEFAULT_PRIMARY_HEX = "#c8102e"
    }
}
