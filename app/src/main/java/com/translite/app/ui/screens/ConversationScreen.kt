package com.translite.app.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.translite.app.domain.model.Language
import com.translite.app.ui.viewmodel.TranslationUiState
import com.translite.app.ui.viewmodel.TranslationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: TranslationViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var isListening by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (!matches.isNullOrEmpty()) {
            recognizedText = matches[0]
            viewModel.updateInput(matches[0])
        }
    }

    fun startListening() {
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "auto")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechLauncher.launch(intent)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "对话翻译",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Text(
            text = "点击麦克风按钮开始语音输入，识别后自动翻译",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // Language selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = true,
                onClick = { },
                label = { Text(uiState.sourceLang.displayName) }
            )
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.align(Alignment.CenterVertically))
            FilterChip(
                selected = true,
                onClick = { },
                label = { Text(uiState.targetLang.displayName) }
            )
        }

        // Recognized text card
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "识别文本",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (recognizedText.isNotBlank()) recognizedText else "等待语音输入...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (recognizedText.isNotBlank())
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.outline
                )
            }
        }

        // Translation result card
        if (uiState.resultText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "翻译结果",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiState.resultText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Error display
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Microphone button
        FloatingActionButton(
            onClick = {
                if (!isListening) {
                    startListening()
                }
            },
            containerColor = if (isListening)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isListening) "停止" else "开始录音",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        if (isListening) {
            Text(
                text = "正在聆听...",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
