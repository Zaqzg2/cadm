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
    entities = [
        SyncMetaEntity::class,
        ContentEntity::class,
        CatalogProductEntity::class,
        CatalogOfferEntity::class,
        ProductExtraEntity::class,
        DocEntity::class,
        CampaignEntity::class,
        OccasionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class CadyMarketingDatabase : RoomDatabase() {
    abstract fun syncMetaDao(): SyncMetaDao

    abstract fun contentDao(): ContentDao

    abstract fun catalogDao(): CatalogDao

    abstract fun docDao(): DocDao

    abstract fun campaignDao(): CampaignDao

    abstract fun occasionDao(): OccasionDao

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

        /** Phase 2: the catalogue copy from Kady, the per-product extras, and the stored documents (brand, vocabulary). */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_product` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`unit` TEXT NOT NULL, " +
                        "`price` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_offer` (" +
                        "`id` TEXT NOT NULL, " +
                        "`productId` TEXT NOT NULL, " +
                        "`percent` REAL, " +
                        "`offerPrice` INTEGER, " +
                        "`amount` INTEGER, " +
                        "`startDate` TEXT, " +
                        "`endDate` TEXT, " +
                        "`active` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_offer_productId` ON `catalog_offer` (`productId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `product_extra` (" +
                        "`productId` TEXT NOT NULL, " +
                        "`picturePath` TEXT, " +
                        "`features` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`productId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mkt_doc` (" +
                        "`docKey` TEXT NOT NULL, " +
                        "`json` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`docKey`))",
                )
            }
        }

        /** Phase 3: the campaigns and the calendar's own occasions. The SQL must match CampaignEntities.kt exactly. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `campaign` (" +
                        "`id` TEXT NOT NULL, " +
                        "`code` TEXT NOT NULL, " +
                        "`number` TEXT, " +
                        "`name` TEXT NOT NULL, " +
                        "`goal` TEXT NOT NULL, " +
                        "`startDate` TEXT, " +
                        "`endDate` TEXT, " +
                        "`status` TEXT NOT NULL, " +
                        "`org` TEXT NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_campaign_updatedAt` ON `campaign` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_campaign_code` ON `campaign` (`code`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `occasion` (" +
                        "`id` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`yearly` INTEGER NOT NULL, " +
                        "`org` TEXT NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_occasion_updatedAt` ON `occasion` (`updatedAt`)")
            }
        }
    }
}
