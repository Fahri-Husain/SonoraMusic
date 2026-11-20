package com.fahrii.musicapp.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fahrii.musicapp.R
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.ArtistResult
import com.fahrii.musicapp.data.Song
import androidx.compose.foundation.layout.PaddingValues

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
    currentSongUrl: String? = null,
    isPlaying: Boolean = false,
    onSongClick: (List<Song>, Int) -> Unit = { _, _ -> },
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (ArtistAlbum) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    // Kotak difokuskan + kosong -> riwayat; difokuskan + ada ketikan -> saran lagu
    var searchFocused by remember { mutableStateOf(false) }
    val showHistory = searchFocused && state.query.isBlank()
    val showSuggestions = searchFocused && state.query.isNotBlank()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val view = LocalView.current
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Ketuk area kosong: keyboard menutup, garis ketik hilang. Kalau kotak sedang aktif
            // (riwayat atau saran tampil), pencarian juga direset total: ketikan, hasil, dan saran
            // dihapus. Saat hanya melihat hasil, ketukan di sela-sela daftar tidak menghapus apa pun.
            // Ketukan pada kotak, tombol, atau baris sudah diproses anak-anaknya, jadi tidak sampai ke sini.
            .pointerInput(searchFocused) {
                detectTapGestures(onTap = {
                    if (searchFocused) viewModel.clearSearch()
                    dismissKeyboard()
                })
            }
            .padding(horizontal = 16.dp)
    ) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = {
                dismissKeyboard()
                viewModel.search(saveToHistory = true)
            },
            onFocusChange = { searchFocused = it },
            onClear = viewModel::clearSearch,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showHistory -> HistoryList(
                    entries = history,
                    onPick = { text ->
                        dismissKeyboard()
                        viewModel.onQueryChange(text)
                        viewModel.search(saveToHistory = true)
                    },
                    onRemove = viewModel::removeFromHistory,
                    onClearAll = viewModel::clearHistory
                )

                showSuggestions -> SuggestionList(
                    query = state.query,
                    suggestions = state.suggestions,
                    loading = state.suggestionsLoading,
                    currentSongUrl = currentSongUrl,
                    isPlaying = isPlaying,
                    onSearchAll = {
                        dismissKeyboard()
                        viewModel.search(saveToHistory = true)
                    },
                    onPick = { index ->
                        val song = state.suggestions[index]
                        // Judul lagu yang dipilih masuk riwayat; hasil saran juga jadi hasil pencarian
                        viewModel.addToHistory(song.title)
                        dismissKeyboard()
                        viewModel.search()
                        onSongClick(state.suggestions, index)
                    }
                )

                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                state.error != null -> Text(
                    text = state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )

                state.hasSearched && state.results.isEmpty() && state.artists.isEmpty() && state.albums.isEmpty() -> Text(
                    text = "Lagu tidak ditemukan",
                    modifier = Modifier.align(Alignment.Center)
                )

                // Belum ada pencarian: tampilan awal berisi petunjuk, supaya halaman tidak kosong
                !state.hasSearched && state.results.isEmpty() -> SearchHint(
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 80.dp)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomInset.current),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Artis di atas lagu: foto bulat + nama, ketuk untuk membuka halaman artis
                    if (state.artists.isNotEmpty()) {
                        item(key = "header-artists") { SectionTitle("Artis") }
                        items(state.artists, key = { "artist-${it.name}" }) { artist ->
                            ArtistRow(
                                artist = artist,
                                onClick = {
                                    viewModel.addToHistory(artist.name)
                                    dismissKeyboard()
                                    onArtistClick(artist.name)
                                }
                            )
                        }
                    }
                    // Judul "Lagu" hanya perlu kalau ada bagian lain (artis/album) yang menyertainya
                    if (state.results.isNotEmpty() && (state.artists.isNotEmpty() || state.albums.isNotEmpty())) {
                        item(key = "header-songs") { SectionTitle("Lagu") }
                    }
                    itemsIndexed(
                        state.results,
                        key = { index, song -> "$index-${song.url}" }
                    ) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.url == currentSongUrl,
                            isPlaying = isPlaying,
                            onClick = {
                                dismissKeyboard()
                                onSongClick(state.results, index)
                            }
                        )
                    }

                    // Album di bawah lagu: sampul + judul + artis, ketuk untuk membuka halaman album
                    if (state.albums.isNotEmpty()) {
                        item(key = "header-albums") { SectionTitle("Album") }
                        items(state.albums, key = { "album-${it.url}" }) { album ->
                            AlbumRow(
                                album = album,
                                onClick = {
                                    dismissKeyboard()
                                    onAlbumClick(album)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kotak pencarian di atas halaman: ikon cari di kiri, teks/placeholder di sebelahnya,
 * dan ikon X di kanan yang hanya muncul saat sudah ada ketikan (ketuk untuk menghapus).
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium
    )

    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Sonora.SurfaceHigh)
            // Ketuk di mana pun di dalam kotak (ikon, sela-sela) mengaktifkan kolom ketik,
            // jadi tidak dianggap ketukan di area kosong
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusRequester.requestFocus() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppIcons.Search,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(start = 20.dp).size(24.dp)
        )

        Box(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Cari lagu atau artis",
                    style = textStyle,
                    color = Sonora.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) }
            )
        }

        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear),
                    contentDescription = "Hapus",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/** Satu artis di hasil pencarian: foto profil bulat, nama, dan label "Artis". */
