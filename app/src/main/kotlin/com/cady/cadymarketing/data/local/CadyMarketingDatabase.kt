package com.cady.cadymarketing.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local store of the app. New tables are added with explicit migrations — never with destructive
 * fallback, because the marketing content lives here until it syncs.
 */
@Database(
    entities = [SyncMetaEntity::class, ContentEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class CadyMarketingDatabase : RoomDatabase() {
    abstract fun syncMetaDao(): SyncMetaDao

    abstract fun contentDao(): ContentDao

    companion object {
        const val NAME = "cady_marketing.db"

        /** Phase 1: the `content` table. The SQL must match ContentEntity exactly (Room validates it). */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `content` (" +
                        "`id` TEXT NOT NULL, " +
                        "`number` TEXT, " +
                        "`productId` TEXT NOT NULL, " +
                        "`productName` TEXT NOT NULL, " +
                        "`channel` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`campaignId` TEXT, " +
                        "`campaignCode` TEXT, " +
                        "`scheduledAt` TEXT, " +
                        "`designId` INTEGER NOT NULL, " +
                        "`picturePath` TEXT, " +
                        "`tone` TEXT, " +
                        "`angle` TEXT, " +
                        "`note` TEXT, " +
                        "`org` TEXT NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_updatedAt` ON `content` (`updatedAt`)")
            }
        }
    }
}
