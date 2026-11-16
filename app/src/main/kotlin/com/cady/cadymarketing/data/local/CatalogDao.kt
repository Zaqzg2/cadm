package com.cady.cadymarketing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_product ORDER BY name")
    fun observeProducts(): Flow<List<CatalogProductEntity>>

    @Query("SELECT * FROM catalog_offer")
    fun observeOffers(): Flow<List<CatalogOfferEntity>>

    @Query("SELECT * FROM product_extra")
    fun observeExtras(): Flow<List<ProductExtraEntity>>

    @Query("SELECT * FROM product_extra WHERE productId = :productId")
    suspend fun extra(productId: String): ProductExtraEntity?

    @Upsert
    suspend fun upsertExtra(entity: ProductExtraEntity)

    @Upsert
    suspend fun upsertProducts(rows: List<CatalogProductEntity>)

    @Upsert
    suspend fun upsertOffers(rows: List<CatalogOfferEntity>)

    @Query("DELETE FROM catalog_product")
    suspend fun clearProducts()

    @Query("DELETE FROM catalog_offer")
    suspend fun clearOffers()
}
