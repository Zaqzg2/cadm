package com.cady.cadymarketing.domain.model

/** The groups the operations log can be filtered by. */
enum class OpCategory(val label: String) {
    CONTENT("المحتوى"),
    CAMPAIGN("الحملات"),
    SYNC("المزامنة"),
    TEAM("الفريق"),
    BACKUP("النسخ"),
    INVENTORY("المخزون"),
}

/** What was done. The codes are the ones stored (and synced), so they never change. */
enum class OpAction(val code: String, val label: String, val category: OpCategory) {
    CONTENT_CREATED("cc", "أنشأ منشورًا", OpCategory.CONTENT),
    CONTENT_SUBMITTED("cs", "أرسل منشورًا للاعتماد", OpCategory.CONTENT),
    CONTENT_APPROVED("ca", "اعتمد منشورًا", OpCategory.CONTENT),
    CONTENT_RETURNED("cr", "أرجع منشورًا للتعديل", OpCategory.CONTENT),
    CONTENT_PUBLISHED("cp", "علّم منشورًا كمنشور", OpCategory.CONTENT),
    CONTENT_STATUS("cx", "غيّر حالة منشور", OpCategory.CONTENT),
    CONTENT_DELETED("cd", "حذف منشورًا", OpCategory.CONTENT),
    CAMPAIGN_CREATED("gc", "أنشأ حملة", OpCategory.CAMPAIGN),
    CAMPAIGN_STATUS("gs", "غيّر حالة حملة", OpCategory.CAMPAIGN),
    CAMPAIGN_DELETED("gd", "حذف حملة", OpCategory.CAMPAIGN),
    SYNC_RUN("sy", "زامن البيانات", OpCategory.SYNC),
    BACKUP_EXPORT("be", "حفظ نسخة احتياطية", OpCategory.BACKUP),
    BACKUP_IMPORT("bi", "استعاد نسخة احتياطية", OpCategory.BACKUP),
    INVENTORY_IMPORTED("ii", "استورد لقطة مخزون", OpCategory.INVENTORY),
    INVENTORY_LINKED("il", "ربط صنف مخزون بصنف كادي", OpCategory.INVENTORY),
    INVENTORY_UNLINKED("iu", "فك ربط صنف مخزون", OpCategory.INVENTORY),
    TEAM_ADDED("ta", "أضاف مسوّقًا", OpCategory.TEAM),
    TEAM_ENABLED("te", "فعّل حساب مسوّق", OpCategory.TEAM),
    TEAM_DISABLED("td", "عطّل حساب مسوّق", OpCategory.TEAM),
    ;

    companion object {
        fun fromCode(code: String?): OpAction? = entries.firstOrNull { it.code == code }
    }
}

/** One line of the operations log: who did what, to what, and when. */
data class OpLogEntry(
    val id: String,
    val at: Long,
    val actor: String,
    val action: OpAction?,
    val actionCode: String,
    val subject: String,
    val detail: String,
) {
    /** The words shown for the action; an action code from a newer app version still shows as itself. */
    val label: String get() = action?.label ?: actionCode
    val category: OpCategory? get() = action?.category
}
