package com.cady.cadymarketing.domain.model

/**
 * The look of the whole app: how the page, the cards and the bars are drawn. Chosen in
 * Settings → المظهر and kept on this device only. The colours still come from the brand identity,
 * so every style works with any brand colour, in light and in dark.
 *
 * [id] is what is saved, so it never changes once shipped; [label] and [blurb] are what the picker shows.
 */
enum class AppStyle(val id: String, val label: String, val blurb: String) {
    CLASSIC("classic", "كلاسيك", "الشكل المعتاد: بطاقات بيضاء بحدود رفيعة"),
    GLASS("glass", "زجاجي", "بطاقات شفافة بحدود لامعة فوق خلفية ملوّنة"),
    SOFT("soft", "ناعم", "أسطح بارزة بظلّين ناعمين وزوايا كبيرة"),
    MINIMAL("minimal", "بسيط", "خلفية نقية وبطاقات بحدود رفيعة بلا تعبئة"),
    GRADIENT("gradient", "متدرّج", "خلفية متدرجة بلون الهوية وبطاقات مستديرة"),
    BOLD("bold", "جريء", "حدود سميكة وظل صلب وزوايا حادة");

    companion object {
        /** The style saved under [id]; nothing saved yet, or anything unknown, is the classic one. */
        fun fromId(id: String?): AppStyle = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** Light, dark, or whatever the phone is set to. */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "تلقائي"),
    LIGHT("light", "فاتح"),
    DARK("dark", "داكن");

    companion object {
        /** The mode saved under [id]; nothing saved yet, or anything unknown, follows the phone. */
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** What was picked in Settings → المظهر. */
data class Appearance(
    val style: AppStyle = AppStyle.CLASSIC,
    val mode: ThemeMode = ThemeMode.SYSTEM,
)
