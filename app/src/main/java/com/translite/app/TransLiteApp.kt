package com.translite.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.translite.app.domain.engine.EngineRouter
import com.translite.app.domain.engine.GemmaTranslator
import com.translite.app.domain.engine.ModelManager
import com.translite.app.domain.engine.OnlineTranslator
import com.translite.app.domain.engine.Phi4Translator

class TransLiteApp : Application() {

    lateinit var onlineEngine: OnlineTranslator
        private set

    lateinit var offlineEngine: GemmaTranslator
        private set

    lateinit var modelManager: ModelManager
        private set

    lateinit var phi4Engine: Phi4Translator
        private set

    lateinit var engineRouter: EngineRouter
        private set

    var useOffline: Boolean = false
        set(value) {
            field = value
            getSharedPreferences("translite_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("use_offline", value)
                .apply()
        }

    override fun onCreate() {
        super.onCreate()
        onlineEngine = OnlineTranslator()
        modelManager = ModelManager(this)
        offlineEngine = GemmaTranslator(this)
        phi4Engine = Phi4Translator(this, modelManager)
        engineRouter = EngineRouter(offlineEngine, phi4Engine)

        useOffline = getSharedPreferences("translite_prefs", MODE_PRIVATE)
            .getBoolean("use_offline", false)

        createNotificationChannels()
    }

    override fun onTerminate() {
        onlineEngine.close()
        engineRouter.close()
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
