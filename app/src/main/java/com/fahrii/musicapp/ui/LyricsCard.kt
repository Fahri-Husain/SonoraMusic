package com.fahrii.musicapp.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fahrii.musicapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Membuat kartu lirik (gambar) untuk dibagikan, seperti di Spotify:
 * latar gradasi dari salah satu warna cover, cover + judul + artis di atas, baris lirik pilihan besar di tengah.
 */
object LyricsCard {

    /** Batas jumlah baris lirik yang boleh dipilih. */
    const val MAX_LINES = 3

    private const val W = 1080
    private const val H = 1350
    private const val PAD = 88f

    /** Warna cadangan kalau cover tidak bisa dimuat. */
    const val FALLBACK_COLOR = 0xFF4A3B6B.toInt()

    /** Gambar kartu dengan warna latar [color] (ARGB), mis. salah satu hasil [palette]. */
    suspend fun render(
        context: Context,
        cover: Bitmap?,
        title: String,
        artist: String,
        lyrics: List<String>,
        color: Int
    ): Bitmap = withContext(Dispatchers.Default) { draw(context, cover, title, artist, lyrics, color) }

    /**
     * Ambil sampai [max] warna yang ada di cover: urut dari yang paling dominan,
     * dan hanya warna yang cukup berbeda satu sama lain.
     */
    suspend fun palette(cover: Bitmap?, max: Int = 6): List<Int> = withContext(Dispatchers.Default) {
        if (cover == null) return@withContext listOf(FALLBACK_COLOR)

        val size = 48
        val small = Bitmap.createScaledBitmap(cover, size, size, true)
        val px = IntArray(size * size)
        small.getPixels(px, 0, size, 0, 0, size, size)

        // Kelompokkan piksel ke "ember" warna (4 bit per kanal)
        val buckets = HashMap<Int, ColorBucket>()
        for (p in px) {
            if (Color.alpha(p) < 128) continue
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            buckets.getOrPut(key) { ColorBucket() }.add(r, g, b)
        }

        val hsv = FloatArray(3)
        val ranked = buckets.values
            .map { bucket ->
                val c = bucket.average()
                Color.colorToHSV(c, hsv)
                // Warna yang lebih berwarna sedikit diutamakan dibanding abu-abu
                c to bucket.count * (1f + hsv[1])
            }
            .sortedByDescending { it.second }

        val picked = mutableListOf<Int>()
        for ((c, _) in ranked) {
            if (picked.none { colorDistance(it, c) < 70 }) picked.add(c)
            if (picked.size >= max) break
        }
        picked.ifEmpty { listOf(FALLBACK_COLOR) }
    }

    private class ColorBucket {
        var count = 0
        private var r = 0L
        private var g = 0L
        private var b = 0L

        fun add(red: Int, green: Int, blue: Int) {
            count++
            r += red
            g += green
            b += blue
        }

        fun average(): Int = Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun colorDistance(a: Int, b: Int): Double {
        val dr = (Color.red(a) - Color.red(b)).toDouble()
        val dg = (Color.green(a) - Color.green(b)).toDouble()
        val db = (Color.blue(a) - Color.blue(b)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }

    /** Simpan kartu ke cache lalu buka menu bagikan bawaan Android. */
    suspend fun share(context: Context, bitmap: Bitmap) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "lirik_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Kartu lirik", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Bagikan lirik"))
    }

    suspend fun loadCover(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(512)
                .build()
            val result = context.imageLoader.execute(request)
            ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        try {
            ResourcesCompat.getFont(context, id) ?: fallback
        } catch (e: Exception) {
            fallback
        }

    private fun draw(
        context: Context,
        cover: Bitmap?,
        title: String,
        artist: String,
        lyrics: List<String>,
        color: Int
    ): Bitmap {
        val bold = font(context, R.font.inter_bold, Typeface.DEFAULT_BOLD)
        val medium = font(context, R.font.inter_medium, Typeface.DEFAULT)

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Latar: gradasi dari warna pilihan ke versi yang lebih gelap
        val top = color
        val bottom = Color.rgb(
            (Color.red(color) * 0.6f).toInt(),
            (Color.green(color) * 0.6f).toInt(),
            (Color.blue(color) * 0.6f).toInt()
        )
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)

        // Warna teks menyesuaikan terang/gelapnya latar supaya tetap terbaca
        val luminance = (
                0.299f * (Color.red(top) + Color.red(bottom)) / 2f +
                        0.587f * (Color.green(top) + Color.green(bottom)) / 2f +
                        0.114f * (Color.blue(top) + Color.blue(bottom)) / 2f
                ) / 255f
        val textColor = if (luminance > 0.6f) 0xFF121212.toInt() else Color.WHITE
        val textColorSoft = Color.argb(
            180, Color.red(textColor), Color.green(textColor), Color.blue(textColor)
        )

        // Cover persegi (dipotong tengah) dengan sudut membulat
        val coverSize = 168f
        val coverRect = RectF(PAD, PAD, PAD + coverSize, PAD + coverSize)
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val src = Rect(
                (cover.width - side) / 2,
                (cover.height - side) / 2,
                (cover.width + side) / 2,
                (cover.height + side) / 2
            )
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(coverRect, 28f, 28f, Path.Direction.CW) })
            canvas.drawBitmap(cover, src, coverRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }

        // Judul + artis di samping cover
        val textX = PAD + coverSize + 40f
        val textMaxW = W - PAD - textX
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            textSize = 46f
            typeface = bold
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColorSoft
            textSize = 36f
            typeface = medium
        }
        canvas.drawText(
            TextUtils.ellipsize(title, titlePaint, textMaxW, TextUtils.TruncateAt.END).toString(),
            textX, PAD + 74f, titlePaint
        )
        canvas.drawText(
            TextUtils.ellipsize(artist, artistPaint, textMaxW, TextUtils.TruncateAt.END).toString(),
            textX, PAD + 128f, artistPaint
        )

        // Lirik pilihan: ukuran huruf dikecilkan bertahap sampai muat
        val text = lyrics.joinToString("\n")
        val areaTop = PAD + coverSize + 72f
        val areaH = (H - 200f - areaTop).toInt()
        val width = (W - 2 * PAD).toInt()
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            typeface = bold
        }
        var size = 80f
        var layout: StaticLayout
        while (true) {
            lyricPaint.textSize = size
            layout = StaticLayout.Builder.obtain(text, 0, text.length, lyricPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
            if (layout.height <= areaH || size <= 36f) break
            size -= 2f
        }
        canvas.save()
        canvas.translate(PAD, areaTop + (areaH - layout.height).coerceAtLeast(0) / 2f)
        layout.draw(canvas)
        canvas.restore()

        // Nama aplikasi di bawah
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            textSize = 40f
            typeface = bold
            letterSpacing = 0.04f
        }
        canvas.drawText("Sonora", PAD, H - 96f, brandPaint)

        return bmp
    }
}