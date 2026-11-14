package com.cady.cadymarketing.domain.model

enum class Severity {
    /** Blocks approval. These are exactly the rules of the original tool. */
    ERROR,

    /** Advice only: shown, never blocks. */
    WARNING,
}

enum class IssueKind {
    EMPTY,
    OFFER_EXPIRED,
    BANNED_PHRASE,
    UNKNOWN_NUMBER,
    MISSING_LINK,
    TOO_LONG,
    TOO_MANY_EMOJI,
    UNKNOWN_PLACEHOLDER,
}

/** One finding of the claim checker. [match] is the offending text when there is one (used for highlighting). */
data class Issue(
    val severity: Severity,
    val kind: IssueKind,
    val message: String,
    val match: String? = null,
)

data class CheckResult(val issues: List<Issue>) {
    val errors: List<Issue> get() = issues.filter { it.severity == Severity.ERROR }
    val warnings: List<Issue> get() = issues.filter { it.severity == Severity.WARNING }

    /** The approval gate: true when there is no blocking error. */
    val passed: Boolean get() = issues.none { it.severity == Severity.ERROR }

    companion object {
        val Empty = CheckResult(emptyList())
    }
}
