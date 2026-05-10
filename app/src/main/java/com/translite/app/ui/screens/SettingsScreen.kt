package com.translite.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.translite.app.R

@Composable
fun SettingsScreen(
    isFloatingActive: Boolean,
    onToggleFloating: (Boolean) -> Unit,
    onLanguagePacks: () -> Unit = {},
    isOfflineMode: Boolean = false,
    onToggleOffline: () -> Unit = {},
    isModelReady: Boolean = false,
    isModelDownloading: Boolean = false,
    downloadProgress: Int = 0,
    downloadSpeedMBps: Float = 0f,
    downloadDownloadedMB: Long = 0,
    downloadTotalMB: Long = 0,
    modelStatusText: String = "",
    onDownloadModel: () -> Unit = {},
    onLanguageChange: (String?) -> Unit = {},
    translationMode: String = "standard",
    isGemmaDownloaded: Boolean = false,
    isPhi4Downloaded: Boolean = false,
    isModeSwitching: Boolean = false,
    onSwitchMode: (String) -> Unit = {},
    onDownloadGemma: () -> Unit = {},
    isPhi4Downloading: Boolean = false,
    phi4DownloadProgress: Int = 0,
    phi4DownloadedMB: Long = 0,
    phi4TotalMB: Long = 0,
    phi4SpeedMBps: Float = 0f,
    phi4StatusText: String = "",
    onDownloadPhi4: () -> Unit = {},
    isClipboardMonitorActive: Boolean = false,
    onToggleClipboardMonitor: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("translite_prefs", android.content.Context.MODE_PRIVATE) }
    val savedLang = prefs.getString("app_language", "system")
    val initialLang = if (savedLang == "system") null else savedLang
    var selectedLanguage by remember { mutableStateOf(initialLang) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(text = stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        }

        // Offline Model Section
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.offline_translation_model), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(R.string.model_info), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(12.dp))
                    when {
                        isModelReady && !isModelDownloading -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.use_offline_model), style = MaterialTheme.typography.bodyLarge)
                                    Text(text = stringResource(if (isOfflineMode) R.string.using_offline else R.string.using_online), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                }
                                Switch(checked = isOfflineMode, onCheckedChange = { onToggleOffline() })
                            }
                        }
                        isModelDownloading -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (downloadProgress in 1..99) LinearProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp))
                                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = modelStatusText.ifEmpty { stringResource(R.string.processing) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                if (downloadProgress in 1..99 && downloadTotalMB > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = stringResource(R.string.download_progress_percent, downloadProgress), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = stringResource(R.string.download_mb_format, downloadDownloadedMB, downloadTotalMB), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    if (downloadSpeedMBps > 0) Text(text = stringResource(R.string.download_speed, downloadSpeedMBps), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                }
                            }
                        }
                        else -> {
                            Button(onClick = onDownloadModel, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.download_offline_model))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = stringResource(R.string.download_wifi_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Language Setting Section
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.language_setting), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val options = listOf(
                        Triple(null, stringResource(R.string.follow_system), "system"),
                        Triple("en", "English", "en"),
                        Triple("zh", "中文", "zh"),
                        Triple("fr", "Français", "fr"),
                        Triple("de", "Deutsch", "de")
                    )
                    options.forEach { (tag, label, key) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedLanguage == tag, onClick = { selectedLanguage = tag; onLanguageChange(tag) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Translation Mode Selector
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.translation_mode), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Standard mode — direct RadioButton onClick (same pattern as language radio buttons)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = translationMode == "standard", onClick = { onSwitchMode("standard") }, enabled = !isModeSwitching)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Standard (Gemma 4)", style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.mode_standard_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                        }
                    }

                    // Academic mode — direct RadioButton onClick
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = translationMode == "academic", onClick = { onSwitchMode("academic") }, enabled = !isModeSwitching)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Academic (Phi-4)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (isPhi4Downloaded) stringResource(R.string.mode_academic_desc) else stringResource(R.string.mode_academic_not_downloaded),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPhi4Downloaded) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Mode switching indicator
                    if (isModeSwitching) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.switching_model), color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Download button for selected model
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        // Always show Gemma download if not downloaded
                        !isGemmaDownloaded && !isModelDownloading -> {
                            Button(onClick = onDownloadGemma, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.download_offline_model))
                            }
                        }
                        // Always show Phi-4 download if not downloaded (regardless of mode)
                        !isPhi4Downloaded && !isPhi4Downloading -> {
                            Button(onClick = {
                                android.widget.Toast.makeText(context, "Starting Phi-4 download...", android.widget.Toast.LENGTH_SHORT).show()
                                onDownloadPhi4()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.download_phi4_model))
                            }
                        }
                        translationMode == "standard" && isModelDownloading -> {
                            LinearProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(modelStatusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        translationMode == "academic" && isPhi4Downloading -> {
                            if (phi4DownloadProgress in 1..99) LinearProgressIndicator(progress = { phi4DownloadProgress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp))
                            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(phi4StatusText.ifEmpty { stringResource(R.string.downloading_model) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            if (phi4DownloadProgress in 1..99 && phi4TotalMB > 0) {
                                Text("${phi4DownloadProgress}% — ${phi4DownloadedMB}MB / ${phi4TotalMB}MB", style = MaterialTheme.typography.bodySmall)
                                if (phi4SpeedMBps > 0) Text(stringResource(R.string.download_speed, phi4SpeedMBps), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        isGemmaDownloaded && isPhi4Downloaded -> Text("✓ Both models ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        translationMode == "standard" && isGemmaDownloaded -> Text("✓ Gemma 4 ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        translationMode == "academic" && isPhi4Downloaded -> Text("✓ Phi-4 ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Other settings
        item {
            Surface(onClick = { onToggleFloating(!isFloatingActive) }, modifier = Modifier.fillMaxWidth()) {
                ListItem(headlineContent = { Text(stringResource(R.string.floating_translate)) }, supportingContent = { Text(stringResource(if (isFloatingActive) R.string.enabled else R.string.disabled)) }, leadingContent = { Icon(Icons.Default.OpenWith, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Switch(checked = isFloatingActive, onCheckedChange = null) })
            }
            HorizontalDivider()
        }
        item {
            Surface(onClick = { val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")); context.startActivity(intent) }, modifier = Modifier.fillMaxWidth()) {
                ListItem(headlineContent = { Text(stringResource(R.string.overlay_permission)) }, supportingContent = { Text(stringResource(R.string.manage_overlay_permission)) }, leadingContent = { Icon(Icons.Default.PictureInPicture, contentDescription = null, tint = MaterialTheme.colorScheme.primary) })
            }
            HorizontalDivider()
        }
        item {
            Surface(onClick = onLanguagePacks, modifier = Modifier.fillMaxWidth()) {
                ListItem(headlineContent = { Text(stringResource(R.string.language_pack_management)) }, supportingContent = { Text(stringResource(R.string.language_pack_desc)) }, leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) })
            }
            HorizontalDivider()
        }
        item {
            Surface(onClick = { onToggleClipboardMonitor(!isClipboardMonitorActive) }, modifier = Modifier.fillMaxWidth()) {
                ListItem(headlineContent = { Text(stringResource(R.string.clipboard_monitor)) }, supportingContent = { Text(stringResource(R.string.clipboard_monitor_desc)) }, leadingContent = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Switch(checked = isClipboardMonitorActive, onCheckedChange = null) })
            }
            HorizontalDivider()
        }
        item {
            ListItem(headlineContent = { Text(stringResource(R.string.about)) }, supportingContent = { Text(stringResource(R.string.app_version)) }, leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) })
        }
    }
}