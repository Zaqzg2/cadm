package com.cady.cadymarketing.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What the cloud is known to hold for one document: [syncedUpdatedAt] is the `updatedAt` of the copy that was last
 * uploaded or taken. A row whose own `updatedAt` is newer than this is waiting to be uploaded.
 */
@Entity(tableName = "sync_state", primaryKeys = ["collection", "docId"])
data class SyncStateEntity(
    val collection: String,
    val docId: String,
    val syncedUpdatedAt: Long,
)

/** One line of the operations log. It is only ever added to; [at] is also its `updatedAt` for the sync. */
@Entity(tableName = "op_log", indices = [Index(value = ["at"])])
data class OpLogEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val actor: String,
    val action: String,
    val subject: String,
    val detail: String,
    val org: String,
    val schemaVersion: Int,
)
