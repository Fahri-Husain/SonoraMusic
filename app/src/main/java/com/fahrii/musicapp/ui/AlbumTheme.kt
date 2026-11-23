package com.fahrii.musicapp.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Palet warna app yang mengikuti sampul lagu yang sedang diputar. Tanpa lagu (atau sampulnya
 * abu-abu / gagal dimuat) isinya sama dengan warna tetap di [Sonora].
 */
@Immutable
data class SonoraPalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val textSecondary: Color,
    val accent: Color,
    /** Warna gradasi di atas Beranda. Alpha-nya 0 = pakai warna bawaan Beranda; 1 = penuh warna sampul. */
    val glow: Color
) {
    companion object {
        val Default = SonoraPalette(
            background = Sonora.Background,
            surface = Sonora.Surface,
            surfaceHigh = Sonora.SurfaceHigh,
            border = Sonora.Border,
            textSecondary = Sonora.TextSecondary,
            accent = Sonora.Accent,
            glow = Color.Transparent
        )

        /**
         * Rona diambil dari [seed]; kecerahan dipatok sedekat mungkin dengan warna tetap di [Sonora]
         * dan saturasi dibatasi, supaya latar tetap gelap dan teks putih selalu terbaca.
         */
        fun fromSeed(seed: Color?): SonoraPalette {
            if (seed == null) return Default
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
            // Sampul hitam-putih tidak punya rona yang berarti: biarkan tampilan netral seperti biasa
            if (hsv[1] < 0.1f) return Default
            return SonoraPalette(
                background = seed.tone(value = 0.16f, maxSaturation = 0.55f),
                surface = seed.tone(value = 0.22f, maxSaturation = 0.50f),
                surfaceHigh = seed.tone(value = 0.29f, maxSaturation = 0.45f),
                border = seed.tone(value = 0.37f, maxSaturation = 0.35f),
                textSecondary = seed.tone(value = 0.70f, maxSaturation = 0.15f),
                accent = seed.tone(value = 0.98f, maxSaturation = 0.35f),
                glow = seed.tone(value = 0.90f, maxSaturation = 0.85f)
            )
        }
    }
}

/** True kalau warna ini nyaris abu-abu (tidak punya rona berarti), jadi tidak layak dijadikan tema. */
internal fun Color.isGrayish(): Boolean {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return hsv[1] < 0.1f
}

/** Rona sama dengan warna ini, saturasi dibatasi [maxSaturation], kecerahan dipatok ke [value] (0..1). */
private fun Color.tone(value: Float, maxSaturation: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtMost(maxSaturation)
    hsv[2] = value
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Palet saat ini. Baca dari composable kecil saja, karena nilainya berubah tiap frame saat animasi. */
val LocalSonoraPalette = compositionLocalOf { SonoraPalette.Default }

/**
 * Warna latar saat ini sebagai fungsi, supaya yang memakainya (latar halaman, dasar kaca) cukup
 * membacanya di fase gambar tanpa membuat composable induknya ikut recompose tiap frame.
 */
val LocalSonoraBackground = staticCompositionLocalOf<() -> Color> { { Sonora.Background } }

private const val NO_COLOR = 0
private const val FADE_MS = 600

/** Warna dominan per URL sampul, supaya lagu yang sama tidak dihitung ulang. */
private val seedCache = LruCache<String, Int>(32)

private suspend fun resolveSeed(context: Context, url: String): Color? {
    val cached = seedCache.get(url)
    if (cached != null && cached != NO_COLOR) return Color(cached)
    val color = try {
        withContext(Dispatchers.Default) { dominantColorOf(context, listOf(url)) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    // Yang gagal tidak disimpan: mungkin cuma jaringan yang sedang putus
    if (color != null) seedCache.put(url, color.toArgb())
    return color
}

/**
 * Membungkus seluruh app: menggambar latar halaman dan menyediakan [LocalSonoraPalette]. Warna
 * memudar pelan saat [coverUrl] berganti.
 */
@Composable
fun AlbumThemeHost(coverUrl: String?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val seed by produceState<Color?>(null, coverUrl) {
        value = if (coverUrl == null) null else resolveSeed(context, coverUrl)
    }

    val target = SonoraPalette.fromSeed(seed)
    val spec = tween<Color>(FADE_MS)
    val background = animateColorAsState(target.background, spec, label = "albumBackground")
    val surface by animateColorAsState(target.surface, spec, label = "albumSurface")
    val surfaceHigh by animateColorAsState(target.surfaceHigh, spec, label = "albumSurfaceHigh")
    val border by animateColorAsState(target.border, spec, label = "albumBorder")
    val textSecondary by animateColorAsState(target.textSecondary, spec, label = "albumTextSecondary")
    val accent by animateColorAsState(target.accent, spec, label = "albumAccent")
    val glow by animateColorAsState(target.glow, spec, label = "albumGlow")

    val palette = SonoraPalette(background.value, surface, surfaceHigh, border, textSecondary, accent, glow)
    val backgroundProvider = rememberColorProvider(background)

    CompositionLocalProvider(
        LocalSonoraPalette provides palette,
        LocalSonoraBackground provides backgroundProvider
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(background.value) }
        ) {
            content()
        }
    }
}

@Composable
private fun rememberColorProvider(state: State<Color>): () -> Color = remember(state) { { state.value } }