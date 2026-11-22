package com.fahrii.musicapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ikon garis (outline) untuk halaman Setting, digambar ulang dari usulan tampilan Setting pada
 * kanvas 24x24 dengan tebal garis 1,6 dan ujung membulat. Berwarna hitam di sini; warna aslinya
 * diberikan lewat `tint` pada Icon.
 */
internal object SettingsIcons {

    /** Dua tuas mixer: Transisi ala DJ. */
    val Sliders: ImageVector by lazy {
        line("Sliders", "M4 7h10M18 7h2M4 17h2M10 17h10", circle(16f, 7f, 2f), circle(8f, 17f, 2f))
    }

    /** Tiga tuas vertikal dengan knob: Equalizer. */
    val Equalizer: ImageVector by lazy {
        line(
            "Equalizer",
            "M6 4v6M6 14v6M12 4v2M12 10v10M18 4v8M18 16v4",
            circle(6f, 12f, 2f), circle(12f, 8f, 2f), circle(18f, 14f, 2f)
        )
    }

    /** Segitiga play dengan garis di ujung: Lewati bagian senyap. */
    val SkipSilence: ImageVector by lazy { line("SkipSilence", "M6 6l9 6-9 6z M19 6v12") }

    /** Gelombang suara: Kualitas audio. */
    val Waveform: ImageVector by lazy { line("Waveform", "M5 9v6M9 5v14M13 8v8M17 10v4M21 11v2") }

    /** Ponsel: Goyang untuk ganti lagu. */
    val Phone: ImageVector by lazy { line("Phone", roundRect(7f, 3f, 10f, 18f, 2f), "M11 18h2") }

    /** Huruf A besar dan kecil: Ukuran lirik. */
    val TextSize: ImageVector by lazy { line("TextSize", "M4 18l5-12 5 12M6 14h6M16 18l3-7 3 7") }

    /** Titik pusat dengan cincin putus-putus: Blur lirik. */
    val Blur: ImageVector by lazy { line("Blur", circle(12f, 12f, 3f), dashedCircle(12f, 12f, 8f, 10)) }

    /** Bulan sabit: Timer tidur. */
    val Moon: ImageVector by lazy { line("Moon", "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z") }

    /** Denyut: AutoMix. */
    val Pulse: ImageVector by lazy { line("Pulse", "M3 12h4l3-7 4 14 3-7h4") }

    /** Sinyal Wi-Fi: Unduh hanya lewat Wi-Fi. */
    val Wifi: ImageVector by lazy {
        line("Wifi", "M3 9a14 14 0 0 1 18 0M6 12.5a9 9 0 0 1 12 0M9.5 16a4 4 0 0 1 5 0", "M12 19h.01")
    }

    /** Panah turun ke garis dasar: unduh / impor. */
    val Download: ImageVector by lazy { line("Download", "M12 4v11M7 11l5 5 5-5M5 20h14") }

    /** Panah naik dari garis dasar: ekspor. */
    val Upload: ImageVector by lazy { line("Upload", "M12 16V5M7 9l5-5 5 5M5 20h14") }

    /** Pembersih: Hapus cache. */
    val Eraser: ImageVector by lazy { line("Eraser", "M4 20l7-7M14 4l6 6-6 6-6-6z") }

    /** Tong sampah. */
    val Trash: ImageVector by lazy { line("Trash", "M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13") }

    /** Jam: riwayat putar. */
    val Clock: ImageVector by lazy { line("Clock", circle(12f, 12f, 9f), "M12 7v5l3 2") }

    /** Not musik: SonoraMusic dan Notifikasi pemutar. */
    val Note: ImageVector by lazy {
        line("Note", "M9 18V6l10-2v12", circle(6.5f, 18f, 2.5f), circle(16.5f, 16f, 2.5f))
    }

    /** Tiga titik tersambung: Kode sumber. */
    val Share: ImageVector by lazy {
        line(
            "Share",
            circle(6f, 12f, 2.5f), circle(18f, 6f, 2.5f), circle(18f, 18f, 2.5f),
            "M8.2 11l7.6-4M8.2 13l7.6 4"
        )
    }

    /** Pensil: Laporkan masalah. */
    val Pencil: ImageVector by lazy { line("Pencil", "M4 20l1-4L16 5l3 3L8 19z") }

    /** Daftar dengan tanda plus: Lisensi open source. */
    val ListPlus: ImageVector by lazy { line("ListPlus", "M4 7h12M4 12h12M4 17h7M19 14v6M16 17h6") }

    // ---- Ikon untuk pengaturan yang tidak ada di usulan, digambar dengan gaya yang sama ----

    /** Kaca pembesar: Hapus riwayat pencarian. */
    val Search: ImageVector by lazy { line("Search", circle(11f, 11f, 6.5f), "M16 16l4 4") }

    /** Lonceng: Notifikasi pemutar. */
    val Bell: ImageVector by lazy { line("Bell", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0") }

    // ---- Pembantu ----

    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

    /** Lingkaran berpusat di ([cx], [cy]) berjari-jari [r], ditulis sebagai dua busur setengah lingkaran. */
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0 z"

    /** Persegi panjang dengan sudut membulat berjari-jari [r]. */
    private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r} $y H${x + w - r} A$r $r 0 0 1 ${x + w} ${y + r} V${y + h - r} " +
                "A$r $r 0 0 1 ${x + w - r} ${y + h} H${x + r} A$r $r 0 0 1 $x ${y + h - r} " +
                "V${y + r} A$r $r 0 0 1 ${x + r} $y z"

    /** Cincin putus-putus: [dashes] busur pendek (40% dari tiap jatah) yang rata mengelilingi lingkaran. */
    private fun dashedCircle(cx: Float, cy: Float, r: Float, dashes: Int): String =
        buildString {
            val step = 2.0 * PI / dashes
            for (i in 0 until dashes) {
                val a0 = step * i
                val a1 = a0 + step * 0.4
                append("M${pt(cx + r * cos(a0))} ${pt(cy + r * sin(a0))} ")
                append("A$r $r 0 0 1 ${pt(cx + r * cos(a1))} ${pt(cy + r * sin(a1))} ")
            }
        }

    /** Dibulatkan ke dua desimal supaya angkanya rapi dan tidak pernah berbentuk notasi ilmiah. */
    private fun pt(v: Double): Float = (v * 100).roundToInt() / 100f
}