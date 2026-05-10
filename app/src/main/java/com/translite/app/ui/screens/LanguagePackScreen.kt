package com.translite.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.translite.app.R
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
                text = stringResource(R.string.language_pack_management),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.download_count_format, languageStatus.values.count { it }, languageStatus.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = stringResource(R.string.auto_download_hint),
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
            val languages = Language.entries.filter { it != Language.AUTO }.toList()
            items(languages.size) { index ->
                val lang = languages[index]
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
            title = { Text(stringResource(R.string.delete_language_pack)) },
            text = { Text(stringResource(R.string.confirm_delete_lang, lang.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        engine.deleteLanguageModel(lang)
                        languageStatus = languageStatus.toMutableMap().apply {
                            put(lang, false)
                        }
                    }
                    showDeleteDialog = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) }
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
                        isDownloaded -> stringResource(R.string.downloaded_checkmark)
                        isDownloading -> stringResource(R.string.downloading_progress, (progress * 100).toInt())
                        else -> stringResource(R.string.not_downloaded)
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
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.download))
                }
            }
        }
    }
}
