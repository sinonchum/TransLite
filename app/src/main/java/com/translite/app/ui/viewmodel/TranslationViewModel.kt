package com.translite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translite.app.data.db.entity.TranslationEntity
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.domain.engine.GemmaTranslator
import com.translite.app.domain.engine.OnlineTranslator
import com.translite.app.domain.model.Language
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TranslationUiState(
    val inputText: String = "",
    val resultText: String = "",
    val sourceLang: Language = Language.AUTO,
    val targetLang: Language = Language.CHINESE,
    val isTranslating: Boolean = false,
    val error: String? = null,
    val languages: List<Language> = Language.entries.toList(),
    val isOfflineMode: Boolean = false,
    val isModelDownloading: Boolean = false,
    val downloadProgress: Int = 0
)

class TranslationViewModel(
    private val repository: TranslationRepository,
    private val onlineEngine: OnlineTranslator,
    private val offlineEngine: GemmaTranslator
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<TranslationEntity>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<TranslationEntity>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Observe offline model download state
        viewModelScope.launch {
            offlineEngine.downloadState.collect { state ->
                when (state) {
                    is GemmaTranslator.DownloadState.Downloading -> {
                        _uiState.update { it.copy(isModelDownloading = true, downloadProgress = 0) }
                    }
                    is GemmaTranslator.DownloadState.Progress -> {
                        _uiState.update { it.copy(downloadProgress = state.percent) }
                    }
                    is GemmaTranslator.DownloadState.Ready -> {
                        _uiState.update { it.copy(isModelDownloading = false, isOfflineMode = true) }
                    }
                    is GemmaTranslator.DownloadState.Error -> {
                        _uiState.update {
                            it.copy(
                                isModelDownloading = false,
                                error = state.message
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun setSourceLang(lang: Language) {
        _uiState.update { it.copy(sourceLang = lang) }
    }

    fun setTargetLang(lang: Language) {
        _uiState.update { it.copy(targetLang = lang) }
    }

    fun swapLanguages() {
        _uiState.update {
            it.copy(
                sourceLang = it.targetLang,
                targetLang = it.sourceLang,
                inputText = it.resultText,
                resultText = it.inputText
            )
        }
    }

    fun toggleOfflineMode() {
        val current = _uiState.value.isOfflineMode
        _uiState.update { it.copy(isOfflineMode = !current) }
    }

    fun downloadOfflineModel() {
        viewModelScope.launch {
            try {
                offlineEngine.downloadLanguage(Language.AUTO).collect { /* handled by downloadState */ }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun translate() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, error = null) }

            val sourceLang = if (state.sourceLang == Language.AUTO) Language.ENGLISH else state.sourceLang

            // Use offline engine if available and enabled
            val result = if (state.isOfflineMode) {
                offlineEngine.translate(state.inputText, sourceLang, state.targetLang)
            } else {
                onlineEngine.translate(state.inputText, sourceLang, state.targetLang)
            }

            result
                .onSuccess { translationResult ->
                    _uiState.update {
                        it.copy(
                            resultText = translationResult.translatedText,
                            isTranslating = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "翻译失败",
                            isTranslating = false
                        )
                    }
                }
        }
    }

    fun clearInput() {
        _uiState.update { it.copy(inputText = "", resultText = "", error = null) }
    }

    fun toggleFavorite(entity: TranslationEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(entity.id, !entity.isFavorite)
        }
    }

    fun deleteTranslation(id: Long) {
        viewModelScope.launch {
            repository.deleteTranslation(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
