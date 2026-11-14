package com.cady.cadymarketing.domain.model

/**
 * Publishing channels with the poster size each one needs.
 * [softLimit] is the text length above which the checker advises shortening (advice only).
 */
enum class Channel(
    val code: String,
    val label: String,
    val width: Int,
    val height: Int,
    val softLimit: Int,
) {
    WHATSAPP("wa", "واتساب (بث)", 1080, 1080, 700),
    WHATSAPP_STATUS("st", "حالة واتساب", 1080, 1920, 250),
    FACEBOOK("fb", "فيسبوك", 1080, 1350, 1000),
    INSTAGRAM("ig", "إنستغرام", 1080, 1350, 800),
    TIKTOK("tt", "تيك توك", 1080, 1920, 300),
    ;

    val aspect: Float get() = width.toFloat() / height.toFloat()

    /** WhatsApp channels link to a chat; the others link to the website with the campaign code. */
    val isWhatsApp: Boolean get() = this == WHATSAPP || this == WHATSAPP_STATUS

    val sizeLabel: String get() = "$width×$height"

    companion object {
        fun fromCode(code: String?): Channel = entries.firstOrNull { it.code == code } ?: WHATSAPP
    }
}
