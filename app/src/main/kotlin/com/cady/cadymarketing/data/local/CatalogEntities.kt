package com.cady.cadymarketing.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A copy of one Kady product. The whole table is replaced on every refresh from Kady. */
@Entity(tableName = "catalog_product")
data class CatalogProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val price: Long,
    val active: Boolean,
    val fetchedAt: Long,
)

/** A copy of one Kady offer, exactly as read (the running one is chosen when it is shown). */
@Entity(tableName = "catalog_offer", indices = [Index(value = ["productId"])])
data class CatalogOfferEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val percent: Double?,
    val offerPrice: Long?,
    val amount: Long?,
    val startDate: String?,
    val endDate: String?,
    val active: Boolean,
)

/**
 * What only the marketing app knows about a product: the picture linked to it on this device and its marketing
 * features (one per line). It is kept apart from the catalogue copy so a refresh from Kady never touches it.
 */
@Entity(tableName = "product_extra")
data class ProductExtraEntity(
    @PrimaryKey val productId: String,
    val picturePath: String?,
    val features: String,
    val updatedAt: Long,
)

/** One stored document (brand, vocabulary): [json] is the whole document. Synced in phase 5 via [updatedAt]. */
@Entity(tableName = "mkt_doc")
data class DocEntity(
    @PrimaryKey val docKey: String,
    val json: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)
