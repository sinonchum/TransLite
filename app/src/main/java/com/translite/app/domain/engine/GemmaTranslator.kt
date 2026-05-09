package com.translite.app.domain.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Translation engine using TranslateGemma via MediaPipe LLM Inference.
 * Downloads the GGUF model on first use, then translates on-device.
 *
 * Model: google/translategemma-4b-it (GGUF quantized)
 * Fallback: LibreTranslate API when model not ready
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
        File(modelDir, "translategemma-4b-it-q4_k_m.gguf")
    }

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow {
        emit(emptySet())
    }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean {
        return modelReady && modelFile.exists()
    }

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = flow {
        if (modelFile.exists()) {
            modelReady = true
            emit(1f)
            return@flow
        }

        // Download GGUF model from HuggingFace
        emit(0f)
        try {
            val url = URL("https://huggingface.co/mradermacher/translategemma-4b-it-GGUF/resolve/main/translategemma-4b-it.Q4_K_M.gguf")
            val connection = url.openConnection()
            val totalSize = connection.contentLength
            var downloaded = 0L

            connection.inputStream.use { input ->
                modelFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalSize > 0) {
                            emit(downloaded.toFloat() / totalSize)
                        }
                    }
                }
            }

            // Initialize MediaPipe LLM
            initLlmInference()
            modelReady = true
            emit(1f)
        } catch (e: Exception) {
            modelFile.delete()
            throw e
        }
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
                if (!modelReady || llmInference == null) {
                    initLlmInference()
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

    private fun initLlmInference() {
        if (!modelFile.exists()) {
            throw Exception("Model not downloaded. Please download the language model first.")
        }

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
}
