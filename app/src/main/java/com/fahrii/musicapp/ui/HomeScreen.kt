package com.fahrii.musicapp.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.ArtistStat
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.delay
import java.util.Calendar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.Dp

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

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlay: (List<Song>, Int) -> Unit,
    onGenreClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPickClick: (Int) -> Unit,
    listState: LazyListState,
    topInset: Dp,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val artists by viewModel.artists.collectAsState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
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
                                onOpen = { onPickClick(index) },
                                onPlay = { onPlay(pick.songs, 0) },
                                modifier = Modifier.fillParentMaxWidth(0.86f)
                            )
                        }
                    }
                }
            }

            if (state.isLoading || state.newReleases.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("Rilis Baru", action = "Lainnya")
                        SongCardRow(state.newReleases, onPlay)
                    }
                }
            }

            item {
                Column {
                    SectionTitle("Lagu Trending")
                    SongCardRow(state.trending, onPlay)
                }
            }

            item {
                Column {
                    SectionTitle("Genre")
                    GenreGrid(onGenreClick)
                }
            }

            item {
                Column {
                    SectionTitle("Artis Pilihan")
                    artists.forEachIndexed { index, artist ->
                        ArtistRow(
                            rank = index + 1,
                            artist = artist,
                            onClick = { onArtistClick(artist.name) }
                        )
                    }
                }
            }
        }
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

@Composable
private fun SectionTitle(title: String, action: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            Text(
                text = action,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Sonora.TextSecondary
            )
        }
    }
}

// ---------- Pilihan Cepat ----------

@Composable
private fun QuickPickCard(pick: QuickPick, onOpen: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val shape = remember { NotchedCardShape() }

    Box(
        modifier = modifier
            .height(168.dp)
            .clip(shape)
            .background(Sonora.Surface)
            .border(1.dp, Sonora.Border, shape)
            .clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(88.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White).clickable(onClick = onPlay),
                ) {
                    Icon(
                        imageVector = AppIcons.Play,
                        contentDescription = "Putar",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                DiagonalCovers(
                    first = pick.songs.getOrNull(0)?.thumbnailUrl,
                    second = pick.songs.getOrNull(1)?.thumbnailUrl,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Spacer(Modifier.weight(1f))
            Text(pick.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(pick.subtitle, fontSize = 12.sp, color = Sonora.TextSecondary)
        }
    }
}

@Composable
private fun DiagonalCovers(first: String?, second: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Sonora.SurfaceHigh)
            .border(1.dp, Sonora.Border, shape)
    ) {
        AsyncImage(
            model = first,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(DiagonalShape(left = true))
        )
        AsyncImage(
            model = second,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(DiagonalShape(left = false))
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color(0xFFE2DAFF),
                start = Offset(size.width * 0.36f, size.height),
                end = Offset(size.width * 0.67f, 0f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/** Dua potongan miring: kiri dan kanan garis diagonal. */
private class DiagonalShape(private val left: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val topX = w * 0.67f
        val bottomX = w * 0.36f
        val path = Path().apply {
            if (left) {
                moveTo(0f, 0f); lineTo(topX, 0f); lineTo(bottomX, h); lineTo(0f, h)
            } else {
                moveTo(topX, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(bottomX, h)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

/** Kartu dengan sudut kiri-atas dan kanan-bawah yang "digigit" (cekung), sudut lain membulat. */
private class NotchedCardShape(
    private val corner: androidx.compose.ui.unit.Dp = 20.dp,
    private val notch: androidx.compose.ui.unit.Dp = 18.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val n = with(density) { notch.toPx() }
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(n, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)       // kanan-atas membulat
            lineTo(w, h - n)
            arcTo(Rect(w - n, h - n, w + n, h + n), -90f, -90f, false)   // kanan-bawah cekung
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)        // kiri-bawah membulat
            lineTo(0f, n)
            arcTo(Rect(-n, -n, n, n), 90f, -90f, false)                  // kiri-atas cekung
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------- Rilis Baru & Lagu Trending (komponen sama) ----------

@Composable
private fun SongCardRow(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (songs.isEmpty()) {
            // Placeholder selagi memuat
            items(4) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Sonora.Surface)
                )
            }
        } else {
            itemsIndexed(songs, key = { index, song -> "$index-${song.url}" }) { index, song ->
                SongCard(song = song, onClick = { onPlay(songs, index) })
            }
        }
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
            text = song.artist,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Sonora.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------- Genre ----------

@Composable
private fun GenreGrid(onGenreClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        genres.chunked(3).forEach { column ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                column.forEach { genre ->
                    GenrePill(genre = genre, onClick = { onGenreClick(genre.query) })
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

@Composable
private fun ArtistRow(rank: Int, artist: ArtistStat, onClick: () -> Unit) {
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
            Text(
                text = artist.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}