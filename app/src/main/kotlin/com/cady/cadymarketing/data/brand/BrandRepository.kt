package com.cady.cadymarketing.data.brand

import com.cady.cadymarketing.data.local.DocDao
import com.cady.cadymarketing.data.local.DocEntity
import com.cady.cadymarketing.domain.engine.BrandJson
import com.cady.cadymarketing.domain.model.Brand
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Where the engines, the poster and the theme get the brand identity. */
interface BrandRepository {
    fun observeBrand(): Flow<Brand>

    suspend fun save(brand: Brand)
}

/** The brand is one stored document; until something is saved the defaults of the original tool apply. */
@Singleton
class RoomBrandRepository @Inject constructor(
    private val docs: DocDao,
) : BrandRepository {
    override fun observeBrand(): Flow<Brand> =
        docs.observe(KEY).map { BrandJson.decode(it?.json) }.distinctUntilChanged()

    override suspend fun save(brand: Brand) {
        docs.upsert(DocEntity(KEY, BrandJson.encode(brand), System.currentTimeMillis(), null))
    }

    companion object {
        const val KEY = "brand"
    }
}
