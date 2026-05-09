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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.translite.app.TransLiteApp

data class SettingsItem(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@Composable
fun SettingsScreen(
    isFloatingActive: Boolean,
    onToggleFloating: (Boolean) -> Unit,
    onLanguagePacks: () -> Unit = {},
    isOfflineMode: Boolean = false,
    onToggleOffline: () -> Unit = {},
    isModelDownloading: Boolean = false,
    downloadProgress: Int = 0,
    onDownloadModel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as TransLiteApp

    // HuggingFace Token state
    var hfToken by remember { mutableStateOf(app.offlineEngine.getHfTokenFromPrefs()) }
    var showToken by remember { mutableStateOf(false) }
    var showTokenSaved by remember { mutableStateOf(false) }

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

        // HuggingFace Token Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HuggingFace Token",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "下载 Gemma 模型需要 Token",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = hfToken,
                        onValueChange = { hfToken = it },
                        label = { Text("hf_xxxxxxxxxxxx") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showToken) "隐藏" else "显示"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showTokenSaved) {
                            Text(
                                text = "✓ 已保存",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "在 huggingface.co/settings/tokens 获取",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                app.offlineEngine.setHfToken(hfToken)
                                showTokenSaved = true
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

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
                    Spacer(modifier = Modifier.height(8.dp))

                    // Model info
                    Text(
                        text = "TranslateGemma 4B (官方翻译模型，约3.9GB)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "或 Gemma3 1B (轻量模型，约555MB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Offline mode toggle
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Download button
                    if (isModelDownloading) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "下载中... $downloadProgress%",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onDownloadModel,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hfToken.isNotBlank()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("下载离线模型")
                        }
                    }

                    if (hfToken.isBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "请先输入 HuggingFace Token",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
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
                supportingContent = { Text("TransLite v1.3.0 — 离线翻译助手") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            )
        }
    }
}
