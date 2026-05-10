package com.translite.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.translite.app.R
import com.translite.app.TransLiteApp
import com.translite.app.domain.engine.EngineRouter
import com.translite.app.domain.model.Language
import kotlinx.coroutines.*

/**
 * Optional clipboard monitoring service.
 * Detects foreign text copied to clipboard and offers quick translation via notification.
 */
class ClipboardMonitorService : Service() {

    private var clipboardManager: ClipboardManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastClipText = ""

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager?.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrBlank() && text != lastClipText && text.length in 2..500) {
            lastClipText = text
            serviceScope.launch { handleClipboardText(text) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        Log.i(TAG, "Clipboard monitor started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TransLite")
            .setContentText(getString(R.string.clipboard_monitor_active))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "Clipboard monitor stopped")
    }

    private suspend fun handleClipboardText(text: String) {
        try {
            val app = application as TransLiteApp
            val sourceLang = detectLanguage(text) ?: return

            val result = app.engineRouter.translate(
                text, sourceLang, Language.CHINESE, EngineRouter.TranslationMode.STANDARD
            )

            result.onSuccess { translation ->
                showTranslationNotification(text, translation.translatedText)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard translation failed: ${e.message}")
        }
    }

    private fun detectLanguage(text: String): Language? {
        val cjkCount = text.count { it.code in 0x4E00..0x9FFF || it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF }
        val latinCount = text.count { it.isLetter() && it.code < 0x0250 }

        return when {
            cjkCount > text.length * 0.3 -> null // Already CJK, don't translate
            latinCount > text.length * 0.5 -> Language.ENGLISH
            text.contains(Regex("[àâçéèêëîïôûùüÿñæœ]")) -> Language.FRENCH
            text.contains(Regex("[äöüß]")) -> Language.GERMAN
            text.contains(Regex("[áéíóúñ¿¡]")) -> Language.SPANISH
            else -> Language.ENGLISH
        }
    }

    private fun showTranslationNotification(original: String, translated: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TransLite 快速翻译")
            .setContentText(translated)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "原文: ${original.take(100)}\n\n翻译: $translated"
            ))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()

        manager.notify(TRANSLATE_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "剪贴板监听",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "检测外语复制并提供快速翻译"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ClipboardMonitor"
        private const val CHANNEL_ID = "clipboard_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val TRANSLATE_NOTIFICATION_ID = 1002
    }
}
