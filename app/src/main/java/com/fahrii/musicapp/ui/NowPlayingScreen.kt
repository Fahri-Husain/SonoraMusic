package com.fahrii.musicapp.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.fahrii.musicapp.CrossfadeState
import com.fahrii.musicapp.data.LyricLine
import com.fahrii.musicapp.data.ArtistNames
import com.fahrii.musicapp.data.LyricsRepository
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.SettingsRepository
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.asin
import kotlin.math.abs
import kotlin.math.roundToInt

// Durasi transisi saat lagu berganti (ms)
private const val SONG_CHANGE_BG_MS = 700
private const val SONG_CHANGE_COVER_MS = 520
private const val SONG_CHANGE_TEXT_MS = 420

// Saat crossfade: cover, judul, dan artis berganti berurutan dengan jeda yang sama, latar berganti pelan (ala Apple Music)
private const val XFADE_COVER_DELAY_MS = 750

/** Seberapa jauh teks lama naik saat memudar di crossfade (1f = setinggi blok judul + artis). */
private const val XFADE_TEXT_RISE = 0.5f

// Judul dan artis berganti lebih dulu: yang lama naik sambil memudar, lalu yang baru langsung muncul
// dari bawah. Cover menyusul sesudahnya (waktunya diatur XFADE_COVER_DELAY_MS di atas).
private const val XFADE_TEXT_EXIT_MS = 450
private const val XFADE_TEXT_EXIT_DELAY_MS = 150
private const val XFADE_TEXT_ENTER_MS = 500

/** Ukuran cover saat paling kecil di tengah transisi (1f = ukuran penuh). */
private const val XFADE_COVER_SCALE = 0.85f

/** Easing lembut (masuk dan keluar sama pelan), supaya gerakan tidak terasa "ngebut di awal". */
private val XFadeEasing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** Lama cover berganti saat crossfade: mengikuti lama fade audio (60%), dibatasi 1,5-3 detik. */
private fun crossfadeCoverMs(fadeMs: Long): Int =
    (fadeMs * 0.6f).toInt().coerceIn(1500, 3000)

