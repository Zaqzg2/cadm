package com.cady.cadymarketing.di

import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.data.brand.DefaultBrandRepository
import com.cady.cadymarketing.data.gateway.HttpGatewayTransport
import com.cady.cadymarketing.data.product.ProductRepository
import com.cady.cadymarketing.data.product.SampleProductRepository
import com.cady.cadymarketing.domain.engine.GatewayTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the interfaces the Studio depends on to today's implementations. Phase 2 swaps products and brand. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindProducts(impl: SampleProductRepository): ProductRepository

    @Binds
    abstract fun bindBrand(impl: DefaultBrandRepository): BrandRepository

    @Binds
    abstract fun bindGatewayTransport(impl: HttpGatewayTransport): GatewayTransport
}
