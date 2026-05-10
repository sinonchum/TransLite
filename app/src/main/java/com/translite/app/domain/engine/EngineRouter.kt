package com.translite.app.domain.engine

import android.util.Log
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Routes translation requests to the appropriate engine based on mode.
 * Handles model hot-swapping with memory cleanup.
 */
class EngineRouter(
    private val gemmaEngine: GemmaTranslator,
    private val phi4Engine: Phi4Translator
) {
    enum class TranslationMode { STANDARD, ACADEMIC }

    @Volatile
    var currentMode: TranslationMode = TranslationMode.STANDARD
        private set

    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        mode: TranslationMode = currentMode
    ): Result<TranslationResult> {
        return withContext(Dispatchers.IO) {
            when (mode) {
                TranslationMode.STANDARD -> {
                    if (!gemmaEngine.ensureModelAvailable()) {
                        return@withContext Result.failure(Exception("Gemma 4 model not available"))
                    }
                    gemmaEngine.translate(text, sourceLang, targetLang)
                }
                TranslationMode.ACADEMIC -> {
                    if (!phi4Engine.ensureModelAvailable()) {
                        return@withContext Result.failure(Exception("Phi-4 model not available"))
                    }
                    phi4Engine.translate(text, sourceLang, targetLang)
                }
            }
        }
    }

    /**
     * Switch active mode. Must run on IO dispatcher to avoid blocking Main thread.
     */
    suspend fun switchMode(mode: TranslationMode): Boolean {
        if (mode == currentMode) return true
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "Switching from $currentMode to $mode")

            // Step 1: Unload current engine
            when (currentMode) {
                TranslationMode.STANDARD -> {
                    Log.i(TAG, "Unloading Gemma engine...")
                    gemmaEngine.close()
                }
                TranslationMode.ACADEMIC -> {
                    Log.i(TAG, "Unloading Phi-4 engine...")
                    phi4Engine.close()
                }
            }

            // Step 2: Force GC with coroutine-friendly delay
            System.gc()
            delay(300)
            System.gc()
            delay(200)

            // Step 3: Load new engine
            val success = when (mode) {
                TranslationMode.STANDARD -> {
                    try {
                        Log.i(TAG, "Loading Gemma engine...")
                        gemmaEngine.ensureModelAvailable()
                        Log.i(TAG, "Gemma loaded OK")
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load Gemma: ${e.message}", e)
                        false
                    }
                }
                TranslationMode.ACADEMIC -> {
                    try {
                        Log.i(TAG, "Loading Phi-4 engine...")
                        phi4Engine.ensureModelAvailable()
                        Log.i(TAG, "Phi-4 loaded OK")
                        true
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "OOM loading Phi-4 — device RAM too low", e)
                        false
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load Phi-4: ${e.message}", e)
                        false
                    }
                }
            }

            if (success) {
                currentMode = mode
                Log.i(TAG, "Switched to $mode")
            } else {
                // Fallback: reload previous engine
                Log.w(TAG, "Fallback: reloading previous engine (${currentMode})")
                try {
                    when (currentMode) {
                        TranslationMode.STANDARD -> gemmaEngine.ensureModelAvailable()
                        TranslationMode.ACADEMIC -> phi4Engine.ensureModelAvailable()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback reload failed: ${e.message}", e)
                }
            }

            success
        }
    }

    fun isModelReady(mode: TranslationMode): Boolean = when (mode) {
        TranslationMode.STANDARD -> gemmaEngine.isModelDownloaded()
        TranslationMode.ACADEMIC -> phi4Engine.isModelDownloaded()
    }

    fun close() {
        gemmaEngine.close()
        phi4Engine.close()
    }

    companion object {
        private const val TAG = "EngineRouter"
    }
}