/** Lama latar berganti saat crossfade: mengikuti lama fade audio (80%), dibatasi 1,8-3,5 detik. */
private fun crossfadeBgMs(fadeMs: Long): Int =
    (fadeMs * 0.8f).toInt().coerceIn(1800, 3500)

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    /** Dipanggil saat titik tiga diketuk, untuk membuka menu lagu (digambar di AppRoot). */
    onOpenMenu: (Song) -> Unit,
    /** Dipanggil saat nama artis diketuk, untuk membuka halaman artis (navigasi ada di AppRoot). */
    onOpenArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    /** Dipanggil setelah layar selesai meluncur keluar karena digeser turun (default: sama dengan [onBack]). */
    onSwipeDismiss: () -> Unit = onBack
) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isCrossfading by CrossfadeState.isCrossfading.collectAsState()
    // Label muncul sejak persiapan (lagu lama masih tampil), bukan cuma setelah lagu baru tampil
    val isCrossfadePreparing by CrossfadeState.isPreparing.collectAsState()
    val showCrossfadeLabel = isCrossfading || isCrossfadePreparing
    val crossfadeFadeMs by CrossfadeState.fadeDurationMs.collectAsState()
    val library by libraryViewModel.state.collectAsState()
    val song = state.currentSong

    if (song == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada lagu yang diputar")
        }
        return
    }

    // Muat thumbnail lagu berikutnya lebih awal supaya saat transisi mulai gambarnya sudah di cache
    // (tidak muncul tiba-tiba di tengah animasi).
    val prefetchContext = LocalContext.current
    LaunchedEffect(state.nextSong?.thumbnailUrl) {
        state.nextSong?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            prefetchContext.imageLoader.enqueue(ImageRequest.Builder(prefetchContext).data(url).build())
        }
    }

    val isLiked = library.liked.any { it.url == song.url }
    var showAddDialog by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    // Durasi dikunci saat jari mulai menggeser. Durasi dari pemutar bisa berubah di tengah
    // gesekan (beberapa stream awalnya hanya perkiraan), dan kalau dipakai langsung, posisi
    // seek saat dilepas tidak lagi cocok dengan tempat thumb berada.
    var dragDurationMs by remember { mutableLongStateOf(0L) }

    // null = sedang memuat, kosong = tidak ditemukan
    var lyrics by remember(song.url) { mutableStateOf<List<LyricLine>?>(null) }
    // Lagu dari Beranda (Deezer) hanya membawa satu artis utama. Metadata asli dari YouTube Music
    // (lagu yang benar-benar diputar) berisi semua artisnya, mis. "Olivia Rodrigo & Chappell Roan".
    val displayArtist = artistLabel(song)
    // Ketuk nama artis: satu artis langsung dibuka, kolaborasi memberi pilihan artis dulu
    var showArtistPicker by remember { mutableStateOf(false) }
    val artistNames = ArtistNames.names(song)
    LaunchedEffect(song.url) {
        lyrics = null
        val meta = runCatching { MusicRepository.resolveSongMeta(song.url) }.getOrNull()
        val fullArtist = ArtistNames.resolve(song)
        // Tunggu durasi dari pemutar (maks. 4 dtk) supaya versi lirik yang waktunya cocok bisa dipilih
        val durationSec = withTimeoutOrNull(4_000) {
            snapshotFlow { progress.durationMs }.first { it > 0 }
        }?.div(1000) ?: -1L
        lyrics = LyricsRepository.get(meta?.song?.title ?: song.title, fullArtist, durationSec)
    }

    LaunchedEffect(lyrics) { if (lyrics?.isEmpty() == true) showLyrics = false }
    BackHandler(enabled = showLyrics) { showLyrics = false }

    if (showArtistPicker) {
        AlertDialog(
            onDismissRequest = { showArtistPicker = false },
            title = { Text("Buka halaman artis") },
            text = {
                Column {
                    artistNames.forEach { name ->
                        TextButton(onClick = {
                            showArtistPicker = false
                            onOpenArtist(name)
                        }) { Text(name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showArtistPicker = false }) { Text("Batal") } }
        )
    }

    val durationMs = progress.durationMs
    val sliderValue = if (isDragging) {
        dragValue
    } else if (durationMs > 0) {
        (progress.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownDurationMs = if (isDragging) dragDurationMs else durationMs
    val shownPositionMs = if (isDragging) (dragValue * dragDurationMs).toLong() else progress.positionMs
    val remainingMs = if (shownDurationMs > 0) (shownDurationMs - shownPositionMs).coerceAtLeast(0L) else 0L

    val inactive = Sonora.TextSecondary

    // Geser ke bawah = kembali (seperti tombol kembali). Layar ikut bergerak mengikuti jari:
    // dilepas setelah cukup jauh / cukup cepat -> meluncur ke bawah lalu kembali; kalau belum ->
    // memantul ke posisi semula. Nonaktif saat lirik penuh terbuka (daftar liriknya punya gulir sendiri).
    val currentOnSwipeDismiss by rememberUpdatedState(onSwipeDismiss)
    var dragY by remember { mutableFloatStateOf(0f) }
    var screenHeightPx by remember { mutableFloatStateOf(0f) }
    // Seberapa jauh layar ditarik ke bawah (0..1, penuh di setengah tinggi layar). Dibaca di fase
    // layout/draw saja (lewat lambda), jadi tidak memicu recompose tiap frame saat menggeser.
    val dragProgress: () -> Float = {
        if (screenHeightPx <= 0f) 0f else (dragY / (screenHeightPx * 0.5f)).coerceIn(0f, 1f)
    }

    // Geser ke bawah di halaman lirik penuh = tutup lirik (seperti tombol kembali di sana), tapi
    // hanya setelah daftar lirik mentok di atas: selama masih bisa digulir, jari menggulir daftar.
    var lyricsDragY by remember { mutableFloatStateOf(0f) }
    var lyricsClosedBySwipe by remember { mutableStateOf(false) }
    val lyricsDismissConnection = remember {
        object : NestedScrollConnection {
            // Halaman lirik sedang turun lalu jari bergerak naik: naikkan halamannya dulu sebelum daftar menggulir
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && lyricsDragY > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -lyricsDragY)
                    lyricsDragY += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            // Daftar sudah mentok di atas, jari masih menarik ke bawah: halaman lirik ikut turun
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    lyricsDragY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            // Jari dilepas: cukup jauh / cukup cepat -> meluncur keluar dan tutup, kalau tidak -> kembali
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (lyricsDragY <= 0f) return Velocity.Zero
                val shouldClose = lyricsDragY > screenHeightPx * 0.25f || available.y > 1500f
                if (shouldClose) {
                    animate(
                        initialValue = lyricsDragY,
                        targetValue = screenHeightPx,
                        animationSpec = tween(220)
                    ) { value, _ -> lyricsDragY = value }
                    lyricsClosedBySwipe = true
                    showLyrics = false
                } else {
                    animate(
                        initialValue = lyricsDragY,
                        targetValue = 0f,
                        animationSpec = tween(250)
                    ) { value, _ -> lyricsDragY = value }
                }
                return available
            }
        }
    }

    Box(
        // graphicsLayer paling depan supaya latar (background dari pemanggil) ikut bergeser
        modifier = Modifier
            .graphicsLayer { translationY = dragY }
            .then(modifier)
            .fillMaxSize()
            .onSizeChanged { screenHeightPx = it.height.toFloat() }
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !showLyrics,
                state = rememberDraggableState { delta ->
                    dragY = (dragY + delta).coerceAtLeast(0f)
                },
                onDragStopped = { velocity ->
                    val shouldClose = dragY > screenHeightPx * 0.25f || velocity > 1500f
                    if (shouldClose) {
                        // Sama seperti tombol back: tutup lewat transisi cover ke mini player. Layar
                        // tetap di posisi geserannya (tidak balik naik dulu) lalu lanjut keluar.
                        currentOnSwipeDismiss()
                    } else {
                        animate(
                            initialValue = dragY,
                            targetValue = 0f,
                            animationSpec = tween(250)
                        ) { value, _ -> dragY = value }
                    }
                }
            )
    ) {

        // Latar: cover diperbesar, diburamkan, dibuat lebih terang dan pekat warnanya,
        // lalu memudar ke warna background di sekitar judul lagu.
        val glowFilter = remember {
            ColorFilter.colorMatrix(
                ColorMatrix().apply {
                    setToSaturation(1.6f)
                    timesAssign(
                        ColorMatrix(
                            floatArrayOf(
                                1.4f, 0f, 0f, 0f, 0f,
                                0f, 1.4f, 0f, 0f, 0f,
                                0f, 0f, 1.4f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
            )
        }
        // Saat lagu berganti, latar lama memudar pelan sambil latar baru muncul (crossfade),
        // jadi warna di belakang berpindah halus seperti di Apple Music.
        AnimatedContent(
            targetState = song,
            contentKey = { it.url },
            transitionSpec = {
                if (isCrossfading) {
                    // Latar baru muncul pelan di atas yang lama (setelah judul), yang lama baru hilang
                    // sesudahnya. Jadi tidak ada momen latar menipis di tengah transisi.
                    val ms = crossfadeBgMs(crossfadeFadeMs)
                    fadeIn(tween(ms, delayMillis = XFADE_COVER_DELAY_MS, easing = XFadeEasing)) togetherWith
                            fadeOut(tween(1, delayMillis = XFADE_COVER_DELAY_MS + ms)) using
                            SizeTransform(clip = false)
                } else {
                    fadeIn(tween(SONG_CHANGE_BG_MS, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween(SONG_CHANGE_BG_MS, easing = FastOutSlowInEasing)) using
                            SizeTransform(clip = false)
                }
            },
            label = "nowPlayingBackground",
            // Blur dipasang sekali di grup (bukan di tiap gambar) supaya saat dua latar tampil
            // bersamaan hanya ada satu proses blur per frame.
            modifier = Modifier.fillMaxWidth().height(640.dp).blur(60.dp)
        ) { s ->
            AsyncImage(
                model = s.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = glowFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(640.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Sonora.Background.copy(alpha = 0.35f),
                        0.85f to Sonora.Background,
                        1f to Sonora.Background
                    )
                )
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
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SEDANG DIPUTAR",
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                    state.sourceLabel?.let { source ->
                        Text(
                            text = source,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            textAlign = TextAlign.Center
                        )
                    }
                }
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
                val sidePx = with(LocalDensity.current) { side.toPx() }
                // Tombol di tepi cover muncul di akhir penerbangan cover, tergambar di atas cover (lihat CoverSharedTransition)
                val edgeMoreProgress = rememberCoverEdgeProgress(0)
                val edgePlaylistProgress = rememberCoverEdgeProgress(1)
                val edgeQueueProgress = rememberCoverEdgeProgress(2)
                // Geser cover: kanan ke kiri = lagu berikutnya, kiri ke kanan = lagu sebelumnya. Cover
                // lagu tujuan ikut terlihat masuk dari sisi layar mengikuti jari (seperti carousel). Dilepas cukup jauh /
                // cukup cepat -> meluncur penuh ke lagu tujuan; kalau belum -> kembali ke tengah.
                val nextSong = state.nextSong
                val previousSong = state.previousSong
                val hasNextSong by rememberUpdatedState(nextSong != null)
                val hasPreviousSong by rememberUpdatedState(previousSong != null)
                val currentUrl by rememberUpdatedState(song.url)
                val spacingPx = sidePx + with(LocalDensity.current) { 24.dp.toPx() }
                var coverDragX by remember { mutableFloatStateOf(0f) }
                var committedFromUrl by remember { mutableStateOf<String?>(null) }

                // Ketuk dua kali cover = suka (selalu aktif). Nilai terbaru dibaca lewat
                // rememberUpdatedState karena pointerInput(Unit) tidak dibuat ulang saat lagu berubah.
                val currentSong by rememberUpdatedState(song)
                val currentIsLiked by rememberUpdatedState(isLiked)
                val haptic = LocalHapticFeedback.current
                var heartBurst by remember { mutableStateOf(HeartBurst()) }
                var swipeCommitting by remember { mutableStateOf(false) }

                // Setelah lagu berganti lewat geseran, cover baru sudah tepat di tengah: reset posisi geser
                LaunchedEffect(song.url) {
                    coverDragX = 0f
                    committedFromUrl = null
                }
                // Selama menunggu lagu berganti, posisi tetap di sisi tujuan; begitu lagu berganti
                // posisi langsung 0 (di frame yang sama) supaya tidak ada kedipan.
                fun shiftX(): Float =
                    if (committedFromUrl != null && song.url != committedFromUrl) 0f else coverDragX

                Box(
                    modifier = Modifier
                        .size(side)
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                // Belum disukai -> jadi favorit (hati utuh); sudah disukai -> batal
                                // favorit (hati retak terbelah dua)
                                val willLike = !currentIsLiked
                                libraryViewModel.toggleLike(currentSong)
                                heartBurst = HeartBurst(id = heartBurst.id + 1, liked = willLike)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            })
                        }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                if (swipeCommitting) return@rememberDraggableState
                                val target = coverDragX + delta
                                // Tidak ada lagu di arah itu: geseran terasa berat dan tidak menampilkan apa-apa
                                val hasTarget = if (target < 0f) hasNextSong else hasPreviousSong
                                val factor = if (hasTarget) 1f else 0.25f
                                coverDragX = (coverDragX + delta * factor).coerceIn(-spacingPx, spacingPx)
                            },
                            onDragStopped = { velocity ->
                                if (swipeCommitting) return@draggable
                                val dx = coverDragX
                                val goingNext = dx < 0f
                                val hasTarget = if (goingNext) hasNextSong else hasPreviousSong
                                val farEnough = abs(dx) > spacingPx * 0.3f
                                val fastEnough = abs(velocity) > 1200f && velocity * dx > 0f && abs(dx) > spacingPx * 0.08f
                                if (hasTarget && (farEnough || fastEnough)) {
                                    swipeCommitting = true
                                    val startUrl = currentUrl
                                    committedFromUrl = startUrl
                                    animate(
                                        initialValue = coverDragX,
                                        targetValue = if (goingNext) -spacingPx else spacingPx,
                                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                                    ) { value, _ -> coverDragX = value }
                                    if (goingNext) viewModel.next() else viewModel.skipToPrevious()
                                    val changed = withTimeoutOrNull(1_500) {
                                        snapshotFlow { currentUrl }.first { it != startUrl }
                                    }
                                    if (changed == null) {
                                        // Lagu tidak berganti (mis. gagal): kembalikan cover ke tengah
                                        committedFromUrl = null
                                        animate(
                                            initialValue = coverDragX,
                                            targetValue = 0f,
                                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                                        ) { value, _ -> coverDragX = value }
                                    }
                                    swipeCommitting = false
                                } else {
                                    animate(
                                        initialValue = coverDragX,
                                        targetValue = 0f,
                                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                                    ) { value, _ -> coverDragX = value }
                                }
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = shiftX() }
                    ) {
                        // Cover lagu tujuan: berikutnya masuk dari kanan (geser ke kiri), sebelumnya dari kiri
                        SwipeCoverPeek(
                            thumbnailUrl = nextSong?.thumbnailUrl,
                            visible = nextSong != null,
                            offsetPx = spacingPx,
                            shown = { shiftX() < 0f }
                        )
                        SwipeCoverPeek(
                            thumbnailUrl = previousSong?.thumbnailUrl,
                            visible = previousSong != null,
                            offsetPx = -spacingPx,
                            shown = { shiftX() > 0f }
                        )

                        // Cover lama mengecil sedikit sambil memudar, cover baru membesar dari ukuran
                        // sedikit lebih kecil sambil muncul, bukan langsung berganti. Kalau pergantian
                        // berasal dari geseran, tidak ada animasi tambahan (geserannya sudah animasi).
                        AnimatedContent(
                            targetState = song,
                            contentKey = { it.url },
                            transitionSpec = {
                                if (committedFromUrl != null) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else if (isCrossfading) {
                                    // Berurutan, bukan bertumpuk: cover lama mengecil sambil memudar sampai
                                    // hilang, BARU cover baru muncul sambil membesar. Kalau keduanya tampil
                                    // bersamaan, yang baru (lebih kecil) terlihat di dalam lingkaran yang
                                    // lama dan tampak seperti bingkai.
                                    val half = crossfadeCoverMs(crossfadeFadeMs) / 2
                                    (fadeIn(tween(half, delayMillis = XFADE_COVER_DELAY_MS + half, easing = XFadeEasing)) +
                                            scaleIn(
                                                initialScale = XFADE_COVER_SCALE,
                                                animationSpec = tween(half, delayMillis = XFADE_COVER_DELAY_MS + half, easing = XFadeEasing)
                                            )) togetherWith
                                            (fadeOut(tween(half, delayMillis = XFADE_COVER_DELAY_MS, easing = XFadeEasing)) +
                                                    scaleOut(
                                                        targetScale = XFADE_COVER_SCALE,
                                                        animationSpec = tween(half, delayMillis = XFADE_COVER_DELAY_MS, easing = XFadeEasing)
                                                    )) using
                                            SizeTransform(clip = false)
                                } else {
                                    (fadeIn(tween(SONG_CHANGE_COVER_MS, delayMillis = 80, easing = FastOutSlowInEasing)) +
                                            scaleIn(
                                                initialScale = 0.9f,
                                                animationSpec = tween(SONG_CHANGE_COVER_MS, easing = FastOutSlowInEasing)
                                            )) togetherWith
                                            (fadeOut(tween(SONG_CHANGE_COVER_MS, easing = FastOutSlowInEasing)) +
                                                    scaleOut(
                                                        targetScale = 0.94f,
                                                        animationSpec = tween(SONG_CHANGE_COVER_MS, easing = FastOutSlowInEasing)
                                                    )) using
                                            SizeTransform(clip = false)
                                }
                            },
                            label = "nowPlayingCover",
                            modifier = Modifier.fillMaxSize()
                        ) { s ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = s.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // Cover mengecil (sampai 65%) mengikuti geseran ke bawah
                                        .coverDragShrink { 1f - 0.35f * dragProgress() }
                                        .coverSharedElement(s.url)
                                        .clip(CoverMorphShape)
                                        .background(Sonora.SurfaceHigh)
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), CoverMorphShape)
                                )
                                // Tombol kecil tepat di garis tepi cover (pusat tombol di keliling lingkaran),
                                // ikut beranimasi bersama cover. Tambah ke playlist di sudut 45° kanan bawah,
                                // titik tiga (menu lagu) di atasnya dan antrean di bawahnya, masing-masing
                                // berjarak satu tombol + celah sepanjang busur.
                                val radius = side.value / 2f
                                val halfButton = 22f // setengah ukuran RoundButton (44 dp)
                                val playlistAngle = (Math.PI / 4).toFloat()
                                val gapAngle = 2f * asin(((44f + 8f) / 2f / radius).coerceAtMost(1f))
                                val moreAngle = playlistAngle - gapAngle
                                val queueAngle = playlistAngle + gapAngle

                                RoundButton(
                                    onClick = { onOpenMenu(s) },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(
                                            x = (radius + radius * cos(moreAngle) - halfButton).dp,
                                            y = (radius + radius * sin(moreAngle) - halfButton).dp
                                        )
                                        .coverEdgeOverlay()
                                        .coverEdgePop(edgeMoreProgress) { 1f - (dragProgress() / 0.24f).coerceIn(0f, 1f) }
                                ) {
                                    Icon(
                                        imageVector = AppIcons.MoreVert,
                                        contentDescription = "Opsi",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                RoundButton(
                                    onClick = { showAddDialog = true },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(
                                            x = (radius + radius * cos(playlistAngle) - halfButton).dp,
                                            y = (radius + radius * sin(playlistAngle) - halfButton).dp
                                        )
                                        .coverEdgeOverlay()
                                        .coverEdgePop(edgePlaylistProgress) { 1f - (dragProgress() / 0.24f).coerceIn(0f, 1f) }
                                ) {
                                    Icon(
                                        imageVector = AppIcons.PlaylistAdd,
                                        contentDescription = "Tambah ke playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // Antrean: tombol ketiga, melanjutkan lengkungan ke bawah
                                RoundButton(
                                    onClick = { showQueue = true },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(
                                            x = (radius + radius * cos(queueAngle) - halfButton).dp,
                                            y = (radius + radius * sin(queueAngle) - halfButton).dp
                                        )
                                        .coverEdgeOverlay()
                                        .coverEdgePop(edgeQueueProgress) { 1f - (dragProgress() / 0.24f).coerceIn(0f, 1f) }
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Queue,
                                        contentDescription = "Antrean",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                // Hati yang muncul di tengah cover setelah diketuk dua kali
                DoubleTapHeartBurst(burst = heartBurst, modifier = Modifier.size(side))
            }

            // Judul dan artis: yang lama memudar sambil naik sedikit, yang baru muncul dari bawah
            AnimatedContent(
                targetState = song,
                contentKey = { it.url },
                transitionSpec = {
                    if (isCrossfading) {
                        // Saat crossfade: judul dan artis lama naik sambil memudar (tanpa mengecil), lalu
                        // yang baru muncul dari bawah begitu yang lama selesai. Cover menyusul sesudahnya.
                        val enterDelay = XFADE_TEXT_EXIT_DELAY_MS + XFADE_TEXT_EXIT_MS
                        (fadeIn(tween(XFADE_TEXT_ENTER_MS, delayMillis = enterDelay, easing = FastOutSlowInEasing)) +
                                slideInVertically(
                                    tween(XFADE_TEXT_ENTER_MS, delayMillis = enterDelay, easing = FastOutSlowInEasing)
                                ) { it / 3 }) togetherWith
                                (fadeOut(
                                    tween(XFADE_TEXT_EXIT_MS, delayMillis = XFADE_TEXT_EXIT_DELAY_MS, easing = FastOutSlowInEasing)
                                ) +
                                        slideOutVertically(
                                            tween(XFADE_TEXT_EXIT_MS, delayMillis = XFADE_TEXT_EXIT_DELAY_MS, easing = FastOutSlowInEasing)
                                        ) { -(it * XFADE_TEXT_RISE).toInt() }) using
                                SizeTransform(clip = false)
                    } else {
                        // Ganti lagu biasa: jeda 120 ms
                        (fadeIn(tween(SONG_CHANGE_TEXT_MS, delayMillis = 120, easing = FastOutSlowInEasing)) +
                                slideInVertically(
                                    tween(SONG_CHANGE_TEXT_MS, delayMillis = 120, easing = FastOutSlowInEasing)
                                ) { it / 3 }) togetherWith
                                (fadeOut(tween(SONG_CHANGE_TEXT_MS / 2, easing = FastOutSlowInEasing)) +
                                        slideOutVertically(
                                            tween(SONG_CHANGE_TEXT_MS, easing = FastOutSlowInEasing)
                                        ) { -it / 3 }) using
                                SizeTransform(clip = false)
                    }
                },
                label = "nowPlayingTitle",
                modifier = Modifier.fillMaxWidth()
            ) { s ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = s.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (s.url == song.url) displayArtist else s.artist,
                        fontSize = 15.sp,
                        color = inactive,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .clickable {
                                if (artistNames.size == 1) onOpenArtist(artistNames[0]) else showArtistPicker = true
                            }
                            .basicMarquee(iterations = Int.MAX_VALUE),
                        textAlign = TextAlign.Center
                    )
                }
            }
            state.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(text = it, fontSize = 13.sp, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(8.dp))

            LyricsView(
                lines = lyrics,
                positionMs = progress.positionMs,
                onClick = {
                    lyricsClosedBySwipe = false
                    lyricsDragY = 0f
                    showLyrics = true
                }
            )

            Spacer(Modifier.height(8.dp))

            ProgressBar(
                value = sliderValue,
                crossfading = isCrossfading,
                enabled = durationMs > 0,
                onChange = {
                    if (!isDragging) dragDurationMs = durationMs
                    isDragging = true
                    dragValue = it
                },
                onFinish = {
                    if (isDragging) viewModel.seekTo((dragValue * dragDurationMs).toLong())
                    isDragging = false
                }
            )
            PlaybackTimeRow(
                elapsed = formatTime(shownPositionMs),
                remaining = "-" + formatTime(remainingMs),
                crossfading = showCrossfadeLabel,
                color = inactive
            )

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
                        tint = Color.White.copy(alpha = if (state.shuffleEnabled) 1f else 0.55f),
                        modifier = Modifier.size(26.dp)
                    )
                }
                PlainIconButton(
                    onClick = viewModel::previous,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.SkipPrevious,
                        contentDescription = "Sebelumnya",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                PlainIconButton(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
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
                        .size(56.dp)
                        .alpha(if (state.hasNext) 1f else 0.4f)
                ) {
                    Icon(
                        imageVector = AppIcons.SkipNext,
                        contentDescription = "Berikutnya",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
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
                        tint = Color.White.copy(alpha = if (state.repeatMode != Player.REPEAT_MODE_OFF) 1f else 0.55f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        // Lirik penuh (seperti Spotify): muncul saat pratinjau lirik diketuk
        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(320)),
            // Ditutup lewat geser: halamannya sudah meluncur keluar sendiri, jadi tanpa animasi keluar lagi
            exit = if (lyricsClosedBySwipe) {
                ExitTransition.None
            } else {
                slideOutVertically(tween(280)) { it } + fadeOut(tween(280))
            }
        ) {
            FullLyricsScreen(
                title = song.title,
                artist = displayArtist,
                coverUrl = song.thumbnailUrl,
                lines = lyrics.orEmpty(),
                positionMs = progress.positionMs,
                onSeek = { viewModel.seekTo(it) },
                onClose = { showLyrics = false },
                // graphicsLayer paling depan supaya latar halaman lirik ikut bergeser
                modifier = Modifier
                    .graphicsLayer { translationY = lyricsDragY }
                    .nestedScroll(lyricsDismissConnection)
            ) {
                ProgressBar(
                    value = sliderValue,
                    crossfading = isCrossfading,
                    enabled = durationMs > 0,
                    onChange = {
                        if (!isDragging) dragDurationMs = durationMs
                        isDragging = true
                        dragValue = it
                    },
                    onFinish = {
                        if (isDragging) viewModel.seekTo((dragValue * dragDurationMs).toLong())
                        isDragging = false
                    }
                )
                PlaybackTimeRow(
                    elapsed = formatTime(shownPositionMs),
                    remaining = "-" + formatTime(remainingMs),
                    crossfading = showCrossfadeLabel,
                    color = inactive
                )
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PlainIconButton(
                        onClick = viewModel::togglePlayPause,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        if (state.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = Color.Black
                            )
                        } else {
                            Icon(
                                imageVector = if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddToPlaylistSheet(
            song = song,
            playlists = library.playlists,
            onAdd = { libraryViewModel.addToPlaylist(it.id, song) },
            onRemove = { libraryViewModel.removeFromPlaylist(it.id, song.url) },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.currentIndex,
            isPlaying = state.isPlaying,
            onDismiss = { showQueue = false },
            onPlayItem = { viewModel.skipToQueueItem(it) },
            onRemoveItem = { viewModel.removeFromQueue(it) },
            onMoveItem = { from, to -> viewModel.moveInQueue(from, to) }
        )
    }
}

/**
 * Tiga baris lirik: sebelumnya (redup), sekarang (terang), berikutnya (redup).
 * Saat baris berganti, ketiganya bergeser halus ke atas sambil fade.
 */
@Composable
private fun LyricsView(
    lines: List<LyricLine>?,
    positionMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .clickable(
                enabled = !lines.isNullOrEmpty(),
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        when {
            lines == null -> Text("Memuat lirik…", fontSize = 14.sp, color = Sonora.TextSecondary)
            lines.isEmpty() -> Text("Lirik tidak tersedia", fontSize = 14.sp, color = Sonora.TextSecondary)
            else -> {
                val index = lines.indexOfLast { it.timeMs <= positionMs }
                AnimatedContent(
                    targetState = index,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        val up = targetState > initialState
                        val dir = if (up) 1 else -1
                        (slideInVertically(tween(400)) { dir * it / 3 } + fadeIn(tween(400))) togetherWith
                                (slideOutVertically(tween(400)) { -dir * it / 3 } + fadeOut(tween(250)))
                    },
                    label = "lyrics"
                ) { i ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // Ujung atas (lirik sebelumnya) dan bawah (lirik berikutnya) memudar.
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.3f to Color.Black,
                                        0.7f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
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

/**
 * Halaman lirik penuh: semua baris bisa digulir, baris yang sedang dinyanyikan
 * disorot dan otomatis diikuti. Ketuk satu baris untuk lompat ke bagian itu.
 *
 * Tombol bagikan (kanan atas) membuka mode pilih: ketuk beberapa baris (maks. [LyricsCard.MAX_LINES]),
 * lalu "Bagikan" untuk melihat kartu lirik dan mengirimnya sebagai gambar, seperti Spotify.
 */
@Composable
private fun FullLyricsScreen(
    title: String,
    artist: String,
    coverUrl: String?,
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val current = lines.indexOfLast { it.timeMs <= positionMs }
    val lyricsFont by SettingsRepository.getInstance(context).lyricsFontSize.collectAsState()
    val lyricsBlurOn by SettingsRepository.getInstance(context).lyricsBlur.collectAsState()

    var selecting by remember { mutableStateOf(false) }
    // Pilihan selalu satu rentang baris berurutan (selStart..selEnd), -1 = belum ada
    var selStart by remember { mutableIntStateOf(-1) }
    var selEnd by remember { mutableIntStateOf(-1) }
    val hasSelection = selStart >= 0
    fun clearSelection() {
        selStart = -1
        selEnd = -1
    }
    fun selectedCount() =
        if (!hasSelection) 0 else (selStart..selEnd).count { lines.getOrNull(it)?.text?.isNotBlank() == true }
    fun countBetween(a: Int, b: Int) =
        (a..b).count { lines.getOrNull(it)?.text?.isNotBlank() == true }
    var showCard by remember { mutableStateOf(false) }
    var cardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sharing by remember { mutableStateOf(false) }

    // Langkah 2 setelah kartu muncul: pilih warna dari warna yang ada di thumbnail
    var colorStep by remember { mutableStateOf(false) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var palette by remember { mutableStateOf<List<Int>>(emptyList()) }
    var cardColor by remember { mutableIntStateOf(0) } // 0 = belum dipilih

    // Ganti lagu / lirik -> reset pilihan
    LaunchedEffect(lines) {
        selecting = false
        showCard = false
        colorStep = false
        clearSelection()
        coverBitmap = null
        palette = emptyList()
        cardColor = 0
    }

    // Back: tutup kartu dulu, lalu mode pilih, baru halaman lirik (di luar)
    BackHandler(enabled = showCard && colorStep) { colorStep = false }
    BackHandler(enabled = showCard && !colorStep) { showCard = false }
    BackHandler(enabled = selecting && !showCard) {
        selecting = false
        clearSelection()
    }

    // Baris aktif diikuti otomatis, kecuali pengguna sedang menggulir / memilih baris
    LaunchedEffect(current, selecting) {
        if (!selecting && current >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(current, -(listState.layoutInfo.viewportSize.height / 4))
        }
    }

    // Saat kartu dibuka: muat cover, ambil warna-warnanya, pakai warna paling dominan sebagai awal
    LaunchedEffect(showCard) {
        if (showCard) {
            colorStep = false
            cardBitmap = null
            val cover = coverBitmap ?: LyricsCard.loadCover(context, coverUrl).also { coverBitmap = it }
            if (palette.isEmpty()) palette = LyricsCard.palette(cover)
            if (cardColor == 0) cardColor = palette.first()
        }
    }

    // Gambar ulang kartu tiap kali dibuka atau warnanya diganti (kartu lama tetap tampil selama menggambar)
    LaunchedEffect(showCard, cardColor) {
        if (showCard && cardColor != 0) {
            cardBitmap = LyricsCard.render(
                context = context,
                cover = coverBitmap,
                title = title,
                artist = artist,
                lyrics = (selStart..selEnd).mapNotNull { lines.getOrNull(it)?.text }.filter { it.isNotBlank() },
                color = cardColor
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Sonora.Background)
            .pointerInput(Unit) {} // cegah ketukan tembus ke layar di belakang
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .blur(60.dp)
                .alpha(0.55f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Sonora.Background
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                RoundButton(
                    onClick = {
                        if (selecting) {
                            selecting = false
                            clearSelection()
                        } else {
                            onClose()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = if (selecting) Icons.Filled.Close else AppIcons.BackArrow,
                        contentDescription = if (selecting) "Batal" else "Tutup lirik",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selecting) {
                        Text(
                            text = "Pilih lirik",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = "${selectedCount()}/${LyricsCard.MAX_LINES} baris",
                            fontSize = 13.sp,
                            color = Sonora.TextSecondary,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Text(
                            text = artist,
                            fontSize = 13.sp,
                            color = Sonora.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
                if (!selecting) {
                    RoundButton(
                        onClick = { selecting = true },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Bagikan lirik",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 32.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Ujung atas dan bawah daftar memudar
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.06f to Color.Black,
                                0.94f to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                itemsIndexed(lines) { index, line ->
                    val highlighted = if (selecting) hasSelection && index in selStart..selEnd else index == current
                    val color by animateColorAsState(
                        targetValue = if (highlighted) Color.White else Color.White.copy(alpha = 0.4f),
                        animationSpec = tween(300),
                        label = "lyricColor"
                    )
                    // Baris yang sedang dinyanyikan membesar sedikit (bukan saat memilih baris untuk kartu)
                    val lineScale by animateFloatAsState(
                        targetValue = if (highlighted && !selecting) 1.05f else 1f,
                        animationSpec = tween(300),
                        label = "lyricScale"
                    )
                    // Gaya Apple Music: makin jauh dari baris aktif makin buram (maksimal 6 dp).
                    // Mati saat memilih baris untuk kartu supaya teks tetap jelas. Efek blur hanya
                    // terlihat di Android 12 ke atas; di versi lebih lama diabaikan sistem.
                    val blurRadius by animateDpAsState(
                        targetValue = if (lyricsBlurOn && !selecting && current >= 0) {
                            (minOf(abs(index - current), 3) * 2).dp
                        } else {
                            0.dp
                        },
                        animationSpec = tween(300),
                        label = "lyricBlur"
                    )
                    Text(
                        text = line.text.ifBlank { "♪" },
                        fontSize = lyricsFont.textSp.sp,
                        lineHeight = lyricsFont.lineHeightSp.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = lineScale
                                scaleY = lineScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!selecting) {
                                    onSeek(line.timeMs)
                                } else if (line.text.isNotBlank()) {
                                    when {
                                        // Belum ada pilihan: mulai dari baris ini
                                        selStart < 0 -> {
                                            selStart = index
                                            selEnd = index
                                        }
                                        // Ketuk baris di dalam pilihan: pilihan menyusut
                                        index in selStart..selEnd -> when {
                                            selStart == selEnd -> clearSelection()
                                            index == selStart -> selStart = index + 1
                                            else -> selEnd = index - 1
                                        }
                                        // Ketuk baris di luar pilihan: kalau masih muat, rentang diperluas
                                        // sampai baris ini (baris di antaranya ikut terpilih). Kalau terlalu
                                        // jauh dan melebihi batas, pilihan dimulai ulang dari baris ini.
                                        else -> {
                                            val newStart = minOf(selStart, index)
                                            val newEnd = maxOf(selEnd, index)
                                            if (countBetween(newStart, newEnd) <= LyricsCard.MAX_LINES) {
                                                selStart = newStart
                                                selEnd = newEnd
                                            } else {
                                                selStart = index
                                                selEnd = index
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }

            if (selecting) {
                SharePillButton(
                    text = "Bagikan",
                    enabled = selectedCount() > 0,
                    onClick = { showCard = true }
                )
            } else {
                controls()
            }
            Spacer(Modifier.height(24.dp))
        }

        // Pratinjau kartu lirik + tombol bagikan
        if (showCard) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Sonora.Background.copy(alpha = 0.97f))
                    .pointerInput(Unit) {}
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        RoundButton(
                            onClick = { if (colorStep) colorStep = false else showCard = false },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(
                                imageVector = if (colorStep) AppIcons.BackArrow else Icons.Filled.Close,
                                contentDescription = "Kembali",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (colorStep) "Pilih warna kartu" else "Bagikan lirik",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = cardBitmap
                        if (bmp == null) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Kartu lirik",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .aspectRatio(bmp.width.toFloat() / bmp.height)
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        }
                    }

                    if (!colorStep) {
                        // Langkah 1: lihat kartu, lanjut untuk memilih warna
                        SharePillButton(
                            text = "Lanjutkan",
                            enabled = cardBitmap != null,
                            onClick = { colorStep = true }
                        )
                    } else {
                        // Langkah 2: pilih warna dari thumbnail, lalu bagikan
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            palette.forEach { swatch ->
                                val chosen = swatch == cardColor
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(swatch))
                                        .border(
                                            width = if (chosen) 3.dp else 1.dp,
                                            color = if (chosen) Color.White else Color.White.copy(alpha = 0.25f),
                                            shape = CircleShape
                                        )
                                        .clickable { cardColor = swatch }
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        SharePillButton(
                            text = "Bagikan",
                            enabled = cardBitmap != null && !sharing,
                            onClick = {
                                val bmp = cardBitmap ?: return@SharePillButton
                                scope.launch {
                                    sharing = true
                                    try {
                                        LyricsCard.share(context, bmp)
                                    } finally {
                                        sharing = false
                                    }
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Tombol pil putih lebar, dipakai di mode pilih lirik dan layar pratinjau kartu. */
@Composable
private fun SharePillButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.25f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}

/**
 * Baris waktu di bawah bar progres: waktu berjalan di kiri, sisa waktu di kanan, dan selama
 * crossfade label "Crossfading" di tengah (posisi dan ukuran label sama dengan SimpMusic, 13sp).
 *
 * Tinggi barisnya dikunci dengan `heightIn(min)`: label 13sp sedikit lebih tinggi daripada teks
 * waktu 12sp, dan tanpa ini seluruh kontrol di bawahnya bergeser saat label muncul/hilang.
 */
@Composable
private fun PlaybackTimeRow(
    elapsed: String,
    remaining: String,
    crossfading: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = elapsed, fontSize = 12.sp, color = color, modifier = Modifier.weight(1f))
        // Sisi kiri dan kanan sama-sama weight(1f), jadi label yang selebar isinya otomatis di tengah
        CrossfadingLabel(
            visible = crossfading,
            style = TextStyle(fontSize = 13.sp),
            baseColor = color
        )
        Text(
            text = remaining,
            fontSize = 12.sp,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Bar progres tipis dengan bulatan kecil, bisa diketuk dan digeser. */
@Composable
private fun ProgressBar(
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    /** True selama crossfade: bagian yang sudah terputar dan bulatannya berganti warna pelangi. */
    crossfading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val trackColor = rememberCrossfadeTrackColor(crossfading)
    val density = LocalDensity.current
    val thumb = 12.dp
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
                .background(Color.White.copy(alpha = 0.3f))
        )
        Box(
            modifier = Modifier
                .width(with(density) { (thumbPx / 2 + value * travelPx).toDp() })
                .height(4.dp)
                .clip(CircleShape)
                .background(trackColor)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset((value * travelPx).roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(trackColor)
        )
    }
}

/**
 * Tombol bulat di layar Sedang Diputar (kembali, suka, dan tiga tombol di tepi cover).
 *
 * Latarnya hitam transparan, bukan putih: tombol di tepi cover separuhnya berada di atas gambar
 * sampul yang bisa sangat terang, dan ikon putih di atas putih transparan jadi tidak terbaca.
 * Garis tepi putih tipis menjaga bentuk tombol tetap terlihat di atas sampul yang gelap.
 */
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
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
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

/**
 * Cover lagu tetangga yang ikut terlihat saat cover di Now Playing digeser. Selalu dikomposisi
 * (transparan saat tidak digeser) supaya gambarnya sudah termuat begitu jari mulai menggeser.
 */
@Composable
private fun SwipeCoverPeek(
    thumbnailUrl: String?,
    visible: Boolean,
    offsetPx: Float,
    shown: () -> Boolean
) {
    if (!visible) return
    AsyncImage(
        model = thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetPx
                alpha = if (shown()) 1f else 0f
            }
            .clip(CircleShape)
            .background(Sonora.SurfaceHigh)
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
    )
}

/** Satu kali animasi ketuk dua kali: [id] naik tiap ketukan (0 = belum pernah), [liked] = jadi favorit atau batal. */
private data class HeartBurst(val id: Int = 0, val liked: Boolean = true)

/**
 * Umpan balik ketuk dua kali di tengah cover. Jadi favorit: hati utuh membesar lalu memudar.
 * Batal favorit: hati membesar, lalu retak di tengah dan kedua belahannya terbuka dan jatuh sambil memudar.
 */
@Composable
private fun DoubleTapHeartBurst(burst: HeartBurst, modifier: Modifier = Modifier) {
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    val split = remember { Animatable(0f) }
    // Jenis hati dikunci saat animasi mulai, supaya tidak berubah di tengah jalan
    var shownLiked by remember { mutableStateOf(true) }
    val leftShape = remember { HeartHalfShape(isLeft = true) }
    val rightShape = remember { HeartHalfShape(isLeft = false) }

    LaunchedEffect(burst.id) {
        if (burst.id == 0) return@LaunchedEffect
        shownLiked = burst.liked
        split.snapTo(0f)
        heartScale.snapTo(0.4f)
        heartAlpha.snapTo(1f)
        heartScale.animateTo(1.15f, tween(160, easing = FastOutSlowInEasing))
        if (burst.liked) {
            delay(240)
            heartAlpha.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        } else {
            delay(160)
            launch { split.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
            delay(200)
            heartAlpha.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (heartAlpha.value > 0f) {
            if (shownLiked) {
                Icon(
                    imageVector = AppIcons.HeartFilled,
                    contentDescription = null,
                    tint = HeartColor,
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            scaleX = heartScale.value
                            scaleY = heartScale.value
                            alpha = heartAlpha.value
                        }
                )
            } else {
                // Dua salinan hati, masing-masing dipotong separuh dengan tepi bergerigi (retakan),
                // lalu diputar dari ujung bawah hati sehingga retakan membuka ke atas
                listOf(leftShape to -1f, rightShape to 1f).forEach { (halfShape, dir) ->
                    Icon(
                        imageVector = AppIcons.HeartFilled,
                        contentDescription = null,
                        tint = HeartColor,
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer {
                                val t = split.value
                                scaleX = heartScale.value
                                scaleY = heartScale.value
                                alpha = heartAlpha.value
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                rotationZ = dir * 20f * t
                                translationX = dir * 20.dp.toPx() * t
                                translationY = 28.dp.toPx() * t * t
                                shape = halfShape
                                clip = true
                            }
                    )
                }
            }
        }
    }
}

/** Separuh kiri atau kanan sebuah kotak, dengan tepi tengah bergerigi seperti retakan. */
private class HeartHalfShape(private val isLeft: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // Pergeseran retakan dari garis tengah (pecahan lebar) di tiap ketinggian (pecahan tinggi)
        val ys = floatArrayOf(0f, 0.2f, 0.38f, 0.56f, 0.74f, 0.9f, 1f)
        val dx = floatArrayOf(0f, 0.05f, -0.05f, 0.06f, -0.04f, 0.04f, 0f)
        val path = Path().apply {
            moveTo(if (isLeft) 0f else w, 0f)
            for (i in ys.indices) lineTo(cx + dx[i] * w, ys[i] * h)
            lineTo(if (isLeft) 0f else w, h)
            close()
        }
        return Outline.Generic(path)
    }
}