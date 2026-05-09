package com.translite.app.data.repository

import com.translite.app.data.db.TranslationDao
import com.translite.app.data.db.entity.TranslationEntity
import com.translite.app.domain.engine.TranslationEngine
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.flow.Flow

class TranslationRepository(
    private val engine: TranslationEngine,
    private val dao: TranslationDao
) {
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult> {
        val result = engine.translate(text, sourceLang, targetLang)

        result.onSuccess { translation ->
            dao.insert(
                TranslationEntity(
                    originalText = translation.originalText,
                    translatedText = translation.translatedText,
                    sourceLangCode = translation.sourceLang.code,
                    targetLangCode = translation.targetLang.code,
                    isOffline = translation.isOffline
                )
            )
        }

        return result
    }

    fun getHistory(): Flow<List<TranslationEntity>> = dao.getAll()

    fun getFavorites(): Flow<List<TranslationEntity>> = dao.getFavorites()

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        dao.setFavorite(id, isFavorite)
    }

    suspend fun deleteTranslation(id: Long) = dao.deleteById(id)

    suspend fun clearHistory() = dao.deleteAll()

    fun searchHistory(query: String): Flow<List<TranslationEntity>> = dao.search(query)
}
