package com.translite.app.domain.engine

import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MlKitTranslator : TranslationEngine {

    private val downloadingLanguages = mutableSetOf<String>()

    override fun observeDownloadingLanguages(): Flow<Set<String>> = callbackFlow {
        trySend(downloadingLanguages.toSet())
        awaitClose {}
    }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean {
        return try {
            val code = toMlCode(lang) ?: return false
            val model = TranslateRemoteModel.Builder(code).build()
            val modelManager = RemoteModelManager.getInstance()
            modelManager.isModelDownloaded(model).await()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = callbackFlow {
        downloadingLanguages.add(lang.code)
        trySend(0f)

        val code = toMlCode(lang)
        if (code == null) {
            close(Exception("Unsupported language: ${lang.code}"))
            return@callbackFlow
        }

        val model = TranslateRemoteModel.Builder(code).build()
        val modelManager = RemoteModelManager.getInstance()

        try {
            val conditions = com.google.mlkit.common.model.DownloadConditions.Builder().build()
            modelManager.download(model, conditions).await()
            downloadingLanguages.remove(lang.code)
            trySend(1f)
            close()
        } catch (e: Exception) {
            downloadingLanguages.remove(lang.code)
            close(e)
        }

        awaitClose {}
    }

    override suspend fun deleteLanguageModel(lang: Language) {
        val code = toMlCode(lang) ?: return
        val model = TranslateRemoteModel.Builder(code).build()
        try {
            val modelManager = RemoteModelManager.getInstance()
            modelManager.deleteDownloadedModel(model).await()
        } catch (_: Exception) { }
    }

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult> {
        return try {
            val srcCode = toMlCode(sourceLang)
                ?: return Result.failure(Exception("Unsupported source: ${sourceLang.code}"))
            val tgtCode = toMlCode(targetLang)
                ?: return Result.failure(Exception("Unsupported target: ${targetLang.code}"))

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcCode)
                .setTargetLanguage(tgtCode)
                .build()

            val translator = Translation.getClient(options)
            val translated = translator.translate(text).await()
            translator.close()

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

    /**
     * Map our Language enum to ML Kit language code strings.
     * ML Kit uses ISO 639-1 codes like "en", "zh", "fr", etc.
     */
    private fun toMlCode(lang: Language): String? {
        return when (lang) {
            Language.AUTO -> null
            Language.CHINESE -> "zh"
            Language.ENGLISH -> "en"
            Language.FRENCH -> "fr"
            Language.SPANISH -> "es"
            Language.GERMAN -> "de"
            Language.JAPANESE -> "ja"
            Language.KOREAN -> "ko"
            Language.RUSSIAN -> "ru"
            Language.PORTUGUESE -> "pt"
            Language.ARABIC -> "ar"
        }
    }
}
