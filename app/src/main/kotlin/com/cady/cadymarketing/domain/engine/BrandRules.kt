package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand

/** Reading, writing and checking the brand identity. Pure Kotlin. */
object BrandRules {
    private val hex6 = Regex("^[0-9a-fA-F]{6}$")
    private val hex3 = Regex("^[0-9a-fA-F]{3}$")
    private val urlPattern = Regex("^https?://[^\\s/]+\\.[^\\s/]+\\S*$")

    /** "#C8102E", "c8102e" and "#c12" all become a lower-case "#rrggbb"; anything else is null. */
    fun normalizeHex(input: String): String? {
        val raw = input.trim().removePrefix("#")
        return when {
            hex6.matches(raw) -> "#" + raw.lowercase()
            hex3.matches(raw) -> "#" + raw.lowercase().map { "$it$it" }.joinToString("")
            else -> null
        }
    }

    /** "#aabbcc" as an Android ARGB int (opaque), or null when the text is not a colour. */
    fun argb(hex: String): Int? {
        val normal = normalizeHex(hex) ?: return null
        return (0xFF000000L or normal.substring(1).toLong(16)).toInt()
    }

    /** A much darker shade of a colour (for the poster's dark corner), or null when the text is not a colour. */
    fun darken(hex: String, keep: Float = 0.28f): String? {
        val color = argb(hex) ?: return null
        fun part(shift: Int): Int = (((color shr shift) and 0xFF) * keep).toInt().coerceIn(0, 255)
        return String.format(java.util.Locale.US, "#%02x%02x%02x", part(16), part(8), part(0))
    }

    /** The WhatsApp number as digits only. */
    fun digits(text: String): String = ArabicText.toWesternDigits(text).filter { it in '0'..'9' }

    /** "#a #b" or "a, b،c" become ["#a", "#b", "#c"]: one tag each, always starting with #, no repeats. */
    fun parseHashtags(text: String): List<String> =
        text.split(Regex("[\\s,،]+"))
            .map { it.trim().removePrefix("#").replace("#", "").replace(" ", "") }
            .filter { it.isNotEmpty() }
            .map { "#$it" }
            .distinct()

    fun formatHashtags(tags: List<String>): String = tags.joinToString(" ")

    /** What is wrong with the brand, one sentence each. Empty means it can be saved. */
    fun validate(brand: Brand): List<String> {
        val problems = ArrayList<String>()
        if (brand.name.isBlank()) problems.add("اكتب اسم العلامة")
        if (normalizeHex(brand.primaryHex) == null) problems.add("اللون الأساسي غير صالح (مثال: #c8102e)")
        if (normalizeHex(brand.darkHex) == null) problems.add("اللون الداكن غير صالح")
        if (normalizeHex(brand.accentHex) == null) problems.add("اللون المميز غير صالح")
        val phone = digits(brand.whatsapp)
        if (phone.length !in 9..15) problems.add("رقم واتساب يجب أن يكون من 9 إلى 15 رقمًا")
        if (!urlPattern.matches(brand.siteUrl.trim())) problems.add("رابط الموقع يجب أن يبدأ بـ https://")
        return problems
    }

    /** The brand with every field cleaned (colours normalised, number as digits, tags well formed). */
    fun clean(brand: Brand): Brand = brand.copy(
        name = brand.name.trim(),
        primaryHex = normalizeHex(brand.primaryHex) ?: brand.primaryHex.trim(),
        darkHex = normalizeHex(brand.darkHex) ?: brand.darkHex.trim(),
        accentHex = normalizeHex(brand.accentHex) ?: brand.accentHex.trim(),
        tone = brand.tone.trim(),
        bannedPhrases = brand.bannedList.joinToString("،"),
        whatsapp = digits(brand.whatsapp),
        siteUrl = brand.siteUrl.trim(),
        hashtags = brand.hashtags.flatMap { parseHashtags(it) }.distinct(),
    )
}

/** The brand as a stored document. Missing keys fall back to the defaults, so old documents still read. */
object BrandJson {
    fun toMap(brand: Brand): Map<String, Any?> = linkedMapOf(
        "name" to brand.name,
        "primaryHex" to brand.primaryHex,
        "darkHex" to brand.darkHex,
        "accentHex" to brand.accentHex,
        "tone" to brand.tone,
        "bannedPhrases" to brand.bannedPhrases,
        "whatsapp" to brand.whatsapp,
        "siteUrl" to brand.siteUrl,
        "hashtags" to brand.hashtags,
        "logoFile" to brand.logoFile,
    )

    fun fromMap(map: Map<String, Any?>): Brand {
        val d = Brand()
        fun text(key: String, fallback: String): String = J.str(map, key)?.takeIf { it.isNotBlank() } ?: fallback
        return Brand(
            name = text("name", d.name),
            primaryHex = text("primaryHex", d.primaryHex),
            darkHex = text("darkHex", d.darkHex),
            accentHex = text("accentHex", d.accentHex),
            tone = J.str(map, "tone") ?: d.tone,
            bannedPhrases = J.str(map, "bannedPhrases") ?: d.bannedPhrases,
            whatsapp = text("whatsapp", d.whatsapp),
            siteUrl = text("siteUrl", d.siteUrl),
            hashtags = if (map.containsKey("hashtags")) J.strings(map["hashtags"]) else d.hashtags,
            logoFile = J.str(map, "logoFile")?.takeIf { it.isNotBlank() },
        )
    }

    fun encode(brand: Brand): String = MiniJson.write(toMap(brand))

    fun decode(json: String?): Brand = fromMap(J.parseMap(json))
}
