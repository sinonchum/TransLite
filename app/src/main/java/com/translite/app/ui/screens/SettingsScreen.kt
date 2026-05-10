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
import androidx.compose.ui.unit.dp
import com.translite.app.TransLiteApp

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Offline Model Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "离线翻译模型",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gemma 4 E2B (约 2.5GB)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        // Model is ready — show toggle
                        isModelReady && !isModelDownloading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("使用离线模型", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = if (isOfflineMode) "当前使用离线翻译" else "当前使用在线翻译",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = isOfflineMode,
                                    onCheckedChange = { onToggleOffline() }
                                )
                            }
                        }

                        // Downloading / Loading — show progress
                        isModelDownloading -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Progress bar
                                if (downloadProgress in 1..99) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Status text
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = modelStatusText.ifEmpty { "处理中..." },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Download details: percentage + MB + speed
                                if (downloadProgress in 1..99 && downloadTotalMB > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Big percentage number
                                    Text(
                                        text = "$downloadProgress%",
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // MB downloaded / total
                                    Text(
                                        text = "${downloadDownloadedMB}MB / ${downloadTotalMB}MB",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )

                                    // Speed
                                    if (downloadSpeedMBps > 0) {
                                        Text(
                                            text = "速度: ${String.format("%.1f", downloadSpeedMBps)} MB/s",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        // Not downloaded — show download button
                        else -> {
                            Button(
                                onClick = onDownloadModel,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("下载离线模型 (~2.5GB)")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "首次使用需下载模型，建议在 Wi-Fi 下进行",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Other settings
        item {
            Surface(
                onClick = { onToggleFloating(!isFloatingActive) },
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("悬浮球翻译") },
                    supportingContent = { Text(if (isFloatingActive) "已开启" else "关闭") },
                    leadingContent = {
                        Icon(Icons.Default.OpenWith, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Switch(checked = isFloatingActive, onCheckedChange = null)
                    }
                )
            }
            HorizontalDivider()
        }

        item {
            Surface(
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("悬浮窗权限") },
                    supportingContent = { Text("管理应用悬浮窗权限") },
                    leadingContent = {
                        Icon(Icons.Default.PictureInPicture, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
            HorizontalDivider()
        }

        item {
            Surface(
                onClick = onLanguagePacks,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("语言包管理") },
                    supportingContent = { Text("下载和管理离线翻译语言包") },
                    leadingContent = {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
            HorizontalDivider()
        }

        item {
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("TransLite v2.1.0") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            )
        }
    }
}
