package com.cady.cadymarketing.data.cloud

import com.cady.cadymarketing.data.auth.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Where Kady's customers come from. A fake can stand in for it in tests. */
interface CustomerSource {
    /** Throws [CloudUnavailableException] without Firebase, or the Firebase exception when the read fails. */
    suspend fun fetch(): List<RawDoc>
}

/** Reads Kady's `customers` collection (read only: the marketing app never writes to it). */
@Singleton
class FirestoreCustomerSource @Inject constructor(
    private val auth: AuthRepository,
) : CustomerSource {
    override suspend fun fetch(): List<RawDoc> {
        if (!auth.isConfigured) throw CloudUnavailableException("Firebase غير مربوط في هذا البناء")
        val snapshot = FirebaseFirestore.getInstance().collection(CUSTOMERS).get().await()
        return snapshot.documents.map { RawDoc(it.id, FirestoreValues.plain(it.data)) }
    }

    private companion object {
        const val CUSTOMERS = "customers"
    }
}
