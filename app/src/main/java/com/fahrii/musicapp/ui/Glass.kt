package com.fahrii.musicapp.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.graphics.RectangleShape

// Satu tempat untuk nama tipe, supaya kalau import library beda cukup dibetulkan di file ini
typealias GlassBackdrop = LayerBackdrop

/**
 * Dibuat sekali di AppRoot: gambar halaman di belakang yang akan "dibiaskan" oleh kaca. Dasarnya
 * mengikuti warna latar saat ini ([LocalSonoraBackground]), jadi kaca ikut bertema sampul.
 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val background = LocalSonoraBackground.current
    return rememberLayerBackdrop {
        drawRect(background())
        drawContent()
    }
}

/** Tandai halaman sebagai sumber gambar untuk kaca. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = layerBackdrop(backdrop)

/** Kaca cair: saturasi warna naik, blur ringan, dan lensa yang membiaskan pinggirnya. */
fun Modifier.glass(backdrop: GlassBackdrop, shape: Shape): Modifier = this
    .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(4f.dp.toPx())
            lens(16f.dp.toPx(), 32f.dp.toPx())
        },
        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.22f)) }
    )
    .clip(shape)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.40f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        ),
        shape = shape
    )


/**
 * Blur untuk bar atas: tanpa border dan lensa. Pekat sampai [fadeHeight] terakhir, lalu memudar
 * halus ke bawah, jadi tidak ada garis tepi keras di bawah bar.
 */
fun Modifier.glassFadeBar(backdrop: GlassBackdrop, fadeHeight: Dp = 20.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = (size.height - fadeHeight.toPx()).coerceAtLeast(0f),
                endY = size.height
            ),
            blendMode = BlendMode.DstIn
        )
    }
    .drawBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            vibrancy()
            blur(14f.dp.toPx())
        },
        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.30f)) }
    )