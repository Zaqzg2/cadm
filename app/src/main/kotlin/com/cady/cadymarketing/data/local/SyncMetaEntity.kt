package com.cady.cadymarketing.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * When each Firestore collection was last synced to this device (used by the sync engine in phase 5).
 * It is also what proves the Room stack builds from the very first phase.
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val collection: String,
    val lastSyncAt: Long,
)
