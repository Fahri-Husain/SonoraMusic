package com.fahrii.musicapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Tinggi maksimum tiap batang (0..1): tengah paling tinggi, pinggir lebih pendek -> bentuk gelombang suara
private val BarPeaks = listOf(0.55f, 0.85f, 1f, 0.8f, 0.5f)
private val BarDurations = listOf(520, 430, 610, 470, 560)

/**
 * Indikator "ritme": lima batang tipis berujung bulat, simetris terhadap garis tengah,
 * bergerak halus saat lagu diputar. Batang paling pendek tetap berbentuk titik bulat.
 */
@Composable
fun PlayingBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Sonora.Accent
) {
    val transition = rememberInfiniteTransition(label = "playingBars")
    val levels = BarDurations.mapIndexed { i, duration ->
        transition.animateFloat(
            initialValue = 0.22f,
            targetValue = BarPeaks[i],
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 85)
            ),
            label = "bar$i"
        )
    }
    val brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)))

    Canvas(modifier = modifier.size(width = 22.dp, height = 16.dp)) {
        val barWidth = 2.5.dp.toPx()
        val gap = (size.width - barWidth * levels.size) / (levels.size - 1)
        levels.forEachIndexed { i, level ->
            // Nilai animasi dibaca di sini (fase gambar), jadi tidak memicu recompose tiap frame
            val fraction = if (isPlaying) level.value else 0.3f
            val barHeight = size.height * fraction
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

/** Pengganti thumbnail: kotak seukuran cover berisi ritme di tengahnya, untuk lagu yang sedang aktif. */
@Composable
fun PlayingThumb(isPlaying: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        PlayingBars(isPlaying = isPlaying)
    }
}