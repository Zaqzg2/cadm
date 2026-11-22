package com.cady.cadymarketing.data.sync

import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.cloud.CloudTimeoutException
import com.cady.cadymarketing.data.cloud.CloudUnavailableException
import com.cady.cadymarketing.data.cloud.FirestoreValues
import com.cady.cadymarketing.domain.engine.J
import com.cady.cadymarketing.domain.engine.NumberRules
import com.cady.cadymarketing.domain.engine.NumberSpec
import com.cady.cadymarketing.domain.engine.PushResult
import com.cady.cadymarketing.domain.engine.RemoteDoc
import com.cady.cadymarketing.domain.engine.SyncRecord
import com.cady.cadymarketing.domain.engine.SyncRemoteStore
import com.cady.cadymarketing.domain.engine.SyncSpecs
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The cloud side of the sync: the `mkt_*` collections of Kady's Firestore project.
 *
 * Every document carries `updatedAt` (the device clock, who wins) and `syncedAt` (the server clock, what a device has
 * not seen yet). Pulling goes by `syncedAt`, so a post written offline days ago and uploaded now still reaches the others.
 * Firestore holds a write while the device is offline and answers it only when the server does, so every write here has
 * a time limit; one that runs out is reported as a lost connection and simply tried again on the next sync.
 */
@Singleton
class FirestoreSyncRemote @Inject constructor(
    private val auth: AuthRepository,
) : SyncRemoteStore {

    private fun db(): FirebaseFirestore {
        if (!auth.isConfigured) throw CloudUnavailableException("Firebase غير مربوط في هذا البناء")
        return FirebaseFirestore.getInstance()
    }

    override suspend fun pull(table: String, sinceServerMs: Long): List<RemoteDoc> {
        val firestore = db()
        val out = ArrayList<RemoteDoc>()
        var last: DocumentSnapshot? = null
        while (true) {
            var query: Query = firestore.collection(table)
                .whereGreaterThan(SYNCED_AT, Timestamp(Date(sinceServerMs)))
                .orderBy(SYNCED_AT)
                .limit(PAGE.toLong())
            if (last != null) query = query.startAfter(last)
            val snapshot = within(READ_TIMEOUT_MS) { query.get().await() }
            for (doc in snapshot.documents) toRemote(doc)?.let { out.add(it) }
            if (snapshot.size() < PAGE) break
            last = snapshot.documents.last()
        }
        return out
    }

    override suspend fun get(table: String, id: String): RemoteDoc? {
        val doc = within(READ_TIMEOUT_MS) { db().collection(table).document(id).get().await() }
        return if (doc.exists()) toRemote(doc) else null
    }

    override suspend fun push(table: String, rows: List<SyncRecord>): PushResult {
        val firestore = db()
        val failed = LinkedHashMap<String, String>()
        for (chunk in rows.chunked(BATCH)) {
            try {
                commit(firestore, table, chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One refused row fails the whole batch: write the rows one by one to find which ones.
                if (e is CloudTimeoutException) throw e
                for (row in chunk) {
                    try {
                        commit(firestore, table, listOf(row))
                    } catch (inner: CancellationException) {
                        throw inner
                    } catch (inner: Exception) {
                        if (inner is CloudTimeoutException) throw inner
                        failed[row.id] = CloudErrors.message(inner)
                    }
                }
            }
        }
        return PushResult(failed)
    }

    private suspend fun commit(firestore: FirebaseFirestore, table: String, rows: List<SyncRecord>) {
        val batch = firestore.batch()
        for (row in rows) {
            val data = LinkedHashMap<String, Any?>(row.data)
            data["updatedAt"] = row.updatedAt
            data[SYNCED_AT] = FieldValue.serverTimestamp()
            batch.set(firestore.collection(table).document(row.id), data, SetOptions.merge())
        }
        within(WRITE_TIMEOUT_MS) {
            batch.commit().await()
            Unit
        }
    }

    override suspend fun allocateNumbers(table: String, spec: NumberSpec, ids: List<String>): Map<String, String> {
        val firestore = db()
        val out = LinkedHashMap<String, String>()
        for (chunk in ids.chunked(NUMBER_CHUNK)) {
            val given = within(WRITE_TIMEOUT_MS) {
                firestore.runTransaction { tx ->
                    val counterRef = firestore.collection(SyncSpecs.COUNTERS).document(spec.counter)
                    // Every read comes before the first write: that is what a Firestore transaction requires.
                    val counter = tx.get(counterRef)
                    var next = counter.getLong("next") ?: 1L
                    val refs: List<DocumentReference> = chunk.map { firestore.collection(table).document(it) }
                    val snaps = refs.map { tx.get(it) }
                    val numbers = LinkedHashMap<String, String>()
                    val fresh = ArrayList<Pair<DocumentReference, String>>()
                    for (index in chunk.indices) {
                        val snap = snaps[index]
                        if (!snap.exists()) continue
                        val existing = snap.getString("number")
                        if (existing != null) {
                            numbers[chunk[index]] = existing
                        } else {
                            val number = NumberRules.format(spec.prefix, next)
                            next++
                            numbers[chunk[index]] = number
                            fresh.add(refs[index] to number)
                        }
                    }
                    if (fresh.isNotEmpty()) {
                        tx.set(counterRef, mapOf("next" to next, "updatedAt" to System.currentTimeMillis()))
                        for ((ref, number) in fresh) {
                            tx.set(ref, mapOf("number" to number, SYNCED_AT to FieldValue.serverTimestamp()), SetOptions.merge())
                        }
                    }
                    numbers
                }.await()
            }
            out.putAll(given)
        }
        return out
    }

    private fun toRemote(doc: DocumentSnapshot): RemoteDoc? {
        val data = FirestoreValues.plain(doc.data)
        val updatedAt = J.long(data, "updatedAt") ?: return null
        val serverMs = J.long(data, SYNCED_AT) ?: return null
        return RemoteDoc(SyncRecord(doc.id, updatedAt, data - SYNCED_AT), serverMs)
    }

    private suspend fun <T : Any> within(limitMs: Long, block: suspend () -> T): T =
        withTimeoutOrNull(limitMs) { block() } ?: throw CloudTimeoutException()

    private companion object {
        const val SYNCED_AT = "syncedAt"
        const val PAGE = 300
        const val BATCH = 100
        const val NUMBER_CHUNK = 20
        const val READ_TIMEOUT_MS = 30_000L
        const val WRITE_TIMEOUT_MS = 25_000L
    }
}
