package com.cady.cadymarketing.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference

/**
 * Turns Firestore values into plain Kotlin ones, so the pure engines never see a Firebase type:
 * a Timestamp becomes epoch milliseconds, a reference becomes its id, maps and lists are converted inside.
 */
object FirestoreValues {
    fun plain(data: Map<String, Any?>?): Map<String, Any?> {
        if (data == null) return emptyMap()
        val out = LinkedHashMap<String, Any?>()
        for ((key, value) in data) out[key] = convert(value)
        return out
    }

    private fun convert(value: Any?): Any? = when (value) {
        null -> null
        is Timestamp -> value.toDate().time
        is DocumentReference -> value.id
        is Map<*, *> -> {
            val inner = LinkedHashMap<String, Any?>()
            for ((k, v) in value) inner[k.toString()] = convert(v)
            inner
        }
        is Iterable<*> -> value.map { convert(it) }
        is String, is Number, is Boolean -> value
        else -> value.toString()
    }
}
