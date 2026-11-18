package com.fahrii.musicapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Header ala referensi: cover memenuhi lebar layar (sampai ke bawah status bar), memudar ke warna
 * background, dan judul/info ditumpuk di atas bagian bawah cover.
 * 4 url atau lebih -> kolase 2x2 tanpa sela; kurang dari itu -> satu cover penuh.
 */
@Composable
fun CoverCollageHeader(
    urls: List<String?>,
    modifier: Modifier = Modifier,
    overlay: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.88f)
            .background(Sonora.SurfaceHigh)
    ) {
        if (urls.size >= 4) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageTile(urls[0], Modifier.weight(1f).fillMaxHeight())
                    CollageTile(urls[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageTile(urls[2], Modifier.weight(1f).fillMaxHeight())
                    CollageTile(urls[3], Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else if (urls.firstOrNull() != null) {
            CollageTile(urls.first(), Modifier.fillMaxSize())
        }

        // Pudar ke background: bening di separuh atas, pekat di tepi bawah
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.70f to Sonora.Background.copy(alpha = 0.65f),
                        1f to Sonora.Background
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = overlay
        )
    }
}

@Composable
private fun CollageTile(url: String?, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

/** Baris aksi: bulatan Acak, pil Putar (lebar), seperti di referensi. */
@Composable
fun HeroActions(
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(enabled = enabled, onClick = onShuffle),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Shuffle, contentDescription = "Acak", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .width(200.dp)
                .height(54.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = enabled, onClick = onPlay),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AppIcons.Play, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Putar", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Bar atas melayang untuk halaman dengan [CoverCollageHeader] (sama seperti Pilihan Cepat/Artis):
 * - bulatan kaca tombol kembali ikut bergeser naik bersama isi saat di-scroll;
 * - setelah judul di header mepet status bar, muncul bar kaca blur berisi panah kembali + judul.
 * [titleBottom] = batas bawah judul di header (boundsInRoot().bottom).
 */
@Composable
fun BoxScope.HeroTopBars(
    title: String,
    listState: LazyListState,
    titleBottom: Float,
    backdrop: GlassBackdrop,
    onBack: () -> Unit
) {
    val statusBarBottom = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val titleBottomState = rememberUpdatedState(titleBottom)
    val titleCovered by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || titleBottomState.value <= statusBarBottom }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (titleCovered) 1f else 0f,
        animationSpec = tween(180),
        label = "heroBar"
    )

    // Bar atas: kaca blur + panah kembali + judul
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
                text = title,
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

    // Tombol kembali di header: bulatan kaca yang bergeser naik bersama isi (tidak menempel di layar)
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