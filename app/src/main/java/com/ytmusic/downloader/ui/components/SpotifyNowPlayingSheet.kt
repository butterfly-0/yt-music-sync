package com.ytmusic.downloader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytmusic.downloader.data.model.Track
import com.ytmusic.downloader.player.LyricLine
import com.ytmusic.downloader.player.PlayerRepeatMode
import com.ytmusic.downloader.ui.theme.SpotifyDark
import com.ytmusic.downloader.ui.theme.SpotifyGreen
import com.ytmusic.downloader.ui.theme.SpotifyLyricsCardBg
import com.ytmusic.downloader.ui.theme.SpotifyTextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpotifyNowPlayingSheet(
    isVisible: Boolean,
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    progress: Float,
    isShuffle: Boolean,
    repeatMode: PlayerRepeatMode,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    isLoadingLyrics: Boolean,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (currentTrack == null) return@AnimatedVisibility

        var isUserSeeking by remember { mutableStateOf(false) }
        var seekSliderProgress by remember { mutableFloatStateOf(0f) }

        var isLiked by remember { mutableStateOf(true) }
        var isHeartPressed by remember { mutableStateOf(false) }
        val heartScale by animateFloatAsState(
            targetValue = if (isHeartPressed) 1.35f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "heart_scale_full"
        )

        // Spotify Signature Album Art Scale (expands when playing, scales down when paused)
        val artScale by animateFloatAsState(
            targetValue = if (isPlaying) 1.0f else 0.88f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
            label = "art_scale"
        )

        val scrollState = rememberScrollState()
        val lyricsListState = rememberLazyListState()

        var dragDownDistance by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(currentLyricIndex) {
            if (currentLyricIndex >= 0 && lyrics.isNotEmpty() && !lyricsListState.isScrollInProgress) {
                lyricsListState.animateScrollToItem((currentLyricIndex - 2).coerceAtLeast(0))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF381530),
                            Color(0xFF1E101D),
                            SpotifyDark,
                            Color.Black
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragDownDistance > 80f) {
                                onCollapse()
                            }
                            dragDownDistance = 0f
                        },
                        onDragCancel = { dragDownDistance = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            dragDownDistance += dragAmount
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(top = 44.dp, bottom = 48.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Згорнути",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ГРАЄ З МЕДІАТЕКИ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextSecondary,
                                letterSpacing = 1.6.sp,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = currentTrack.album.ifBlank { "Завантажені пісні" },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }

                    IconButton(
                        onClick = { /* Menu */ },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Опції",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Big Album Art with Spotify Scale Pulse
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .scale(artScale)
                        .shadow(
                            elevation = if (isPlaying) 28.dp else 12.dp,
                            shape = RoundedCornerShape(14.dp),
                            spotColor = Color.Black
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentTrack.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentTrack.thumbnailUrl,
                            contentDescription = currentTrack.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Title & Artist Row with Animated Bouncy Heart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 21.sp
                            ),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentTrack.artist,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = SpotifyTextSecondary,
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }

                    IconButton(
                        onClick = {
                            isLiked = !isLiked
                            isHeartPressed = true
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .scale(heartScale)
                    ) {
                        LaunchedEffect(isHeartPressed) {
                            if (isHeartPressed) {
                                delay(180)
                                isHeartPressed = false
                            }
                        }
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Улюблене",
                            tint = if (isLiked) SpotifyGreen else SpotifyTextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Seek Bar / Timeline
                val currentSliderValue = if (isUserSeeking) seekSliderProgress else progress
                Slider(
                    value = currentSliderValue.coerceIn(0f, 1f),
                    onValueChange = { newValue ->
                        isUserSeeking = true
                        seekSliderProgress = newValue
                    },
                    onValueChangeFinished = {
                        isUserSeeking = false
                        val targetMs = (seekSliderProgress * durationMs).toLong()
                        onSeek(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Timestamps Row
                val displayCurrentMs = if (isUserSeeking) (seekSliderProgress * durationMs).toLong() else currentPositionMs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(displayCurrentMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SpotifyTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SpotifyTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Main Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Shuffle
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Перемішати",
                            tint = if (isShuffle) SpotifyGreen else SpotifyTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Попередній",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Big Play / Pause Button with AnimatedContent
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "big_play_pause_transition"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Пауза" else "Грати",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // Next
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Наступний",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.size(42.dp)
                    ) {
                        val repeatIcon = if (repeatMode == PlayerRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Повтор",
                            tint = if (repeatMode != PlayerRepeatMode.OFF) SpotifyGreen else SpotifyTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(34.dp))

                // Spotify Real-time Synced Lyrics Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SpotifyLyricsCardBg)
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Текст пісні",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            )
                            if (isLoadingLyrics) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (lyrics.isEmpty()) {
                            Text(
                                text = if (isLoadingLyrics) "Пошук та завантаження слів пісні…" else "Для цього треку слова пісні не знайдено",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        } else {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(lyrics) { index, line ->
                                    val isActive = index == currentLyricIndex
                                    val alpha by animateFloatAsState(
                                        targetValue = if (isActive) 1f else 0.4f,
                                        label = "lyric_alpha"
                                    )

                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = Color.White.copy(alpha = alpha),
                                            fontSize = if (isActive) 21.sp else 16.sp,
                                            lineHeight = 26.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (line.timeMs > 0L) {
                                                    onSeek(line.timeMs)
                                                }
                                            }
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

private fun formatTime(timeMs: Long): String {
    val totalSec = (timeMs / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
