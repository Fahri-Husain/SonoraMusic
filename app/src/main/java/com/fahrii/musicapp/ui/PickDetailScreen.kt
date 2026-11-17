package com.fahrii.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.Song

/**
 * Detail satu "Pilihan Cepat": cover mosaik, tombol Putar/Acak, lalu daftar lagunya.
 */
@Composable
fun PickDetailScreen(
    pick: QuickPick,
    currentSongUrl: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val songs = pick.songs

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)
        ) {
            item {
                PickHero(
                    pick = pick,
                    onPlayAll = { onPlay(songs, 0) },
                    onShuffle = { onPlay(songs.shuffled(), 0) }
                )
            }

            if (songs.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Memuat lagu…", fontSize = 14.sp, color = Sonora.TextSecondary)
                    }
                }
            } else {
                itemsIndexed(songs, key = { index, song -> "$index-${song.url}" }) { index, song ->
                    TrackRow(
                        number = index + 1,
                        song = song,
                        isCurrent = song.url == currentSongUrl,
                        isPlaying = isPlaying,
                        onClick = { onPlay(songs, index) }
                    )
                }
            }
        }

        // Tombol kembali melayang di atas hero
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.BackArrow,
                contentDescription = "Kembali",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PickHero(pick: QuickPick, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    val songs = pick.songs
    val totalSeconds = songs.sumOf { it.durationSeconds.coerceAtLeast(0L) }
    val info = buildString {
        append("${songs.size} lagu")
        if (totalSeconds > 0) append(" · ${formatTotal(totalSeconds)}")
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Latar: cover pertama diperbesar, diburamkan, lalu memudar ke warna background
        AsyncImage(
            model = songs.firstOrNull()?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(40.dp).alpha(0.55f)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Sonora.Background)))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 56.dp, bottom = 20.dp)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CoverMosaic(
                urls = songs.take(4).map { it.thumbnailUrl },
                modifier = Modifier.size(208.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = pick.label,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pick.subtitle,
                fontSize = 14.sp,
                color = Color(0xFFD0D0D5),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(info, fontSize = 12.sp, color = Sonora.TextSecondary)
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PillButton(
                    text = "Putar",
                    icon = AppIcons.Play,
                    filled = true,
                    enabled = songs.isNotEmpty(),
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f)
                )
                PillButton(
                    text = "Acak",
                    icon = AppIcons.Shuffle,
                    filled = false,
                    enabled = songs.isNotEmpty(),
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Empat cover digabung 2x2. Kalau lagunya kurang dari empat, pakai satu cover saja. */
@Composable
private fun CoverMosaic(urls: List<String?>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Sonora.SurfaceHigh)
            .border(1.dp, Sonora.Border, shape)
    ) {
        if (urls.size >= 4) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MosaicTile(urls[0], Modifier.weight(1f).fillMaxHeight())
                    MosaicTile(urls[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MosaicTile(urls[2], Modifier.weight(1f).fillMaxHeight())
                    MosaicTile(urls[3], Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            MosaicTile(urls.firstOrNull(), Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MosaicTile(url: String?, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun PillButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (filled) Color.White else Color.White.copy(alpha = 0.08f)
    val content = if (filled) Color.Black else Color.White

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            )
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = content, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrackRow(
    number: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val thumbShape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            if (isCurrent) {
                Icon(
                    imageVector = AppIcons.MusicNote,
                    contentDescription = null,
                    tint = if (isPlaying) Color.White else Sonora.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text("$number", fontSize = 14.sp, color = Sonora.TextSecondary)
            }
        }
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(thumbShape)
                .background(Sonora.Surface)
                .border(1.dp, Sonora.Border, thumbShape)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontSize = 13.sp,
                color = Sonora.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (song.durationSeconds > 0) {
            Spacer(Modifier.width(12.dp))
            Text(formatDuration(song.durationSeconds), fontSize = 12.sp, color = Sonora.TextSecondary)
        }
    }
}

private fun formatDuration(seconds: Long): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatTotal(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60} jam ${minutes % 60} mnt" else "$minutes mnt"
}
