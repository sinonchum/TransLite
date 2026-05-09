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
 *
 * Uses the LiteRT-LM framework which is Google's recommended runtime for Gemma models.
 * Supports .litertlm and .task model formats.
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
        data class Progress(val percent: Int) : DownloadState()
        data object Ready : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val modelDir: File by lazy {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.mkdirs()
        dir
    }

    // Primary: Gemma 4 E2B (litertlm format, native Android)
    private val primaryModelFile: File by lazy {
        File(modelDir, "gemma-4-E2B-it.litertlm")
    }

    // Alternative: Web .task format (TFLite flatbuffer)
    private val webModelFile: File by lazy {
        File(modelDir, "gemma-4-E2B-it-web.task")
    }

    // Source on shared storage
    private val externalModelFile: File by lazy {
        File("/sdcard/translite_model.task")
    }

    private val activeModelFile: File
        get() = when {
            primaryModelFile.exists() && primaryModelFile.length() > 100_000_000 -> primaryModelFile
            webModelFile.exists() && webModelFile.length() > 100_000_000 -> webModelFile
            else -> primaryModelFile
        }

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow {
        emit(emptySet())
    }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean {
        ensureModelReady()
        return modelReady
    }

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = flow {
        if (activeModelFile.exists() && activeModelFile.length() > 100_000_000) {
            synchronized(initLock) {
                if (!modelReady) initEngine()
            }
            _downloadState.value = DownloadState.Ready
            emit(1f)
            return@flow
        }

        // Try to copy from /sdcard/ if available
        val sdcardCopied = withContext(Dispatchers.IO) { copyFromSdcardIfNeeded() }
        if (sdcardCopied) {
            synchronized(initLock) {
                if (!modelReady) initEngine()
            }
            _downloadState.value = DownloadState.Ready
            emit(1f)
            return@flow
        }

        // Try assets first
        val copied = copyModelFromAssets()
        if (copied) {
            synchronized(initLock) {
                if (!modelReady) initEngine()
            }
            _downloadState.value = DownloadState.Ready
            emit(1f)
            return@flow
        }

        // Get HF token from SharedPreferences
        val hfToken = getHfToken()

        // Download from HuggingFace
        val models = listOf(
            ModelInfo(
                primaryModelFile,
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                "gemma-4-e2b-litertlm"
            ),
            ModelInfo(
                webModelFile,
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task",
                "gemma-4-e2b-web"
            )
        )

        for (model in models) {
            if (model.file.exists() && model.file.length() > 100_000_000) continue

            try {
                Log.i(TAG, "Downloading ${model.name} from: ${model.url}")
                _downloadState.value = DownloadState.Downloading
                emit(0f)
                withContext(Dispatchers.IO) {
                    downloadFromUrl(model.url, model.file, hfToken)
                }
                synchronized(initLock) {
                    if (!modelReady) initEngine()
                }
                _downloadState.value = DownloadState.Ready
                emit(1f)
                return@flow
            } catch (e: Exception) {
                Log.w(TAG, "Download failed from ${model.url}: ${e.message}")
                model.file.delete()
            }
        }

        _downloadState.value = DownloadState.Error("翻译模型下载失败。请检查网络连接。")
        throw Exception("翻译模型下载失败")
    }

    override suspend fun deleteLanguageModel(lang: Language) {
        synchronized(initLock) {
            engine?.close()
            engine = null
            modelReady = false
        }
        primaryModelFile.delete()
        webModelFile.delete()
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

                // Create a conversation for this translation
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
        if (modelReady && activeModelFile.exists()) return true

        synchronized(initLock) {
            if (activeModelFile.exists()) {
                initEngine()
                return true
            }
            val copied = copyModelFromAssets()
            if (copied && activeModelFile.exists()) {
                initEngine()
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
                initEngine()
                return
            }
            val copied = copyModelFromAssets()
            if (copied && activeModelFile.exists()) {
                initEngine()
            }
        }
    }

    private fun getHfToken(): String? {
        return context.getSharedPreferences("translite_prefs", Context.MODE_PRIVATE)
            .getString("hf_token", null)
    }

    fun setHfToken(token: String) {
        context.getSharedPreferences("translite_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("hf_token", token)
            .apply()
    }

    fun getHfTokenFromPrefs(): String {
        return context.getSharedPreferences("translite_prefs", Context.MODE_PRIVATE)
            .getString("hf_token", "") ?: ""
    }

    private fun initEngine() {
        val modelFile = activeModelFile
        if (!modelFile.exists() || modelFile.length() < 100_000_000) {
            throw Exception("Model file not found or too small at ${modelFile.absolutePath} (${modelFile.length()} bytes)")
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

            for (modelName in listOf(
                "gemma-4-E2B-it.litertlm",
                "gemma-4-E2B-it-web.task"
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

    private fun copyFromSdcardIfNeeded(): Boolean {
        // Try .litertlm first, then .task
        val candidates = listOf(
            primaryModelFile to externalModelFile,
            webModelFile to File("/sdcard/translite_model.task")
        )

        for ((target, source) in candidates) {
            if (target.exists() && target.length() > 100_000_000) return true

            if (source.exists() && source.length() > 100_000_000) {
                return try {
                    Log.i(TAG, "Copying model from ${source.absolutePath} to ${target.absolutePath}...")
                    _downloadState.value = DownloadState.Downloading
                    source.inputStream().use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Model copied: ${target.length() / 1024 / 1024}MB")
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Copy failed from ${source.absolutePath}: ${e.message}")
                    false
                }
            }
        }

        return false
    }

    private fun downloadFromUrl(urlStr: String, targetFile: File, hfToken: String?) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 300_000  // 5 min for large models
            connection.requestMethod = "GET"

            if (!hfToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $hfToken")
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                throw Exception("HTTP $responseCode")
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

    private fun buildPrompt(text: String, sourceLang: Language, targetLang: Language): String {
        val srcName = langName(sourceLang)
        val tgtName = langName(targetLang)

        // Gemma 4 instruction format: <start_of_turn>user ... <start_of_turn>model
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
    }

    private data class ModelInfo(
        val file: File,
        val url: String,
        val name: String
    )
}
