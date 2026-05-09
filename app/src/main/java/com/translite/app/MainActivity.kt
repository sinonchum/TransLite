package com.translite.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.translite.app.data.db.AppDatabase
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.service.FloatingBallService
import com.translite.app.service.ScreenCaptureService
import com.translite.app.ui.screens.*
import com.translite.app.ui.theme.TransLiteTheme
import com.translite.app.ui.viewmodel.TranslationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: TranslationViewModel
    private var isFloatingActive = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "部分权限未授予: ${denied.joinToString()}", Toast.LENGTH_SHORT).show()
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_CAPTURE
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                }
                startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "屏幕截图服务启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("translite_prefs", MODE_PRIVATE)
        isFloatingActive = prefs.getBoolean("floating_active", false)

        val db = AppDatabase.getInstance(this)
        val engine = (application as TransLiteApp).gemmaTranslator
        val repository = TranslationRepository(engine, db.translationDao())
        viewModel = TranslationViewModel(repository)

        requestPermissions()

        // Copy model from assets to internal storage on first launch
        copyModelToStorage(engine)

        setContent {
            TransLiteTheme {
                MainApp(
                    viewModel = viewModel,
                    engine = engine,
                    isFloatingActive = isFloatingActive,
                    onToggleFloating = { toggle ->
                        isFloatingActive = toggle
                        prefs.edit().putBoolean("floating_active", toggle).apply()
                        if (toggle) {
                            startForegroundService(Intent(this, FloatingBallService::class.java))
                        } else {
                            stopService(Intent(this, FloatingBallService::class.java))
                        }
                    },
                    onScreenCapture = {
                        try {
                            val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            mediaProjectionLauncher.launch(pm.createScreenCaptureIntent())
                        } catch (e: Exception) {
                            Toast.makeText(this, "屏幕截图权限请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    private fun copyModelToStorage(engine: com.translite.app.domain.engine.GemmaTranslator) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val modelDir = File(filesDir, "models").also { it.mkdirs() }
                    val modelFile = File(modelDir, com.translite.app.domain.engine.GemmaTranslator.MODEL_FILENAME)
                    if (modelFile.exists()) return@withContext

                    val assetManager = assets
                    val modelsDir = assetManager.list("models") ?: emptyArray()
                    if (modelsDir.any { it == com.translite.app.domain.engine.GemmaTranslator.MODEL_FILENAME }) {
                        Toast.makeText(this@MainActivity, "正在复制翻译模型...", Toast.LENGTH_SHORT).show()
                        assetManager.open("models/${com.translite.app.domain.engine.GemmaTranslator.MODEL_FILENAME}").use { input ->
                            FileOutputStream(modelFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Toast.makeText(this@MainActivity, "模型复制完成", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // Model not in assets — will need to download
                }
            }
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).forEach { p ->
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    perms.add(p)
                }
            }
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: TranslationViewModel,
    engine: com.translite.app.domain.engine.GemmaTranslator,
    isFloatingActive: Boolean,
    onToggleFloating: (Boolean) -> Unit,
    onScreenCapture: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLanguagePacks by remember { mutableStateOf(false) }

    if (showLanguagePacks) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("语言包管理") },
                    navigationIcon = {
                        IconButton(onClick = { showLanguagePacks = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            LanguagePackScreen(
                engine = engine,
                modifier = Modifier.padding(padding)
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TransLite") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = onScreenCapture) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "屏幕翻译")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    label = { Text("翻译") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text("对话") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("历史") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                uiState = uiState,
                onInputChange = viewModel::updateInput,
                onTranslate = viewModel::translate,
                onSwapLanguages = viewModel::swapLanguages,
                onSourceLangChange = viewModel::setSourceLang,
                onTargetLangChange = viewModel::setTargetLang,
                onClear = viewModel::clearInput,
                modifier = Modifier.padding(padding)
            )
            1 -> ConversationScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
            2 -> HistoryScreen(
                history = history,
                onToggleFavorite = viewModel::toggleFavorite,
                onDelete = viewModel::deleteTranslation,
                onClearAll = viewModel::clearHistory,
                modifier = Modifier.padding(padding)
            )
            3 -> SettingsScreen(
                isFloatingActive = isFloatingActive,
                onToggleFloating = onToggleFloating,
                onLanguagePacks = { showLanguagePacks = true },
                modifier = Modifier.padding(padding)
            )
        }
    }
}
