package com.fahrii.musicapp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.LyricLine
import com.fahrii.musicapp.data.LyricsRepository
import kotlin.math.roundToInt

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

    // null = sedang memuat, kosong = tidak ditemukan
    var lyrics by remember(song.url) { mutableStateOf<List<LyricLine>?>(null) }
    LaunchedEffect(song.url) {
        lyrics = null
        lyrics = LyricsRepository.get(song.title, song.artist)
    }

    val durationMs = progress.durationMs
    val sliderValue = if (isDragging) {
        dragValue
    } else if (durationMs > 0) {
        (progress.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownPositionMs = if (isDragging) (dragValue * durationMs).toLong() else progress.positionMs
    val remainingMs = if (durationMs > 0) (durationMs - shownPositionMs).coerceAtLeast(0L) else 0L

    val active = Sonora.Accent
    val inactive = Sonora.TextSecondary

    Box(modifier = modifier.fillMaxSize()) {

        // Latar: cover diperbesar, diburamkan, memudar ke warna background
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .blur(60.dp)
                .alpha(0.65f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Sonora.Background)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bar atas: kembali | judul | suka
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                RoundButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = AppIcons.BackArrow,
                        contentDescription = "Kembali",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Sedang Diputar",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
                RoundButton(
                    onClick = { libraryViewModel.toggleLike(song) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = if (isLiked) AppIcons.HeartFilled else AppIcons.Heart,
                        contentDescription = "Suka",
                        tint = if (isLiked) HeartColor else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Cover bulat besar; ukurannya menyesuaikan sisa ruang layar
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val side = minOf(maxWidth * 0.9f, maxHeight)
                Box(modifier = Modifier.size(side)) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Sonora.SurfaceHigh)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    )
                    // Tambah ke playlist: tombol kecil di tepi cover
                    RoundButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = side * 0.06f, bottom = side * 0.06f)
                    ) {
                        Icon(
                            imageVector = AppIcons.PlaylistAdd,
                            contentDescription = "Tambah ke playlist",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Text(
                text = song.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = song.artist,
                fontSize = 15.sp,
                color = inactive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            state.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(text = it, fontSize = 13.sp, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(12.dp))

            LyricsView(lines = lyrics, positionMs = progress.positionMs)

            Spacer(Modifier.height(8.dp))

            ProgressBar(
                value = sliderValue,
                enabled = durationMs > 0,
                onChange = {
                    isDragging = true
                    dragValue = it
                },
                onFinish = {
                    viewModel.seekTo((dragValue * durationMs).toLong())
                    isDragging = false
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(shownPositionMs), fontSize = 12.sp, color = inactive)
                Text("-" + formatTime(remainingMs), fontSize = 12.sp, color = inactive)
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlainIconButton(
                    onClick = viewModel::toggleShuffle,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleEnabled) active else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                PlainIconButton(
                    onClick = viewModel::previous,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Sonora.SurfaceHigh)
                ) {
                    Icon(
                        imageVector = AppIcons.SkipPrevious,
                        contentDescription = "Sebelumnya",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                PlainIconButton(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(active)
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                            color = Color.Black
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                PlainIconButton(
                    onClick = viewModel::next,
                    enabled = state.hasNext,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Sonora.SurfaceHigh)
                        .alpha(if (state.hasNext) 1f else 0.4f)
                ) {
                    Icon(
                        imageVector = AppIcons.SkipNext,
                        contentDescription = "Berikutnya",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                PlainIconButton(
                    onClick = viewModel::cycleRepeat,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                            AppIcons.RepeatOne
                        } else {
                            AppIcons.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) active else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
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
}

/** Tiga baris lirik: sebelumnya (redup), sekarang (terang), berikutnya (redup). */
@Composable
private fun LyricsView(lines: List<LyricLine>?, positionMs: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(104.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            lines == null -> Text("Memuat lirik…", fontSize = 14.sp, color = Sonora.TextSecondary)
            lines.isEmpty() -> Text("Lirik tidak tersedia", fontSize = 14.sp, color = Sonora.TextSecondary)
            else -> {
                val index = lines.indexOfLast { it.timeMs <= positionMs }
                Crossfade(targetState = index, label = "lyrics") { i ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LyricText(lines.getOrNull(i - 1)?.text.orEmpty(), current = false)
                        LyricText(if (i >= 0) lines[i].text else "", current = true)
                        LyricText(lines.getOrNull(i + 1)?.text.orEmpty(), current = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricText(text: String, current: Boolean) {
    Text(
        text = text,
        fontSize = if (current) 18.sp else 15.sp,
        fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
        color = if (current) Color.White else Color.White.copy(alpha = 0.3f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}

/** Bar progres tipis dengan bulatan kecil, bisa diketuk dan digeser. */
@Composable
private fun ProgressBar(
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumb = 16.dp
    val thumbPx = with(density) { thumb.toPx() }
    var widthPx by remember { mutableIntStateOf(0) }
    val travelPx = (widthPx - thumbPx).coerceAtLeast(0f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onChange((offset.x / size.width).coerceIn(0f, 1f))
                    onFinish()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { onChange((it.x / size.width).coerceIn(0f, 1f)) },
                    onHorizontalDrag = { change, _ ->
                        onChange((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = onFinish,
                    onDragCancel = onFinish
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
        )
        Box(
            modifier = Modifier
                .width(with(density) { (thumbPx / 2 + value * travelPx).toDp() })
                .height(4.dp)
                .clip(CircleShape)
                .background(Sonora.Accent)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset((value * travelPx).roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(Sonora.Accent)
        )
    }
}

/** Tombol bulat transparan seperti di desain referensi. */
@Composable
private fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Tombol tanpa ukuran/ripple bawaan IconButton, supaya ukuran dan bentuknya persis yang kita atur. */
@Composable
private fun PlainIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
