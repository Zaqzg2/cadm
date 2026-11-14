package com.cady.cadymarketing.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved piece of content. The field names follow the document the original tool kept
 * (id, number, product, channel, text, status, campaign, schedule, design, timestamps).
 * [number] (CNT-0001) is assigned at the first sync, so it is null for now. A deleted row only gets [deletedAt].
 */
@Entity(tableName = "content", indices = [Index(value = ["updatedAt"])])
data class ContentEntity(
    @PrimaryKey val id: String,
    val number: String?,
    val productId: String,
    val productName: String,
    val channel: String,
    val text: String,
    val status: String,
    val campaignId: String?,
    val campaignCode: String?,
    val scheduledAt: String?,
    val designId: Int,
    val picturePath: String?,
    val tone: String?,
    val angle: String?,
    val note: String?,
    val org: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
