package com.cady.cadymarketing.domain.model

/** What a customer agreed to. The codes are the ones stored in the database and in `mkt_consent`. */
enum class ConsentStatus(val code: String, val label: String) {
    UNKNOWN("u", "لا موافقة مسجّلة"),
    GRANTED("g", "موافق على الرسائل"),
    DECLINED("d", "طلب التوقف"),
    ;

    companion object {
        fun fromCode(code: String?): ConsentStatus = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * A customer of Kady as the marketing app sees it (a read-only copy: the marketing app never writes to `customers`).
 * [phone] is international digits ready for wa.me (for example 967730487946), or null when the number is missing or
 * unusable; [rawPhone] is the number as Kady has it, so a bad number can be fixed there.
 */
data class Customer(
    val id: String,
    val name: String,
    val phone: String?,
    val rawPhone: String,
    val area: String,
    val repName: String,
    val active: Boolean,
)

/** The consent of one customer. It lives in `mkt_consent`, apart from Kady's own documents, and is never inferred. */
data class Consent(
    val customerId: String,
    val status: ConsentStatus,
    /** How it was given, in the manager's words ("قال لي في اتصال"). */
    val note: String,
    val updatedAt: Long,
)

enum class SegmentKind(val code: String, val label: String) {
    /** Everyone who matches the area and rep written on the segment (both empty: every customer). */
    FILTER("f", "بحسب شروط"),

    /** Exactly the customers picked by hand. */
    MANUAL("m", "قائمة يدوية"),
    ;

    companion object {
        fun fromCode(code: String?): SegmentKind = entries.firstOrNull { it.code == code } ?: FILTER
    }
}

data class Segment(
    val id: String,
    val name: String,
    val kind: SegmentKind,
    /** For a [SegmentKind.FILTER]: text the customer's area must contain (empty: any). */
    val area: String,
    /** For a [SegmentKind.FILTER]: the rep's name (empty: any). */
    val repName: String,
    /** For a [SegmentKind.MANUAL]: the customers picked. */
    val memberIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

/** What happened to one customer in the send list of one post. */
enum class SendStatus(val code: String, val label: String) {
    SENT("s", "أُرسل"),
    SKIPPED("k", "تُخطّي"),
    ;

    companion object {
        fun fromCode(code: String?): SendStatus? = entries.firstOrNull { it.code == code }
    }
}

/** Why a customer cannot be sent to. */
enum class Exclusion(val label: String) {
    DECLINED("طلب التوقف عن الرسائل"),
    NO_CONSENT("لا موافقة مسجّلة"),
    INACTIVE("عميل غير نشط في كادي"),
    NO_PHONE("رقم غير صالح"),
}
