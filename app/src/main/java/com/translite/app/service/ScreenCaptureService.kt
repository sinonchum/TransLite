package com.translite.app.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.translite.app.MainActivity
import com.translite.app.TransLiteApp
import com.translite.app.data.db.AppDatabase
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.domain.model.Language
import com.translite.app.domain.ocr.MlKitOcr
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var ocrEngine: MlKitOcr
    private lateinit var repository: TranslationRepository
    private var overlayView: android.view.View? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ocrEngine = MlKitOcr()
        val db = AppDatabase.getInstance(this)
        val engine = (application as TransLiteApp).gemmaTranslator
        repository = TranslationRepository(engine, db.translationDao())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cleanupCapture()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startCapture(resultCode, data)
                } else {
                    showResultOverlay("屏幕截图权限未授予")
                    serviceScope.launch {
                        delay(2000)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanupCapture()
        ocrEngine.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        startForeground(NOTIFICATION_ID, createNotification())

        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                showResultOverlay("屏幕截图服务初始化失败")
                serviceScope.launch {
                    delay(2000)
                    stopSelf()
                }
                return
            }

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(
                width, height, PixelFormat.RGBA_8888, 2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "TransLiteCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null, null
            )

            // Capture after a short delay to let the display render
            serviceScope.launch {
                delay(500)
                captureAndProcess()
            }
        } catch (e: Exception) {
            showResultOverlay("屏幕截图失败: ${e.message}")
            serviceScope.launch {
                delay(3000)
                stopSelf()
            }
        }
    }

    @SuppressLint("WrongConstant")
    private suspend fun captureAndProcess() {
        val image = try {
            imageReader?.acquireLatestImage()
        } catch (e: Exception) {
            null
        }

        if (image == null) {
            showResultOverlay("无法获取屏幕图像")
            cleanupCapture()
            serviceScope.launch {
                delay(2000)
                stopSelf()
            }
            return
        }

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to actual screen size
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            if (cropped != bitmap) bitmap.recycle()

            // OCR
            val ocrResult = ocrEngine.recognizeFromBitmap(cropped)
            cropped.recycle()

            ocrResult.onSuccess { text ->
                if (text.isNotBlank()) {
                    // Translate the recognized text
                    val translation = repository.translate(
                        text.trim(),
                        Language.AUTO,
                        Language.CHINESE
                    )
                    translation.onSuccess { result ->
                        showResultOverlay(
                            "原文: ${result.originalText.take(100)}\n\n翻译: ${result.translatedText.take(100)}"
                        )
                    }.onFailure {
                        showResultOverlay("识别文本: ${text.take(200)}")
                    }
                } else {
                    showResultOverlay("未检测到文字")
                }
            }.onFailure {
                showResultOverlay("OCR识别失败: ${it.message}")
            }
        } catch (e: Exception) {
            showResultOverlay("处理失败: ${e.message}")
        } finally {
            image.close()
        }

        cleanupCapture()
        // Auto-dismiss after 5 seconds
        serviceScope.launch {
            delay(5000)
            hideOverlay()
            stopSelf()
        }
    }

    @SuppressLint("InflateParams")
    private fun showResultOverlay(screenText: String) {
        hideOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dpToPx(40)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(0xDD2D2D2D.toInt())
                setStroke(1, 0xFF1A73E8.toInt())
            }
        }

        val title = android.widget.TextView(this).apply {
            text = "TransLite 屏幕翻译"
            setTextColor(0xFF8AB4F8.toInt())
            textSize = 12f
        }

        val content = android.widget.TextView(this).apply {
            this.text = screenText
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(0, dpToPx(8), 0, 0)
        }

        container.addView(title)
        container.addView(content)

        overlayView = container
        windowManager?.addView(container, params)
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun cleanupCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, TransLiteApp.CHANNEL_OCR)
            .setContentTitle("TransLite 屏幕识别")
            .setContentText("正在截取屏幕...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val ACTION_STOP = "com.translite.app.STOP_CAPTURE"
        const val ACTION_CAPTURE = "com.translite.app.CAPTURE_SCREEN"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val NOTIFICATION_ID = 1002
    }
}
