package com.fahrii.musicapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.R
import com.fahrii.musicapp.data.ArtistImageRepository
import com.fahrii.musicapp.data.AlbumTracks
import com.fahrii.musicapp.data.MonthArtist
import com.fahrii.musicapp.data.MonthSong
import com.fahrii.musicapp.data.MusicRepository
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
private const val MAX_ALBUMS = 10

/** Album dicari dari lagu-lagu teratas saja, supaya tidak menembak YouTube untuk semua lagu. */
private const val ALBUM_LOOKUP_LIMIT = 30

/** Artis hanya masuk Top Artists kalau total waktu dengarnya minimal segini (1 menit). */
private const val MIN_ARTIST_LISTEN_MS = 60_000L

/** Album hanya masuk Top Albums kalau total waktu dengar lagu-lagunya di bulan itu minimal segini (1 menit). */
private const val MIN_ALBUM_LISTEN_MS = 60_000L

/** Di daftar lengkap album, pencarian album diperluas ke lebih banyak lagu (tetap dibatasi). */
private const val FULL_ALBUM_LOOKUP_LIMIT = 100

/** Daftar lengkap yang dibuka lewat panah di samping judul tiap bagian. */
private enum class ReplayList(val title: String) {
    Artists("Your Top Artists"),
    Songs("Your Top Songs"),
    Albums("Your Top Albums")
}

private val Muted = Color.White.copy(alpha = 0.45f)

private data class TopAlbum(
    val key: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val plays: Int,
    val listenedMs: Long,
    /** Lagu-lagu dari album ini yang pernah kamu dengar di bulan tersebut. */
    val songs: List<MonthSong>
)

/**
 * Kelompokkan lagu bulan ini per album (judul + artis utama), jumlahkan waktu dengar dan play-nya.
 * Lagu yang albumnya belum diketahui dilewati. Sampul diambil dari lagu yang paling sering diputar.
 */
