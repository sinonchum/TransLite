package com.translite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translite.app.data.db.entity.TranslationEntity
import com.translite.app.data.repository.TranslationRepository
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
    val languages: List<Language> = Language.entries.toList()
)

class TranslationViewModel(
    private val repository: TranslationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<TranslationEntity>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<TranslationEntity>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun translate() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, error = null) }

            val sourceLang = if (state.sourceLang == Language.AUTO) Language.ENGLISH else state.sourceLang

            repository.translate(state.inputText, sourceLang, state.targetLang)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            resultText = result.translatedText,
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
