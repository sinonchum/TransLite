package com.translite.app.domain.languagemanager

import com.translite.app.domain.engine.TranslationEngine
import com.translite.app.domain.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LanguagePackageManager(
    private val engine: TranslationEngine
) {
    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus

    data class DownloadStatus(
        val language: Language,
        val progress: Float,
        val isDownloading: Boolean,
        val isDownloaded: Boolean
    )

    suspend fun checkAllLanguages(): Map<Language, Boolean> {
        return Language.entries.filter { it != Language.AUTO }.associateWith { lang ->
            engine.isLanguageDownloaded(lang)
        }
    }

    suspend fun downloadLanguage(lang: Language) {
        val current = _downloadStatus.value.toMutableMap()
        current[lang.code] = DownloadStatus(lang, 0f, true, false)
        _downloadStatus.value = current

        engine.downloadLanguage(lang).collect { progress ->
            val updated = _downloadStatus.value.toMutableMap()
            updated[lang.code] = DownloadStatus(lang, progress, progress < 1f, progress >= 1f)
            _downloadStatus.value = updated
        }
    }

    suspend fun deleteLanguage(lang: Language) {
        engine.deleteLanguageModel(lang)
        val current = _downloadStatus.value.toMutableMap()
        current.remove(lang.code)
        _downloadStatus.value = current
    }
}
