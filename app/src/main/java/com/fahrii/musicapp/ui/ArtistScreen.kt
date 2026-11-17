package com.fahrii.musicapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.Song
import java.util.Locale

/** Foto artis dibuat hitam-putih seperti mockup; ubah ke false untuk warna asli. */
private const val HERO_GRAYSCALE = false

private val HeroImageHeight = 420.dp
private val BioColor = Color(0xFFD0D0D5)
private val GrayFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Halaman artis: foto besar + nama + bio, tombol Listen/About, daftar lagu populer,
 * lalu (setelah di-scroll ke bawah) album-albumnya.
 */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    currentSongUrl: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    // Backdrop lokal: hanya daftar/hero yang jadi sumber blur, bar kaca ada di luarnya
    val backdrop = rememberGlassBackdrop()
    var bioExpanded by remember { mutableStateOf(false) }

    var titleBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val statusBarBottom = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    // Bar atas baru muncul setelah nama di hero mepet/lewat status bar (atau hero sudah lewat)
    val titleCovered by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || titleBottom <= statusBarBottom }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (titleCovered) 1f else 0f,
        animationSpec = tween(180),
        label = "artistBar"
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)
        ) {
            item {
                ArtistHero(
                    state = state,
                    bioExpanded = bioExpanded,
                    onToggleBio = { bioExpanded = !bioExpanded },
                    onListen = { onPlay(state.songs, 0) },
                    onTitleBottom = { titleBottom = it }
                )
            }

            // Lagu populer
            item { SectionTitle("Popular Songs", topPadding = 8.dp) }
            when {
                state.songs.isNotEmpty() -> {
                    itemsIndexed(state.songs, key = { index, song -> "$index-${song.url}" }) { index, song ->
                        PopularSongRow(
                            number = index + 1,
                            song = song,
                            isCurrent = song.url == currentSongUrl,
                            isPlaying = isPlaying,
                            onClick = { onPlay(state.songs, index) }
                        )
                    }
                }
                state.songsLoading -> item { LoadingRow("Memuat lagu…") }
                else -> item {
                    Text(
                        text = "Lagu artis ini tidak ditemukan.",
                        fontSize = 14.sp,
                        color = Sonora.TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            // Album, muncul setelah daftar lagu
            if (state.albums.isNotEmpty() || state.albumsLoading) {
                item { SectionTitle("Albums", topPadding = 28.dp) }
                item {
                    if (state.albums.isEmpty()) {
                        LoadingRow("Memuat album…")
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(state.albums, key = { index, album -> "$index-${album.url}" }) { _, album ->
                                AlbumTile(album)
                            }
                        }
                    }
                }
            }
        }

        // Bar atas: kaca blur + panah kembali + nama artis, muncul setelah nama di hero mepet status bar
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
                    .padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
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
                    text = state.name,
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

@Composable
private fun ArtistHero(
    state: ArtistUiState,
    bioExpanded: Boolean,
    onToggleBio: () -> Unit,
    onListen: () -> Unit,
    onTitleBottom: (Float) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = HeroImageHeight)) {
        if (state.imageUrl != null) {
            AsyncImage(
                model = state.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                colorFilter = if (HERO_GRAYSCALE) GrayFilter else null,
                modifier = Modifier.fillMaxWidth().height(HeroImageHeight)
            )
        }
        // Atas sedikit digelapkan (di belakang status bar), bawah memudar ke warna background
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.40f to Color.Transparent,
                        0.95f to Sonora.Background
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "ARTIST",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = Sonora.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.name,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.onGloballyPositioned { onTitleBottom(it.boundsInRoot().bottom) }
            )
            if (state.subscribers >= 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${compactCount(state.subscribers)} subscribers",
                    fontSize = 13.sp,
                    color = Sonora.TextSecondary
                )
            }
            if (state.bio != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.bio,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = BioColor,
                    maxLines = if (bioExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArtistPill(
                    text = "Listen",
                    icon = AppIcons.Play,
                    filled = true,
                    enabled = state.songs.isNotEmpty(),
                    onClick = onListen
                )
                ArtistPill(
                    text = "About",
                    icon = null,
                    filled = false,
                    enabled = state.bio != null,
                    onClick = onToggleBio
                )
            }
        }
    }
}

@Composable
private fun ArtistPill(
    text: String,
    icon: ImageVector?,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val background = if (filled) Color.White else Color.White.copy(alpha = 0.08f)
    val content = if (filled) Color.Black else Color.White

    Row(
        modifier = Modifier
            .width(124.dp)
            .height(44.dp)
            .clip(CircleShape)
            .background(background)
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            )
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = content, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 10.dp)
    )
}

@Composable
private fun LoadingRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = Sonora.TextSecondary)
    }
}

@Composable
private fun PopularSongRow(
    number: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val thumbShape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%02d".format(number),
            fontSize = 13.sp,
            color = Sonora.TextSecondary,
            modifier = Modifier.width(30.dp)
        )
        if (isCurrent && isPlaying) {
            PlayingThumb(isPlaying = isPlaying, size = 48.dp)
        } else {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(thumbShape)
                    .background(Sonora.Surface)
                    .border(1.dp, Sonora.Border, thumbShape)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontSize = 13.sp,
                color = Sonora.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumTile(album: ArtistAlbum) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = Modifier.width(140.dp)) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(shape)
                .background(Sonora.SurfaceHigh)
                .border(1.dp, Sonora.Border, shape)
        )
        Text(
            text = album.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = album.artist,
            fontSize = 12.sp,
            color = Sonora.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun compactCount(n: Long): String = when {
    n >= 1_000_000_000L -> "%.1fB".format(Locale.US, n / 1_000_000_000.0)
    n >= 1_000_000L -> "%.1fM".format(Locale.US, n / 1_000_000.0)
    n >= 1_000L -> "%.1fK".format(Locale.US, n / 1_000.0)
    else -> "$n"
}.replace(".0", "")
