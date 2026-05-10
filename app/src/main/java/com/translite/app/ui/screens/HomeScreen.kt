package com.translite.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.translite.app.domain.model.Language
import com.translite.app.ui.components.TranslationInput
import com.translite.app.ui.components.TranslationResultCard
import com.translite.app.ui.viewmodel.TranslationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: TranslationUiState,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onSwapLanguages: () -> Unit,
    onSourceLangChange: (Language) -> Unit,
    onTargetLangChange: (Language) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSourceLangPicker by remember { mutableStateOf(false) }
    var showTargetLangPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Language selector bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = true,
                onClick = { showSourceLangPicker = true },
                label = { Text(uiState.sourceLang.getDisplayName(LocalContext.current)) },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
            )

            IconButton(onClick = onSwapLanguages) {
                Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.swap_languages))
            }

            FilterChip(
                selected = true,
                onClick = { showTargetLangPicker = true },
                label = { Text(uiState.targetLang.getDisplayName(LocalContext.current)) },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
            )
        }

        // Input area
        TranslationInput(
            text = uiState.inputText,
            onTextChange = onInputChange,
            hint = stringResource(R.string.input_hint),
            modifier = Modifier.weight(1f, fill = false)
        )

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
            }

            Button(
                onClick = onTranslate,
                enabled = uiState.inputText.isNotBlank() && !uiState.isTranslating
            ) {
                if (uiState.isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.translate))
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

        // Result
        if (uiState.resultText.isNotBlank()) {
            TranslationResultCard(text = uiState.resultText)
        }
    }

    if (showSourceLangPicker) {
        LanguagePickerDialog(
            languages = uiState.languages,
            onSelect = { lang ->
                onSourceLangChange(lang)
                showSourceLangPicker = false
            },
            onDismiss = { showSourceLangPicker = false }
        )
    }

    if (showTargetLangPicker) {
        LanguagePickerDialog(
            languages = uiState.languages.filter { it != Language.AUTO },
            onSelect = { lang ->
                onTargetLangChange(lang)
                showTargetLangPicker = false
            },
            onDismiss = { showTargetLangPicker = false }
        )
    }
}

@Composable
fun LanguagePickerDialog(
    languages: List<Language>,
    onSelect: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            Column {
                languages.forEach { lang ->
                    TextButton(
                        onClick = { onSelect(lang) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.language_name_format, lang.getDisplayName(LocalContext.current), lang.displayNameEn))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
