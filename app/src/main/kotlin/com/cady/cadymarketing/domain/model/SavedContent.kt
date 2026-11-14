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
)
