package com.fahrii.musicapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ikon buatan sendiri (dari file add-list.png dan playlist.png), digambar ulang sebagai vector
 * pada kanvas 256x256 supaya tajam di ukuran berapa pun dan bisa diwarnai lewat `tint`.
 */
internal object CustomIcons {

    /** Daftar dengan tanda plus: tambah ke playlist. */
    val AddList: ImageVector by lazy {
        builder("AddList").apply {
            roundRect(21f, 64f, 160f, 85f)
            roundRect(21f, 117f, 160f, 139f)
            roundRect(21f, 171f, 117f, 192f)
            roundRect(149f, 159.5f, 235f, 181.5f) // lengan plus mendatar
            roundRect(181f, 128f, 203f, 213f)     // lengan plus tegak
        }.build()
    }

    /** Daftar dengan segitiga play: antrean. */
    val Playlist: ImageVector by lazy {
        builder("Playlist").apply {
            roundRect(32f, 45f, 192f, 72f)
            roundRect(32f, 104f, 192f, 131f)
            roundRect(32f, 163f, 160f, 189f)
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 14f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(188f, 162f)
                lineTo(188f, 222f)
                lineTo(241f, 192f)
                close()
            }
        }.build()
    }

    /**
     * Kotak caption (dari file caption.png): persegi bersudut membulat dengan empat lubang berbentuk
     * pil yang menyerupai baris teks. Lubangnya dibuat dengan aturan even-odd, jadi tembus pandang
     * dan ikut warna latar.
     */
    val Caption: ImageVector by lazy {
        builder("Caption").apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                rectPath(11f, 43f, 245f, 213f, 30f)                       // badan kotak
                rectPath(53f, 107f, 149f, 128f, 10.5f)                    // baris 1, panjang
                rectPath(171f, 107f, 203f, 128f, 10.5f)                   // baris 1, pendek
                rectPath(53f, 149f, 96f, 171f, 11f)                       // baris 2, pendek
                rectPath(117f, 149f, 203f, 171f, 11f)                     // baris 2, panjang
            }
        }.build()
    }

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f
    )

    /** Menambah satu subpath persegi panjang dengan sudut membulat berjari-jari [r]. */
    private fun PathBuilder.rectPath(x0: Float, y0: Float, x1: Float, y1: Float, r: Float) {
        moveTo(x0 + r, y0)
        lineTo(x1 - r, y0)
        arcTo(r, r, 0f, false, true, x1, y0 + r)
        lineTo(x1, y1 - r)
        arcTo(r, r, 0f, false, true, x1 - r, y1)
        lineTo(x0 + r, y1)
        arcTo(r, r, 0f, false, true, x0, y1 - r)
        lineTo(x0, y0 + r)
        arcTo(r, r, 0f, false, true, x0 + r, y0)
        close()
    }

    /** Persegi panjang dengan ujung setengah lingkaran penuh (radius = setengah sisi terpendek). */
    private fun ImageVector.Builder.roundRect(x0: Float, y0: Float, x1: Float, y1: Float) {
        val r = minOf(x1 - x0, y1 - y0) / 2f
        path(fill = SolidColor(Color.Black)) {
            moveTo(x0 + r, y0)
            lineTo(x1 - r, y0)
            arcTo(r, r, 0f, false, true, x1, y0 + r)
            lineTo(x1, y1 - r)
            arcTo(r, r, 0f, false, true, x1 - r, y1)
            lineTo(x0 + r, y1)
            arcTo(r, r, 0f, false, true, x0, y1 - r)
            lineTo(x0, y0 + r)
            arcTo(r, r, 0f, false, true, x0 + r, y0)
            close()
        }
    }
}