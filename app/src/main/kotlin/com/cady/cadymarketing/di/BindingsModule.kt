package com.cady.cadymarketing.di

import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.data.brand.RoomBrandRepository
import com.cady.cadymarketing.data.cloud.CatalogSource
import com.cady.cadymarketing.data.cloud.FirestoreCatalogSource
import com.cady.cadymarketing.data.gateway.HttpGatewayTransport
import com.cady.cadymarketing.data.product.CatalogProductRepository
import com.cady.cadymarketing.data.product.ProductRepository
import com.cady.cadymarketing.domain.engine.GatewayTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the interfaces the screens depend on to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindProducts(impl: CatalogProductRepository): ProductRepository

    @Binds
    abstract fun bindBrand(impl: RoomBrandRepository): BrandRepository

    @Binds
    abstract fun bindGatewayTransport(impl: HttpGatewayTransport): GatewayTransport

    @Binds
    abstract fun bindCatalogSource(impl: FirestoreCatalogSource): CatalogSource
}
