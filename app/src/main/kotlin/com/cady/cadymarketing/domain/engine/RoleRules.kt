package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.MktRole
import com.cady.cadymarketing.domain.model.TeamMember

/** Working out who the signed-in person is. Pure Kotlin. */
object RoleRules {
    /**
     * The role from Kady's `users/{uid}` document and the marketing team document `mkt_team/{uid}`.
     * A Kady manager is the manager. Otherwise only an active member the manager listed as a marketer gets in.
     * A missing `isActive` on Kady's own document does not lock the manager out; on the team document it does
     * (the team is opt-in, so an unclear entry is not trusted).
     */
    fun resolve(kadyUser: Map<String, Any?>?, teamDoc: Map<String, Any?>?): MktRole {
        if (kadyUser != null) {
            val role = J.str(kadyUser, "role").orEmpty().trim().lowercase()
            val active = J.bool(kadyUser, "isActive") ?: true
            if (role == "manager" && active) return MktRole.MANAGER
        }
        if (teamDoc != null) {
            val role = J.str(teamDoc, "role").orEmpty().trim().lowercase()
            val active = J.bool(teamDoc, "isActive") ?: false
            if (role == "marketer" && active) return MktRole.MARKETER
        }
        return MktRole.NONE
    }
}

/** What a screen or an action is for. Each one is allowed to the manager only, for now. */
enum class Feature {
    /** Approving, publishing and returning posts; editing a post that is already approved. */
    REVIEW_CONTENT,
    MANAGE_CAMPAIGNS,
    MANAGE_OCCASIONS,
    EDIT_IDENTITY,
    EDIT_TEMPLATES,

    /** The marketing features written for each product (a picture is linked on each device by anyone). */
    EDIT_PRODUCT_FEATURES,

    /** Importing the inventory snapshot and confirming which inventory line is which Kady product. */
    MANAGE_INVENTORY,
    AUDIENCE,
    REPORTS,
    MANAGE_TEAM,
    SEE_TEAM_LOG,
}

object Permissions {
    /** Nothing is opened to the marketer yet beyond the Studio, the library, the products and the calendar. */
    private val marketerFeatures: Set<Feature> = emptySet()

    fun can(role: MktRole, feature: Feature): Boolean = when (role) {
        MktRole.MANAGER -> true
        MktRole.MARKETER -> feature in marketerFeatures
        MktRole.NONE -> false
    }
}

/** How a post moves between people: what the Studio's main button does and who may change what. Pure Kotlin. */
object ContentFlow {
    /** The statuses a post may have while a marketer still owns it. */
    private val marketerOwned = setOf(ContentStatus.DRAFT, ContentStatus.PENDING, ContentStatus.RETURNED)

    /** The statuses a marketer may give a post. */
    private val marketerMay = setOf(ContentStatus.DRAFT, ContentStatus.PENDING)

    /** The status the Studio's main button gives the post: approved for the manager, waiting for approval for a marketer. */
    fun submitStatus(role: MktRole): ContentStatus =
        if (role == MktRole.MANAGER) ContentStatus.APPROVED else ContentStatus.PENDING

    fun submitLabel(role: MktRole): String = if (role == MktRole.MANAGER) "اعتماد" else "أرسل"

    fun submittedMessage(role: MktRole): String =
        if (role == MktRole.MANAGER) "اعتُمد المحتوى" else "أُرسل المنشور للاعتماد"

    /** True when the person may open the post and save changes to it. */
    fun canEdit(role: MktRole, status: ContentStatus): Boolean = when (role) {
        MktRole.MANAGER -> true
        MktRole.MARKETER -> status in marketerOwned
        MktRole.NONE -> false
    }

    fun canDelete(role: MktRole, status: ContentStatus): Boolean = canEdit(role, status)

    /** True when the person may move a post from [from] to [to]. A marketer only moves her own posts between draft and waiting. */
    fun canMove(role: MktRole, from: ContentStatus, to: ContentStatus): Boolean = when (role) {
        MktRole.MANAGER -> true
        MktRole.MARKETER -> from in marketerOwned && to in marketerMay
        MktRole.NONE -> false
    }

    /** Only the manager sends a post back, and only one that waits for approval. */
    fun canReturn(role: MktRole, status: ContentStatus): Boolean =
        role == MktRole.MANAGER && status == ContentStatus.PENDING

    /** True when the sync may upload a post in this status for this role: a marketer only uploads drafts and posts waiting for approval. */
    fun canPush(role: MktRole, status: ContentStatus): Boolean =
        role == MktRole.MANAGER || status in marketerMay
}

/** Marketing team accounts: login names, passwords and the team document. Pure Kotlin. */
object TeamRules {
    /** The same synthetic address domain Kady gives its accounts, so one login convention serves both apps. */
    const val LOGIN_DOMAIN = "kady-app.local"
    private val usernamePattern = Regex("^[a-z0-9._-]{3,30}$")
    const val MIN_PASSWORD = 6

    /** What the person typed on the login screen as an e-mail: a bare user name gets the Kady domain. */
    fun emailFor(input: String): String {
        val text = input.trim()
        return if (text.contains('@')) text else text.lowercase() + "@" + LOGIN_DOMAIN
    }

    fun normalizeUsername(input: String): String = input.trim().lowercase()

    /** What is wrong with a login name (a sentence), or null when it is fine. */
    fun usernameProblem(input: String): String? {
        val name = normalizeUsername(input)
        return when {
            name.isEmpty() -> "اكتب اسم المستخدم"
            !usernamePattern.matches(name) -> "اسم المستخدم من 3 إلى 30 حرفًا إنجليزيًا صغيرًا أو أرقامًا أو . _ - بلا مسافات"
            else -> null
        }
    }

    fun passwordProblem(input: String): String? =
        if (input.length < MIN_PASSWORD) "كلمة المرور $MIN_PASSWORD أحرف على الأقل" else null

    fun nameProblem(input: String): String? = if (input.isBlank()) "اكتب اسم المسوّق" else null

    /** The team document as the fields stored in Firestore. */
    fun memberDoc(name: String, username: String, active: Boolean, now: Long): Map<String, Any?> = linkedMapOf(
        "name" to name.trim(),
        "username" to normalizeUsername(username),
        "role" to MktRole.MARKETER.code,
        "isActive" to active,
        "updatedAt" to now,
    )

    /** A team member from a document; null for a document that is not a marketer entry. */
    fun member(uid: String, data: Map<String, Any?>): TeamMember? {
        val role = MktRole.fromCode(J.str(data, "role")?.trim()?.lowercase())
        if (role != MktRole.MARKETER) return null
        val username = J.str(data, "username").orEmpty()
        return TeamMember(
            uid = uid,
            name = J.str(data, "name")?.takeIf { it.isNotBlank() } ?: username.ifEmpty { uid },
            username = username,
            role = role,
            active = J.bool(data, "isActive") ?: false,
            updatedAt = J.long(data, "updatedAt") ?: 0L,
        )
    }
}
