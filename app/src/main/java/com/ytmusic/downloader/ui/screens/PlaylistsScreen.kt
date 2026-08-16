package com.ytmusic.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytmusic.downloader.R
import com.ytmusic.downloader.data.model.Playlist
import com.ytmusic.downloader.data.model.Track
import com.ytmusic.downloader.ui.components.PlaylistCard
import com.ytmusic.downloader.ui.theme.SpotifyCard
import com.ytmusic.downloader.ui.theme.SpotifyCardHover
import com.ytmusic.downloader.ui.theme.SpotifyDark
import com.ytmusic.downloader.ui.theme.SpotifyGreen
import com.ytmusic.downloader.ui.theme.SpotifySurface
import com.ytmusic.downloader.ui.theme.SpotifyTextSecondary
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

    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val playlistTracks by viewModel.playlistTracks.collectAsState()
    val isLoadingTracks by viewModel.isLoadingTracks.collectAsState()
    val downloadingTrackIds by viewModel.downloadingTrackIds.collectAsState()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsState()
    val currentPlayingTrackId by viewModel.currentPlayingTrackId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }
    var inputTitle by remember { mutableStateOf("") }

    Scaffold(
        containerColor = SpotifyDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = SpotifyGreen,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 90.dp)
                    .shadow(elevation = 12.dp, shape = CircleShape)
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
            // Spotify Header with Sync button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Плейлисти",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 28.sp
                        )
                    )
                    Text(
                        text = "Синхронізація вподобаного та плейлистів",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = SpotifyTextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }

                IconButton(
                    onClick = { viewModel.syncPlaylists() },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SpotifySurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Оновити плейлисти",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Spotify Info Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifySurface)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Натисніть на будь-який плейлист, щоб переглянути список треків всередині.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = SpotifyTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Playlists List
            LazyColumn(
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = playlists,
                    key = { it.id }
                ) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { viewModel.selectPlaylist(playlist) },
                        onToggleEnabled = { isEnabled ->
                            viewModel.togglePlaylistEnabled(playlist, isEnabled)
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

    // Modal Bottom Sheet: Playlist Tracks Details
    if (selectedPlaylist != null) {
        val playlist = selectedPlaylist!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { viewModel.closePlaylistDetail() },
            sheetState = sheetState,
            containerColor = SpotifyDark,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifySurface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playlist.thumbnailUrl != null) {
                            AsyncImage(
                                model = playlist.thumbnailUrl,
                                contentDescription = playlist.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        } else {
                            Icon(
                                imageVector = if (playlist.isLikedMusic) Icons.Default.Favorite else Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = if (playlist.isLikedMusic) SpotifyGreen else SpotifyTextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLoadingTracks) "Отримання треків…" else "${playlistTracks.size} знайдених треків",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SpotifyTextSecondary,
                                fontSize = 13.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = { viewModel.closePlaylistDetail() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SpotifySurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрити",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Content
                if (isLoadingTracks) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = SpotifyGreen,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Завантаження списку треків з YouTube Music…",
                                color = SpotifyTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (playlistTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "У цьому плейлисті поки немає доступних треків",
                            color = SpotifyTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlistTracks, key = { it.id }) { track ->
                            val isThisPlaying = isPlaying && currentPlayingTrackId == track.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpotifySurface)
                                    .clickable { viewModel.togglePlayPreview(track) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SpotifyCardHover),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (track.thumbnailUrl != null) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.MusicNote, null, tint = SpotifyTextSecondary)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isThisPlaying) SpotifyGreen else Color.White,
                                            fontSize = 14.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = SpotifyTextSecondary,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val isDownloading = downloadingTrackIds.contains(track.id)
                                val isDownloaded = downloadedTrackIds.contains(track.id)

                                if (isDownloading) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(SpotifyGreen.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = SpotifyGreen,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else if (isDownloaded) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(SpotifyGreen.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Завантажено",
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { viewModel.downloadSingleTrack(track) },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(SpotifySurface)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Завантажити трек",
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Custom Playlist Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                viewModel.clearError()
            },
            containerColor = SpotifyCard,
            title = {
                Text(
                    text = "Додати список відтворення",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Введіть посилання на плейлист або його ID (наприклад: PL...):",
                        style = MaterialTheme.typography.bodyMedium.copy(color = SpotifyTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("URL або ID плейлиста") },
                        placeholder = { Text("https://music.youtube.com/playlist?list=PL...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SpotifySurface,
                            unfocusedContainerColor = SpotifySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SpotifyGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Назва (опціонально)") },
                        placeholder = { Text("Мій плейлист") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SpotifySurface,
                            unfocusedContainerColor = SpotifySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SpotifyGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (addError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = addError!!,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFF5252))
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            viewModel.addPlaylist(
                                urlOrId = inputUrl.trim(),
                                title = inputTitle.trim().ifBlank { null }
                            )
                            showAddDialog = false
                            inputUrl = ""
                            inputTitle = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !isAdding && inputUrl.isNotBlank()
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Додати", fontWeight = FontWeight.Bold)
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
                    Text("Скасувати", color = SpotifyTextSecondary)
                }
            }
        )
    }
}

