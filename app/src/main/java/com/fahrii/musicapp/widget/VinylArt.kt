package com.fahrii.musicapp.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Menggambar seluruh latar widget sebagai satu bitmap: kartu gradient, piringan hitam dengan cover
 * sebagai label, dan tonearm di pojok kanan atas. Widget tidak bisa beranimasi terus-menerus,
 * jadi status putar ditunjukkan lewat posisi tonearm: menepi saat dijeda, turun ke piringan
 * saat diputar.
 *
 * Semua ukuran dinyatakan sebagai pecahan tinggi widget supaya bentuknya sama di ukuran apa pun.
 */
internal object VinylArt {

    private val DEFAULT_BASE = 0xFF3B403D.toInt()
    private const val ARM_PLAYING_ANGLE = 22f

    fun render(width: Int, height: Int, cover: Bitmap?, tint: Int?, playing: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val base = tint ?: DEFAULT_BASE

        drawBackground(canvas, w, h, base)

        val pivotX = w - 0.20f * h
        val pivotY = 0.10f * h
        val discRadius = 0.41f * h
        val discX = pivotX - 0.40f * h
        val discY = 0.5f * h

        drawDisc(canvas, discX, discY, discRadius, h, cover, base)
        drawTonearm(canvas, pivotX, pivotY, h, playing)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float, base: Int) {
        val top = ColorUtils.blendARGB(base, Color.WHITE, 0.14f)
        val bottom = ColorUtils.blendARGB(base, Color.BLACK, 0.12f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        }
        val radius = 0.18f * h
        canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, paint)
    }

    private fun drawDisc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        h: Float,
        cover: Bitmap?,
        base: Int
    ) {
        // Bayangan di bawah piringan
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111111.toInt()
            setShadowLayer(0.05f * h, 0f, 0.02f * h, 0x80000000.toInt())
        })

        // Badan piringan
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r,
                intArrayOf(0xFF1B1B1B.toInt(), 0xFF101010.toInt(), 0xFF191919.toInt()),
                floatArrayOf(0f, 0.85f, 1f),
                Shader.TileMode.CLAMP
            )
        })

        // Alur halus, selang-seling terang dan gelap
        val labelR = 0.63f * r
        val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, 0.0035f * h)
        }
        var grooveR = labelR + 0.02f * h
        var i = 0
        while (grooveR < r - 0.015f * h) {
            groove.color = if (i % 2 == 0) 0x1AFFFFFF else 0x22000000
            canvas.drawCircle(cx, cy, grooveR, groove)
            grooveR += 0.011f * h
            i++
        }

        // Dua kilau berseberangan seperti pantulan lampu di piringan asli
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(
                cx, cy,
                intArrayOf(0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.10f, 0.22f, 0.5f, 0.60f, 0.72f, 1f)
            )
        })

        // Tepi piringan
        canvas.drawCircle(cx, cy, r - 0.003f * h, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.006f * h
            color = 0x33FFFFFF
        })

        drawLabel(canvas, cx, cy, labelR, h, cover, base)
    }

    /** Cover dipotong persegi dari tengah lalu dipakai sebagai label bulat. */
    private fun drawLabel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        labelR: Float,
        h: Float,
        cover: Bitmap?,
        base: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (cover != null) {
            val side = min(cover.width, cover.height)
            val square = Bitmap.createBitmap(
                cover, (cover.width - side) / 2, (cover.height - side) / 2, side, side
            )
            val scale = 2f * labelR / side
            paint.shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(cx - labelR, cy - labelR)
                })
            }
        } else {
            paint.color = ColorUtils.blendARGB(base, Color.WHITE, 0.25f)
        }
        canvas.drawCircle(cx, cy, labelR, paint)

        canvas.drawCircle(cx, cy, labelR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.008f * h
            color = 0x99000000.toInt()
        })
    }

    private fun drawTonearm(canvas: Canvas, px: Float, py: Float, h: Float, playing: Boolean) {
        canvas.save()
        canvas.rotate(if (playing) ARM_PLAYING_ANGLE else 0f, px, py)

        val arm = Path().apply {
            moveTo(px, py)
            lineTo(px + 0.037f * h, py + 0.120f * h)
            lineTo(px + 0.044f * h, py + 0.475f * h)
            lineTo(px + 0.075f * h, py + 0.587f * h)
        }
        val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 0.028f * h
        }

        // Bayangan lengan
        canvas.save()
        canvas.translate(0.010f * h, 0.015f * h)
        armPaint.color = 0x55000000
        canvas.drawPath(arm, armPaint)
        canvas.restore()

        // Lengan logam
        armPaint.shader = LinearGradient(
            px - 0.02f * h, 0f, px + 0.10f * h, 0f,
            intArrayOf(0xFFE8E8E8.toInt(), 0xFF9A9A9A.toInt(), 0xFFC9C9C9.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(arm, armPaint)

        // Kepala jarum
        canvas.drawLine(
            px + 0.060f * h, py + 0.545f * h, px + 0.078f * h, py + 0.605f * h,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = 0.042f * h
                color = 0xFF2A2A2A.toInt()
            }
        )
        canvas.restore()

        drawPivot(canvas, px, py, h)
    }

    /** Dudukan tonearm berbentuk roda gigi; tidak ikut berputar. */
    private fun drawPivot(canvas: Canvas, px: Float, py: Float, h: Float) {
        canvas.drawCircle(px, py, 0.07f * h, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                px, py, 0.07f * h,
                intArrayOf(0xFFF4F4F4.toInt(), 0xFFB5B5B5.toInt(), 0xFF8A8A8A.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        })

        val gear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = 0xFF5A5A5A.toInt()
        }
        gear.strokeWidth = 0.010f * h
        canvas.drawCircle(px, py, 0.032f * h, gear)
        gear.strokeWidth = 0.011f * h
        for (k in 0 until 8) {
            val a = k * (PI / 4).toFloat()
            canvas.drawLine(
                px + cos(a) * 0.036f * h, py + sin(a) * 0.036f * h,
                px + cos(a) * 0.048f * h, py + sin(a) * 0.048f * h,
                gear
            )
        }
        canvas.drawCircle(px, py, 0.012f * h, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5A5A5A.toInt()
        })
    }
}
