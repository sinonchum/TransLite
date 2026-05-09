package com.translite.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.translite.app.domain.engine.TranslationEngine
import com.translite.app.domain.model.Language
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePackScreen(
    engine: TranslationEngine,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var languageStatus by remember { mutableStateOf<Map<Language, Boolean>>(emptyMap()) }
    var downloading by remember { mutableStateOf<Language?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var showDeleteDialog by remember { mutableStateOf<Language?>(null) }

    // Check language status on load
    LaunchedEffect(Unit) {
        val status = mutableMapOf<Language, Boolean>()
        Language.entries.filter { it != Language.AUTO }.forEach { lang ->
            status[lang] = engine.isLanguageDownloaded(lang)
        }
        languageStatus = status
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "语言包管理",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "${languageStatus.values.count { it }}/${languageStatus.size} 已下载",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "下载语言包后可离线翻译。每个语言包约30-50MB。",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(Language.entries.filter { it != Language.AUTO }, key = { it.code }) { lang ->
                val isDownloaded = languageStatus[lang] ?: false
                val isDownloading = downloading == lang

                LanguagePackItem(
                    language = lang,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    progress = if (isDownloading) downloadProgress else if (isDownloaded) 1f else 0f,
                    onDownload = {
                        downloading = lang
                        downloadProgress = 0f
                        scope.launch {
                            try {
                                engine.downloadLanguage(lang).collect { p ->
                                    downloadProgress = p
                                }
                                languageStatus = languageStatus.toMutableMap().apply {
                                    put(lang, true)
                                }
                            } catch (_: Exception) {
                                languageStatus = languageStatus.toMutableMap().apply {
                                    put(lang, false)
                                }
                            }
                            downloading = null
                        }
                    },
                    onDelete = {
                        showDeleteDialog = lang
                    }
                )
            }
        }
    }

    showDeleteDialog?.let { lang ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除语言包") },
            text = { Text("确定要删除 ${lang.displayName} 语言包吗？删除后需要重新下载才能离线翻译。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        engine.deleteLanguageModel(lang)
                        languageStatus = languageStatus.toMutableMap().apply {
                            put(lang, false)
                        }
                    }
                    showDeleteDialog = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun LanguagePackItem(
    language: Language,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloaded)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${language.displayName} (${language.displayNameEn})",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = when {
                        isDownloaded -> "已下载 ✓"
                        isDownloading -> "下载中... ${(progress * 100).toInt()}%"
                        else -> "未下载"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isDownloaded -> MaterialTheme.colorScheme.primary
                        isDownloading -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            }

            // Action
            if (isDownloading) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            } else if (isDownloaded) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("下载")
                }
            }
        }
    }
}
