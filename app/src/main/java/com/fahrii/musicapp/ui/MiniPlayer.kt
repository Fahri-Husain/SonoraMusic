package com.fahrii.musicapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Isi bagian lagu di mini player (cover, judul, subjudul). Kuncinya URL lagu: ganti lagu = animasi. */
private data class MiniSongInfo(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val subtitle: String,
    val subtitleColor: Color
)

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    backdrop: GlassBackdrop,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSonoraPalette.current
    val song = state.currentSong ?: return

    val artist = artistLabel(song)
    val subtitle = when {
        state.error != null -> state.error
        state.isBuffering -> "Memuat…"
        else -> artist
    }
    val subtitleColor = if (state.error != null) {
        MaterialTheme.colorScheme.error
    } else {
        // Putih, sama dengan warna judul lagu
        MaterialTheme.colorScheme.onSurface
    }

    val info = MiniSongInfo(
        url = song.url,
        title = song.title,
        thumbnailUrl = song.thumbnailUrl,
        subtitle = subtitle ?: "",
        subtitleColor = subtitleColor
    )

    // Pil (kaca) tetap diam. Hanya isi lagunya yang bergeser dari kanan ke kiri dengan pantulan
    // saat lagu berganti; tombol putar/jeda tidak ikut bergerak.
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .glass(backdrop, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = info,
            modifier = Modifier.weight(1f),
            contentKey = { it.url },
            transitionSpec = {
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                    initialOffsetX = { fullWidth -> fullWidth / 2 }
                ) + fadeIn(tween(180, delayMillis = 40)))
                    .togetherWith(
                        slideOutHorizontally(tween(200)) { fullWidth -> -fullWidth / 3 } +
                                fadeOut(tween(140))
                    )
            },
            label = "miniPlayerSong"
        ) { shown ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = shown.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shown.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Text(
                        text = shown.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = shown.subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
            }
        }
        // Ukuran tetap (sama dengan tombol), supaya pil tidak berubah tinggi/lebar saat memuat
        Box(modifier = Modifier.padding(end = 12.dp).size(48.dp), contentAlignment = Alignment.Center) {
            if (state.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent
                )
            } else {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = palette.accent
                    )
                }
            }
        }
    }
}