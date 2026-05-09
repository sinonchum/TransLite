package com.translite.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.translite.app.domain.model.Language
import com.translite.app.service.FloatingBallService

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val settingsItems = listOf(
        SettingsItem(
            title = "悬浮球翻译",
            subtitle = if (isFloatingActive) "已开启" else "关闭",
            icon = Icons.Default.OpenWith
        ) { onToggleFloating(!isFloatingActive) },
        SettingsItem(
            title = "悬浮窗权限",
            subtitle = "管理应用悬浮窗权限",
            icon = Icons.Default.PictureInPicture
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        },
        SettingsItem(
            title = "语言包管理",
            subtitle = "下载和管理离线翻译语言包",
            icon = Icons.Default.Language
        ) { onLanguagePacks() },
        SettingsItem(
            title = "关于",
            subtitle = "TransLite v1.2.0",
            icon = Icons.Default.Info
        ) { /* no-op */ }
    )

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

        items(settingsItems) { item ->
            Surface(
                onClick = item.onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(item.subtitle) },
                    leadingContent = {
                        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }

            HorizontalDivider()
        }
    }
}
