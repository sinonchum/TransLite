package com.translite.app.domain.ocr

import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MlKitOcr : OcrEngine {

    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    override suspend fun recognizeFromUri(uri: Uri): Result<String> {
        return try {
            Result.failure(NotImplementedError("Use recognizeFromBitmap for direct input"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recognizeFromBitmap(bitmap: Bitmap): Result<String> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)

            // Try Chinese recognizer first (handles both Chinese + Latin)
            try {
                val result = chineseRecognizer.process(image).await()
                if (result.text.isNotBlank()) {
                    return Result.success(result.text)
                }
            } catch (_: Exception) { }

            // Fallback to Latin recognizer
            val result = latinRecognizer.process(image).await()
            Result.success(result.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        latinRecognizer.close()
        chineseRecognizer.close()
    }
}
