package com.fahrii.musicapp.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.State
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import kotlin.math.floor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import com.fahrii.musicapp.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.ArtistStat
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.data.isAlbumEntry
import com.fahrii.musicapp.data.toAlbumRef
import kotlinx.coroutines.delay
import java.util.Calendar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.Dp

/** Jumlah lagu yang tampil di baris beranda; sisanya ada di halaman daftar lengkap. */
private const val HOME_ROW_LIMIT = 10

private data class Genre(val name: String, val query: String, val accent: Color)

// Urutan ini mengisi grid kolom demi kolom (3 baris per kolom), sama seperti di desain
private val genres = listOf(
    Genre("Pop", "pop hits", Color(0xFF7FB8FF)),
    Genre("K-Pop", "K-Pop hits", Color(0xFFFF7A9C)),
    Genre("R&B & Soul", "r&b soul", Color(0xFFB48CFF)),
    Genre("Dance &\nElektronik", "electronic dance music", Color(0xFF7CE08A)),
    Genre("Hip-Hop", "hip hop", Color(0xFFFFD166)),
    Genre("Indonesia Pop", "lagu pop indonesia", Color(0xFFFFA45C)),
    Genre("J-Pop", "J-Pop hits", Color(0xFFFF8FB8)),
    Genre("Jazz", "jazz", Color(0xFF6FB5FF)),
    Genre("Klasik", "classical music", Color(0xFFC9A0FF)),
    Genre("Metal", "metal", Color(0xFF9A9AA5)),
    Genre("Rock", "rock hits", Color(0xFFFF7A7A)),
    Genre("Pop Punk", "pop punk", Color(0xFFFF6FA0))
)

fun greetingForHour(hour: Int): String = when (hour) {
    in 5..10 -> "Selamat Pagi"
    in 11..14 -> "Selamat Siang"
    in 15..17 -> "Selamat Sore"
    else -> "Selamat Malam"
}

