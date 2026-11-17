package com.fahrii.musicapp.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val library by libraryViewModel.state.collectAsState()
    val song = state.currentSong

    if (song == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada lagu yang diputar")
        }
        return
    }

    val isLiked = library.liked.any { it.url == song.url }
    var showAddDialog by remember { mutableStateOf(false) }

    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val durationMs = progress.durationMs
    val sliderValue = if (isDragging) {
        dragValue
    } else if (durationMs > 0) {
        (progress.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownPositionMs = if (isDragging) (dragValue * durationMs).toLong() else progress.positionMs

    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.Back, contentDescription = "Kembali")
            }
        }

        Spacer(Modifier.height(16.dp))

        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = inactive,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { libraryViewModel.toggleLike(song) }) {
                Icon(
                    imageVector = if (isLiked) AppIcons.HeartFilled else AppIcons.Heart,
                    contentDescription = "Suka",
                    tint = if (isLiked) HeartColor else inactive
                )
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = AppIcons.PlaylistAdd,
                    contentDescription = "Tambah ke playlist",
                    tint = inactive
                )
            }
        }

        if (showAddDialog) {
            AddToPlaylistDialog(
                playlists = library.playlists,
                onSelect = {
                    libraryViewModel.addToPlaylist(it.id, song)
                    showAddDialog = false
                },
                onCreateNew = { name ->
                    libraryViewModel.createPlaylist(name, song)
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false }
            )
        }

        Spacer(Modifier.height(16.dp))

        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                viewModel.seekTo((dragValue * durationMs).toLong())
                isDragging = false
            },
            enabled = durationMs > 0
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(shownPositionMs), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(durationMs), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    imageVector = AppIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffleEnabled) active else inactive
                )
            }
            IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = AppIcons.Previous,
                    contentDescription = "Sebelumnya",
                    modifier = Modifier.size(36.dp)
                )
            }
            FilledIconButton(
                onClick = viewModel::togglePlayPause,
                modifier = Modifier.size(72.dp)
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            IconButton(
                onClick = viewModel::next,
                enabled = state.hasNext,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Next,
                    contentDescription = "Berikutnya",
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = viewModel::cycleRepeat) {
                Icon(
                    imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                        AppIcons.RepeatOne
                    } else {
                        AppIcons.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) active else inactive
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}