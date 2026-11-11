package com.cady.cadymarketing.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local store of the app. New tables are added with explicit migrations — never with destructive
 * fallback, because the marketing content lives here until it syncs.
 */
@Database(
    entities = [SyncMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CadyMarketingDatabase : RoomDatabase() {
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val NAME = "cady_marketing.db"
    }
}
