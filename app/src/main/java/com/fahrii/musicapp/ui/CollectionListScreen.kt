package com.fahrii.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.fahrii.musicapp.R
import com.fahrii.musicapp.data.Song

/**
 * Layar daftar lagu untuk koleksi (Favorit, Paling Sering Diputar).
 * Header: judul + jumlah trek di kiri, tombol Putar (bulat putih), Acak, dan Cari di kanan.
 * Teks "SonoraMusic" tampil di tengah saat kosong, dan di bawah daftar trek saat ada lagu.
 */
@Composable
fun CollectionListScreen(
    title: String,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onRemove: (Song) -> Unit,
    removeIcon: ImageVector,
    removeTint: Color,
    removeDescription: String,
    modifier: Modifier = Modifier,
    currentSongUrl: String? = null,
    isPlaying: Boolean = false
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val shown = remember(songs, query) {
        val q = query.trim()
        if (q.isEmpty()) songs
        else songs.filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (searching) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { searching = false; query = "" }) {
                    Icon(AppIcons.BackArrow, contentDescription = "Tutup pencarian")
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari di $title") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.BackArrow, contentDescription = "Kembali", modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${songs.size} trek",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onPlay(songs, 0) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Play,
                        contentDescription = "Putar semua",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { onPlay(songs.shuffled(), 0) }, modifier = Modifier.size(44.dp)) {
                    Icon(painterResource(R.drawable.ic_shuffle), contentDescription = "Acak", modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { searching = true }, modifier = Modifier.size(44.dp)) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = "Cari", modifier = Modifier.size(28.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (shown.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 28.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                BrandFooter()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = LocalBottomInset.current)
            ) {
                itemsIndexed(
                    shown,
                    key = { index, song -> "$index-${song.url}" }
                ) { index, song ->
                    CollectionSongRow(
                        song = song,
                        isCurrent = song.url == currentSongUrl,
                        isPlaying = isPlaying,
                        onClick = { onPlay(shown, index) },
                        onRemove = { onRemove(song) },
                        removeIcon = removeIcon,
                        removeTint = removeTint,
                        removeDescription = removeDescription
                    )
                }
                // Teks merek berada di bawah trek lagu terakhir
                item(key = "brand-footer") {
                    Spacer(Modifier.height(20.dp))
                    BrandFooter()
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
internal fun BrandFooter() {
    Text(
        text = "SonoraMusic",
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CollectionSongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    removeIcon: ImageVector,
    removeTint: Color,
    removeDescription: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent && isPlaying) {
            PlayingThumb(isPlaying = isPlaying, size = 44.dp)
        } else {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
            Text(
                text = song.artist,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
        IconButton(onClick = onRemove) {
            Icon(imageVector = removeIcon, contentDescription = removeDescription, tint = removeTint)
        }
    }
}