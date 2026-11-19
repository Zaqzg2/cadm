package com.cady.cadymarketing.domain.engine

/** Phone numbers as customers are typed in Kady (Yemeni mobiles, with or without the country code) turned into wa.me digits. */
object PhoneRules {
    private const val YEMEN = "967"
    private const val MIN_INTERNATIONAL = 11
    private const val MAX_INTERNATIONAL = 15

    /**
     * International digits without "+" or spaces, or null when the number cannot be a WhatsApp number.
     * A 9-digit number starting with 7 (or 07...) is a Yemeni mobile; 00 or + in front is dropped; a landline
     * (starting with another digit) or a number of a wrong length gives null.
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        var digits = ArabicText.toWesternDigits(raw).filter { it in '0'..'9' }
        if (digits.startsWith("00")) digits = digits.substring(2)
        return when {
            digits.isEmpty() -> null
            digits.length == 9 && digits[0] == '7' -> YEMEN + digits
            digits.length == 10 && digits.startsWith("07") -> YEMEN + digits.substring(1)
            digits.startsWith("0") -> null
            digits.startsWith(YEMEN) -> if (digits.length == 12 && digits[3] == '7') digits else null
            digits.length in MIN_INTERNATIONAL..MAX_INTERNATIONAL -> digits
            else -> null
        }
    }

    /** The link that opens a WhatsApp chat with [phone] (already normalized) and the text filled in. */
    fun waLink(phone: String, text: String): String = "https://wa.me/$phone?text=" + ArabicText.encodeUriComponent(text)
}
