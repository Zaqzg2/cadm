package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.MktRole
import com.cady.cadymarketing.domain.model.SavedContent

/** A change of status the library offers on a post, with the words on its menu item. */
data class StatusMove(val label: String, val target: ContentStatus)

/** Searching and filtering the saved posts, and which status changes the library offers. Pure Kotlin. */
object LibraryRules {
    /** The posts of [status] (all when null) whose product, text or campaign code contains [query]. */
    fun filter(all: List<SavedContent>, status: ContentStatus?, query: String): List<SavedContent> {
        val needle = query.trim()
        return all.filter { post ->
            (status == null || post.status == status) &&
                (
                    needle.isEmpty() ||
                        post.productName.contains(needle, ignoreCase = true) ||
                        post.text.contains(needle, ignoreCase = true) ||
                        post.campaignCode.orEmpty().contains(needle, ignoreCase = true)
                    )
        }
    }

    /** How many posts each status has (every status is present, zero included). */
    fun counts(all: List<SavedContent>): Map<ContentStatus, Int> =
        ContentStatus.entries.associateWith { status -> all.count { it.status == status } }

    /** The status changes [role] is offered on a post: the manager gets all of them, a marketer only her own (see [ContentFlow]). */
    fun moves(status: ContentStatus, role: MktRole): List<StatusMove> =
        moves(status).filter { ContentFlow.canMove(role, status, it.target) }

    /**
     * The status changes offered on a post. Approval is not among them: a post is approved in the Studio, where the
     * claims check runs, so nothing can reach "approved" without passing it.
     */
    fun moves(status: ContentStatus): List<StatusMove> = when (status) {
        ContentStatus.DRAFT -> listOf(StatusMove("أرشفة", ContentStatus.ARCHIVED))
        ContentStatus.PENDING -> listOf(StatusMove("إعادة إلى مسودة", ContentStatus.DRAFT))
        ContentStatus.RETURNED -> listOf(
            StatusMove("إعادة إلى مسودة", ContentStatus.DRAFT),
            StatusMove("أرشفة", ContentStatus.ARCHIVED),
        )
        ContentStatus.APPROVED -> listOf(
            StatusMove("تعليم كمنشور", ContentStatus.PUBLISHED),
            StatusMove("إعادة إلى مسودة", ContentStatus.DRAFT),
            StatusMove("أرشفة", ContentStatus.ARCHIVED),
        )
        ContentStatus.PUBLISHED -> listOf(
            StatusMove("إعادة إلى مسودة", ContentStatus.DRAFT),
            StatusMove("أرشفة", ContentStatus.ARCHIVED),
        )
        ContentStatus.ARCHIVED -> listOf(StatusMove("استعادة كمسودة", ContentStatus.DRAFT))
    }
}
