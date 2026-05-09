package com.translite.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.translite.app.MainActivity
import com.translite.app.R
import com.translite.app.TransLiteApp
import com.translite.app.data.db.AppDatabase
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.domain.engine.GemmaTranslator
import com.translite.app.domain.model.Language
import kotlinx.coroutines.*

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var expandedView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: TranslationRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val db = AppDatabase.getInstance(this)
        val engine = GemmaTranslator(this@FloatingBallService)
        repository = TranslationRepository(engine, db.translationDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, createNotification())
        if (floatingView == null) {
            createFloatingBall()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        floatingView?.let { windowManager.removeView(it) }
        expandedView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBall() {
        // Floating ball
        val ballSize = dpToPx(56)
        val ballParams = WindowManager.LayoutParams(
            ballSize, ballSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(200)
        }

        val ball = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1A73E8.toInt())
                setStroke(dpToPx(2), 0xFFFFFFFF.toInt())
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        ball.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballParams.x
                    initialY = ballParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > dpToPx(10) * dpToPx(10)) {
                        isClick = false
                    }
                    ballParams.x = initialX + dx.toInt()
                    ballParams.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(ball, ballParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        toggleExpanded()
                    }
                    // Snap to edge
                    val centerX = ballParams.x + ballSize / 2
                    val screenWidth = resources.displayMetrics.widthPixels
                    ballParams.x = if (centerX < screenWidth / 2) 0 else screenWidth - ballSize
                    windowManager.updateViewLayout(ball, ballParams)
                    true
                }
                else -> false
            }
        }

        floatingView = ball
        windowManager.addView(ball, ballParams)
    }

    private fun toggleExpanded() {
        if (expandedView?.isShown == true) {
            expandedView?.visibility = View.GONE
            return
        }
        createExpandedPanel()
    }

    @SuppressLint("InflateParams")
    private fun createExpandedPanel() {
        expandedView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }

        val params = WindowManager.LayoutParams(
            dpToPx(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(270)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16).toFloat()
                setColor(0xFF2D2D2D.toInt())
                setStroke(1, 0xFF444444.toInt())
            }
        }

        val title = TextView(this).apply {
            text = "TransLite"
            setTextColor(0xFF8AB4F8.toInt())
            textSize = 14f
        }

        val input = EditText(this).apply {
            hint = "输入翻译文本..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            textSize = 14f
            maxLines = 3
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(0xFF3C3C3C.toInt())
                setStroke(1, 0xFF555555.toInt())
            }
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }

        val resultText = TextView(this).apply {
            setTextColor(0xFFBDC1C6.toInt())
            textSize = 14f
            setPadding(0, dpToPx(8), 0, 0)
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val translateBtn = android.widget.Button(this).apply {
            text = "翻译"
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    resultText.text = "翻译中..."
                    serviceScope.launch {
                        val result = repository.translate(text, Language.ENGLISH, Language.CHINESE)
                        result.onSuccess { r ->
                            resultText.text = r.translatedText
                        }.onFailure { e ->
                            resultText.text = "错误: ${e.message}"
                        }
                    }
                }
            }
        }

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0x00000000)
            setOnClickListener {
                expandedView?.visibility = View.GONE
            }
        }

        buttonRow.addView(translateBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttonRow.addView(closeBtn, LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)))

        container.addView(title)
        container.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(8) })
        container.addView(resultText)
        container.addView(buttonRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(8) })

        expandedView = container
        windowManager.addView(container, params)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingBallService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, TransLiteApp.CHANNEL_FLOATING)
            .setContentTitle("TransLite 悬浮翻译")
            .setContentText("悬浮球已激活")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val ACTION_STOP = "com.translite.app.STOP_FLOATING"
        const val NOTIFICATION_ID = 1001
    }
}
