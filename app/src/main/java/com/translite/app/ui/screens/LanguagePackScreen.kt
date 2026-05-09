package com.translite.app.ui.screens

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
import com.translite.app.domain.engine.GemmaTranslator
import com.translite.app.domain.engine.TranslationEngine
import com.translite.app.domain.model.Language
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePackScreen(
    engine: TranslationEngine,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isModelReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Check model status on load
    LaunchedEffect(Unit) {
        isLoading = true
        isModelReady = engine.isLanguageDownloaded(Language.ENGLISH)
        isLoading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Model status card
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isModelReady)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isModelReady) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isModelReady) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "翻译模型",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when {
                                isLoading -> "检查中..."
                                isModelReady -> "TranslateGemma 4B · 已就绪"
                                isDownloading -> "下载中... ${(downloadProgress * 100).toInt()}%"
                                else -> "未加载"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "模型文件: ${GemmaTranslator.MODEL_FILENAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "请将模型文件放入 APK 的 assets/ 目录后重新编译",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                errorMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row {
                    if (!isModelReady && !isDownloading) {
                        FilledTonalButton(
                            onClick = {
                                isDownloading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        engine.downloadLanguage(Language.ENGLISH).collect { p ->
                                            downloadProgress = p
                                        }
                                        isModelReady = true
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "加载失败"
                                    }
                                    isDownloading = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("加载模型")
                        }
                    }

                    if (isModelReady) {
                        FilledTonalButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除模型")
                        }
                    }
                }
            }
        }

        // Supported languages info
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "支持的语言",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                val supportedLanguages = listOf(
                    "🇨🇳 中文" to "Chinese",
                    "🇺🇸 English" to "English",
                    "🇫🇷 Français" to "French",
                    "🇪🇸 Español" to "Spanish",
                    "🇩🇪 Deutsch" to "German",
                    "🇯🇵 日本語" to "Japanese",
                    "🇰🇷 한국어" to "Korean",
                    "🇷🇺 Русский" to "Russian",
                    "🇧🇷 Português" to "Portuguese",
                    "🇸🇦 العربية" to "Arabic"
                )
                supportedLanguages.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { (display, _) ->
                            Text(
                                text = display,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除翻译模型") },
            text = { Text("确定要删除翻译模型吗？删除后需要重新将模型文件放入 assets 目录并重新编译。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        engine.deleteLanguageModel(Language.ENGLISH)
                        isModelReady = false
                    }
                    showDeleteDialog = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}
