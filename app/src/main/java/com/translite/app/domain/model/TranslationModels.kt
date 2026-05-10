package com.translite.app.domain.model

data class TranslationRequest(
    val text: String,
    val sourceLang: Language,
    val targetLang: Language
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: Language,
    val targetLang: Language,
    val timestamp: Long = System.currentTimeMillis(),
    val isOffline: Boolean = true,
    val analysisData: String? = null  // JSON string for academic mode results
)
