package com.translite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translite.app.data.db.entity.TranslationEntity
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.domain.engine.EngineRouter
import com.translite.app.domain.engine.GemmaTranslator
import com.translite.app.domain.engine.ModelManager
import com.translite.app.domain.model.Language
import android.util.Log
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
    // Model state
    val isModelReady: Boolean = false,
    val isModelDownloading: Boolean = false,
    val modelStatusText: String = "",
    val downloadProgress: Int = 0,
    val downloadSpeedMBps: Float = 0f,
    val downloadDownloadedMB: Long = 0,
    val downloadTotalMB: Long = 0,
    // Dual-mode state
    val translationMode: EngineRouter.TranslationMode = EngineRouter.TranslationMode.STANDARD,
    val isPhi4Downloaded: Boolean = false,
    val isModeSwitching: Boolean = false,
    val academicResultJson: String? = null,
    // Phi-4 download state
    val isPhi4Downloading: Boolean = false,
    val phi4DownloadProgress: Int = 0,
    val phi4DownloadedMB: Long = 0,
    val phi4TotalMB: Long = 0,
    val phi4SpeedMBps: Float = 0f,
    val phi4StatusText: String = ""
)

class TranslationViewModel(
    private val repository: TranslationRepository,
    private val engineRouter: EngineRouter,
    private val gemmaEngine: GemmaTranslator,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<TranslationEntity>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<TranslationEntity>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val gemmaReady = modelManager.isModelDownloaded(ModelManager.ModelType.GEMMA_4_E2B)
            val phi4Ready = modelManager.isModelDownloaded(ModelManager.ModelType.PHI_4_MINI)
            _uiState.update { it.copy(isModelReady = gemmaReady, isPhi4Downloaded = phi4Ready) }
            if (gemmaReady) {
                _uiState.update { it.copy(modelStatusText = "Loading model...") }
                try {
                    gemmaEngine.ensureModelAvailable()
                    _uiState.update { it.copy(isModelReady = true, modelStatusText = "Model ready") }
                } catch (e: Exception) {
                    _uiState.update { it.copy(modelStatusText = "Model load failed: ${e.message ?: "Unknown"}") }
                }
            }
        }
        viewModelScope.launch {
            gemmaEngine.downloadState.collect { state ->
                when (state) {
                    is GemmaTranslator.DownloadState.Downloading -> _uiState.update { it.copy(isModelDownloading = true, modelStatusText = "Downloading model...", downloadProgress = 0) }
                    is GemmaTranslator.DownloadState.Progress -> _uiState.update { it.copy(downloadProgress = state.percent, downloadSpeedMBps = state.speedMBps, downloadDownloadedMB = state.downloadedMB, downloadTotalMB = state.totalMB, modelStatusText = "Downloading... ${state.percent}%") }
                    is GemmaTranslator.DownloadState.Copying -> _uiState.update { it.copy(isModelDownloading = true, modelStatusText = "Extracting model...", downloadProgress = 0) }
                    is GemmaTranslator.DownloadState.Loading -> _uiState.update { it.copy(isModelDownloading = true, modelStatusText = "Download complete, loading...", downloadProgress = 100) }
                    is GemmaTranslator.DownloadState.Ready -> _uiState.update { it.copy(isModelReady = true, isModelDownloading = false, isOfflineMode = true, modelStatusText = "Model ready ✓", downloadProgress = 100) }
                    is GemmaTranslator.DownloadState.Error -> _uiState.update { it.copy(isModelDownloading = false, modelStatusText = "", error = state.message) }
                    else -> {}
                }
            }
        }
    }

    fun updateInput(text: String) { _uiState.update { it.copy(inputText = text) } }
    fun setSourceLang(lang: Language) { _uiState.update { it.copy(sourceLang = lang) } }
    fun setTargetLang(lang: Language) { _uiState.update { it.copy(targetLang = lang) } }

    fun swapLanguages() {
        _uiState.update { it.copy(sourceLang = it.targetLang, targetLang = it.sourceLang, inputText = it.resultText, resultText = it.inputText) }
    }

    fun toggleOfflineMode() {
        val current = _uiState.value.isOfflineMode
        _uiState.update { it.copy(isOfflineMode = !current) }
    }

    fun downloadOfflineModel() {
        viewModelScope.launch {
            try { gemmaEngine.downloadLanguage(Language.AUTO).collect { } }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun downloadPhi4Model() {
        android.util.Log.d("TransLite", "downloadPhi4Model called!")
        viewModelScope.launch {
            _uiState.update { it.copy(isPhi4Downloading = true, phi4StatusText = "Starting Phi-4 download...", phi4DownloadProgress = 0) }
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    modelManager.downloadModel(ModelManager.ModelType.PHI_4_MINI) { percent, downloadedMB, totalMB, speedMBps ->
                        _uiState.update {
                            it.copy(phi4DownloadProgress = percent, phi4DownloadedMB = downloadedMB, phi4TotalMB = totalMB, phi4SpeedMBps = speedMBps, phi4StatusText = "Downloading Phi-4... $percent%")
                        }
                    }
                }
                _uiState.update { it.copy(isPhi4Downloading = false, isPhi4Downloaded = true, phi4StatusText = "Phi-4 ready ✓", phi4DownloadProgress = 100) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPhi4Downloading = false, phi4StatusText = "", phi4DownloadProgress = 0, error = "Phi-4 download failed: ${e.message ?: "Unknown"}") }
            }
        }
    }

    fun switchTranslationMode(mode: EngineRouter.TranslationMode) {
        if (mode == _uiState.value.translationMode) return
        viewModelScope.launch {
            _uiState.update { it.copy(isModeSwitching = true, error = null) }

            // If switching to Academic and Phi-4 not downloaded, download first
            if (mode == EngineRouter.TranslationMode.ACADEMIC && !_uiState.value.isPhi4Downloaded) {
                _uiState.update { it.copy(phi4StatusText = "Downloading Phi-4 model first...", isPhi4Downloading = true, phi4DownloadProgress = 0) }
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        modelManager.downloadModel(ModelManager.ModelType.PHI_4_MINI) { percent, downloadedMB, totalMB, speedMBps ->
                            _uiState.update {
                                it.copy(phi4DownloadProgress = percent, phi4DownloadedMB = downloadedMB, phi4TotalMB = totalMB, phi4SpeedMBps = speedMBps, phi4StatusText = "Downloading Phi-4... $percent%")
                            }
                        }
                    }
                    _uiState.update { it.copy(isPhi4Downloaded = true, isPhi4Downloading = false, phi4StatusText = "Phi-4 ready ✓", phi4DownloadProgress = 100) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isPhi4Downloading = false, isModeSwitching = false, phi4StatusText = "", phi4DownloadProgress = 0, error = "Phi-4 download failed: ${e.message ?: "Unknown"}")
                    }
                    return@launch
                }
            }

            // Now switch engine
            val success = engineRouter.switchMode(mode)
            _uiState.update {
                it.copy(translationMode = if (success) mode else it.translationMode, isModeSwitching = false, isOfflineMode = true, error = if (!success) "Model switch failed" else null)
            }
        }
    }

    fun translate() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, error = null, academicResultJson = null) }
            val sourceLang = if (state.sourceLang == Language.AUTO) Language.ENGLISH else state.sourceLang
            val result = if (state.isOfflineMode) {
                if (state.translationMode == com.translite.app.domain.engine.EngineRouter.TranslationMode.ACADEMIC) {
                    // Fix #3: 15s hard timeout for Phi-4, fallback to Gemma on timeout
                    val phi4Result = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                        engineRouter.translate(state.inputText, sourceLang, state.targetLang, com.translite.app.domain.engine.EngineRouter.TranslationMode.ACADEMIC)
                    }
                    if (phi4Result != null) {
                        phi4Result
                    } else {
                        Log.w("TransLite", "Phi-4 timed out (15s), falling back to Gemma")
                        _uiState.update { it.copy(error = "Phi-4 timed out — using Gemma instead") }
                        engineRouter.translate(state.inputText, sourceLang, state.targetLang, com.translite.app.domain.engine.EngineRouter.TranslationMode.STANDARD)
                    }
                } else {
                    engineRouter.translate(state.inputText, sourceLang, state.targetLang, state.translationMode)
                }
            } else {
                com.translite.app.domain.engine.OnlineTranslator().translate(state.inputText, sourceLang, state.targetLang)
            }
                .onSuccess { tr ->
                    _uiState.update { it.copy(resultText = tr.translatedText, isTranslating = false, academicResultJson = tr.analysisData) }
                    repository.saveTranslation(TranslationEntity(originalText = tr.originalText, translatedText = tr.translatedText, sourceLangCode = sourceLang.code, targetLangCode = state.targetLang.code, isOffline = state.isOfflineMode, mode = state.translationMode.name.lowercase(), analysisData = tr.analysisData))
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message ?: "Translation failed", isTranslating = false) } }
        }
    }

    fun clearInput() { _uiState.update { it.copy(inputText = "", resultText = "", error = null, academicResultJson = null) } }
    fun toggleFavorite(entity: TranslationEntity) { viewModelScope.launch { repository.toggleFavorite(entity.id, !entity.isFavorite) } }
    fun deleteTranslation(id: Long) { viewModelScope.launch { repository.deleteTranslation(id) } }
    fun clearHistory() { viewModelScope.launch { repository.clearHistory() } }
}
