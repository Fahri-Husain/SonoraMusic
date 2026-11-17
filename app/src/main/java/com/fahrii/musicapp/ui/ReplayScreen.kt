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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.ArtistImageRepository
import com.fahrii.musicapp.data.MonthArtist
import com.fahrii.musicapp.data.MonthSong
import com.fahrii.musicapp.data.MonthStats
import com.fahrii.musicapp.data.Song
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private val MonthShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val MonthLong = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private const val SONGS_PER_PAGE = 4
private const val MAX_SONGS = 12
private const val MAX_ARTISTS = 10

private val Muted = Color.White.copy(alpha = 0.45f)

private fun monthKey(year: Int, month: Int) = "%04d-%02d".format(year, month)

/** Hanya menit penuh yang dihitung (dibulatkan ke bawah). */
private fun minutesLabel(ms: Long): String {
    val min = maxOf(0L, ms) / 60_000L
    return "${NumberFormat.getIntegerInstance(Locale.US).format(min)} ${if (min == 1L) "minute" else "minutes"}"
}

@Composable
fun ReplayScreen(
    stats: Map<String, MonthStats>,
    artistImages: Map<String, String>,
    onRequestArtistImage: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }

    val years = remember(stats) {
        (stats.keys.mapNotNull { it.take(4).toIntOrNull() } + today.year).distinct().sortedDescending()
    }
    val lastMonth = if (year == today.year) today.monthValue else 12
    val shownMonth = month.coerceIn(1, lastMonth)

    val monthStats = stats[monthKey(year, shownMonth)] ?: MonthStats()
    val topArtists = remember(monthStats) {
        monthStats.artists.sortedByDescending { it.listenedMs }.take(MAX_ARTISTS)
    }
    val topSongs = remember(monthStats) {
        monthStats.songs
            .sortedWith(compareByDescending<MonthSong> { it.plays }.thenByDescending { it.listenedMs })
            .take(MAX_SONGS)
    }
    // Minta foto profil untuk artis yang tampil (hasilnya di-cache)
    LaunchedEffect(topArtists) { topArtists.forEach { onRequestArtistImage(it.name) } }

    val totalMs = monthStats.totalMs
    val hasData = monthStats.songs.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        // Cahaya hangat di bagian atas, seperti referensi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFF9F0A).copy(alpha = 0.50f),
                            Color(0xFFE5173F).copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.BackArrow, contentDescription = "Kembali", modifier = Modifier.size(20.dp))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replay",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    YearPill(
                        year = year,
                        years = years,
                        onSelect = { y ->
                            year = y
                            month = if (y == today.year) today.monthValue
                            else (1..12).lastOrNull { stats.containsKey(monthKey(y, it)) } ?: 12
                        }
                    )
                }
            }

            item {
                LazyRow(
                    state = rememberLazyListState(
                        initialFirstVisibleItemIndex = (shownMonth - 3).coerceAtLeast(0)
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    items(lastMonth) { index ->
                        val m = index + 1
                        Text(
                            text = MonthShort[index],
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (m == shownMonth) Color.White else Muted,
                            modifier = Modifier.clickable { month = m }
                        )
                    }
                }
            }

            item {
                val monthName = MonthLong[shownMonth - 1]
                if (hasData) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Muted)) { append("You listened for ") }
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                append(minutesLabel(totalMs))
                            }
                            withStyle(SpanStyle(color = Muted)) { append(" in $monthName.") }
                        },
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 28.dp)
                    )
                } else {
                    Text(
                        text = "Nothing played in $monthName yet. Play a few songs and your Replay will show up here.",
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        color = Muted,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 56.dp)
                    )
                }
            }

            if (hasData) {
                if (topArtists.isNotEmpty()) {
                    item { SectionHeading("Your Top Artists") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(topArtists, key = { _, a -> a.name }) { index, artist ->
                                ArtistCard(
                                    rank = index + 1,
                                    artist = artist,
                                    imageUrl = artistImages[ArtistImageRepository.keyOf(artist.name)]
                                )
                            }
                        }
                    }
                }

                item { SectionHeading("Your Top Songs", topPadding = 32.dp) }
                item {
                    val pageWidth = (LocalConfiguration.current.screenWidthDp - 40).dp
                    val queue = remember(topSongs) { topSongs.map { it.song } }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(topSongs.chunked(SONGS_PER_PAGE)) { page, rows ->
                            Column(modifier = Modifier.width(pageWidth)) {
                                rows.forEachIndexed { i, item ->
                                    val rank = page * SONGS_PER_PAGE + i + 1
                                    TopSongRow(
                                        rank = rank,
                                        item = item,
                                        onClick = { onPlay(queue, rank - 1) }
                                    )
                                    if (i < rows.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 88.dp)
                                                .height(1.dp)
                                                .background(Sonora.Border.copy(alpha = 0.6f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = topPadding, bottom = 12.dp)
    )
}

@Composable
private fun YearPill(year: Int, years: List<Int>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$year", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Icon(AppIcons.Back, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            years.forEach { y ->
                DropdownMenuItem(
                    text = { Text("$y") },
                    onClick = {
                        open = false
                        onSelect(y)
                    }
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(rank: Int, artist: MonthArtist, imageUrl: String?) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(200.dp)
            .clip(shape)
            .background(Sonora.SurfaceHigh)
            .border(1.dp, Sonora.Border, shape)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Foto profil belum ada / sedang dicari: pakai inisial, bukan thumbnail lagu
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = artist.name.trim().take(1).uppercase(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.25f)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.80f)
                        )
                    )
                )
        )
        Text(
            text = "$rank",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp)
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = artist.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = minutesLabel(artist.listenedMs),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun TopSongRow(rank: Int, item: MonthSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            text = "$rank",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.song.title,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.song.artist} · ${item.plays} ${if (item.plays == 1) "Play" else "Plays"}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}