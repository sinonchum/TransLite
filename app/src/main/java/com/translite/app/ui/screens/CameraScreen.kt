package com.translite.app.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onTextRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照翻译") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Capture Button
            FloatingActionButton(
                onClick = {
                    if (!isProcessing) {
                        isProcessing = true
                        captureAndRecognize(context, imageCapture) { text ->
                            isProcessing = false
                            if (text.isNotBlank()) {
                                onTextRecognized(text)
                            } else {
                                Toast.makeText(context, "未识别到文字", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .size(72.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "拍照",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private fun captureAndRecognize(
    context: Context,
    imageCapture: ImageCapture?,
    onResult: (String) -> Unit
) {
    val capture = imageCapture ?: run {
        onResult("")
        return
    }

    val photoFile = File(
        context.cacheDir,
        "ocr_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val inputImage = InputImage.fromFilePath(context, Uri.fromFile(photoFile))

                    // Use both Latin and Chinese recognizers
                    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            photoFile.delete()
                            onResult(visionText.text)
                        }
                        .addOnFailureListener { e ->
                            photoFile.delete()
                            Log.e("CameraScreen", "OCR failed", e)
                            // Fallback to Latin recognizer
                            val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            latinRecognizer.process(inputImage)
                                .addOnSuccessListener { text -> onResult(text.text) }
                                .addOnFailureListener { onResult("") }
                        }
                } catch (e: Exception) {
                    photoFile.delete()
                    Log.e("CameraScreen", "Image processing failed", e)
                    onResult("")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScreen", "Photo capture failed", exception)
                onResult("")
            }
        }
    )
}
