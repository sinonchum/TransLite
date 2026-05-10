package com.translite.app.domain.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class Phi4Translator(
    private val context: Context,
    private val modelManager: ModelManager
) : TranslationEngine {

    @Volatile private var engine: Engine? = null
    @Volatile private var modelReady = false
    private val initLock = Any()
    private val modelFile: File get() = modelManager.getModelFile(ModelManager.ModelType.PHI_4_MINI)

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow { emit(emptySet()) }
    override suspend fun isLanguageDownloaded(lang: Language): Boolean { ensureModelReady(); return modelReady }

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = flow {
        if (modelReady && modelManager.isModelDownloaded(ModelManager.ModelType.PHI_4_MINI)) { emit(1f); return@flow }
        if (!modelManager.isModelDownloaded(ModelManager.ModelType.PHI_4_MINI)) throw Exception("Phi-4 not downloaded")
        withContext(Dispatchers.IO) { synchronized(initLock) { if (!modelReady) initEngine() } }
        emit(1f)
    }

    override suspend fun deleteLanguageModel(lang: Language) {
        synchronized(initLock) { engine?.close(); engine = null; modelReady = false }
        modelManager.deleteModel(ModelManager.ModelType.PHI_4_MINI)
    }

    override suspend fun translate(text: String, sourceLang: Language, targetLang: Language): Result<TranslationResult> {
        return withContext(Dispatchers.IO) {
            try {
                ensureModelReady()
                if (!modelReady || engine == null) return@withContext Result.failure(Exception("Phi-4 not loaded"))
                val eng = engine ?: return@withContext Result.failure(Exception("Engine not initialized"))

                val chunks = chunkText(text, MAX_TOKENS_PER_CHUNK)
                Log.i(TAG, "Translating ${chunks.size} chunk(s), total ${text.length} chars")

                val translations = mutableListOf<String>()
                for ((i, chunk) in chunks.withIndex()) {
                    Log.i(TAG, "Chunk ${i + 1}/${chunks.size}: ${chunk.length} chars")
                    val prompt = buildPrompt(chunk, sourceLang, targetLang)
                    val config = ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 0.9, temperature = TEMPERATURE.toDouble()))
                    val conversation = eng.createConversation(config)
                    val response = conversation.use { it.sendMessage(prompt) }
                    val raw = response.toString()
                    val cleaned = raw
                        .replace(Regex("""<start_of_turn>user.*"""), "")
                        .replace(Regex("""<end_of_turn>.*"""), "")
                        .replace(Regex("""\n{2,}.*""", RegexOption.DOT_MATCHES_ALL), "")
                        .trim()
                    translations.add(extractTranslation(cleaned))
                    if (i < chunks.size - 1) { System.gc(); delay(100) }
                }

                val finalTranslation = translations.joinToString(" ")
                Log.i(TAG, "Done: ${finalTranslation.length} chars")
                Result.success(TranslationResult(originalText = text, translatedText = finalTranslation, sourceLang = sourceLang, targetLang = targetLang, isOffline = true))
            } catch (e: OutOfMemoryError) { Log.e(TAG, "OOM", e); System.gc(); Result.failure(Exception("Phi-4 out of memory"))
            } catch (e: Exception) { Log.e(TAG, "Failed", e); Result.failure(e) }
        }
    }

    private fun chunkText(text: String, maxTokens: Int): List<String> {
        val maxChars = maxTokens * 3
        if (text.length <= maxChars) return listOf(text)
        val chunks = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[\u3002\uff01\uff1f.!?\\n])\\s*")).filter { it.isNotBlank() }
        val buf = StringBuilder()
        for (s in sentences) {
            if (buf.length + s.length > maxChars && buf.isNotEmpty()) { chunks.add(buf.toString().trim()); buf.clear() }
            buf.append(s)
            if (!s.endsWith(" ")) buf.append(" ")
        }
        if (buf.isNotBlank()) chunks.add(buf.toString().trim())
        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    fun isModelDownloaded(): Boolean = modelManager.isModelDownloaded(ModelManager.ModelType.PHI_4_MINI)

    suspend fun ensureModelAvailable(): Boolean {
        if (modelReady && modelFile.exists()) return true
        synchronized(initLock) {
            if (modelReady && modelFile.exists()) return true
            if (modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE) {
                return try { initEngine(); true } catch (e: OutOfMemoryError) { Log.e(TAG, "OOM", e); System.gc(); false }
            }
        }
        return false
    }

    private fun ensureModelReady() {
        if (modelReady && modelFile.exists()) return
        synchronized(initLock) {
            if (modelReady && modelFile.exists()) return
            if (modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE) {
                try { initEngine() } catch (e: OutOfMemoryError) { Log.e(TAG, "OOM", e); System.gc() }
            }
        }
    }

    private fun initEngine() {
        if (!modelFile.exists() || modelFile.length() < MIN_MODEL_SIZE) throw Exception("Phi-4 not found")
        Log.i(TAG, "Loading Phi-4: ${modelFile.length() / 1024 / 1024}MB")
        val config = EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU(), cacheDir = context.cacheDir.path)
        engine = Engine(config)
        engine!!.initialize()
        modelReady = true
        Log.i(TAG, "Phi-4 ready")
    }

    private fun buildPrompt(text: String, sourceLang: Language, targetLang: Language): String {
        val src = langName(sourceLang)
        val tgt = langName(targetLang)
        return "<start_of_turn>user\nYou are a professional academic translator. Translate from $src to $tgt.\nOutput a JSON object: {\"translation\": \"...\", \"grammar\": [{\"structure\": \"...\", \"notes\": \"...\"}], \"vocabulary\": [{\"term\": \"...\", \"definition\": \"...\"}], \"notes\": \"...\"}\nRULES: Output ONLY valid JSON. No markdown, no explanations. Stop after closing brace.\n\n$text\n<start_of_turn>model\n"
    }

    private fun extractTranslation(response: String): String {
        val pattern = Regex("\"translation\"\\s*:\\s*\"([^\"]*)\"")
        val match = pattern.find(response)
        return match?.groupValues?.get(1) ?: response.take(500)
    }

    private fun langName(lang: Language): String = when (lang) {
        Language.AUTO -> "any language"; Language.CHINESE -> "Chinese"; Language.ENGLISH -> "English"
        Language.FRENCH -> "French"; Language.SPANISH -> "Spanish"; Language.GERMAN -> "German"
        Language.JAPANESE -> "Japanese"; Language.KOREAN -> "Korean"; Language.RUSSIAN -> "Russian"
        Language.PORTUGUESE -> "Portuguese"; Language.ARABIC -> "Arabic"
    }

    fun close() { synchronized(initLock) { engine?.close(); engine = null; modelReady = false } }

    companion object {
        private const val TAG = "Phi4Translator"
        private const val MIN_MODEL_SIZE = 100_000_000L
        private const val TEMPERATURE = 0.1f
        private const val MAX_TOKENS_PER_CHUNK = 200
    }
}
