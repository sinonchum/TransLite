package com.translite.app.domain.engine

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages multiple LLM models: download, status, hardware detection.
 */
class ModelManager(private val context: Context) {

    enum class ModelType(
        val filename: String,
        val displayName: String,
        val sizeGB: Float,
        val url: String
    ) {
        GEMMA_4_E2B(
            "gemma-4-E2B-it.litertlm",
            "Gemma 4 E2B",
            2.5f,
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        ),
        PHI_4_MINI(
            "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            "Phi-4 Mini",
            3.7f,
            "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        )
    }

    sealed class ModelState {
        data object NotDownloaded : ModelState()
        data class Downloading(val percent: Int = 0, val downloadedMB: Long = 0, val totalMB: Long = 0, val speedMBps: Float = 0f) : ModelState()
        data object Loading : ModelState()
        data object Ready : ModelState()
        data class Error(val message: String) : ModelState()
    }

    private val modelDir: File by lazy {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.mkdirs()
        dir
    }

    fun getModelFile(type: ModelType): File = File(modelDir, type.filename)

    fun isModelDownloaded(type: ModelType): Boolean {
        val file = getModelFile(type)
        return file.exists() && file.length() > MIN_MODEL_SIZE
    }

    fun getModelSizeMB(type: ModelType): Long {
        val file = getModelFile(type)
        return if (file.exists()) file.length() / (1024 * 1024) else 0
    }

    fun getDeviceRAMGB(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024 * 1024)).toInt()
    }

    fun isDeviceHighEnd(): Boolean = getDeviceRAMGB() >= 6

    fun deleteModel(type: ModelType) {
        getModelFile(type).delete()
        Log.i(TAG, "Deleted model: ${type.filename}")
    }

    /**
     * Download model from HuggingFace. Calls onProgress with (percent, downloadedMB, totalMB, speedMBps).
     */
    suspend fun downloadModel(type: ModelType, onProgress: (Int, Long, Long, Float) -> Unit) {
        val targetFile = getModelFile(type)
        val tmpFile = File(targetFile.path + ".tmp")

        try {
            val url = URL(type.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 600_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            Log.i(TAG, "HTTP $responseCode for ${type.filename}")
            if (responseCode !in listOf(200, 201)) throw Exception("HTTP $responseCode")

            val contentLength = connection.contentLength.toLong()
            val totalMB = if (contentLength > 0) contentLength / (1024 * 1024) else 0L
            Log.i(TAG, "Downloading ${type.filename} (content-length: ${contentLength}, ~${totalMB}MB)...")

            connection.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var totalDownloaded = 0L
                    var bytesRead: Int
                    val startTime = System.currentTimeMillis()
                    var lastReportTime = startTime

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 500) {
                            val totalElapsed = (now - startTime).coerceAtLeast(1)
                            val speed = (totalDownloaded.toFloat() / 1024 / 1024) / (totalElapsed / 1000f)
                            val downloadedMB = totalDownloaded / (1024 * 1024)
                            val percent = if (contentLength > 0) (totalDownloaded * 100 / contentLength).toInt()
                                         else (downloadedMB * 100 / 3700).toInt().coerceAtMost(99)
                            onProgress(percent, downloadedMB, if (totalMB > 0) totalMB else 3700L, speed)
                            lastReportTime = now
                        }
                    }
                    Log.i(TAG, "Downloaded ${totalDownloaded / (1024 * 1024)}MB of ${type.filename}")
                }
            }

            if (targetFile.exists()) targetFile.delete()
            val renamed = tmpFile.renameTo(targetFile)
            if (renamed) {
                Log.i(TAG, "Download complete: ${type.filename} (${targetFile.length() / 1024 / 1024}MB)")
            } else {
                // renameTo failed (cross-fs?), try copy instead
                Log.w(TAG, "renameTo failed, copying file instead")
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
                Log.i(TAG, "Download complete via copy: ${type.filename} (${targetFile.length() / 1024 / 1024}MB)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            tmpFile.delete()
            throw e
        }
    }

    companion object {
        private const val TAG = "ModelManager"
        private const val MIN_MODEL_SIZE = 100_000_000L
    }
}
