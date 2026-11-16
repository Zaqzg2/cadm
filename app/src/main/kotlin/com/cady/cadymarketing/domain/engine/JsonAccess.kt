package com.cady.cadymarketing.domain.engine

/**
 * Safe readers for the values MiniJson.parse returns, and for Firestore documents turned into plain maps.
 * Every reader answers null (or empty) instead of throwing, so a document from an older or newer app
 * version can never crash the screen that reads it.
 */
object J {
    fun map(value: Any?): Map<String, Any?> {
        val raw = value as? Map<*, *> ?: return emptyMap()
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in raw) out[k.toString()] = v
        return out
    }

    fun list(value: Any?): List<Any?> = (value as? List<*>)?.toList() ?: emptyList()

    fun strings(value: Any?): List<String> = list(value).mapNotNull { it as? String }

    fun str(map: Map<String, Any?>, key: String): String? = map[key] as? String

    fun long(map: Map<String, Any?>, key: String): Long? = when (val v = map[key]) {
        is Number -> v.toLong()
        is String -> v.trim().toDoubleOrNull()?.toLong()
        else -> null
    }

    fun double(map: Map<String, Any?>, key: String): Double? = when (val v = map[key]) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }

    fun bool(map: Map<String, Any?>, key: String): Boolean? = when (val v = map[key]) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when (v.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
        else -> null
    }

    /** Parses stored JSON into a map; text that is null, empty or broken gives an empty map. */
    fun parseMap(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            map(MiniJson.parse(json))
        } catch (e: MiniJson.ParseException) {
            emptyMap()
        }
    }
}
