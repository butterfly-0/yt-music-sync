package com.ytmusic.downloader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.ui.theme.SpotifyCard
import com.ytmusic.downloader.ui.theme.SpotifyCardHover
import com.ytmusic.downloader.ui.theme.SpotifyDark
import com.ytmusic.downloader.ui.theme.SpotifyGreen
import com.ytmusic.downloader.ui.theme.SpotifySurface
import com.ytmusic.downloader.ui.theme.SpotifyTextSecondary
import com.ytmusic.downloader.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val accountName by viewModel.accountName.collectAsState()
    val accountEmail by viewModel.accountEmail.collectAsState()
    val audioFormat by viewModel.audioFormat.collectAsState()
    val syncInterval by viewModel.syncIntervalHours.collectAsState()
    val isWifiOnly by viewModel.isWifiOnly.collectAsState()
    val isChargingOnly by viewModel.isChargingOnly.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val storageDisplayName by viewModel.customDownloadDisplayName.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uri.path ?: uri.toString()
            val friendlyName = path.substringAfterLast(":").ifBlank { "Обрана папка" }
            viewModel.setCustomDownloadFolder(uri, friendlyName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 160.dp)
    ) {
        // Spotify Header
        Column(
            modifier = Modifier.padding(top = 18.dp, bottom = 14.dp)
        ) {
            Text(
                text = "Налаштування",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 28.sp
                )
            )
            Text(
                text = "Параметри акаунту, формату та сховища",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = SpotifyTextSecondary,
                    fontSize = 13.sp
                )
            )
        }

        // Section: YouTube Account
        SpotifySectionHeader("ОБЛІКОВИЙ ЗАПИС")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SpotifyCard)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isLoggedIn) SpotifyGreen.copy(alpha = 0.2f) else SpotifySurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = if (isLoggedIn) SpotifyGreen else SpotifyTextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) (accountName.ifBlank { "Авторизовано" }) else "Не авторизовано",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = if (isLoggedIn) (accountEmail.ifBlank { "Синхронізація плейлистів активна" }) else "Увійдіть для синхронізації вподобаного",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SpotifyTextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoggedIn) {
                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Вийти з акаунту", color = Color(0xFFFF5252))
                    }
                } else {
                    Button(
                        onClick = onNavigateToLogin,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Login,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Увійти через Google / YouTube", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Storage & Directory
        SpotifySectionHeader("СХОВИЩЕ ТА ПАПКА ДЛЯ МУЗИКИ")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SpotifyCard)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.openMusicFolder() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Системна папка аудіо",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Music/YouTubeSync/ (Доступна у всіх плеєрах)",
                            style = MaterialTheme.typography.bodySmall.copy(color = SpotifyTextSecondary, fontSize = 11.sp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Відкрити",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { folderPickerLauncher.launch(null) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Власна папка збереження",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = storageDisplayName ?: "Стандартна системна директорія",
                            style = MaterialTheme.typography.bodySmall.copy(color = SpotifyTextSecondary, fontSize = 11.sp)
                        )
                    }
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifySurface, contentColor = Color.White)
                    ) {
                        Text("Обрати", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Audio Format
        SpotifySectionHeader("ФОРМАТ ТА ЯКІСТЬ АУДІО")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SpotifyCard)
                .padding(16.dp)
        ) {
            Column {
                SpotifyFormatOption(
                    title = "M4A (AAC 256 kbps)",
                    subtitle = "Нативна якість без перекодування • Рекомендовано",
                    isSelected = audioFormat == AudioFormat.M4A,
                    onClick = { viewModel.setAudioFormat(AudioFormat.M4A) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SpotifyFormatOption(
                    title = "MP3 (320 kbps)",
                    subtitle = "Максимальна сумісність із тегами ID3v2",
                    isSelected = audioFormat == AudioFormat.MP3,
                    onClick = { viewModel.setAudioFormat(AudioFormat.MP3) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Background Sync
        SpotifySectionHeader("ФОНОВА СИНХРОНІЗАЦІЯ")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SpotifyCard)
                .padding(16.dp)
        ) {
            Column {
                SpotifySwitchOption(
                    icon = Icons.Default.Wifi,
                    title = "Тільки через Wi-Fi",
                    subtitle = "Не використовувати мобільні дані для завантаження",
                    checked = isWifiOnly,
                    onCheckedChange = { viewModel.setWifiOnly(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SpotifySwitchOption(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Тільки під час зарядки",
                    subtitle = "Заощаджувати заряд акумулятора",
                    checked = isChargingOnly,
                    onCheckedChange = { viewModel.setChargingOnly(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: App Updates
        SpotifySectionHeader("ОНОВЛЕННЯ ПРОГРАМИ")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SpotifyCard)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Поточна версія: v1.1.0",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Перевірка нових випусків через GitHub Releases",
                            style = MaterialTheme.typography.bodySmall.copy(color = SpotifyTextSecondary, fontSize = 11.sp)
                        )
                    }

                    Button(
                        onClick = { viewModel.checkForUpdates() },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Перевірити", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            color = SpotifyTextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SpotifyFormatOption(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SpotifySurface else Color.Transparent)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) SpotifyGreen else Color.White,
                    fontSize = 14.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = SpotifyTextSecondary, fontSize = 11.sp)
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SpotifySwitchOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = SpotifyTextSecondary, fontSize = 11.sp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SpotifyGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}