private fun buildTopAlbums(songs: List<MonthSong>, songAlbums: Map<String, String>): List<TopAlbum> {
    class Entry(val album: String, val artist: String, val item: MonthSong)

    return songs
        .mapNotNull { item ->
            val album = songAlbums[item.song.url]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Entry(album, item.song.artist.substringBefore(',').trim(), item)
        }
        .groupBy { it.album.lowercase() + "|" + it.artist.lowercase() }
        .map { (key, group) ->
            val byPlays = group.sortedWith(
                compareByDescending<Entry> { it.item.plays }.thenByDescending { it.item.listenedMs }
            )
            TopAlbum(
                key = key,
                title = byPlays.first().album,
                artist = byPlays.first().artist,
                coverUrl = byPlays.firstNotNullOfOrNull { it.item.song.thumbnailUrl },
                plays = group.sumOf { it.item.plays },
                listenedMs = group.sumOf { it.item.listenedMs },
                songs = group.map { it.item }
            )
        }
        .sortedWith(compareByDescending<TopAlbum> { it.listenedMs }.thenByDescending { it.plays })
}

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
    songAlbums: Map<String, String>,
    onRequestSongAlbum: (Song) -> Unit,
    onOpenArtist: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
    currentSongUrl: String? = null,
    isPlaying: Boolean = false
) {
    val today = remember { LocalDate.now() }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }

    // Daftar lengkap yang sedang dibuka (null = halaman Replay biasa). Bulan dan tahun yang
    // dipilih ikut terbawa karena statenya ada di sini.
    var fullList by rememberSaveable { mutableStateOf<ReplayList?>(null) }
    BackHandler(enabled = fullList != null) { fullList = null }
    // Halaman album yang sedang dibuka (kunci album); menumpuk di atas halaman Replay / daftar lengkap
    var openedAlbumKey by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openedAlbumKey != null) { openedAlbumKey = null }
    // Di-hoist supaya posisi scroll halaman Replay tidak hilang setelah kembali dari daftar lengkap
    val listState = rememberLazyListState()

    // Bar atas kaca seperti halaman playlist: tombol kembali di hero ikut bergeser naik saat di-scroll,
    // lalu bar blur berisi panah kembali + judul "Replay" muncul setelah judul mepet status bar
    val backdrop = rememberGlassBackdrop()
    var titleBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val statusBarBottom = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    // Item 0 hanya ruang untuk tombol kembali dan item 1 berisi judul, jadi "sudah lewat" = index > 1
    val titleCovered by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 || titleBottom <= statusBarBottom }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (titleCovered) 1f else 0f,
        animationSpec = tween(180),
        label = "replayBar"
    )

    val years = remember(stats) {
        (stats.keys.mapNotNull { it.take(4).toIntOrNull() } + today.year).distinct().sortedDescending()
    }
    val lastMonth = if (year == today.year) today.monthValue else 12
    val shownMonth = month.coerceIn(1, lastMonth)

    val monthStats = stats[monthKey(year, shownMonth)] ?: MonthStats()
    // Halaman Replay menampilkan sebagian teratas (10 artis, 12 lagu, 10 album); sisanya
    // lengkap ada di daftar yang dibuka lewat panah di samping judul
    val allArtists = remember(monthStats) {
        monthStats.artists
            // Artis baru muncul setelah didengar minimal 1 menit
            .filter { it.listenedMs >= MIN_ARTIST_LISTEN_MS }
            .sortedByDescending { it.listenedMs }
    }
    val topArtists = remember(allArtists) { allArtists.take(MAX_ARTISTS) }
    val allSongs = remember(monthStats) {
        monthStats.songs
            // Lagu yang belum pernah selesai diputar (play = 0) tidak masuk daftar, tapi waktu
            // dengarnya tetap dihitung di total menit
            .filter { it.plays > 0 }
            .sortedWith(compareByDescending<MonthSong> { it.plays }.thenByDescending { it.listenedMs })
    }
    val topSongs = remember(allSongs) { allSongs.take(MAX_SONGS) }
    // Minta foto profil untuk artis yang tampil (hasilnya di-cache)
    LaunchedEffect(topArtists) { topArtists.forEach { onRequestArtistImage(it.name) } }

    // Album tiap lagu dicari dulu (di-cache), lalu lagu dikelompokkan per album
    val albumLookup = remember(monthStats) {
        monthStats.songs
            .sortedWith(compareByDescending<MonthSong> { it.plays }.thenByDescending { it.listenedMs })
            .take(ALBUM_LOOKUP_LIMIT)
    }
    LaunchedEffect(albumLookup) { albumLookup.forEach { onRequestSongAlbum(it.song) } }
    val allAlbums = remember(monthStats, songAlbums) {
        buildTopAlbums(monthStats.songs, songAlbums)
            // Album baru muncul setelah didengar minimal 1 menit; di bawah itu tidak ditampilkan
            .filter { it.listenedMs >= MIN_ALBUM_LISTEN_MS }
    }
    val topAlbums = remember(allAlbums) { allAlbums.take(MAX_ALBUMS) }

    val totalMs = monthStats.totalMs
    val hasData = monthStats.songs.isNotEmpty()

    val openedAlbum = openedAlbumKey?.let { key -> allAlbums.firstOrNull { it.key == key } }
    if (openedAlbum != null) {
        AlbumDetailScreen(
            album = openedAlbum,
            monthLabel = "${MonthLong[shownMonth - 1]} $year",
            onOpenArtist = onOpenArtist,
            onPlay = onPlay,
            onBack = { openedAlbumKey = null },
            currentSongUrl = currentSongUrl,
            isPlaying = isPlaying,
            modifier = modifier
        )
        return
    }

    val openedList = fullList
    if (openedList != null) {
        ReplayFullList(
            kind = openedList,
            monthLabel = "${MonthLong[shownMonth - 1]} $year",
            artists = allArtists,
            songs = allSongs,
            albums = allAlbums,
            monthSongs = monthStats.songs,
            artistImages = artistImages,
            onRequestArtistImage = onRequestArtistImage,
            onRequestSongAlbum = onRequestSongAlbum,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = { openedAlbumKey = it },
            onPlay = onPlay,
            onBack = { fullList = null },
            modifier = modifier,
            currentSongUrl = currentSongUrl,
            isPlaying = isPlaying
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Sumber blur untuk bar dan tombol kaca: cahaya hangat + daftar
        Box(modifier = Modifier.fillMaxSize().glassSource(backdrop)) {
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = 24.dp + LocalBottomInset.current
                )
            ) {
                // Ruang untuk tombol kembali (tombolnya sendiri melayang di atas, lihat di bawah)
                item { Spacer(Modifier.height(48.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Replay",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { titleBottom = it.boundsInRoot().bottom }
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
                        item {
                            SectionHeading("Your Top Artists", onSeeAll = { fullList = ReplayList.Artists })
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(topArtists, key = { _, a -> a.name }) { index, artist ->
                                    ArtistCard(
                                        rank = index + 1,
                                        artist = artist,
                                        imageUrl = artistImages[ArtistImageRepository.keyOf(artist.name)],
                                        onClick = { onOpenArtist(artist.name) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SectionHeading(
                            "Your Top Songs",
                            topPadding = 32.dp,
                            onSeeAll = { fullList = ReplayList.Songs }
                        )
                    }
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
                                            isCurrent = item.song.url == currentSongUrl,
                                            isPlaying = isPlaying,
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

                    if (topAlbums.isNotEmpty()) {
                        item {
                            SectionHeading(
                                "Your Top Albums",
                                topPadding = 32.dp,
                                onSeeAll = { fullList = ReplayList.Albums }
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(topAlbums, key = { _, a -> a.key }) { index, album ->
                                    AlbumCard(rank = index + 1, album = album, onClick = { openedAlbumKey = album.key })
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bar atas: kaca blur + panah kembali + judul "Replay", muncul setelah judul di halaman
        // mepet status bar (sama seperti halaman playlist)
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
                    text = "Replay",
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
        // (tidak menempel di layar). Setelah judul tertutup, panahnya muncul lagi di bar atas.
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

/** Judul bagian; [onSeeAll] menampilkan panah kanan di ujung untuk membuka daftar lengkapnya. */
@Composable
private fun SectionHeading(text: String, topPadding: Dp = 0.dp, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = topPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
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

/** Daftar lengkap satu bagian Replay (semua artis, lagu, atau album bulan yang dipilih). */
@Composable
private fun ReplayFullList(
    kind: ReplayList,
    monthLabel: String,
    artists: List<MonthArtist>,
    songs: List<MonthSong>,
    albums: List<TopAlbum>,
    monthSongs: List<MonthSong>,
    artistImages: Map<String, String>,
    onRequestArtistImage: (String) -> Unit,
    onRequestSongAlbum: (Song) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    currentSongUrl: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Nama album belum diketahui untuk semua lagu: cari untuk lebih banyak lagu (di-cache,
    // maksimal 2 permintaan sekaligus)
    if (kind == ReplayList.Albums) {
        LaunchedEffect(monthSongs) {
            monthSongs
                .sortedByDescending { it.listenedMs }
                .take(FULL_ALBUM_LOOKUP_LIMIT)
                .forEach { onRequestSongAlbum(it.song) }
        }
    }

    val queue = remember(songs) { songs.map { it.song } }
    val contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 4.dp,
        bottom = 24.dp + LocalBottomInset.current
    )

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.BackArrow, contentDescription = "Kembali", modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = kind.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(text = monthLabel, fontSize = 13.sp, color = Muted)
            }
        }

        val isEmpty = when (kind) {
            ReplayList.Artists -> artists.isEmpty()
            ReplayList.Songs -> songs.isEmpty()
            ReplayList.Albums -> albums.isEmpty()
        }
        if (isEmpty) {
            Text(
                text = if (kind == ReplayList.Albums) "Album sedang dicari, coba lagi sebentar lagi." else "Belum ada data.",
                fontSize = 15.sp,
                color = Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            when (kind) {
                ReplayList.Artists -> itemsIndexed(artists, key = { _, a -> a.name }) { index, artist ->
                    FullArtistRow(
                        rank = index + 1,
                        artist = artist,
                        imageUrl = artistImages[ArtistImageRepository.keyOf(artist.name)],
                        onRequestImage = { onRequestArtistImage(artist.name) },
                        onClick = { onOpenArtist(artist.name) }
                    )
                }

                ReplayList.Songs -> itemsIndexed(songs, key = { _, s -> s.song.url }) { index, item ->
                    TopSongRow(
                        rank = index + 1,
                        item = item,
                        isCurrent = item.song.url == currentSongUrl,
                        isPlaying = isPlaying,
                        onClick = { onPlay(queue, index) },
                        numberFirst = true
                    )
                }

                ReplayList.Albums -> itemsIndexed(albums, key = { _, a -> a.key }) { index, album ->
                    FullAlbumRow(rank = index + 1, album = album, onClick = { onOpenAlbum(album.key) })
                }
            }
        }
    }
}

@Composable
private fun FullArtistRow(
    rank: Int,
    artist: MonthArtist,
    imageUrl: String?,
    onRequestImage: () -> Unit,
    onClick: () -> Unit
) {
    // Foto dicari saat barisnya muncul di layar, bukan untuk semua artis sekaligus
    LaunchedEffect(artist.name) { onRequestImage() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp)
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Sonora.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = artist.name.trim().take(1).uppercase(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = minutesLabel(artist.listenedMs),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FullAlbumRow(rank: Int, album: TopAlbum, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp)
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Sonora.SurfaceHigh)
        ) {
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Teks panjang berjalan (marquee) alih-alih dipotong "..."
            Text(
                text = album.title,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
            Text(
                text = "${album.artist} · ${minutesLabel(album.listenedMs)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    }
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
private fun ArtistCard(rank: Int, artist: MonthArtist, imageUrl: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(200.dp)
            .clip(shape)
            .background(Sonora.SurfaceHigh)
            .border(1.dp, Sonora.Border, shape)
            .clickable(onClick = onClick)
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
private fun AlbumCard(rank: Int, album: TopAlbum, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = Modifier.width(150.dp).clip(shape).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(shape)
                .background(Sonora.SurfaceHigh)
                .border(1.dp, Sonora.Border, shape)
        ) {
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "$rank",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.width(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Text(
                    text = album.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }
    }
}

@Composable
private fun TopSongRow(
    rank: Int,
    item: MonthSong,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    /** true = angka di depan thumbnail (daftar lengkap); false = thumbnail dulu, baru angka (pratinjau). */
    numberFirst: Boolean = false
) {
    val rankText: @Composable () -> Unit = {
        Text(
            text = "$rank",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp)
        )
    }
    val songMenu = LocalSongMenu.current
    // Lagu yang sedang diputar: thumbnail diganti ritme (sama seperti daftar lagu lain)
    val thumb: @Composable () -> Unit = {
        if (isCurrent && isPlaying) {
            PlayingThumb(isPlaying = isPlaying, size = 48.dp)
        } else {
            AsyncImage(
                model = item.song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (numberFirst) {
            rankText()
            thumb()
        } else {
            thumb()
            rankText()
        }
        // Angka di depan: teks butuh jarak dari thumbnail (kalau angka di tengah, lebar angkanya sudah jadi jarak)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (numberFirst) 12.dp else 0.dp)
        ) {
            Text(
                text = item.song.title,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ArtistLine(item.song.url) { mod ->
                Text(
                    text = "${item.song.artist} · ${item.plays} ${if (item.plays == 1) "Play" else "Plays"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = mod
                )
            }
        }
        IconButton(onClick = { songMenu.show(item.song) }) {
            Icon(AppIcons.MoreVert, contentDescription = "Opsi")
        }
    }
}

/**
 * Halaman satu album ala Spotify: sampul, judul, artis, dan tracklist lengkap album (dimuat dari
 * YouTube Music). Lagu yang pernah kamu putar bulan ini diberi keterangan jumlah play-nya.
 * Kalau tracklist gagal dimuat, yang tampil hanya lagu album yang pernah kamu dengar.
 */
@Composable
private fun AlbumDetailScreen(
    album: TopAlbum,
    monthLabel: String,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onBack: () -> Unit,
    currentSongUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var loading by remember(album.key) { mutableStateOf(true) }
    var full by remember(album.key) { mutableStateOf<AlbumTracks?>(null) }
    LaunchedEffect(album.key) {
        loading = true
        full = try {
            MusicRepository.findAlbumTracks(album.title, album.artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        loading = false
    }

    // Lagu yang pernah selesai diputar bulan ini (dipakai untuk keterangan play dan sebagai cadangan)
    val listened = remember(album) {
        album.songs
            .filter { it.plays > 0 }
            .sortedWith(compareByDescending<MonthSong> { it.plays }.thenByDescending { it.listenedMs })
    }
    val playsByUrl = remember(album) { album.songs.associate { it.song.url to it.plays } }
    val playsByTitle = remember(album) {
        album.songs.associate { it.song.title.trim().lowercase() to it.plays }
    }

    val tracks: List<Song> = full?.songs ?: listened.map { it.song }
    val coverUrl = full?.coverUrl ?: album.coverUrl
    val listState = rememberLazyListState()
    val backdrop = rememberGlassBackdrop()
    var titleBottom by remember(album.key) { mutableFloatStateOf(Float.MAX_VALUE) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CoverCollageHeader(urls = listOf(coverUrl)) {
                        Text(
                            text = album.title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.onGloballyPositioned { titleBottom = it.boundsInRoot().bottom }
                        )
                        Text(
                            text = album.artist,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenArtist(album.artist) }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        val totalSec = tracks.sumOf { it.durationSeconds.coerceAtLeast(0L) }
                        val info = buildString {
                            append("Album · ${tracks.size} lagu")
                            if (full != null && totalSec > 0) append(" · ${totalSec / 60} mnt")
                        }
                        if (tracks.isNotEmpty()) {
                            Text(text = info, fontSize = 14.sp, color = Sonora.TextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text(
                            text = "Kamu mendengarkan ${minutesLabel(album.listenedMs)} · $monthLabel",
                            fontSize = 13.sp,
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (tracks.isNotEmpty()) {
                        HeroActions(
                            enabled = true,
                            onPlay = { onPlay(tracks, 0) },
                            onShuffle = { onPlay(tracks.shuffled(), 0) }
                        )
                    }
                }
            }

            when {
                loading && full == null -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }

                full != null -> itemsIndexed(tracks, key = { index, t -> "$index-${t.url}" }) { index, song ->
                    val plays = playsByUrl[song.url] ?: playsByTitle[song.title.trim().lowercase()] ?: 0
                    Box(Modifier.padding(start = 16.dp, end = 4.dp)) {
                        AlbumTrackRow(
                            number = index + 1,
                            song = song,
                            plays = plays,
                            isCurrent = song.url == currentSongUrl,
                            isPlaying = isPlaying,
                            onClick = { onPlay(tracks, index) }
                        )
                    }
                }

                else -> {
                    item {
                        Text(
                            text = "Tracklist lengkap tidak bisa dimuat. Yang tampil hanya lagu album ini yang sudah kamu dengar.",
                            fontSize = 13.sp,
                            color = Muted,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    itemsIndexed(listened, key = { _, t -> t.song.url }) { index, item ->
                        Box(Modifier.padding(start = 16.dp, end = 4.dp)) {
                            TopSongRow(
                                rank = index + 1,
                                item = item,
                                isCurrent = item.song.url == currentSongUrl,
                                isPlaying = isPlaying,
                                onClick = { onPlay(tracks, index) }
                            )
                        }
                    }
                }
            }
        }

        HeroTopBars(
            title = album.title,
            listState = listState,
            titleBottom = titleBottom,
            backdrop = backdrop,
            onBack = onBack
        )
    }
}

@Composable
private fun AlbumTrackRow(
    number: Int,
    song: Song,
    plays: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val songMenu = LocalSongMenu.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$number",
            fontSize = 15.sp,
            color = Muted,
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
            ArtistLine(song.url) { mod ->
                Text(
                    text = if (plays > 0) "${song.artist} · $plays ${if (plays == 1) "Play" else "Plays"}" else song.artist,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = mod
                )
            }
        }
        // Ritme lagu aktif, di sebelah kiri durasi: bergerak saat diputar, diam saat dijeda
        if (isCurrent) {
            PlayingBars(isPlaying = isPlaying, modifier = Modifier.padding(start = 12.dp))
        }
        if (song.durationSeconds > 0) {
            Text(
                text = "%d:%02d".format(song.durationSeconds / 60, song.durationSeconds % 60),
                fontSize = 13.sp,
                color = Muted,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        IconButton(onClick = { songMenu.show(song) }) {
            Icon(AppIcons.MoreVert, contentDescription = "Opsi")
        }
    }
}