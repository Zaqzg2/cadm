package com.cady.cadymarketing.data.cloud

import com.cady.cadymarketing.data.auth.AuthRepository
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * What one read of an orders collection brings: a sample document (to show the real field names), the documents that
 * carry a campaign code, and how many documents the collection has in all (null when it could not be counted).
 */
data class RawOrders(val sample: RawDoc?, val attributed: List<RawDoc>, val total: Long?)

/** Where the orders come from (the website's, or Kady's invoices). A fake can stand in for it in tests. */
interface OrderSource {
    /** Throws [CloudUnavailableException] without Firebase, or the Firebase exception when the read fails. */
    suspend fun fetch(collection: String, codeField: String, limit: Int): RawOrders
}

/** Reads one collection, read only. Only the documents that carry a campaign code are downloaded. */
@Singleton
class FirestoreOrderSource @Inject constructor(
    private val auth: AuthRepository,
) : OrderSource {
    override suspend fun fetch(collection: String, codeField: String, limit: Int): RawOrders {
        if (!auth.isConfigured) throw CloudUnavailableException("Firebase غير مربوط في هذا البناء")
        val reference = FirebaseFirestore.getInstance().collection(collection)
        val sample = reference.limit(1).get().await().documents.firstOrNull()
            ?.let { RawDoc(it.id, FirestoreValues.plain(it.data)) }
        // A string greater than "" exists only where the field holds a non-empty text: it is the documents that carry a code.
        val attributed = reference.whereGreaterThan(codeField, "").limit(limit.toLong()).get().await()
            .documents.map { RawDoc(it.id, FirestoreValues.plain(it.data)) }
        val total = try {
            reference.count().get(AggregateSource.SERVER).await().count
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return RawOrders(sample, attributed, total)
    }
}