private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlay: (List<Song>, Int, String) -> Unit,
    onOpenAlbum: (ArtistAlbum) -> Unit,
    onGenreClick: (name: String, query: String, accentArgb: Int) -> Unit,
    onArtistClick: (String) -> Unit,
    onPickClick: (Int) -> Unit,
    onSeeAllNewReleases: () -> Unit,
    onSeeAllTrending: () -> Unit,
    listState: LazyListState,
    topInset: Dp,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val subscribers by viewModel.subscribers.collectAsState()
    val glowPosition = rememberGlowPosition()
    val pullState = rememberPullToRefreshState()

    // Tarik daftar ke bawah saat sudah di paling atas: ikon refresh muncul, lalu Beranda dimuat ulang
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::pullRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = state.isRefreshing,
                // Turun di bawah header Beranda supaya tidak tertutup
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
                containerColor = Sonora.SurfaceHigh,
                color = Sonora.Accent
            )
        }
    ) {
        // Cahaya gradasi di atas beranda: warnanya berganti pelan dan mulus, tidak ikut ter-scroll
        HomeGlow(position = glowPosition)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset + 12.dp, bottom = 24.dp + LocalBottomInset.current),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (state.error != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.error ?: "",
                            fontSize = 13.sp,
                            color = Sonora.TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::refresh) { Text("Coba lagi") }
                    }
                }
            }

            item {
                Column {
                    SectionTitle("Pilihan Cepat")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(state.quickPicks, key = { _, pick -> pick.label }) { index, pick ->
                            QuickPickCard(
                                pick = pick,
                                // Skeleton selama Beranda masih memuat (sama dengan Rilis Baru dan Lagu Trending),
                                // atau kalau kartu ini memang belum punya lagu sama sekali
                                loading = state.isLoading || (state.quickPicksLoading && pick.songs.isEmpty()),
                                glowPosition = glowPosition,
                                onOpen = { onPickClick(index) },
                                onPlay = { onPlay(pick.songs, 0, "Pilihan Cepat \"${pick.label}\"") },
                                modifier = Modifier.fillParentMaxWidth(0.86f)
                            )
                        }
                    }
                }
            }

            if (state.isLoading || state.newReleases.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("Rilis Baru", onSeeAll = onSeeAllNewReleases)
                        SongCardRow(state.newReleases.take(HOME_ROW_LIMIT), "Rilis Baru", state.isLoading, onPlay, onOpenAlbum)
                    }
                }
            }

            item {
                Column {
                    SectionTitle("Lagu Trending", onSeeAll = onSeeAllTrending)
                    SongCardRow(state.trending.take(HOME_ROW_LIMIT), "Lagu Trending", state.isLoading, onPlay, onOpenAlbum)
                }
            }

            item {
                Column {
                    SectionTitle("Genre")
                    GenreGrid(loading = state.isLoading, onGenreClick = onGenreClick)
                }
            }

            item {
                Column {
                    SectionTitle("Artis Pilihan")
                    if (state.isLoading) {
                        // Skeleton selagi Beranda memuat (tiga artis teratas)
                        repeat(3) { index -> ArtistRowSkeleton(rank = index + 1) }
                    } else {
                        artists.forEachIndexed { index, artist ->
                            ArtistRow(
                                rank = index + 1,
                                artist = artist,
                                subscribers = subscribers[artist.name],
                                onClick = { onArtistClick(artist.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val HomeGlowPalette = listOf(
    Color(0xFFFF9F0A), // oranye
    Color(0xFFE5173F), // merah
    Color(0xFFB5179E), // magenta
    Color(0xFF5E5CE6), // indigo
    Color(0xFF0A84FF), // biru
    Color(0xFF1FC8B4)  // tosca
)

private const val GLOW_STEP_MS = 5000

private fun glowColor(position: Float): Color {
    val n = HomeGlowPalette.size
    val base = floor(position).toInt()
    val fraction = position - floor(position)
    return lerp(HomeGlowPalette[base % n], HomeGlowPalette[(base + 1) % n], fraction)
}

/** Posisi warna di palet (0..jumlah warna), berputar terus. Dibagi ke gradasi atas dan garis kartu. */
@Composable
private fun rememberGlowPosition(): State<Float> {
    val transition = rememberInfiniteTransition(label = "homeGlow")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = HomeGlowPalette.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(HomeGlowPalette.size * GLOW_STEP_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "homeGlowPosition"
    )
}

/** Gradasi hangat seperti di Replay, tapi pelan-pelan berganti warna mengitari palet. */
@Composable
private fun HomeGlow(position: State<Float>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .drawBehind {
                // Dibaca di dalam draw: hanya menggambar ulang, tanpa recomposition tiap frame
                val top = glowColor(position.value)
                val middle = glowColor(position.value + 1f)
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            top.copy(alpha = 0.50f),
                            middle.copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
            }
    )
}


@Composable
fun HomeHeader(
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sapaan ikut jam, dicek ulang tiap 30 detik
    var hour by remember { mutableIntStateOf(currentHour()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            hour = currentHour()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("SonoraMusic", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(greetingForHour(hour), fontSize = 13.sp, color = Color(0xFFD0D0D5))
        }
        IconButton(onClick = onHistoryClick) {
            Icon(AppIcons.History, contentDescription = "Riwayat", modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onSettingsClick) {
            Icon(AppIcons.Settings, contentDescription = "Pengaturan", modifier = Modifier.size(24.dp))
        }
    }
}

/** Judul bagian; [onSeeAll] menampilkan panah kanan di ujung (sama seperti di Replay) untuk membuka daftar lengkapnya. */
@Composable
private fun SectionTitle(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSeeAll),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_right_arrow),
                    contentDescription = "Lihat semua",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ---------- Pilihan Cepat ----------

private val QuickPickFrontHeight = 164.dp   // tinggi lapisan depan (hitam)
private val QuickPickLip = 8.dp                // tebal garis warna di bawah lapisan depan
private val QuickPickBackExtra = 18.dp         // tambahan tinggi: lapisan hitam paling belakang
private val QuickPickRadius = 26.dp
private val QuickPickBezel = 7.dp              // bingkai hitam di sekeliling panel cover
private val QuickPickPanelHeight = 92.dp       // tinggi panel di sisi kanan (yang paling tinggi)
private const val PANEL_LEFT_FRACTION = 0.74f  // tinggi panel di sisi kiri relatif terhadap kanan

/**
 * Kartu Pilihan Cepat: lapisan hitam bertumpuk dengan garis warna di sela-selanya (warnanya ikut
 * gradasi beranda), panel cover yang bagian bawahnya melandai ke kanan, tombol putar di kiri atas
 * panel, dan teks di ceruk kiri bawah.
 */
@Composable
private fun QuickPickCard(
    pick: QuickPick,
    loading: Boolean,
    glowPosition: State<Float>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(QuickPickRadius)
    val panelShape = remember { StepPanelShape(QuickPickRadius - QuickPickBezel) }

    Box(
        modifier = modifier
            .height(QuickPickFrontHeight + QuickPickBackExtra)
            .clip(cardShape)
            .drawBehind {
                val r = CornerRadius(QuickPickRadius.toPx())
                val frontH = QuickPickFrontHeight.toPx()
                val c1 = glowColor(glowPosition.value)
                val c2 = glowColor(glowPosition.value + 1f)
                // Lapisan paling belakang
                drawRoundRect(Color.Black, size = size, cornerRadius = r)
                // Garis warna (warna yang sama dengan gradasi beranda, dibuat lembut dan menyatu dengan hitam): lapisan depan digeser
                // ke bawah sedikit sehingga yang terlihat hanya tepi bawahnya
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            c1.copy(alpha = 0.55f),
                            lerp(c1, c2, 0.5f).copy(alpha = 0.50f),
                            c2.copy(alpha = 0.45f)
                        )
                    ),
                    topLeft = Offset(0f, QuickPickLip.toPx()),
                    size = Size(size.width, frontH),
                    cornerRadius = r
                )
                // Lapisan depan
                drawRoundRect(Color.Black, size = Size(size.width, frontH), cornerRadius = r)
            }
            .clickable(enabled = !loading, onClick = onOpen)
    ) {
        // Panel cover: bentuknya pas dengan lekukan, dengan tepi bergradasi tipis sebagai pembatas
        Box(
            modifier = Modifier
                .padding(QuickPickBezel)
                .fillMaxWidth()
                .height(QuickPickPanelHeight)
                .clip(panelShape)
                .drawBehind {
                    // Latar saat cover belum termuat: gradasi warna beranda
                    val c1 = glowColor(glowPosition.value)
                    val c2 = glowColor(glowPosition.value + 1f)
                    drawRect(Brush.linearGradient(listOf(c1, c2), Offset.Zero, Offset(size.width, size.height)))
                }
        ) {
            val covers = pick.songs.take(4).map { it.thumbnailUrl }
            if (covers.isNotEmpty()) {
                BlendedCovers(urls = covers, modifier = Modifier.fillMaxSize())
            }
            // Kilau diagonal tipis + gelap di tepi bawah supaya tepi panel tegas
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.16f),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.28f)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.04f))),
                        panelShape
                    )
            )
            if (loading) {
                // Skeleton selagi daftar lagunya dimuat: menutupi panel, tombol putar belum ada
                Box(modifier = Modifier.matchParentSize().shimmerBackground())
            }
            if (!loading) Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Play,
                    contentDescription = "Putar",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Teks di ceruk kiri bawah panel
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = QuickPickBezel + 10.dp,
                    top = QuickPickBezel + QuickPickPanelHeight * PANEL_LEFT_FRACTION + 8.dp,
                    end = 14.dp
                )
        ) {
            if (loading) {
                // Skeleton teks: dua bar setinggi baris label dan subjudul
                Spacer(Modifier.height(3.dp))
                Box(modifier = Modifier.width(88.dp).height(13.dp).shimmerBackground(RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(7.dp))
                Box(modifier = Modifier.width(130.dp).height(11.dp).shimmerBackground(RoundedCornerShape(6.dp)))
            } else {
                Text(pick.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pick.subtitle, fontSize = 12.sp, color = Sonora.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Cover berjejer, tiap cover saling tumpang tindih dan memudar halus dari tepi kirinya sehingga
 * perpindahan antar foto menyatu (tidak ada garis patah).
 */
@Composable
private fun BlendedCovers(urls: List<String?>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val n = urls.size
        val segment = maxWidth / n
        val overlap = segment * 0.6f
        urls.forEachIndexed { index, url ->
            val blended = index > 0
            val start = if (blended) segment * index - overlap else 0.dp
            val width = if (blended) segment + overlap else segment
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = start)
                    .width(width)
                    .fillMaxHeight()
                    .then(if (blended) Modifier.fadeInFromLeft(overlap / width) else Modifier)
            )
        }
    }
}

/** Membuat bagian kiri (sebesar [fraction] lebar) memudar dari transparan ke penuh, dengan kurva halus. */
private fun Modifier.fadeInFromLeft(fraction: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                fraction * 0.35f to Color.Black.copy(alpha = 0.18f),
                fraction * 0.70f to Color.Black.copy(alpha = 0.75f),
                fraction to Color.Black,
                1f to Color.Black
            ),
            blendMode = BlendMode.DstIn
        )
    }

