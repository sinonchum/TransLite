package com.translite.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.translite.app.domain.engine.GemmaTranslator
import com.translite.app.domain.engine.OnlineTranslator

class TransLiteApp : Application() {

    lateinit var onlineEngine: OnlineTranslator
        private set

    lateinit var offlineEngine: GemmaTranslator
        private set

    var useOffline: Boolean = false
        set(value) {
            field = value
            getSharedPreferences("translite_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("use_offline", value)
                .apply()
        }

    val activeEngine: Any
        get() = if (useOffline) offlineEngine else onlineEngine

    override fun onCreate() {
        super.onCreate()
        onlineEngine = OnlineTranslator()
        offlineEngine = GemmaTranslator(this)

        // Restore offline preference
        useOffline = getSharedPreferences("translite_prefs", MODE_PRIVATE)
            .getBoolean("use_offline", false)

        createNotificationChannels()
    }

    override fun onTerminate() {
        onlineEngine.close()
        offlineEngine.close()
        super.onTerminate()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val floatingChannel = NotificationChannel(
            CHANNEL_FLOATING,
            "悬浮翻译",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "悬浮球翻译服务通知"
        }

        val ocrChannel = NotificationChannel(
            CHANNEL_OCR,
            "OCR识别",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "屏幕文字识别通知"
        }

        manager.createNotificationChannel(floatingChannel)
        manager.createNotificationChannel(ocrChannel)
    }

    companion object {
        const val CHANNEL_FLOATING = "floating_ball"
        const val CHANNEL_OCR = "screen_ocr"
        const val CHANNEL_MODEL = "model_download"
    }
}
