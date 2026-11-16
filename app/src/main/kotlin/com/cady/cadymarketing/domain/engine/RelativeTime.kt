package com.cady.cadymarketing.domain.engine

/** "Just now", "5 minutes ago": how long ago something happened, in Arabic. */
object RelativeTime {
    fun ago(deltaMillis: Long): String {
        val minutes = deltaMillis / 60_000L
        return when {
            minutes < 1L -> "الآن"
            minutes < 60L -> "قبل $minutes دقيقة"
            minutes < 24L * 60L -> "قبل ${minutes / 60L} ساعة"
            else -> "قبل ${minutes / (24L * 60L)} يومًا"
        }
    }
}
