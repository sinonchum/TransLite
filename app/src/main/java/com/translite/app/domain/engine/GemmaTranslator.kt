package com.translite.app.domain.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Translation engine using MediaPipe LLM Inference API.
 *
 * Supports two models:
 * - translate-gemma-4b-it (official translation model, ~3.9GB int8)
 * - gemma3-1b-it (smaller general model, ~555MB q4, good for translation)
 *
 * Model is downloaded from HuggingFace on first use.
 * User must accept Gemma license on HuggingFace first.
 */
class GemmaTranslator(
    private val context: Context
) : TranslationEngine {

    @Volatile private var llmInference: LlmInference? = null
    @Volatile private var modelReady = false
    private val initLock = Any()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    sealed class DownloadState {
        data object Idle : DownloadState()
        data object Downloading : DownloadState()
        data class Progress(val percent: Int) : DownloadState()
        data object Ready : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val modelDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    // Primary: translate-gemma-4b-it (official)
    private val primaryModelFile: File by lazy {
        File(modelDir, "translategemma-4b-it-int8-web.task")
    }

    // Fallback: gemma3-1b-it (smaller, q4 quantized)
    private val fallbackModelFile: File by lazy {
        File(modelDir, "gemma3-1b-it-q4-ekv2048.task")
    }

    private val activeModelFile: File
        get() = if (primaryModelFile.exists()) primaryModelFile else fallbackModelFile

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow {
        emit(emptySet())
    }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean {
        ensureModelReady()
        return modelReady
    }

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = flow {
        if (activeModelFile.exists()) {
            synchronized(initLock) {
                if (!modelReady) initLlmInference()
            }
            _downloadState.value = DownloadState.Ready
            emit(1f)
            return@flow
        }

        // Try assets first
        val copied = copyModelFromAssets()
        if (copied) {
            synchronized(initLock) {
                if (!modelReady) initLlmInference()
            }
            _downloadState.value = DownloadState.Ready
            emit(1f)
            return@flow
        }

        // Download from HuggingFace (try primary model first, then fallback)
        val models = listOf(
            ModelInfo(
                primaryModelFile,
                "https://huggingface.co/litert-community/TranslateGemma-4B-IT/resolve/main/translategemma-4b-it-int8-web.task",
                "translate-gemma-4b"
            ),
            ModelInfo(
                fallbackModelFile,
                "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
                "gemma3-1b"
            )
        )

        for (model in models) {
            if (model.file.exists()) continue

            try {
                Log.i(TAG, "Downloading ${model.name} from: ${model.url}")
                _downloadState.value = DownloadState.Downloading
                emit(0f)
                withContext(Dispatchers.IO) {
                    downloadFromUrl(model.url, model.file)
                }
                synchronized(initLock) {
                    if (!modelReady) initLlmInference()
                }
                _downloadState.value = DownloadState.Ready
                emit(1f)
                return@flow
            } catch (e: Exception) {
                Log.w(TAG, "Download failed from ${model.url}: ${e.message}")
                model.file.delete()
            }
        }

        val errorMsg = "翻译模型下载失败。请先在 HuggingFace 接受 Gemma 许可协议，然后重试。"
        _downloadState.value = DownloadState.Error(errorMsg)
        throw Exception(errorMsg)
    }

    override suspend fun deleteLanguageModel(lang: Language) {
        synchronized(initLock) {
            llmInference?.close()
            llmInference = null
            modelReady = false
        }
        primaryModelFile.delete()
        fallbackModelFile.delete()
        _downloadState.value = DownloadState.Idle
    }

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult> {
        return withContext(Dispatchers.IO) {
            try {
                ensureModelReady()

                if (!modelReady || llmInference == null) {
                    return@withContext Result.failure(
                        Exception("翻译模型未加载。请先在设置中下载模型。")
                    )
                }

                val inference = llmInference
                    ?: return@withContext Result.failure(Exception("Model not initialized"))

                val prompt = buildPrompt(text, sourceLang, targetLang)
                Log.i(TAG, "Generating translation with prompt length: ${prompt.length}")

                val response = inference.generateResponse(prompt)
                val translated = extractTranslation(response)

                Log.i(TAG, "Translation result: $translated")

                Result.success(
                    TranslationResult(
                        originalText = text,
                        translatedText = translated,
                        sourceLang = sourceLang,
                        targetLang = targetLang,
                        isOffline = true
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun ensureModelAvailable(): Boolean {
        if (modelReady && activeModelFile.exists()) return true

        synchronized(initLock) {
            if (activeModelFile.exists()) {
                initLlmInference()
                return true
            }
            val copied = copyModelFromAssets()
            if (copied && activeModelFile.exists()) {
                initLlmInference()
                return true
            }
        }

        return false
    }

    private fun ensureModelReady() {
        if (modelReady && activeModelFile.exists()) return

        synchronized(initLock) {
            if (modelReady && activeModelFile.exists()) return

            if (activeModelFile.exists()) {
                initLlmInference()
                return
            }
            val copied = copyModelFromAssets()
            if (copied && activeModelFile.exists()) {
                initLlmInference()
            }
        }
    }

    private fun copyModelFromAssets(): Boolean {
        return try {
            val assetManager = context.assets
            val modelsDir = assetManager.list("models") ?: emptyArray()

            // Try to copy primary model
            for (modelName in listOf(
                "translategemma-4b-it-int8-web.task",
                "gemma3-1b-it-q4-ekv2048.task"
            )) {
                if (modelsDir.any { it == modelName }) {
                    val targetFile = File(modelDir, modelName)
                    _downloadState.value = DownloadState.Downloading
                    assetManager.open("models/$modelName").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Model copied from assets/models/$modelName")
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy model from assets: ${e.message}")
            false
        }
    }

    private fun downloadFromUrl(urlStr: String, targetFile: File) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != 200) {
                throw Exception("HTTP ${connection.responseCode}")
            }

            val contentLength = connection.contentLength.toLong()
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        if (contentLength > 0) {
                            val progress = (downloaded * 100 / contentLength).toInt()
                            _downloadState.value = DownloadState.Progress(progress)
                        }
                    }
                }
            }

            Log.i(TAG, "Downloaded ${downloaded / 1024 / 1024}MB to ${targetFile.name}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun initLlmInference() {
        val modelFile = activeModelFile
        if (!modelFile.exists()) {
            throw Exception("Model file not found at ${modelFile.absolutePath}")
        }

        Log.i(TAG, "Loading model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")

        // Correct MediaPipe LLM Inference API usage
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .setResultListener { _, _ -> }
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
        modelReady = true
        Log.i(TAG, "Model loaded successfully")
    }

    private fun buildPrompt(text: String, sourceLang: Language, targetLang: Language): String {
        val srcName = langName(sourceLang)
        val tgtName = langName(targetLang)

        // Use the official TranslateGemma prompt template for best results
        return """You are a professional $srcName to $tgtName translator. Your goal is to accurately convey the meaning and nuances of the original text.

Produce only the $tgtName translation, without any additional explanations or commentary. Please translate the following $srcName text into $tgtName:

$text"""
    }

    private fun extractTranslation(response: String): String {
        return response
            .replace(Regex("^(Translate|Translation|Output|Here|The translation).*?\\n", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
    }

    private fun langName(lang: Language): String = when (lang) {
        Language.AUTO -> "any language"
        Language.CHINESE -> "Chinese"
        Language.ENGLISH -> "English"
        Language.FRENCH -> "French"
        Language.SPANISH -> "Spanish"
        Language.GERMAN -> "German"
        Language.JAPANESE -> "Japanese"
        Language.KOREAN -> "Korean"
        Language.RUSSIAN -> "Russian"
        Language.PORTUGUESE -> "Portuguese"
        Language.ARABIC -> "Arabic"
    }

    fun close() {
        synchronized(initLock) {
            llmInference?.close()
            llmInference = null
            modelReady = false
        }
    }

    companion object {
        private const val TAG = "GemmaTranslator"
    }

    private data class ModelInfo(
        val file: File,
        val url: String,
        val name: String
    )
}