/** Persegi panjang bersudut membulat; sisi bawahnya lebih rendah di kanan, disambung kurva S yang halus. */
private class StepPanelShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }
        val yl = h * PANEL_LEFT_FRACTION   // dasar panel di sisi kiri
        val xa = w * 0.40f                 // kurva mulai (kiri)
        val xb = w * 0.58f                 // kurva selesai (kanan)
        val k = (xb - xa) * 0.55f
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(xb, h)
            cubicTo(xb - k, h, xa + k, yl, xa, yl)
            lineTo(r, yl)
            arcTo(Rect(0f, yl - 2 * r, 2 * r, yl), 90f, 90f, false)
            lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------- Rilis Baru & Lagu Trending (komponen sama) ----------

@Composable
private fun SongCardRow(
    songs: List<Song>,
    source: String,
    loading: Boolean,
    onPlay: (List<Song>, Int, String) -> Unit,
    onOpenAlbum: (ArtistAlbum) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (loading) {
            // Skeleton selagi memuat, juga saat dimuat ulang walau daftar lama masih ada
            items(4) { SongCardSkeleton() }
        } else if (songs.isNotEmpty()) {
            itemsIndexed(songs, key = { index, song -> "$index-${song.url}" }) { index, song ->
                SongCard(
                    song = song,
                    onClick = {
                        if (song.isAlbumEntry()) {
                            // Album/EP: buka halaman albumnya. Antrean hanya berisi lagu yang bisa diputar.
                            onOpenAlbum(song.toAlbumRef())
                        } else {
                            val playable = songs.filterNot { it.isAlbumEntry() }
                            val start = playable.indexOfFirst { it.url == song.url }
                            if (start >= 0) onPlay(playable, start, source)
                        }
                    }
                )
            }
        }
    }
}

