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
    // Model download/load state
    val isModelReady: Boolean = false,
    val isModelDownloading: Boolean = false,
    val modelStatusText: String = "",
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
        // Check if model already exists on startup
        viewModelScope.launch {
            val exists = offlineEngine.isModelDownloaded()
            _uiState.update { it.copy(isModelReady = exists) }

            // If model exists but engine not loaded, initialize it in background
            if (exists) {
                _uiState.update { it.copy(modelStatusText = "正在加载模型...") }
                try {
                    offlineEngine.ensureModelAvailable()
                    _uiState.update {
                        it.copy(isModelReady = true, modelStatusText = "模型已就绪")
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(modelStatusText = "模型加载失败: ${e.message}")
                    }
                }
            }
        }

        // Observe offline model download state
        viewModelScope.launch {
            offlineEngine.downloadState.collect { state ->
                when (state) {
                    is GemmaTranslator.DownloadState.Downloading -> {
                        _uiState.update {
                            it.copy(
                                isModelDownloading = true,
                                modelStatusText = "正在下载模型...",
                                downloadProgress = 0
                            )
                        }
                    }
                    is GemmaTranslator.DownloadState.Progress -> {
                        _uiState.update {
                            it.copy(
                                downloadProgress = state.percent,
                                modelStatusText = "下载中... ${state.percent}%"
                            )
                        }
                    }
                    is GemmaTranslator.DownloadState.Copying -> {
                        _uiState.update {
                            it.copy(
                                isModelDownloading = true,
                                modelStatusText = "正在从安装包解压模型...",
                                downloadProgress = 0
                            )
                        }
                    }
                    is GemmaTranslator.DownloadState.Loading -> {
                        _uiState.update {
                            it.copy(
                                isModelDownloading = true,
                                modelStatusText = "下载完成，正在加载模型...",
                                downloadProgress = 100
                            )
                        }
                    }
                    is GemmaTranslator.DownloadState.Ready -> {
                        _uiState.update {
                            it.copy(
                                isModelReady = true,
                                isModelDownloading = false,
                                isOfflineMode = true,
                                modelStatusText = "模型已就绪 ✓",
                                downloadProgress = 100
                            )
                        }
                    }
                    is GemmaTranslator.DownloadState.Error -> {
                        _uiState.update {
                            it.copy(
                                isModelDownloading = false,
                                modelStatusText = "",
                                error = state.message
                            )
                        }
                    }
                    is GemmaTranslator.DownloadState.Idle -> {
                        // Don't reset status text here — keep showing last state
                    }
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
