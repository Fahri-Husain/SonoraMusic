package com.fahrii.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.Song

/**
 * Halaman satu album: sampul, judul, artis (ketuk untuk membuka halaman artis), tombol Putar,
 * dan tracklist lengkap sesuai urutan album.
 */
@Composable
fun AlbumScreen(
    state: AlbumUiState,
    currentSongUrl: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coverShape = RoundedCornerShape(16.dp)
    val songs = state.songs

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp + LocalBottomInset.current
        )
    ) {
        item {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.BackArrow, contentDescription = "Kembali", modifier = Modifier.size(20.dp))
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(coverShape)
                        .background(Sonora.SurfaceHigh)
                        .border(1.dp, Sonora.Border, coverShape)
                ) {
                    if (state.coverUrl != null) {
                        AsyncImage(
                            model = state.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Text(
                    text = state.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = state.artist,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenArtist(state.artist) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                if (songs.isNotEmpty()) {
                    val totalSec = songs.sumOf { it.durationSeconds.coerceAtLeast(0L) }
                    val info = buildString {
                        append("Album · ${songs.size} ${if (songs.size == 1) "song" else "songs"}")
                        if (totalSec > 0) append(" · ${totalSec / 60} min")
                    }
                    Text(
                        text = info,
                        fontSize = 13.sp,
                        color = Sonora.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onPlay(songs, 0) }
                            .padding(horizontal = 32.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Putar",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        when {
            state.loading -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }

            state.failed || songs.isEmpty() -> item {
                Text(
                    text = "Tracklist album ini tidak bisa dimuat.",
                    fontSize = 14.sp,
                    color = Sonora.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            }

            else -> itemsIndexed(songs, key = { index, song -> "$index-${song.url}" }) { index, song ->
                AlbumTrackRow(
                    number = index + 1,
                    song = song,
                    isCurrent = song.url == currentSongUrl,
                    isPlaying = isPlaying,
                    onClick = { onPlay(songs, index) }
                )
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    number: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$number",
            fontSize = 15.sp,
            color = Sonora.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Ritme lagu yang sedang aktif, di sebelah kiri durasi: bergerak saat diputar, diam saat dijeda
        if (isCurrent) {
            PlayingBars(isPlaying = isPlaying, modifier = Modifier.padding(start = 12.dp))
        }
        if (song.durationSeconds > 0) {
            Text(
                text = "%d:%02d".format(song.durationSeconds / 60, song.durationSeconds % 60),
                fontSize = 13.sp,
                color = Sonora.TextSecondary,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}