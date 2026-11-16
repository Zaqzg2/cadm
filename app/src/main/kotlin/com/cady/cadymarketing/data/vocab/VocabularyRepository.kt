package com.cady.cadymarketing.data.vocab

import com.cady.cadymarketing.data.local.DocDao
import com.cady.cadymarketing.data.local.DocEntity
import com.cady.cadymarketing.domain.engine.MergedVocabulary
import com.cady.cadymarketing.domain.engine.VocabularyEdits
import com.cady.cadymarketing.domain.engine.VocabularyJson
import com.cady.cadymarketing.domain.engine.VocabularyMerge
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The vocabulary edits are one stored document; the engines get the merge of the built-in bank and the edits. */
@Singleton
class VocabularyRepository @Inject constructor(
    private val docs: DocDao,
) {
    fun observeEdits(): Flow<VocabularyEdits> = docs.observe(KEY).map { VocabularyJson.decode(it?.json) }

    fun observeMerged(): Flow<MergedVocabulary> = observeEdits().map { VocabularyMerge.merge(it) }

    suspend fun save(edits: VocabularyEdits) {
        val now = System.currentTimeMillis()
        docs.upsert(DocEntity(KEY, VocabularyJson.encode(edits.copy(updatedAt = now)), now, null))
    }

    companion object {
        const val KEY = "vocabulary"
    }
}
