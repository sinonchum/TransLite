package com.translite.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class TransLiteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
    }
}