/** Penanda kartu lagu selagi memuat: bentuknya sama dengan [SongCard] (cover, artis, judul). */
@Composable
private fun SongCardSkeleton() {
    Column(modifier = Modifier.width(132.dp)) {
        Box(modifier = Modifier.size(132.dp).shimmerBackground(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.width(96.dp).height(12.dp).shimmerBackground(RoundedCornerShape(6.dp)))
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.width(70.dp).height(11.dp).shimmerBackground(RoundedCornerShape(6.dp)))
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)

    Column(modifier = Modifier.width(132.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(132.dp)
                .clip(shape)
                .background(Sonora.Surface)
                .border(1.dp, Sonora.Border, shape)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artistLabel(song),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        )
        Text(
            text = if (song.isAlbumEntry()) "Album • ${song.title}" else song.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Sonora.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        )
    }
}

// ---------- Genre ----------

@Composable
private fun GenreGrid(loading: Boolean, onGenreClick: (name: String, query: String, accentArgb: Int) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        genres.chunked(3).forEach { column ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                column.forEach { genre ->
                    if (loading) {
                        // Skeleton selagi Beranda memuat: ukuran sama dengan GenrePill
                        Box(modifier = Modifier.size(width = 112.dp, height = 44.dp).shimmerBackground(RoundedCornerShape(12.dp)))
                    } else {
                        GenrePill(
                            genre = genre,
                            onClick = {
                                // Nama di pil bisa dua baris ("Dance &\nElektronik"); judul halaman satu baris
                                onGenreClick(genre.name.replace("\n", " "), genre.query, genre.accent.toArgb())
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenrePill(genre: Genre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val borderBrush = Brush.linearGradient(listOf(genre.accent, genre.accent.copy(alpha = 0.25f)))

    Box(
        modifier = modifier
            .size(width = 112.dp, height = 44.dp)
            .clip(shape)
            .background(Sonora.Surface)
            .border(1.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = genre.name,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

// ---------- Artis Pilihan ----------

/** Penanda baris artis selagi memuat: nomor urut, foto bulat, nama, dan jumlah subscriber. */
@Composable
private fun ArtistRowSkeleton(rank: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Box(modifier = Modifier.size(52.dp).shimmerBackground(CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Box(modifier = Modifier.width(110.dp).height(13.dp).shimmerBackground(RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.width(80.dp).height(11.dp).shimmerBackground(RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun ArtistRow(rank: Int, artist: ArtistStat, subscribers: Long?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = artist.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Sonora.SurfaceHigh)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = artist.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                if (subscribers != null && subscribers >= 0) {
                    Text(
                        text = "${compactCount(subscribers)} subscribers",
                        fontSize = 12.sp,
                        color = Sonora.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}