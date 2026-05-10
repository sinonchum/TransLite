package com.translite.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.translite.app.R
import com.translite.app.data.db.AppDatabase
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.service.FloatingBallService
import com.translite.app.service.ScreenCaptureService
import com.translite.app.ui.screens.*
import com.translite.app.ui.theme.TransLiteTheme
import com.translite.app.ui.viewmodel.TranslationViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: TranslationViewModel
    private var isFloatingActive = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, getString(R.string.permission_denied, denied.joinToString()), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.screen_capture_service_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedLocale()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("translite_prefs", MODE_PRIVATE)
        isFloatingActive = prefs.getBoolean("floating_active", false)

        val db = AppDatabase.getInstance(this)
        val app = application as TransLiteApp
        val repository = TranslationRepository(app.onlineEngine, db.translationDao())
        viewModel = TranslationViewModel(repository, app.engineRouter, app.offlineEngine, app.modelManager)

        requestPermissions()

        setContent {
            TransLiteTheme {
                val activity = LocalContext.current as Activity
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
                            Toast.makeText(this, getString(R.string.screen_capture_permission_failed, e.message), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleClipboardMonitor = { active ->
                        val intent = Intent(this, com.translite.app.service.ClipboardMonitorService::class.java)
                        if (active) startForegroundService(intent) else stopService(intent)
                    },
                    onLanguageChange = { tag ->
                        val langToSave = tag ?: "system"
                        activity.getSharedPreferences("translite_prefs", MODE_PRIVATE)
                            .edit().putString("app_language", langToSave).apply()
                        activity.recreate()
                    }
                )
            }
        }
    }

    private fun applySavedLocale() {
        val prefs = getSharedPreferences("translite_prefs", MODE_PRIVATE)
        val langCode = prefs.getString("app_language", null) ?: return
        if (langCode == "system") return
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
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
    onScreenCapture: () -> Unit,
    onToggleClipboardMonitor: (Boolean) -> Unit,
    onLanguageChange: (String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLanguagePacks by remember { mutableStateOf(false) }
    var isClipboardMonitorActive by remember { mutableStateOf(false) }
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
                    title = { Text(stringResource(R.string.language_pack_management)) },
                    navigationIcon = {
                        IconButton(onClick = { showLanguagePacks = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
            }
        ) { padding ->
            val app = LocalContext.current.applicationContext as TransLiteApp
            LanguagePackScreen(engine = app.onlineEngine, modifier = Modifier.padding(padding))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    IconButton(onClick = { showCamera = true }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.camera_translate))
                    }
                    IconButton(onClick = onScreenCapture) {
                        Icon(Icons.Default.ScreenShare, contentDescription = stringResource(R.string.screen_translate))
                    }
                    IconButton(onClick = { viewModel.toggleOfflineMode() }) {
                        Icon(
                            if (uiState.isOfflineMode) Icons.Default.CloudOff else Icons.Default.Cloud,
                            contentDescription = stringResource(if (uiState.isOfflineMode) R.string.switch_to_online else R.string.switch_to_offline)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Default.Translate, contentDescription = null) }, label = { Text(stringResource(R.string.tab_translate)) }, selected = selectedTab == 0, onClick = { selectedTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.Mic, contentDescription = null) }, label = { Text(stringResource(R.string.tab_conversation)) }, selected = selectedTab == 1, onClick = { selectedTab = 1 })
                NavigationBarItem(icon = { Icon(Icons.Default.History, contentDescription = null) }, label = { Text(stringResource(R.string.tab_history)) }, selected = selectedTab == 2, onClick = { selectedTab = 2 })
                NavigationBarItem(icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text(stringResource(R.string.tab_settings)) }, selected = selectedTab == 3, onClick = { selectedTab = 3 })
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(uiState = uiState, onInputChange = viewModel::updateInput, onTranslate = viewModel::translate, onSwapLanguages = viewModel::swapLanguages, onSourceLangChange = viewModel::setSourceLang, onTargetLangChange = viewModel::setTargetLang, onClear = viewModel::clearInput, modifier = Modifier.padding(padding))
            1 -> ConversationScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
            2 -> HistoryScreen(history = history, onToggleFavorite = viewModel::toggleFavorite, onDelete = viewModel::deleteTranslation, onClearAll = viewModel::clearHistory, modifier = Modifier.padding(padding))
            3 -> SettingsScreen(
                isFloatingActive = isFloatingActive,
                onToggleFloating = onToggleFloating,
                onLanguagePacks = { showLanguagePacks = true },
                isOfflineMode = uiState.isOfflineMode,
                onToggleOffline = { viewModel.toggleOfflineMode() },
                isModelReady = uiState.isModelReady,
                isModelDownloading = uiState.isModelDownloading,
                downloadProgress = uiState.downloadProgress,
                downloadSpeedMBps = uiState.downloadSpeedMBps,
                downloadDownloadedMB = uiState.downloadDownloadedMB,
                downloadTotalMB = uiState.downloadTotalMB,
                modelStatusText = uiState.modelStatusText,
                onDownloadModel = { viewModel.downloadOfflineModel() },
                onLanguageChange = onLanguageChange,
                translationMode = uiState.translationMode.name.lowercase(),
                isGemmaDownloaded = uiState.isModelReady,
                isPhi4Downloaded = uiState.isPhi4Downloaded,
                isModeSwitching = uiState.isModeSwitching,
                onSwitchMode = { viewModel.switchTranslationMode(
                    if (it == "academic") com.translite.app.domain.engine.EngineRouter.TranslationMode.ACADEMIC
                    else com.translite.app.domain.engine.EngineRouter.TranslationMode.STANDARD
                ) },
                onDownloadGemma = { viewModel.downloadOfflineModel() },
                isPhi4Downloading = uiState.isPhi4Downloading,
                phi4DownloadProgress = uiState.phi4DownloadProgress,
                phi4DownloadedMB = uiState.phi4DownloadedMB,
                phi4TotalMB = uiState.phi4TotalMB,
                phi4SpeedMBps = uiState.phi4SpeedMBps,
                phi4StatusText = uiState.phi4StatusText,
                onDownloadPhi4 = { viewModel.downloadPhi4Model() },
                isClipboardMonitorActive = isClipboardMonitorActive,
                onToggleClipboardMonitor = { active ->
                    isClipboardMonitorActive = active
                    onToggleClipboardMonitor(active)
                },
                modifier = Modifier.padding(padding)
            )
        }
    }
}