@Composable
private fun ArtistRow(artist: ArtistResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Sonora.SurfaceHigh),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Artis",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Satu album di hasil pencarian: sampul persegi, judul album, dan label "Album · artis". */
@Composable
private fun AlbumRow(album: ArtistAlbum, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Sonora.SurfaceHigh),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (album.artist.isBlank()) "Album" else "Album · ${album.artist}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Tampilan awal halaman Cari sebelum ada pencarian. */
@Composable
private fun SearchHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = AppIcons.Search,
            contentDescription = null,
            tint = Sonora.TextSecondary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Temukan musik favoritmu",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Cari lagu atau artis, lalu putar langsung dari sini.",
            fontSize = 14.sp,
            color = Sonora.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** Jumlah lagu yang tampil sebagai saran saat mengetik. */
private const val MAX_SUGGESTIONS = 6

/** Jumlah riwayat yang ditampilkan; sisanya tetap tersimpan, tapi layar disisakan kosong di bawahnya. */
private const val MAX_HISTORY_SHOWN = 7

/** Riwayat pencarian: ketuk untuk mencari lagi, X untuk menghapus satu, "Hapus semua" untuk semuanya. */
@Composable
private fun HistoryList(
    entries: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    if (entries.isEmpty()) {
        Text(
            text = "Belum ada riwayat pencarian",
            fontSize = 14.sp,
            color = Sonora.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
        )
        return
    }

    // Tinggi mengikuti isi (tidak memenuhi layar): sisa layar di bawah daftar jadi area kosong
    // yang bisa diketuk untuk mereset pencarian
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pencarian terakhir",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Hapus semua",
                    fontSize = 13.sp,
                    color = Sonora.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        items(entries.take(MAX_HISTORY_SHOWN), key = { it }) { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(query) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.History,
                    contentDescription = null,
                    tint = Sonora.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp)
                )
                Text(
                    text = query,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(query) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear),
                        contentDescription = "Hapus dari riwayat",
                        tint = Sonora.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Saran saat mengetik, seperti Spotify: baris pertama untuk mencari kata kunci itu,
 * di bawahnya beberapa lagu yang cocok (ketuk untuk langsung memutar).
 */
@Composable
private fun SuggestionList(
    query: String,
    suggestions: List<Song>,
    loading: Boolean,
    currentSongUrl: String?,
    isPlaying: Boolean,
    onSearchAll: () -> Unit,
    onPick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSearchAll)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = null,
                    tint = Sonora.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp)
                )
                Text(
                    text = "Cari \"${query.trim()}\"",
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 16.dp)
                )
            }
        }

        if (loading && suggestions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }

        itemsIndexed(
            suggestions.take(MAX_SUGGESTIONS),
            key = { index, song -> "$index-${song.url}" }
        ) { index, song ->
            SongRow(
                song = song,
                isCurrent = song.url == currentSongUrl,
                isPlaying = isPlaying,
                onClick = { onPick(index) }
            )
        }
    }
}

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent && isPlaying) {
            PlayingThumb(isPlaying = isPlaying, size = 56.dp)
        } else {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
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
            ArtistLine(song.url) { mod ->
                Text(
                    text = artistLabel(song),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = mod.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(song.durationSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds < 0) return ""
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}