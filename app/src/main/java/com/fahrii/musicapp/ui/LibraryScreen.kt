package com.fahrii.musicapp.ui

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.LibraryState
import com.fahrii.musicapp.data.PlayedSong
import com.fahrii.musicapp.data.Playlist
import com.fahrii.musicapp.data.Song

private val PillSelected = Color.White
private val AccentFavorite = Color(0xFFFF7A9C)
private val AccentReplay = Color(0xFFFFD166)
private val AccentMostPlayed = Color(0xFF6FB5FF)
private val AccentDownloads = Color(0xFF7CE08A)

/** Jumlah lagu riwayat yang ditampilkan di halaman Koleksi (daftar lengkap ada di Replay). */
private const val HISTORY_PREVIEW = 30

/** Jumlah kolom di grid playlist. */
private const val PLAYLIST_COLUMNS = 3

@Composable
fun LibraryScreen(
    library: LibraryState,
    history: List<PlayedSong>,
    currentSongUrl: String?,
    isPlaying: Boolean,
    onOpenFavorites: () -> Unit,
    onOpenReplay: () -> Unit,
    onOpenMostPlayed: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlayHistory: (List<Song>, Int) -> Unit,
    onRemoveHistory: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreate by remember { mutableStateOf(false) }
    // 0 = Your Library, 1 = Your Playlist
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val recent = remember(history) { history.take(HISTORY_PREVIEW).map { it.song } }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = LocalBottomInset.current)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Koleksi",
                    fontSize = 20.sp, // sama dengan teks "SonoraMusic" di beranda
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LibraryPill("Your Library", selected = tab == 0) { tab = 0 }
                LibraryPill("Your Playlist", selected = tab == 1) { tab = 1 }
            }
        }

        if (tab == 0) {
            item {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CollectionCard(
                            title = "Favorit",
                            accent = AccentFavorite,
                            icon = AppIcons.HeartFilled,
                            onClick = onOpenFavorites,
                            modifier = Modifier.weight(1f)
                        )
                        CollectionCard(
                            title = "Replay",
                            accent = AccentReplay,
                            icon = AppIcons.Replay,
                            onClick = onOpenReplay,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CollectionCard(
                            title = "Paling Sering Diputar",
                            accent = AccentMostPlayed,
                            icon = AppIcons.Trending,
                            onClick = onOpenMostPlayed,
                            modifier = Modifier.weight(1f)
                        )
                        CollectionCard(
                            title = "Diunduh",
                            accent = AccentDownloads,
                            icon = AppIcons.Download,
                            onClick = onOpenDownloads,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
                )
            }

            if (recent.isEmpty()) {
                item {
                    Text(
                        text = "Belum ada riwayat. Lagu yang kamu putar akan muncul di sini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                itemsIndexed(recent, key = { _, song -> song.url }) { index, song ->
                    HistoryRow(
                        song = song,
                        isCurrent = song.url == currentSongUrl,
                        isPlaying = isPlaying,
                        onClick = { onPlayHistory(recent, index) },
                        onRemove = { onRemoveHistory(song) }
                    )
                }
            }
        } else {
            // Grid 3 kolom: tile "+" di dalam kotak jadi sel pertama, playlist berikutnya
            // mengisi kolom lalu turun ke baris di bawahnya.
            val cells: List<Playlist?> = listOf<Playlist?>(null) + library.playlists
            val rows = cells.chunked(PLAYLIST_COLUMNS)
            items(rows, key = { row -> row.first()?.id ?: "create" }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { playlist ->
                        if (playlist == null) {
                            PlaylistTile(
                                title = "Buat playlist",
                                subtitle = null,
                                onClick = { showCreate = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        AppIcons.Plus,
                                        contentDescription = "Playlist baru",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        } else {
                            PlaylistTile(
                                title = playlist.name,
                                subtitle = "Kamu",
                                onClick = { onOpenPlaylist(playlist.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                val cover = playlist.thumbnailPath?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                                    ?: playlist.songs.firstOrNull()?.thumbnailUrl
                                if (cover != null) {
                                    AsyncImage(
                                        model = cover,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(AppIcons.MusicNote, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }
                    // Baris terakhir yang belum penuh: isi sisa kolom supaya ukuran tile tetap sama
                    repeat(PLAYLIST_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // Teks merek di tengah layar, di bawah grid playlist
            item(key = "brand-footer") {
                Spacer(Modifier.height(28.dp))
                BrandFooter()
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Playlist baru",
            confirmText = "Buat",
            onConfirm = {
                onCreatePlaylist(it)
                showCreate = false
                tab = 1
            },
            onDismiss = { showCreate = false }
        )
    }
}

@Composable
private fun LibraryPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(PillSelected)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.Black else Color.White
        )
    }
}

@Composable
private fun CollectionCard(
    title: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Gaya sama dengan GenrePill di Beranda: latar gelap + garis tepi gradien aksen
    val shape = RoundedCornerShape(12.dp)
    val borderBrush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.25f)))

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Sonora.Surface)
            .border(1.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HistoryRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent && isPlaying) {
            PlayingThumb(isPlaying = true, size = 48.dp)
        } else {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(AppIcons.MoreVert, contentDescription = "Opsi")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Hapus dari riwayat") },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    }
                )
            }
        }
    }
}

/** Satu sel di grid playlist: cover persegi, nama di bawahnya, lalu teks kecil (opsional). */
@Composable
private fun PlaylistTile(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            cover()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}