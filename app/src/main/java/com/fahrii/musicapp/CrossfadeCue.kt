package com.fahrii.musicapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle

/**
 * Tanda visual crossfade, mengikuti SimpMusic: bar progres berganti warna pelangi dan muncul
 * label "Crossfading" dengan kilau yang bergerak.
 */

/** Lama satu putaran warna pelangi di bar progres. */
private const val RAINBOW_CYCLE_MS = 1_000

/** Lama kilau menyapu label dari kiri ke kanan. */
private const val SWEEP_CYCLE_MS = 3_200

/** Lebar sapuan kilau (px) dan seberapa jauh ia melewati ujung teks sebelum mengulang. */
private const val SHIMMER_SPAN_PX = 140f

/**
 * Warna bar progres. Selama [active] berputar melewati seluruh warna pelangi, setelahnya kembali
 * halus ke [rest]. Animasi putarnya hanya dibuat saat [active], jadi di luar crossfade tidak ada
 * animasi tak berujung yang terus meminta frame.
 */
@Composable
fun rememberCrossfadeTrackColor(active: Boolean, rest: Color = Color.White): Color {
    val target = if (active) rainbowColor() else rest
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(300),
        label = "crossfadeTrackColor"
    )
    return color
}

@Composable
private fun rainbowColor(): Color {
    val transition = rememberInfiniteTransition(label = "crossfadeRainbow")
    val hue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(RAINBOW_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbowHue"
    )
    return Color.hsv(hue, 1f, 1f)
}

/**
 * Label "Crossfading" yang muncul/hilang dengan memudar. Kilaunya dilukis langsung di huruf
 * (brush pada [TextStyle]), jadi tidak butuh lapisan tambahan dan tetap benar berapa pun lebar teksnya.
 *
 * @param baseColor warna dasar huruf; bagian di luar kilau memakainya dengan alfa rendah.
 */
@Composable
fun CrossfadingLabel(
    visible: Boolean,
    style: TextStyle,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        // Berada di dalam AnimatedVisibility: animasi kilau hanya hidup selama label terlihat
        val transition = rememberInfiniteTransition(label = "crossfadeSweep")
        val sweep by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(SWEEP_CYCLE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweepHead"
        )
        val head = sweep * (SHIMMER_SPAN_PX * 3f) - SHIMMER_SPAN_PX
        Text(
            text = "Crossfading",
            maxLines = 1,
            style = style.copy(
                brush = Brush.horizontalGradient(
                    0f to baseColor.copy(alpha = 0.45f),
                    // Puncak kilau putih murni: warna dasar biasanya abu-abu, dan kilau abu-abu
                    // tidak terlihat sebagai kilau sama sekali.
                    0.5f to Color.White,
                    1f to baseColor.copy(alpha = 0.45f),
                    startX = head,
                    endX = head + SHIMMER_SPAN_PX,
                    tileMode = TileMode.Clamp
                )
            )
        )
    }
}