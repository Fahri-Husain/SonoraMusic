package com.fahrii.musicapp.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.DownloadStatus
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.Playlist
import com.fahrii.musicapp.data.Song
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SortMode(val label: String) {
    CUSTOM("Custom order"),
    TITLE("Title"),
    ARTIST("Artist")
}

/**
 * Halaman playlist: cover penuh dengan judul di atasnya, tombol acak/putar/unduh, pengurutan,
 * lalu daftar lagu. Titik tiga di kanan atas membuka menu playlist, titik tiga di tiap lagu
 * membuka menu lagu.
 */
@Composable
fun PlaylistScreen(
    playlist: Playlist,
    playlists: List<Playlist>,
    likedUrls: Set<String>,
    songAlbums: Map<String, String>,
    currentSongUrl: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onMoveSong: (from: Int, to: Int) -> Unit,
    onRename: (String) -> Unit,
    onSetThumbnail: (Uri) -> Unit,
    onDeletePlaylist: () -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAddToPlaylist: (Playlist, Song) -> Unit,
    onRemoveFromPlaylist: (Playlist, Song) -> Unit,
    onRequestAlbum: (Song) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (ArtistAlbum) -> Unit,
    modifier: Modifier = Modifier,
    downloadedUrls: Set<String> = emptySet(),
    downloadingUrls: Set<String> = emptySet(),
    onDownloadSong: (Song) -> Unit = {},
    onDownloadAll: (List<Song>) -> Unit = {},
    onRemoveDownload: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val songs = playlist.songs
    val listState = rememberLazyListState()
    // Backdrop lokal: hanya daftar/hero yang jadi sumber blur, bar dan tombol kaca ada di luarnya
    val backdrop = rememberGlassBackdrop()
    var titleBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    var sortMode by remember { mutableStateOf(SortMode.CUSTOM) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var showPlaylistMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSetThumbnail(uri)
    }

    // Daftar yang tampil: hasil cari, lalu diurutkan sesuai pilihan
    val shown = remember(songs, sortMode, query) {
        val filtered = if (query.isBlank()) songs else songs.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
        when (sortMode) {
            SortMode.CUSTOM -> filtered
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
        }
    }
    // Geser urutan hanya masuk akal untuk urutan kustom dan daftar utuh (tanpa filter)
    val canReorder = sortMode == SortMode.CUSTOM && query.isBlank()
    LaunchedEffect(canReorder) { if (!canReorder) reorderMode = false }

    // Satu cover penuh (bukan kolase): thumbnail pilihan pengguna, atau cover lagu pertama
    val coverUrls = remember(playlist.thumbnailPath, songs) {
        val custom = playlist.thumbnailPath
        listOf(
            if (custom != null) Uri.fromFile(File(custom)).toString()
            else songs.firstOrNull()?.thumbnailUrl
        )
    }

    // Latar halaman: gradasi dari warna dominan cover (gelap), bukan hitam polos. Nilai animasinya
    // sengaja dibaca di dalam draw/item saja supaya seluruh layar tidak ikut recompose tiap frame.
    // Cover yang belum terbaca atau abu-abu tidak menghasilkan gradasi hitam: pakai warna tema saat ini
    val palette = LocalSonoraPalette.current
    val dominant = rememberDominantColor(coverUrls)?.takeUnless { it.isGrayish() }
    val tintTop = animateColorAsState(
        targetValue = dominant?.asBackdrop(0.26f) ?: palette.surfaceHigh,
        animationSpec = tween(600),
        label = "playlistTintTop"
    )
    val tintBottom = animateColorAsState(
        targetValue = dominant?.asBackdrop(0.12f) ?: palette.background,
        animationSpec = tween(600),
        label = "playlistTintBottom"
    )

    // Cari nama album lagu yang menunya dibuka; hasilnya muncul sebagai baris album di menu
    LaunchedEffect(menuSong) { menuSong?.let(onRequestAlbum) }

    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) runCatching { searchFocus.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                // Digambar di dalam sumber blur, jadi kaca bar atas/bawah ikut berwarna sama
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(tintTop.value, tintBottom.value)))
                },
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)
        ) {
            item(key = "hero") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CoverCollageHeader(
                        urls = coverUrls,
                        // Tepi bawah cover kira-kira di tengah layar, jadi pudar ke warna latar di titik itu
                        fadeColor = lerp(tintTop.value, tintBottom.value, 0.5f)
                    ) {
                        Text(
                            text = playlist.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.onGloballyPositioned { titleBottom = it.boundsInRoot().bottom }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Playlist Kamu",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        if (playlist.createdAt > 0L) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Dibuat pada ${formatCreated(playlist.createdAt)}",
                                fontSize = 15.sp,
                                color = Sonora.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    HeroActions(
                        enabled = shown.isNotEmpty(),
                        onPlay = { onPlay(shown, 0) },
                        onShuffle = { onPlay(shown.shuffled(), 0) },
                        playLabel = "Play",
                        onDownload = { onDownloadAll(shown) }
                    )
                }
            }

            item(key = "info") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(
                        text = "${songs.size} trek",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable { sortMenuOpen = true }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.Sort, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Sort by: ${sortMode.label}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            sortMode = mode
                                            sortMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (reorderMode) "Done" else "Change order",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .alpha(if (songs.size > 1) 1f else 0.4f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = songs.size > 1) {
                                    if (reorderMode) {
                                        reorderMode = false
                                    } else {
                                        // Geser urutan hanya berlaku di urutan kustom tanpa pencarian
                                        sortMode = SortMode.CUSTOM
                                        query = ""
                                        searching = false
                                        reorderMode = true
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (songs.isEmpty()) "Belum ada lagu" else "Lagu tidak ditemukan",
                            color = Sonora.TextSecondary
                        )
                    }
                }
            } else {
                itemsIndexed(shown, key = { index, song -> "$index-${song.url}" }) { index, song ->
                    PlaylistTrackRow(
                        song = song,
                        isCurrent = song.url == currentSongUrl,
                        isPlaying = isPlaying,
                        reorder = reorderMode,
                        canMoveUp = index > 0,
                        canMoveDown = index < shown.lastIndex,
                        onClick = { onPlay(shown, index) },
                        onMore = { menuSong = song },
                        onMoveUp = { onMoveSong(index, index - 1) },
                        onMoveDown = { onMoveSong(index, index + 1) }
                    )
                }
            }
        }

        // Bar atas: tombol kembali + judul (muncul setelah judul di cover mepet status bar)
        HeroTopBars(
            title = if (searching) "" else playlist.name,
            listState = listState,
            titleBottom = titleBottom,
            backdrop = backdrop,
            onBack = onBack,
            trailingInset = 96.dp
        )

        if (searching) {
            // Kolom cari menggantikan pil Cari + Opsi, melebar dari kanan sampai dekat tombol kembali
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 60.dp, end = 12.dp, top = 8.dp)
                    .height(40.dp)
            ) {
                Box(modifier = Modifier.matchParentSize().glass(backdrop, CircleShape))
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(start = 14.dp).size(18.dp)
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .focusRequester(searchFocus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text("Cari di playlist", color = Sonora.TextSecondary, fontSize = 15.sp)
                                }
                                inner()
                            }
                        }
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                searching = false
                                query = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Close, contentDescription = "Tutup pencarian", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else {
            // Pil kaca di kanan atas: Cari + titik tiga (menu playlist)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp, top = 8.dp)
                    .height(40.dp)
                    .width(88.dp)
            ) {
                Box(modifier = Modifier.matchParentSize().glass(backdrop, CircleShape))
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { searching = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Search, contentDescription = "Cari", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { showPlaylistMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.MoreVert, contentDescription = "Opsi", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showPlaylistMenu) {
        PlaylistOptionsSheet(
            onDismiss = { showPlaylistMenu = false },
            onEditTitle = { showRename = true },
            onEditThumbnail = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onAddToQueue = {
                if (songs.isEmpty()) {
                    toast("Playlist masih kosong")
                } else {
                    onAddToQueue(songs)
                    toast("Ditambahkan ke daftar antri")
                }
            },
            onSyncToYouTubeMusic = { toast("Sinkronisasi ke YouTube Music belum tersedia") },
            onDeletePlaylist = { confirmDelete = true }
        )
    }

    menuSong?.let { song ->
        SongOptionsSheet(
            song = song,
            liked = song.url in likedUrls,
            albumName = songAlbums[song.url]?.trim()?.takeIf { it.isNotEmpty() },
            onDismiss = { menuSong = null },
            remove = SongMenuRemove("Menghapus lagu ini dari daftar putar") { onRemoveSong(song) },
            onToggleLike = { onToggleLike(song) },
            downloadStatus = when {
                song.url in downloadedUrls -> DownloadStatus.Downloaded
                song.url in downloadingUrls -> DownloadStatus.Downloading
                else -> DownloadStatus.None
            },
            onDownload = { onDownloadSong(song) },
            onRemoveDownload = {
                onRemoveDownload(song)
                toast("Unduhan dihapus")
            },
            onAddToPlaylist = { addToPlaylistSong = song },
            onPlayNext = {
                onPlayNext(song)
                toast("Akan diputar selanjutnya")
            },
            onAddToQueue = {
                onAddToQueue(listOf(song))
                toast("Ditambahkan ke daftar antri")
            },
            onOpenArtist = { onOpenArtist(song.artist) },
            onOpenAlbum = {
                val album = songAlbums[song.url]?.trim().orEmpty()
                if (album.isNotEmpty()) {
                    onOpenAlbum(ArtistAlbum(title = album, artist = song.artist, coverUrl = null, url = ""))
                }
            },
            onPlayRadio = { toast("Fitur radio belum tersedia") },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}\n${song.url}")
                }
                context.startActivity(Intent.createChooser(send, "Bagikan"))
            }
        )
    }

    addToPlaylistSong?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = playlists,
            onAdd = { onAddToPlaylist(it, song) },
            onRemove = { onRemoveFromPlaylist(it, song) },
            onDismiss = { addToPlaylistSong = null }
        )
    }

    if (showRename) {
        NameDialog(
            title = "Edit judul",
            confirmText = "Simpan",
            initialName = playlist.name,
            onConfirm = {
                onRename(it)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus playlist?") },
            text = { Text("Playlist \"${playlist.name}\" akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeletePlaylist()
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    reorder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !reorder, onClick = onClick)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCurrent && isPlaying) {
                PlayingThumb(isPlaying = isPlaying, size = 52.dp)
            } else {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Sonora.Surface)
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
                ArtistLine(song.url) { mod ->
                    Text(
                        text = artistLabel(song),
                        fontSize = 13.sp,
                        color = Sonora.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = mod.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
            }
            if (reorder) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = AppIcons.MoveUp,
                        contentDescription = "Naikkan",
                        modifier = Modifier.size(20.dp).alpha(if (canMoveUp) 1f else 0.3f)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = AppIcons.MoveDown,
                        contentDescription = "Turunkan",
                        modifier = Modifier.size(20.dp).alpha(if (canMoveDown) 1f else 0.3f)
                    )
                }
            } else {
                IconButton(onClick = onMore) {
                    Icon(AppIcons.MoreVert, contentDescription = "Opsi")
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = Color.White.copy(alpha = 0.08f)
        )
    }
}

private fun formatCreated(millis: Long): String =
    SimpleDateFormat("HH:mm - d MMMM yyyy", Locale.forLanguageTag("id-ID")).format(Date(millis))