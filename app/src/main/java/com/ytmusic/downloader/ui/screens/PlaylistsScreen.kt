package com.ytmusic.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytmusic.downloader.R
import com.ytmusic.downloader.ui.components.PlaylistCard
import com.ytmusic.downloader.ui.theme.AccentBlue
import com.ytmusic.downloader.ui.theme.AccentRed
import com.ytmusic.downloader.ui.theme.BorderSubtle
import com.ytmusic.downloader.ui.theme.DarkBackground
import com.ytmusic.downloader.ui.theme.DarkCard
import com.ytmusic.downloader.ui.theme.DarkSurface
import com.ytmusic.downloader.ui.theme.TextPrimary
import com.ytmusic.downloader.ui.theme.TextSecondary
import com.ytmusic.downloader.ui.theme.TextTertiary
import com.ytmusic.downloader.ui.viewmodel.PlaylistsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val isAdding by viewModel.isAddingPlaylist.collectAsState()
    val addError by viewModel.addPlaylistError.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }
    var inputTitle by remember { mutableStateOf("") }

    Scaffold(
        containerColor = DarkBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 70.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_playlist)
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Column(
                modifier = Modifier.padding(top = 18.dp, bottom = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_playlists),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Керування списками відтворення для завантаження",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }

            // Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Додаток автоматично відстежує увімкнені плейлисти та завантажує нові треки за розкладом.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Playlists List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onToggleEnabled = { enabled ->
                            viewModel.togglePlaylistEnabled(playlist, enabled)
                        },
                        onToggleSyncOnlyNew = { syncOnlyNew ->
                            viewModel.toggleSyncOnlyNew(playlist, syncOnlyNew)
                        },
                        onDelete = {
                            viewModel.deletePlaylist(playlist)
                        }
                    )
                }
            }
        }
    }

    // Add Playlist Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isAdding) {
                    showAddDialog = false
                    viewModel.clearError()
                }
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = stringResource(R.string.add_playlist),
                    style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Вставте посилання на плейлист з YouTube або YouTube Music:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text(stringResource(R.string.playlist_url_hint), color = TextTertiary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            containerColor = DarkCard,
                            focusedBorderColor = AccentRed,
                            unfocusedBorderColor = BorderSubtle,
                            cursorColor = AccentRed,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        placeholder = { Text(stringResource(R.string.playlist_title_hint), color = TextTertiary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            containerColor = DarkCard,
                            focusedBorderColor = AccentRed,
                            unfocusedBorderColor = BorderSubtle,
                            cursorColor = AccentRed,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (addError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = addError ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(color = AccentRed)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            viewModel.addPlaylist(inputUrl, inputTitle)
                            inputUrl = ""
                            inputTitle = ""
                            showAddDialog = false
                        }
                    },
                    enabled = inputUrl.isNotBlank() && !isAdding,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Додати", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        viewModel.clearError()
                    }
                ) {
                    Text("Скасувати", color = TextSecondary)
                }
            }
        )
    }
}
