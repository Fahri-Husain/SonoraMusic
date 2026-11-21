package com.fahrii.musicapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * Latar skeleton: warna permukaan gelap dengan kilau tipis yang bergerak dari kiri ke kanan.
 * Dipakai untuk kotak-kotak penanda selagi data dimuat.
 */
@Composable
fun Modifier.shimmerBackground(shape: Shape = RectangleShape): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    return this
        .clip(shape)
        .drawBehind {
            drawRect(Sonora.Surface)
            val sweep = size.width.coerceAtLeast(1f)
            // Kilau mulai dari luar kiri, lewat seluruh lebar, lalu keluar di kanan
            val x = -sweep + (size.width + sweep) * progress.value
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.09f), Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + sweep, 0f)
                )
            )
        }
}
