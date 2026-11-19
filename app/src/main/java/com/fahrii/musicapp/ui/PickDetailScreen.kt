package com.fahrii.musicapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material3.IconButton
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
    val listState = rememberLazyListState()
    // Backdrop lokal: hanya daftar/hero yang jadi sumber blur, bar kaca ada di luarnya
    val backdrop = rememberGlassBackdrop()
    var titleBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val statusBarBottom = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    // Bar baru muncul setelah nama di hero sudah mepet/lewat status bar (atau hero sudah lewat)
    val titleCovered by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || titleBottom <= statusBarBottom }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (titleCovered) 1f else 0f,
        animationSpec = tween(180),
        label = "pickBar"
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)
        ) {
            item {
                PickHero(
                    pick = pick,
                    onPlayAll = { onPlay(songs, 0) },
                    onShuffle = { onPlay(songs.shuffled(), 0) },
                    onTitleBottom = { titleBottom = it }
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

        // Bar atas: kaca blur + panah kembali + nama playlist, muncul setelah nama di hero mepet status bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = barAlpha }
                    .glassFadeBar(backdrop)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 24.dp), // bawah lebih tinggi: blur sampai di bawah teks
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = titleCovered, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.BackArrow,
                        contentDescription = "Kembali",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).alpha(barAlpha)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = pick.label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(barAlpha)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }

        // Tombol kembali di hero: bulatan kaca yang ikut bergeser naik bersama isi saat di-scroll
        // (tidak menempel di layar). Setelah nama playlist tertutup, panahnya muncul lagi di bar atas.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .graphicsLayer {
                    val scrolled = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        Float.MAX_VALUE
                    }
                    translationY = -scrolled.coerceAtMost(100_000f)
                    alpha = if (listState.firstVisibleItemIndex == 0) 1f else 0f
                }
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.matchParentSize().glass(backdrop, CircleShape))
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
private fun PickHero(
    pick: QuickPick,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onTitleBottom: (Float) -> Unit
) {
    val songs = pick.songs
    val totalSeconds = songs.sumOf { it.durationSeconds.coerceAtLeast(0L) }
    val info = buildString {
        append("${songs.size} lagu")
        if (totalSeconds > 0) append(" · ${formatTotal(totalSeconds)}")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        CoverCollageHeader(urls = songs.take(4).map { it.thumbnailUrl }) {
            Text(
                text = pick.label,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.onGloballyPositioned { onTitleBottom(it.boundsInRoot().bottom) }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pick.subtitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(info, fontSize = 14.sp, color = Sonora.TextSecondary)
        }
        HeroActions(enabled = songs.isNotEmpty(), onPlay = onPlayAll, onShuffle = onShuffle)
    }
}

@Composable
internal fun TrackRow(
    number: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val thumbShape = RoundedCornerShape(10.dp)
    val songMenu = LocalSongMenu.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            Text("$number", fontSize = 14.sp, color = Sonora.TextSecondary)
        }
        if (isCurrent && isPlaying) {
            PlayingThumb(isPlaying = isPlaying, size = 52.dp)
        } else {
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
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
            Text(
                text = song.artist,
                fontSize = 13.sp,
                color = Sonora.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
        if (song.durationSeconds > 0) {
            Spacer(Modifier.width(12.dp))
            Text(formatDuration(song.durationSeconds), fontSize = 12.sp, color = Sonora.TextSecondary)
        }
        IconButton(onClick = { songMenu.show(song) }) {
            Icon(AppIcons.MoreVert, contentDescription = "Opsi")
        }
    }
}

private fun formatDuration(seconds: Long): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatTotal(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60} jam ${minutes % 60} mnt" else "$minutes mnt"
}