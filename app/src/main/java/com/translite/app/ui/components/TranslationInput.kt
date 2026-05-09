package com.translite.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TranslationInput(
    text: String,
    onTextChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 120.dp),
        placeholder = { Text(hint) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}
