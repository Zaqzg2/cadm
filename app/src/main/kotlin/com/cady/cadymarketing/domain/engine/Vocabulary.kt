package com.cady.cadymarketing.domain.engine

/**
 * The phrases of one tone. Every line may hold {placeholders}.
 * [urgency] lines are used when the offer has an end date; [questions] open a post with a question.
 */
data class ToneVocabulary(
    val hooks: List<String>,
    val prices: List<String>,
    val offers: List<String>,
    val calls: List<String>,
    val urgency: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
)

/** A saved text template with {placeholders}. */
data class TextTemplate(val id: String, val name: String, val text: String)
