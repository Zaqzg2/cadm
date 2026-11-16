package com.cady.cadymarketing.domain.engine

/** The marketing features of a product are typed one per line; this is the single place that reads and writes them. */
object ProductText {
    fun features(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun featuresText(features: List<String>): String = features.joinToString("\n")
}
