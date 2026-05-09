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

/**
 * Translation engine using TranslateGemma via MediaPipe LLM Inference.
 *
 * Model loading priority:
 * 1. Internal storage (filesDir/models/) — previously copied or downloaded
 * 2. Assets directory — bundled model in APK
 * 3. Error if neither found
 *
 * To bundle the model: place translategemma-4b-it-q4_k_m.gguf
 * in app/src/main/assets/ before building.
 */
class GemmaTranslator(
    private val context: Context
) : TranslationEngine {

    private var llmInference: LlmInference? = null
    private var modelReady = false
    private val modelDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }
    private val modelFile: File by lazy {
        File(modelDir, MODEL_FILENAME)
    }
    private val assetsModelPath = "models/$MODEL_FILENAME"

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow {
        emit(emptySet())
    }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean {
        ensureModelReady()
        return modelReady
    }

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = flow {
        // Model should be pre-bundled in assets
        // Try to copy from assets to internal storage
        if (modelFile.exists()) {
            modelReady = true
            emit(1f)
            return@flow
        }

        // Try to copy from assets
        val copied = copyModelFromAssets()
        if (copied) {
            modelReady = true
            emit(1f)
            return@flow
        }

        // Model not found anywhere
        throw Exception("翻译模型未找到。请将 $MODEL_FILENAME 放入 APK 的 assets/models/ 目录后重新编译。")
    }

    override suspend fun deleteLanguageModel(lang: Language) {
        llmInference?.close()
        llmInference = null
        modelReady = false
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

                if (!modelReady || llmInference == null) {
                    return@withContext Result.failure(
                        Exception("翻译模型未加载。请先在设置中加载模型。")
                    )
                }

                val prompt = buildPrompt(text, sourceLang, targetLang)
                val response = llmInference?.generateResponse(prompt)
                    ?: return@withContext Result.failure(Exception("Model not initialized"))

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

        // Check internal storage first
        if (modelFile.exists()) {
            initLlmInference()
            modelReady = true
            return
        }

        // Try to copy from assets
        val copied = copyModelFromAssets()
        if (copied && modelFile.exists()) {
            initLlmInference()
            modelReady = true
            return
        }
    }

    private fun copyModelFromAssets(): Boolean {
        return try {
            val assetManager = context.assets
            // Check if model exists in assets
            val assetFiles = assetManager.list("") ?: emptyArray()
            val modelInAssets = assetFiles.any { it == "models" } &&
                    (assetManager.list("models") ?: emptyArray()).any { it == MODEL_FILENAME }

            if (!modelInAssets) {
                // Also check root assets (user may place it directly)
                val rootAssets = assetManager.list("") ?: emptyArray()
                if (rootAssets.any { it == MODEL_FILENAME }) {
                    // Copy from root assets
                    assetManager.open(MODEL_FILENAME).use { input ->
                        FileOutputStream(modelFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return true
                }
                return false
            }

            assetManager.open(assetsModelPath).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Model copied from assets to ${modelFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy model from assets: ${e.message}")
            false
        }
    }

    private fun initLlmInference() {
        if (!modelFile.exists()) {
            throw Exception("Model file not found at ${modelFile.absolutePath}")
        }

        Log.i(TAG, "Loading model from ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024}MB)")

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setResultListener { partialResult, _ ->
                // Streaming callback (optional)
            }
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    private fun buildPrompt(text: String, sourceLang: Language, targetLang: Language): String {
        val srcName = langName(sourceLang)
        val tgtName = langName(targetLang)
        return """Translate the following text from $srcName to $tgtName. Output ONLY the translation, nothing else.

Text: $text

Translation:"""
    }

    private fun extractTranslation(response: String): String {
        // Clean up the response - remove prompt echo and formatting
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
        llmInference?.close()
        llmInference = null
        modelReady = false
    }

    companion object {
        private const val TAG = "GemmaTranslator"
        const val MODEL_FILENAME = "translategemma-4b-it-q4_k_m.gguf"
    }
}
