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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.R
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.ArtistResult
import com.fahrii.musicapp.data.Song

/** Jumlah lagu yang tampil di halaman genre; sisanya ada di halaman daftar lengkap (panah kanan). */
private const val SONG_PREVIEW = 5

/**
 * Halaman satu genre: header cover (kolase dari lagu teratas) dengan nama genre dan warna aksennya,
 * tombol Acak/Putar, lalu rak Lagu populer, Artis populer, dan Album. Artis dan album membuka
 * halaman artis/album yang sudah ada.
 */
@Composable
fun GenreScreen(
    state: GenreUiState,
    accent: Color,
    currentSongUrl: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (ArtistAlbum) -> Unit,
    onSeeAllSongs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs = state.songs
    val listState = rememberLazyListState()
    val backdrop = rememberGlassBackdrop()
    var titleBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CoverCollageHeader(urls = songs.take(4).map { it.thumbnailUrl }) {
                        Text(
                            text = "Genre",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                        Text(
                            text = state.name,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.onGloballyPositioned { titleBottom = it.boundsInRoot().bottom }
                        )
                        if (songs.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${songs.size} lagu populer",
                                fontSize = 14.sp,
                                color = Sonora.TextSecondary
                            )
                        }
                    }
                    HeroActions(
                        enabled = songs.isNotEmpty(),
                        onPlay = { onPlay(songs, 0) },
                        onShuffle = { onPlay(songs.shuffled(), 0) }
                    )
                }
            }

            // ---- Lagu populer ----
            when {
                state.loading -> item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Memuat lagu…", fontSize = 14.sp, color = Sonora.TextSecondary)
                    }
                }

                state.failed || songs.isEmpty() -> item {
                    Text(
                        text = "Lagu genre ini tidak bisa dimuat. Periksa koneksi internet.",
                        fontSize = 14.sp,
                        color = Sonora.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp)
                    )
                }

                else -> {
                    item {
                        ShelfTitle(
                            title = "Lagu populer",
                            // Panah kanan hanya kalau memang ada lagu lain yang belum tampil
                            onSeeAll = if (songs.size > SONG_PREVIEW) onSeeAllSongs else null
                        )
                    }
                    itemsIndexed(songs.take(SONG_PREVIEW), key = { index, song -> "$index-${song.url}" }) { index, song ->
                        TrackRow(
                            number = index + 1,
                            song = song,
                            isCurrent = song.url == currentSongUrl,
                            isPlaying = isPlaying,
                            // Antrian tetap seluruh daftar, jadi lagu berikutnya lanjut walau rak dipersingkat
                            onClick = { onPlay(songs, index) }
                        )
                    }
                }
            }

            // ---- Artis populer ----
            if (state.artists.isNotEmpty()) {
                item {
                    Column {
                        ShelfTitle(title = "Artis populer", modifier = Modifier.padding(top = 16.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(state.artists, key = { _, artist -> artist.name }) { _, artist ->
                                ArtistBubble(artist, onClick = { onOpenArtist(artist.name) })
                            }
                        }
                    }
                }
            }

            // ---- Album ----
            if (state.albumsLoading || state.albums.isNotEmpty()) {
                item {
                    Column {
                        ShelfTitle(title = "Album", modifier = Modifier.padding(top = 16.dp))
                        if (state.albums.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Memuat album…", fontSize = 14.sp, color = Sonora.TextSecondary)
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(state.albums, key = { index, album -> "$index-${album.url}" }) { _, album ->
                                    AlbumTile(album, onClick = { onOpenAlbum(album) })
                                }
                            }
                        }
                    }
                }
            }
        }

        HeroTopBars(
            title = state.name,
            listState = listState,
            titleBottom = titleBottom,
            backdrop = backdrop,
            onBack = onBack
        )
    }
}

@Composable
private fun ShelfTitle(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        // Panah kanan yang sama dengan di Beranda dan Replay
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

/** Artis berupa foto bulat dengan nama di bawahnya; ketuk untuk membuka halaman artis. */
@Composable
private fun ArtistBubble(artist: ArtistResult, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Sonora.SurfaceHigh)
                .border(1.dp, Sonora.Border, CircleShape)
        )
        Text(
            text = artist.name,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            // Tinggi selalu dua baris, walau nama artisnya satu baris, supaya rak Album di bawahnya tidak bergeser
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}