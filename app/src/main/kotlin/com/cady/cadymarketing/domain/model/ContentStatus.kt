package com.cady.cadymarketing.domain.model

/** Lifecycle of a piece of content. The codes are the ones stored in the database (and used by the original tool). */
enum class ContentStatus(val code: String, val label: String) {
    DRAFT("d", "مسودة"),
    PENDING("p", "بانتظار الموافقة"),
    APPROVED("a", "معتمد"),
    PUBLISHED("u", "منشور"),
    ARCHIVED("x", "مؤرشف"),
    RETURNED("r", "مُرجَع"),
    ;

    companion object {
        fun fromCode(code: String?): ContentStatus = entries.firstOrNull { it.code == code } ?: DRAFT
    }
}
