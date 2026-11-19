package com.fahrii.musicapp.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HUE_BINS = 24

/**
 * Warna dominan dari [models] (url/file cover; kalau lebih dari satu, digabung). Null selama belum
 * selesai dihitung, kalau daftarnya kosong, atau kalau gambarnya gagal dimuat.
 */
@Composable
fun rememberDominantColor(models: List<Any?>): Color? {
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(models) {
        color = withContext(Dispatchers.Default) {
            try {
                dominantColorOf(context, models)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }
    return color
}

/**
 * Pilih warna yang paling "terasa" di gambar: piksel yang berwarna dan terang dikelompokkan per
 * rona (hue), kelompok dengan bobot terbesar menang. Gambar yang nyaris hitam-putih tidak punya
 * kelompok berarti, jadi dipakai rata-rata seluruh piksel.
 */
private suspend fun dominantColorOf(context: Context, models: List<Any?>): Color? {
    val weight = FloatArray(HUE_BINS)
    val sumR = FloatArray(HUE_BINS)
    val sumG = FloatArray(HUE_BINS)
    val sumB = FloatArray(HUE_BINS)
    var allR = 0L
    var allG = 0L
    var allB = 0L
    var pixelCount = 0L
    val hsv = FloatArray(3)

    for (model in models.filterNotNull().take(4)) {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(model)
                .size(64)
                .allowHardware(false)
                .build()
        )
        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: continue
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            allR += r
            allG += g
            allB += b
            pixelCount++

            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            if (hsv[1] < 0.2f || hsv[2] < 0.15f) continue // abu-abu atau gelap: tidak menentukan warna
            val wgt = hsv[1] * hsv[2]
            val bin = ((hsv[0] / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            weight[bin] += wgt
            sumR[bin] += r * wgt
            sumG[bin] += g * wgt
            sumB[bin] += b * wgt
        }
    }
    if (pixelCount == 0L) return null

    // Skor satu rona = bin itu + dua tetangganya, supaya satu warna tidak terpecah di perbatasan bin
    var bestBin = -1
    var bestScore = 0f
    for (i in 0 until HUE_BINS) {
        val score = weight[(i + HUE_BINS - 1) % HUE_BINS] + weight[i] + weight[(i + 1) % HUE_BINS]
        if (score > bestScore) {
            bestScore = score
            bestBin = i
        }
    }

    // Terlalu sedikit piksel berwarna: anggap gambar abu-abu
    if (bestBin < 0 || bestScore < pixelCount * 0.02f) {
        return Color(
            red = allR.toFloat() / pixelCount / 255f,
            green = allG.toFloat() / pixelCount / 255f,
            blue = allB.toFloat() / pixelCount / 255f
        )
    }

    var r = 0f
    var g = 0f
    var b = 0f
    var w = 0f
    for (d in -1..1) {
        val i = (bestBin + d + HUE_BINS) % HUE_BINS
        r += sumR[i]
        g += sumG[i]
        b += sumB[i]
        w += weight[i]
    }
    return Color(red = r / w / 255f, green = g / w / 255f, blue = b / w / 255f)
}

/**
 * Versi gelap warna ini untuk latar halaman: rona dipertahankan, saturasi dibatasi supaya tidak
 * menyala, dan kecerahan dipatok ke [value] (0..1). Warna nyaris abu-abu dibuat lebih gelap lagi.
 */
fun Color.asBackdrop(value: Float, maxSaturation: Float = 0.55f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    val grayish = hsv[1] < 0.1f
    hsv[1] = hsv[1].coerceAtMost(maxSaturation)
    hsv[2] = if (grayish) value * 0.6f else value
    return Color(android.graphics.Color.HSVToColor(hsv))
}