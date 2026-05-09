package com.translite.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.translite.app.domain.engine.OnlineTranslator

class TransLiteApp : Application() {

    lateinit var translationEngine: OnlineTranslator
        private set

    override fun onCreate() {
        super.onCreate()
        translationEngine = OnlineTranslator()
        createNotificationChannels()
    }

    override fun onTerminate() {
        translationEngine.close()
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
