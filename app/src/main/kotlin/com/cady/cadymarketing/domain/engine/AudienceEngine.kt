package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Consent
import com.cady.cadymarketing.domain.model.ConsentStatus
import com.cady.cadymarketing.domain.model.Customer
import com.cady.cadymarketing.domain.model.Exclusion
import com.cady.cadymarketing.domain.model.Segment
import com.cady.cadymarketing.domain.model.SegmentKind
import com.cady.cadymarketing.domain.model.SendStatus

/** Who may be sent to, and who may not (with the reason). */
data class SendPartition(
    val eligible: List<Customer>,
    val excluded: List<Pair<Customer, Exclusion>>,
)

/** How far a send list has got: [done] customers handled out of [total] who can be sent to. */
data class SendProgress(val done: Int, val total: Int) {
    val finished: Boolean get() = total > 0 && done >= total
}

/** Segments, consent and the personalised text of a send list. Pure Kotlin. */
object AudienceEngine {
    /** A soft daily ceiling for messages sent one by one from a personal number; above it WhatsApp may restrict the number. */
    const val SOFT_DAILY_LIMIT = 30
    const val FALLBACK_NAME = "عميلنا العزيز"

    /** Words that are not a name on their own: the name goes on with the next word ("الحاج علي", "أبو أحمد", "مؤسسة النور"). */
    private val prefixWords = setOf(
        "الحاج", "حاج", "الأخ", "الاخ", "أخي", "الأستاذ", "الاستاذ", "أستاذ", "استاذ", "الشيخ", "شيخ",
        "الدكتور", "دكتور", "د.", "المهندس", "مهندس", "م.", "الكابتن", "كابتن", "السيد", "سيد", "الأخت", "الاخت",
        "أبو", "ابو", "أم", "ام", "مؤسسة", "شركة", "محل", "مطعم", "بقالة", "سوبرماركت", "مخبز", "مغسلة", "فندق",
        "مركز", "معرض", "مجمع", "مستشفى", "مدرسة",
    )
    private const val MAX_NAME_WORDS = 3
    private val spaces = Regex("\\s+")

    /** The customers a segment covers, whatever their consent (consent is applied by [partition]). */
    fun members(segment: Segment, customers: List<Customer>): List<Customer> = when (segment.kind) {
        SegmentKind.MANUAL -> {
            val ids = segment.memberIds.toSet()
            customers.filter { it.id in ids }
        }
        SegmentKind.FILTER -> {
            val area = segment.area.trim()
            val rep = segment.repName.trim()
            customers.filter { customer ->
                (area.isEmpty() || customer.area.contains(area, ignoreCase = true)) &&
                    (rep.isEmpty() || customer.repName.trim().equals(rep, ignoreCase = true))
            }
        }
    }

    /** The reason a customer must not be sent to, or null when they can be. A refusal always wins over everything else. */
    fun exclusion(customer: Customer, consent: Consent?): Exclusion? = when {
        consent?.status == ConsentStatus.DECLINED -> Exclusion.DECLINED
        consent?.status != ConsentStatus.GRANTED -> Exclusion.NO_CONSENT
        !customer.active -> Exclusion.INACTIVE
        customer.phone == null -> Exclusion.NO_PHONE
        else -> null
    }

    fun partition(members: List<Customer>, consents: Map<String, Consent>): SendPartition {
        val eligible = ArrayList<Customer>()
        val excluded = ArrayList<Pair<Customer, Exclusion>>()
        for (customer in members) {
            val reason = exclusion(customer, consents[customer.id])
            if (reason == null) eligible.add(customer) else excluded.add(customer to reason)
        }
        return SendPartition(eligible, excluded)
    }

    /** What is wrong with a segment form, one sentence each. Empty means it can be saved. */
    fun validate(name: String, kind: SegmentKind, memberIds: Collection<String>): List<String> {
        val problems = ArrayList<String>()
        if (name.isBlank()) problems.add("اكتب اسم الشريحة")
        if (kind == SegmentKind.MANUAL && memberIds.isEmpty()) problems.add("اختر عميلًا واحدًا على الأقل")
        return problems
    }

    /** "الحاج علي محمد" gives "الحاج علي"; "أحمد سالم" gives "أحمد"; blank gives blank. */
    fun firstName(fullName: String): String {
        val words = fullName.trim().split(spaces).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        var end = 1
        while (end < words.size && end < MAX_NAME_WORDS && words[end - 1] in prefixWords) end++
        return words.subList(0, end).joinToString(" ")
    }

    /** The text of the post for one customer: the customer's name goes where the post says {اسم العميل}. */
    fun personalize(text: String, customer: Customer): String {
        val name = firstName(customer.name).ifEmpty { FALLBACK_NAME }
        return text.replace(Placeholders.CUSTOMER_NAME, name)
    }

    fun hasNamePlaceholder(text: String): Boolean = text.contains(Placeholders.CUSTOMER_NAME)

    /** The chat link of one customer with their own text, or null when they have no usable number. */
    fun chatLink(text: String, customer: Customer): String? {
        val phone = customer.phone ?: return null
        return PhoneRules.waLink(phone, personalize(text, customer))
    }

    fun progress(eligible: List<Customer>, marks: Map<String, SendStatus>): SendProgress =
        SendProgress(done = eligible.count { it.id in marks }, total = eligible.size)
}
