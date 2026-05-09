package com.translite.app.domain.ocr

import android.graphics.Bitmap
import android.net.Uri

interface OcrEngine {
    suspend fun recognizeFromUri(uri: Uri): Result<String>
    suspend fun recognizeFromBitmap(bitmap: Bitmap): Result<String>
}
