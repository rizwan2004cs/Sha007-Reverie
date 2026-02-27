package com.theveloper.pixelplay.presentation.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.theveloper.pixelplay.presentation.components.AlwaysOnScalingPositionIndicator
import com.theveloper.pixelplay.presentation.components.PlayingEqIcon
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.screenBackgroundColor
import com.theveloper.pixelplay.presentation.theme.surfaceContainerColor
import com.theveloper.pixelplay.presentation.theme.surfaceContainerHighColor
import com.theveloper.pixelplay.presentation.viewmodel.WearDownloadsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel
import com.theveloper.pixelplay.shared.WearTransferProgress

/**
 * Screen showing songs stored locally on the watch.
 * Tapping a song starts local ExoPlayer playback.
 */
@Composable
fun DownloadsScreen(
    onSongClick: (songId: String) -> Unit = {},
    viewModel: WearDownloadsViewModel = hiltViewModel(),
    playerViewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val localSongs by viewModel.localSongs.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val deviceSongs by viewModel.deviceSongs.collectAsState()
    val isDeviceLibraryLoading by viewModel.isDeviceLibraryLoading.collectAsState()
    val deviceLibraryError by viewModel.deviceLibraryError.collectAsState()
    val playerState by playerViewModel.playerState.collectAsState()
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()
    val context = LocalContext.current
    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasAudioPermission by remember {
        mutableStateOf(hasAudioLibraryPermission(context, audioPermission))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
    }

    LaunchedEffect(Unit) {
        hasAudioPermission = hasAudioLibraryPermission(context, audioPermission)
    }
    LaunchedEffect(hasAudioPermission) {
        viewModel.refreshDeviceLibrary(hasPermission = hasAudioPermission)
    }
    val transferringStates = activeTransfers.values
        .filter { it.status == WearTransferProgress.STATUS_TRANSFERRING }
        .sortedByDescending { it.bytesTransferred }

    val background = palette.screenBackgroundColor()
    val surfaceContainer = palette.surfaceContainerColor()
    val elevatedSurfaceContainer = palette.surfaceContainerHighColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = "Watch Library",
                    style = MaterialTheme.typography.title3,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            if (transferringStates.isNotEmpty()) {
                item {
                    Text(
                        text = "Transferring from phone",
                        style = MaterialTheme.typography.caption2,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                    )
                }

                items(transferringStates.size) { index ->
                    val transfer = transferringStates[index]
                    val progressText = if (transfer.totalBytes > 0L) {
                        "${(transfer.progress * 100f).toInt().coerceIn(0, 100)}%"
                    } else {
                        "Starting..."
                    }
                    Chip(
                        label = {
                            Text(
                                text = transfer.songTitle.ifBlank { "Preparing transfer..." },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = progressText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textSecondary.copy(alpha = 0.82f),
                            )
                        },
                        icon = {
                            CircularProgressIndicator(
                                indicatorColor = palette.shuffleActive,
                                trackColor = surfaceContainer,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        },
                        onClick = {},
                        colors = ChipDefaults.chipColors(
                            backgroundColor = elevatedSurfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Text(
                    text = "Saved from phone",
                    style = MaterialTheme.typography.caption2,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                )
            }

            if (localSongs.isEmpty()) {
                item {
                    Text(
                        text = "No transferred songs",
                        style = MaterialTheme.typography.body2,
                        color = palette.textSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            } else {
                items(localSongs.size) { index ->
                    val song = localSongs[index]
                    val isCurrentSong = song.songId == playerState.songId && playerState.songId.isNotBlank()
                    val isPlayingSong = isCurrentSong && playerState.isPlaying
                    val secondaryText = if (
                        song.artist.isNotEmpty() ||
                        song.album.isNotEmpty() ||
                        song.duration > 0L
                    ) {
                        buildSongSubtitle(
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.duration,
                        )
                    } else {
                        ""
                    }
                    Chip(
                        label = {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = if (secondaryText.isNotEmpty() || isCurrentSong) {
                            {
                                Text(
                                    text = if (isCurrentSong) {
                                        if (secondaryText.isNotEmpty()) {
                                            "${if (isPlayingSong) "Playing" else "Current"} · $secondaryText"
                                        } else if (isPlayingSong) {
                                            "Playing"
                                        } else {
                                            "Current"
                                        }
                                    } else {
                                        secondaryText
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrentSong && isPlayingSong) {
                                        palette.shuffleActive.copy(alpha = 0.90f)
                                    } else {
                                        palette.textSecondary.copy(alpha = 0.78f)
                                    },
                                )
                            }
                        } else null,
                        icon = {
                            if (isCurrentSong) {
                                PlayingEqIcon(
                                    color = if (isPlayingSong) palette.shuffleActive else palette.textSecondary,
                                    isPlaying = isPlayingSong,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        onClick = {
                            viewModel.playLocalSong(song.songId)
                            onSongClick(song.songId)
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (isCurrentSong) elevatedSurfaceContainer else surfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Text(
                    text = "Songs on watch storage",
                    style = MaterialTheme.typography.caption2,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                )
            }

            if (!hasAudioPermission) {
                item {
                    Chip(
                        label = {
                            Text(
                                text = "Allow audio access",
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = "Read watch library",
                                color = palette.textSecondary.copy(alpha = 0.8f),
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { permissionLauncher.launch(audioPermission) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = surfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (isDeviceLibraryLoading) {
                item {
                    Text(
                        text = "Scanning watch storage...",
                        style = MaterialTheme.typography.body2,
                        color = palette.textSecondary.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            } else if (deviceLibraryError != null) {
                item {
                    Chip(
                        label = {
                            Text(
                                text = "Retry scan",
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = deviceLibraryError.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textSecondary.copy(alpha = 0.8f),
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { viewModel.refreshDeviceLibrary(hasPermission = true) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = surfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (deviceSongs.isEmpty()) {
                item {
                    Text(
                        text = "No local songs found",
                        style = MaterialTheme.typography.body2,
                        color = palette.textSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            } else {
                items(deviceSongs.size) { index ->
                    val song = deviceSongs[index]
                    val isCurrentSong = song.songId == playerState.songId && playerState.songId.isNotBlank()
                    val isPlayingSong = isCurrentSong && playerState.isPlaying
                    val secondaryText = if (song.artist.isNotEmpty() || song.album.isNotEmpty()) {
                        buildSongSubtitle(
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.durationMs,
                        )
                    } else {
                        ""
                    }
                    Chip(
                        label = {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = if (secondaryText.isNotEmpty() || isCurrentSong) {
                            {
                                Text(
                                    text = if (isCurrentSong) {
                                        if (secondaryText.isNotEmpty()) {
                                            "${if (isPlayingSong) "Playing" else "Current"} · $secondaryText"
                                        } else if (isPlayingSong) {
                                            "Playing"
                                        } else {
                                            "Current"
                                        }
                                    } else {
                                        secondaryText
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrentSong && isPlayingSong) {
                                        palette.shuffleActive.copy(alpha = 0.90f)
                                    } else {
                                        palette.textSecondary.copy(alpha = 0.78f)
                                    },
                                )
                            }
                        } else null,
                        icon = {
                            if (isCurrentSong) {
                                PlayingEqIcon(
                                    color = if (isPlayingSong) palette.shuffleActive else palette.textSecondary,
                                    isPlaying = isPlayingSong,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        onClick = {
                            viewModel.playDeviceSong(song.songId)
                            onSongClick(song.songId)
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (isCurrentSong) elevatedSurfaceContainer else surfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

private fun hasAudioLibraryPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun buildSongSubtitle(artist: String, album: String, durationMs: Long): String {
    val parts = buildList {
        if (artist.isNotBlank()) add(artist)
        if (album.isNotBlank()) add(album)
        if (durationMs > 0L) add(formatDuration(durationMs))
    }
    return parts.joinToString(" · ")
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
