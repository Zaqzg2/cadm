package com.cady.cadymarketing.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One marketing campaign. [code] is the short code carried by the campaign's links (never changes once links are
 * shared). [number] (CMP-0001) is assigned at the first sync, so it is null for now. A deleted row only gets [deletedAt].
 */
@Entity(
    tableName = "campaign",
    indices = [Index(value = ["updatedAt"]), Index(value = ["code"])],
)
data class CampaignEntity(
    @PrimaryKey val id: String,
    val code: String,
    val number: String?,
    val name: String,
    val goal: String,
    val startDate: String?,
    val endDate: String?,
    val status: String,
    val org: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** An occasion the manager added to the calendar. A [yearly] one repeats on the same day every year. */
@Entity(tableName = "occasion", indices = [Index(value = ["updatedAt"])])
data class OccasionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val date: String,
    val yearly: Boolean,
    val org: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
