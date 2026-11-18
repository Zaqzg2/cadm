package com.cady.cadymarketing.domain.model

/** A piece of content saved from the Studio: the text, and the recipe of its poster (design + picture). */
data class SavedContent(
    val id: String,
    val productId: String,
    val productName: String,
    val channel: Channel,
    val text: String,
    val status: ContentStatus,
    val designId: Int,
    val picturePath: String?,
    val scheduledAt: String?,
    val campaignId: String?,
    val campaignCode: String?,
    val tone: String?,
    val angle: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** The day the post is scheduled for ("yyyy-MM-dd"), or null when it has no schedule. */
    val scheduledDate: String?
        get() = scheduledAt?.substringBefore('T')?.takeIf { it.length == DATE_LENGTH }

    private companion object {
        const val DATE_LENGTH = 10
    }
}
