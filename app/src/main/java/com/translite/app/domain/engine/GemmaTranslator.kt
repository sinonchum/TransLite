package com.translite.app.domain.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Translation engine using TranslateGemma via MediaPipe LLM Inference.
 *
 * Thread-safe singleton pattern: use (application as TransLiteApp).gemmaTranslator.
 */
class GemmaTranslator(
    private val context: Context
) : TranslationEngine {

    @Volatile private var llmInference: LlmInference? = null
    @Volatile private var modelReady = false
    private val initLock = Any()

    private val modelDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
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
        if (modelFile.exists()) {
            synchronized(initLock) {
                if (!modelReady) initLlmInference()
            }
            emit(1f)
            return@flow
        }

        // Try assets first
        val copied = copyModelFromAssets()
        if (copied) {
            synchronized(initLock) {
                if (!modelReady) initLlmInference()
            }
            emit(1f)
            return@flow
        }

        // Try Chinese mirror first, then HuggingFace
        val urls = listOf(
            "https://hf-mirror.com/mradermacher/translategemma-4b-it-GGUF/resolve/main/translategemma-4b-it.Q4_K_M.gguf",
            "https://huggingface.co/mradermacher/translategemma-4b-it-GGUF/resolve/main/translategemma-4b-it.Q4_K_M.gguf"
        )

        for (urlStr in urls) {
            try {
                Log.i(TAG, "Trying download from: $urlStr")
                emit(0f)
                downloadFromUrl(urlStr)
                synchronized(initLock) {
                    if (!modelReady) initLlmInference()
                }
                emit(1f)
                return@flow
            } catch (e: Exception) {
                Log.w(TAG, "Download failed from $urlStr: ${e.message}")
                modelFile.delete()
            }
        }

        throw Exception("翻译模型下载失败。请手动将 $MODEL_FILENAME 放入内部存储 models/ 目录。")
    }

    override suspend fun deleteLanguageModel(lang: Language) {
        synchronized(initLock) {
            llmInference?.close()
            llmInference = null
            modelReady = false
        }
        modelFile.delete()
    }

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult> {
        return withContext(Dispatchers.IO) {
            try {
                ensureModelReady()

                val inference = llmInference
                if (!modelReady || inference == null) {
                    return@withContext Result.failure(
                        Exception("翻译模型未加载。请先在设置中加载模型。")
                    )
                }

                val prompt = buildPrompt(text, sourceLang, targetLang)
                val response = inference.generateResponse(prompt)
                val translated = extractTranslation(response)

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
                Result.failure(e)
            }
        }
    }

    private fun ensureModelReady() {
        if (modelReady && modelFile.exists()) return

        synchronized(initLock) {
            if (modelReady && modelFile.exists()) return

            if (modelFile.exists()) {
                initLlmInference()
                return
            }

            val copied = copyModelFromAssets()
            if (copied && modelFile.exists()) {
                initLlmInference()
            }
        }
    }

    private fun copyModelFromAssets(): Boolean {
        return try {
            val assetManager = context.assets
            val modelsDir = assetManager.list("models") ?: emptyArray()
            if (modelsDir.any { it == MODEL_FILENAME }) {
                assetManager.open("models/$MODEL_FILENAME").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Model copied from assets/models/")
                return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy model from assets: ${e.message}")
            false
        }
    }

    private fun downloadFromUrl(urlStr: String) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != 200) {
                throw Exception("HTTP ${connection.responseCode}")
            }

            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                    }
                }
            }

            Log.i(TAG, "Downloaded ${downloaded / 1024 / 1024}MB from $urlStr")
        } finally {
            connection?.disconnect()
        }
    }

    private fun initLlmInference() {
        if (!modelFile.exists()) {
            throw Exception("Model file not found at ${modelFile.absolutePath}")
        }

        Log.i(TAG, "Loading model: ${modelFile.length() / 1024 / 1024}MB")

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setResultListener { _, _ -> }
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
        modelReady = true
    }

    private fun buildPrompt(text: String, sourceLang: Language, targetLang: Language): String {
        val srcName = langName(sourceLang)
        val tgtName = langName(targetLang)
        return """Translate the following text from $srcName to $tgtName. Output ONLY the translation, nothing else.

Text: $text

Translation:"""
    }

    private fun extractTranslation(response: String): String {
        return response
            .replace(Regex("^(Translate|Translation|Output).*?\n", RegexOption.DOT_MATCHES_ALL), "")
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
        const val MODEL_FILENAME = "translategemma-4b-it-q4_k_m.gguf"
    }
}
