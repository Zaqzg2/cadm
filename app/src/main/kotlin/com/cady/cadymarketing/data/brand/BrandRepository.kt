package com.cady.cadymarketing.data.brand

import com.cady.cadymarketing.domain.model.Brand
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Where the engines and the poster get the brand identity. Phase 2 reads the Identity screen instead. */
interface BrandRepository {
    fun observeBrand(): Flow<Brand>
}

@Singleton
class DefaultBrandRepository @Inject constructor() : BrandRepository {
    override fun observeBrand(): Flow<Brand> = flowOf(Brand())
}
