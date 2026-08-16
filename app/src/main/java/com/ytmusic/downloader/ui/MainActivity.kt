package com.ytmusic.downloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ytmusic.downloader.ui.components.BottomNavBar
import com.ytmusic.downloader.ui.components.Screen
import com.ytmusic.downloader.ui.components.SpotifyMiniPlayer
import com.ytmusic.downloader.ui.components.SpotifyNowPlayingSheet
import com.ytmusic.downloader.ui.screens.HomeScreen
import com.ytmusic.downloader.ui.screens.LoginScreen
import com.ytmusic.downloader.ui.screens.PlaylistsScreen
import com.ytmusic.downloader.ui.screens.PlayerScreen
import com.ytmusic.downloader.ui.screens.SettingsScreen
import com.ytmusic.downloader.ui.theme.DarkBackground
import com.ytmusic.downloader.ui.theme.YTMusicDownloaderTheme
import com.ytmusic.downloader.ui.viewmodel.MainViewModel
import com.ytmusic.downloader.ui.viewmodel.PlaylistsViewModel
import com.ytmusic.downloader.ui.viewmodel.PlayerViewModel
import com.ytmusic.downloader.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val playlistsViewModel: PlaylistsViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAppPermissions()

        setContent {
            YTMusicDownloaderTheme {
                MainAppNavHost(
                    mainViewModel = mainViewModel,
                    playlistsViewModel = playlistsViewModel,
                    playerViewModel = playerViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun MainAppNavHost(
    mainViewModel: MainViewModel,
    playlistsViewModel: PlaylistsViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val showBottomBar = currentRoute != Screen.Login.route
    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val progress by playerViewModel.progress.collectAsState()
    val currentPositionMs by playerViewModel.currentPositionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val isShuffle by playerViewModel.isShuffle.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val isLoadingLyrics by playerViewModel.isLoadingLyrics.collectAsState()
    val isNowPlayingExpanded by playerViewModel.isNowPlayingExpanded.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = DarkBackground,
            bottomBar = {
                if (showBottomBar) {
                    Column {
                        if (currentTrack != null) {
                            SpotifyMiniPlayer(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                progress = progress,
                                onPlayPause = { playerViewModel.playOrPause() },
                                onClick = { playerViewModel.expandNowPlaying() }
                            )
                        }

                        BottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(viewModel = mainViewModel)
                }

                composable(Screen.Playlists.route) {
                    PlaylistsScreen(viewModel = playlistsViewModel)
                }

                composable(Screen.Player.route) {
                    PlayerScreen(viewModel = playerViewModel)
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToLogin = {
                            navController.navigate(Screen.Login.route)
                        }
                    )
                }

                composable(Screen.Login.route) {
                    LoginScreen(
                        viewModel = settingsViewModel,
                        onLoginSuccess = {
                            mainViewModel.startSync()
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        // Full Screen Spotify Now Playing Player Overlay
        SpotifyNowPlayingSheet(
            isVisible = isNowPlayingExpanded,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            progress = progress,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            lyrics = lyrics,
            currentLyricIndex = currentLyricIndex,
            isLoadingLyrics = isLoadingLyrics,
            onCollapse = { playerViewModel.collapseNowPlaying() },
            onPlayPause = { playerViewModel.playOrPause() },
            onNext = { playerViewModel.next() },
            onPrevious = { playerViewModel.previous() },
            onSeek = { playerViewModel.seekTo(it) },
            onToggleShuffle = { playerViewModel.toggleShuffle() },
            onToggleRepeat = { playerViewModel.toggleRepeat() }
        )
    }
}
