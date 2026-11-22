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
        CustomerEntity::class,
        ConsentEntity::class,
        SegmentEntity::class,
        SendMarkEntity::class,
        ResultEntity::class,
        OrderEntity::class,
        SyncStateEntity::class,
        OpLogEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class CadyMarketingDatabase : RoomDatabase() {
    abstract fun syncMetaDao(): SyncMetaDao

    abstract fun contentDao(): ContentDao

    abstract fun catalogDao(): CatalogDao

    abstract fun docDao(): DocDao

    abstract fun campaignDao(): CampaignDao

    abstract fun occasionDao(): OccasionDao

    abstract fun audienceDao(): AudienceDao

    abstract fun reportDao(): ReportDao

    abstract fun syncDao(): SyncDao

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

        /** Phase 4: the customers copy, consent, segments, send lists, results by hand and the orders read for the reports. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `customer_copy` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`phone` TEXT, " +
                        "`rawPhone` TEXT NOT NULL, " +
                        "`area` TEXT NOT NULL, " +
                        "`repName` TEXT NOT NULL, " +
                        "`active` INTEGER NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `consent` (" +
                        "`customerId` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`org` TEXT NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`customerId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consent_updatedAt` ON `consent` (`updatedAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `segment` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`area` TEXT NOT NULL, " +
                        "`repName` TEXT NOT NULL, " +
                        "`memberIds` TEXT NOT NULL, " +
                        "`org` TEXT NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_segment_updatedAt` ON `segment` (`updatedAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `send_mark` (" +
                        "`contentId` TEXT NOT NULL, " +
                        "`customerId` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`org` TEXT NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`contentId`, `customerId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_send_mark_updatedAt` ON `send_mark` (`updatedAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `result` (" +
                        "`id` TEXT NOT NULL, " +
                        "`campaignId` TEXT NOT NULL, " +
                        "`channel` TEXT, " +
                        "`metric` TEXT NOT NULL, " +
                        "`value` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`org` TEXT NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_result_updatedAt` ON `result` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_result_campaignId` ON `result` (`campaignId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `report_order` (" +
                        "`id` TEXT NOT NULL, " +
                        "`origin` TEXT NOT NULL, " +
                        "`campaignCode` TEXT NOT NULL, " +
                        "`channel` TEXT, " +
                        "`amount` INTEGER, " +
                        "`date` TEXT, " +
                        "`cancelled` INTEGER NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_order_origin` ON `report_order` (`origin`)")
            }
        }

        /** Phase 5: what the cloud is known to hold per document (for the sync) and the operations log. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (" +
                        "`collection` TEXT NOT NULL, " +
                        "`docId` TEXT NOT NULL, " +
                        "`syncedUpdatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`collection`, `docId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `op_log` (" +
                        "`id` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, " +
                        "`actor` TEXT NOT NULL, " +
                        "`action` TEXT NOT NULL, " +
                        "`subject` TEXT NOT NULL, " +
                        "`detail` TEXT NOT NULL, " +
                        "`org` TEXT NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_op_log_at` ON `op_log` (`at`)")
            }
        }
    }
}
