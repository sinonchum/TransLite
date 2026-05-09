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
import androidx.compose.ui.platform.LocalContext
import com.translite.app.data.db.AppDatabase
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.service.FloatingBallService
import com.translite.app.service.ScreenCaptureService
import com.translite.app.ui.screens.*
import com.translite.app.ui.theme.TransLiteTheme
import com.translite.app.ui.viewmodel.TranslationViewModel

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
        val app = application as TransLiteApp
        val repository = TranslationRepository(app.onlineEngine, db.translationDao())
        viewModel = TranslationViewModel(repository, app.onlineEngine, app.offlineEngine)

        requestPermissions()

        setContent {
            TransLiteTheme {
                MainApp(
                    viewModel = viewModel,
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

        // Request MANAGE_EXTERNAL_STORAGE for model file access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: TranslationViewModel,
    isFloatingActive: Boolean,
    onToggleFloating: (Boolean) -> Unit,
    onScreenCapture: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLanguagePacks by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }

    if (showCamera) {
        CameraScreen(
            onBack = { showCamera = false },
            onTextRecognized = { text ->
                showCamera = false
                viewModel.updateInput(text)
                viewModel.translate()
            }
        )
        return
    }

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
            val app = LocalContext.current.applicationContext as TransLiteApp
            LanguagePackScreen(
                engine = app.onlineEngine,
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
                    // Camera button for photo OCR
                    IconButton(onClick = { showCamera = true }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "拍照翻译")
                    }
                    // Screen capture button
                    IconButton(onClick = onScreenCapture) {
                        Icon(Icons.Default.ScreenShare, contentDescription = "屏幕翻译")
                    }
                    // Offline mode toggle
                    IconButton(onClick = { viewModel.toggleOfflineMode() }) {
                        Icon(
                            if (uiState.isOfflineMode) Icons.Default.CloudOff else Icons.Default.Cloud,
                            contentDescription = if (uiState.isOfflineMode) "切换在线" else "切换离线"
                        )
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
                isOfflineMode = uiState.isOfflineMode,
                onToggleOffline = { viewModel.toggleOfflineMode() },
                isModelDownloading = uiState.isModelDownloading,
                downloadProgress = uiState.downloadProgress,
                onDownloadModel = { viewModel.downloadOfflineModel() },
                modifier = Modifier.padding(padding)
            )
        }
    }
}
