package com.translite.app.domain.engine

import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.flow.Flow

interface TranslationEngine {
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult>

    fun observeDownloadingLanguages(): Flow<Set<String>>

    suspend fun isLanguageDownloaded(lang: Language): Boolean

    suspend fun downloadLanguage(lang: Language): Flow<Float>

    suspend fun deleteLanguageModel(lang: Language)
}
