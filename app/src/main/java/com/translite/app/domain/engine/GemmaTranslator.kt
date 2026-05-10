package com.translite.app.domain.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
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
 * Translation engine using LiteRT-LM (Gemma 4) for fully offline on-device translation.
 */
class GemmaTranslator(
    private val context: Context
) : TranslationEngine {

    @Volatile private var engine: Engine? = null
    @Volatile private var modelReady = false
    private val initLock = Any()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    sealed class DownloadState {
        data object Idle : DownloadState()
        data object Downloading : DownloadState()
        data class Progress(
            val percent: Int,
            val downloadedMB: Long = 0,
            val totalMB: Long = 0,
            val speedMBps: Float = 0f
        ) : DownloadState()
        data object Copying : DownloadState()
        data object Loading : DownloadState()
        data object Ready : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val modelDir: File by lazy {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.mkdirs()
        dir
    }

    private val modelFile: File by lazy {
        File(modelDir, MODEL_FILENAME)
    }

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow {
        emit(emptySet())
    }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean {
        ensureModelReady()
        return modelReady
    }

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = flow {
        // If model already exists and engine is ready, nothing to do
        if (modelReady && modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE) {
            _downloadState.value = DownloadState.Ready
            emit(1f)
            return@flow
        }

        // If model file exists but engine not initialized, just load it
        if (modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE) {
            _downloadState.value = DownloadState.Loading
            emit(0.5f)
            withContext(Dispatchers.IO) {
                synchronized(initLock) {
                    if (!modelReady) initEngine()
                }
            }
            _downloadState.value = DownloadState.Ready
            emit(1f)
            return@flow
        }

        // Try assets first (bundled in APK)
        _downloadState.value = DownloadState.Copying
        emit(0f)
        val copied = withContext(Dispatchers.IO) { copyModelFromAssets() }
        if (copied && modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE) {
            _downloadState.value = DownloadState.Loading
            withContext(Dispatchers.IO) {
                synchronized(initLock) {
                    if (!modelReady) initEngine()
                }
            }
            _downloadState.value = DownloadState.Ready
            emit(1f)
            return@flow
        }

        // Download from HuggingFace (public repo, no token required)
        _downloadState.value = DownloadState.Downloading
        emit(0f)
        try {
            withContext(Dispatchers.IO) {
                downloadFromHuggingFace { progress, downloadedMB, totalMB, speed ->
                    _downloadState.value = DownloadState.Progress(progress, downloadedMB, totalMB, speed)
                }
            }

            // Download complete — now load the engine
            _downloadState.value = DownloadState.Loading
            withContext(Dispatchers.IO) {
                synchronized(initLock) {
                    if (!modelReady) initEngine()
                }
            }
            _downloadState.value = DownloadState.Ready
            emit(1f)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            modelFile.delete()
            _downloadState.value = DownloadState.Error(
                "模型下载失败: ${e.message}\n请检查网络连接后重试。"
            )
            throw e
        }
    }

    override suspend fun deleteLanguageModel(lang: Language) {
        synchronized(initLock) {
            engine?.close()
            engine = null
            modelReady = false
        }
        modelFile.delete()
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

                if (!modelReady || engine == null) {
                    return@withContext Result.failure(
                        Exception("翻译模型未加载。请先在设置中下载模型。")
                    )
                }

                val eng = engine
                    ?: return@withContext Result.failure(Exception("Model not initialized"))

                val prompt = buildPrompt(text, sourceLang, targetLang)
                Log.i(TAG, "Generating translation with prompt length: ${prompt.length}")

                val conversationConfig = ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 1,
                        topP = 1.0,
                        temperature = 0.1
                    )
                )

                val conversation = eng.createConversation(conversationConfig)
                val response = conversation.use { conv ->
                    conv.sendMessage(prompt)
                }

                val translated = extractTranslation(response.toString())
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
        if (modelReady && modelFile.exists()) return true

        synchronized(initLock) {
            if (modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE) {
                initEngine()
                return true
            }
            val copied = copyModelFromAssets()
            if (copied && modelFile.exists()) {
                initEngine()
                return true
            }
        }

        return false
    }

    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE
    }

    private fun ensureModelReady() {
        if (modelReady && modelFile.exists()) return

        synchronized(initLock) {
            if (modelReady && modelFile.exists()) return

            if (modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE) {
                initEngine()
                return
            }
            val copied = copyModelFromAssets()
            if (copied && modelFile.exists()) {
                initEngine()
            }
        }
    }

    private fun initEngine() {
        if (!modelFile.exists() || modelFile.length() < MIN_MODEL_SIZE) {
            throw Exception(
                "Model file not found or too small at ${modelFile.absolutePath} " +
                "(${modelFile.length()} bytes)"
            )
        }

        Log.i(TAG, "Loading model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")

        val engineConfig = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.path
        )

        engine = Engine(engineConfig)
        engine!!.initialize()
        modelReady = true
        Log.i(TAG, "LiteRT-LM engine initialized successfully")
    }

    private fun copyModelFromAssets(): Boolean {
        return try {
            val assetManager = context.assets
            val modelsDir = assetManager.list("models") ?: emptyArray()

            if (!modelsDir.any { it == MODEL_FILENAME }) {
                Log.d(TAG, "Model $MODEL_FILENAME not found in assets/models/")
                return false
            }

            Log.i(TAG, "Copying bundled model from assets...")
            _downloadState.value = DownloadState.Copying

            assetManager.open("models/$MODEL_FILENAME").use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var totalCopied = 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalCopied += bytesRead
                    }
                }
            }

            Log.i(TAG, "Model copied: ${modelFile.length() / 1024 / 1024}MB")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy model from assets: ${e.message}")
            modelFile.delete()
            false
        }
    }

    private fun downloadFromHuggingFace(onProgress: (Int, Long, Long, Float) -> Unit) {
        val tmpFile = File(modelFile.path + ".tmp")

        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 600_000
            connection.requestMethod = "GET"
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                throw Exception("HTTP $responseCode from HuggingFace")
            }

            val contentLength = connection.contentLength.toLong()
            val totalMB = contentLength / (1024 * 1024)
            Log.i(TAG, "Downloading model from HuggingFace (${totalMB}MB)...")

            connection.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(1024 * 1024) // 1MB buffer
                    var totalDownloaded = 0L
                    var bytesRead: Int
                    val startTime = System.currentTimeMillis()
                    var lastReportTime = startTime
                    var lastReportBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastReportTime

                        // Report every 500ms to avoid flooding the UI
                        if (elapsed >= 500 || totalDownloaded == contentLength) {
                            val totalElapsed = (now - startTime).coerceAtLeast(1)
                            val speed = (totalDownloaded.toFloat() / 1024 / 1024) / (totalElapsed / 1000f)
                            val downloadedMB = totalDownloaded / (1024 * 1024)
                            val percent = if (contentLength > 0) {
                                (totalDownloaded * 100 / contentLength).toInt()
                            } else {
                                0
                            }

                            onProgress(percent, downloadedMB, totalMB, speed)
                            lastReportTime = now
                            lastReportBytes = totalDownloaded
                        }
                    }
                }
            }

            if (modelFile.exists()) modelFile.delete()
            tmpFile.renameTo(modelFile)

            Log.i(TAG, "Download complete: ${modelFile.length() / 1024 / 1024}MB")
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        } finally {
            tmpFile.delete()
        }
    }

    private fun buildPrompt(text: String, sourceLang: Language, targetLang: Language): String {
        val srcName = langName(sourceLang)
        val tgtName = langName(targetLang)

        return "<start_of_turn>user\nYou are a professional $srcName to $tgtName translator. Translate the following text accurately and concisely. Output ONLY the translation, nothing else.\n\n$text\n<start_of_turn>model\n"
    }

    private fun extractTranslation(response: String): String {
        return response
            .replace(Regex("<start_of_turn>.*?"), "")
            .replace(Regex("<end_of_turn>.*?"), "")
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .ifEmpty { response.trim() }
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
            engine?.close()
            engine = null
            modelReady = false
        }
    }

    companion object {
        private const val TAG = "GemmaTranslator"
        private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        private const val MIN_MODEL_SIZE = 100_000_000L

        private const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    }
}
