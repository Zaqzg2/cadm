package com.cady.cadymarketing.data.cloud

import com.cady.cadymarketing.data.auth.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** One document as read from the cloud: its id and its fields as plain values. */
data class RawDoc(val id: String, val data: Map<String, Any?>)

data class RawCatalog(val products: List<RawDoc>, val offers: List<RawDoc>)

/** Where Kady's catalogue comes from. A fake can stand in for it in tests. */
interface CatalogSource {
    /** Throws [CloudUnavailableException] without Firebase, or the Firebase exception when the read fails. */
    suspend fun fetch(): RawCatalog
}

/** Reads Kady's `products` and `offers` collections (read only: the marketing app never writes to them). */
@Singleton
class FirestoreCatalogSource @Inject constructor(
    private val auth: AuthRepository,
) : CatalogSource {
    override suspend fun fetch(): RawCatalog {
        if (!auth.isConfigured) throw CloudUnavailableException("Firebase غير مربوط في هذا البناء")
        val db = FirebaseFirestore.getInstance()
        val products = db.collection(PRODUCTS).get().await()
        val offers = db.collection(OFFERS).get().await()
        return RawCatalog(
            products = products.documents.map { RawDoc(it.id, FirestoreValues.plain(it.data)) },
            offers = offers.documents.map { RawDoc(it.id, FirestoreValues.plain(it.data)) },
        )
    }

    private companion object {
        const val PRODUCTS = "products"
        const val OFFERS = "offers"
    }
}
