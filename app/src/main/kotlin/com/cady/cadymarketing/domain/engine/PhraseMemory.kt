package com.cady.cadymarketing.domain.engine

import kotlin.random.Random

/**
 * Remembers the phrases used recently so the next generation starts with the ones used longest ago
 * (never-used phrases first). This is what keeps new texts from repeating the old ones.
 */
object PhraseMemory {
    const val CAPACITY = 80

    /** Shuffled, then ordered by how long ago each phrase was last used (the sort is stable). */
    fun fresh(candidates: List<String>, used: List<String>, random: Random): List<String> =
        candidates.shuffled(random).sortedBy { used.lastIndexOf(it) }

    fun remember(used: List<String>, newlyUsed: List<String>): List<String> =
        (used + newlyUsed).takeLast(CAPACITY)
}
