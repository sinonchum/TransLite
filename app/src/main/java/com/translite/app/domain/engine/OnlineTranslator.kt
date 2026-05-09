package com.translite.app.domain.engine

import android.util.Log
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Translation engine using MyMemory API (free, no API key needed).
 * Works on any device with internet access — no GMS required.
 * Rate limit: 5000 words/day for anonymous users.
 */
class OnlineTranslator : TranslationEngine {

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow {
        emit(emptySet())
    }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean = true

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
                val src = if (sourceLang == Language.AUTO) "autodetect" else sourceLang.code
                val tgt = targetLang.code

                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = URL("https://api.mymemory.translated.net/get?q=$encoded&langpair=$src|$tgt")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()

                    // Use proper JSON parsing instead of naive substring matching
                    val json = JSONObject(body)
                    val responseData = json.optJSONObject("responseData")
                    val translated = responseData?.optString("translatedText", "") ?: ""

                    Log.i(TAG, "Translation result: $translated")

                    if (translated.isNotBlank() && translated != text) {
                        Result.success(
                            TranslationResult(
                                originalText = text,
                                translatedText = translated,
                                sourceLang = sourceLang,
                                targetLang = targetLang,
                                isOffline = false
                            )
                        )
                    } else {
                        Result.failure(Exception("翻译结果为空"))
                    }
                } else {
                    Result.failure(Exception("API错误: ${conn.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                Result.failure(e)
            }
        }
    }

    fun close() { }

    companion object {
        private const val TAG = "OnlineTranslator"
        const val MODEL_FILENAME = "online_api"
    }
}
