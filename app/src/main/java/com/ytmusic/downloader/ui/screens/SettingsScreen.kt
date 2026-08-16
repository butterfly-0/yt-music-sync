package com.ytmusic.downloader.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytmusic.downloader.R
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.ui.theme.AccentBlue
import com.ytmusic.downloader.ui.theme.AccentGreen
import com.ytmusic.downloader.ui.theme.AccentRed
import com.ytmusic.downloader.ui.theme.BorderSubtle
import com.ytmusic.downloader.ui.theme.DarkBackground
import com.ytmusic.downloader.ui.theme.DarkCard
import com.ytmusic.downloader.ui.theme.DarkCardElevated
import com.ytmusic.downloader.ui.theme.DarkSurface
import com.ytmusic.downloader.ui.theme.TextPrimary
import com.ytmusic.downloader.ui.theme.TextSecondary
import com.ytmusic.downloader.ui.theme.TextTertiary
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 90.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.padding(top = 18.dp, bottom = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Text(
                text = "Конфігурація завантажень, якості та акаунту",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
        }

        // Section: YouTube Account
        SettingsSectionHeader("Обліковий запис YouTube")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isLoggedIn) AccentGreen.copy(alpha = 0.15f) else DarkCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = if (isLoggedIn) AccentGreen else TextTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) (accountName.ifBlank { "Авторизовано" }) else "Не авторизовано",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isLoggedIn) (accountEmail.ifBlank { "Доступ до приватного вподобаного активний" }) else "Увійдіть для синхронізації вподобаних треків",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoggedIn) {
                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Вийти з акаунту", color = AccentRed)
                    }
                } else {
                    Button(
                        onClick = onNavigateToLogin,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Login,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Увійти через Google / YouTube", color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Audio Format & Quality
        SettingsSectionHeader("Формат та якість аудіо")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                FormatOptionItem(
                    title = "M4A (256 kbps AAC)",
                    subtitle = "Нативний потік з YouTube без втрати якості та найшвидше завантаження",
                    isSelected = audioFormat == AudioFormat.M4A,
                    onClick = { viewModel.setAudioFormat(AudioFormat.M4A) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                FormatOptionItem(
                    title = "MP3 (320 kbps)",
                    subtitle = "Конвертований формат з повним вшиванням ID3v2 тегів та обкладинки",
                    isSelected = audioFormat == AudioFormat.MP3,
                    onClick = { viewModel.setAudioFormat(AudioFormat.MP3) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Background Sync & Constraints
        SettingsSectionHeader("Фонова синхронізація")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Sync Interval Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Інтервал перевірки",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                        )
                        Text(
                            text = "Кожні $syncInterval год",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 6, 24).forEach { hours ->
                        val selected = syncInterval == hours
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AccentRed else DarkCardElevated)
                                .clickable { viewModel.setSyncIntervalHours(hours) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (hours == 24) "1 раз/день" else "$hours год",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else TextSecondary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Wi-Fi Only Switch
                SettingsSwitchRow(
                    icon = Icons.Default.Wifi,
                    title = "Тільки по Wi-Fi",
                    subtitle = "Не використовувати мобільний трафік у фоні",
                    checked = isWifiOnly,
                    onCheckedChange = { viewModel.setWifiOnly(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Charging Only Switch
                SettingsSwitchRow(
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
        SettingsSectionHeader("Оновлення додатку")
        val updateState by viewModel.updateState.collectAsState()

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Поточна версія: v1.0.0",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                        )
                        Text(
                            text = "GitHub Releases (автооновлення без видалення)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (val state = updateState) {
                    is com.ytmusic.downloader.update.UpdateState.Idle -> {
                        Button(
                            onClick = { viewModel.checkForUpdates() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCardElevated),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Перевірити оновлення", color = TextPrimary)
                        }
                    }
                    is com.ytmusic.downloader.update.UpdateState.Checking -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AccentRed,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Перевірка наявності нової версії…", color = TextSecondary)
                        }
                    }
                    is com.ytmusic.downloader.update.UpdateState.Available -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCard)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Доступна нова версія: v${state.info.versionName}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.info.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.downloadAndInstallUpdate(state.info) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Завантажити та встановити оновлення", color = Color.White)
                            }
                        }
                    }
                    is com.ytmusic.downloader.update.UpdateState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Завантаження нової версії…", color = TextPrimary)
                                Text("${state.progressPercent}%", color = AccentRed)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = AccentRed,
                                trackColor = DarkCardElevated
                            )
                        }
                    }
                    is com.ytmusic.downloader.update.UpdateState.ReadyToInstall -> {
                        Button(
                            onClick = { viewModel.appUpdateManager.triggerInstall(state.apkFile) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Встановити оновлення", color = Color.White)
                        }
                    }
                    is com.ytmusic.downloader.update.UpdateState.UpToDate -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Check, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("У вас встановлено найновішу версію", color = AccentGreen)
                        }
                    }
                    is com.ytmusic.downloader.update.UpdateState.Error -> {
                        Column {
                            Text(state.message, color = AccentRed, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCardElevated)
                            ) {
                                Text("Повторити", color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Storage info
        SettingsSectionHeader("Сховище")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Системна папка аудіо",
                        style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                    )
                    Text(
                        text = "Music/YouTubeSync/ (автоматично доступно у плеєрах)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = AccentRed,
            fontSize = 13.sp
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun FormatOptionItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) DarkCardElevated else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Audiotrack,
            contentDescription = null,
            tint = if (isSelected) AccentRed else TextTertiary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = TextPrimary
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
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
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentRed,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = DarkCardElevated
            )
        )
    }
}
