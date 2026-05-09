package com.translite.app.domain.engine

import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FallbackTranslator(
    private val baseUrl: String = "https://libretranslate.com"
) : TranslationEngine {

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow { emit(emptySet()) }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean = false

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = flow {
        emit(1f)
    }

    override suspend fun deleteLanguageModel(lang: Language) { }

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = URL("$baseUrl/translate?q=$encoded&source=${sourceLang.code}&target=${targetLang.code}&format=text")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 10000

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val translatedText = body.substringAfter("\"translatedText\":\"").substringBefore("\"")
                    Result.success(
                        TranslationResult(
                            originalText = text,
                            translatedText = translatedText,
                            sourceLang = sourceLang,
                            targetLang = targetLang,
                            isOffline = false
                        )
                    )
                } else {
                    Result.failure(Exception("API error: ${conn.responseCode}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
