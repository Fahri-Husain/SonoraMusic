package com.fahrii.musicapp.desktop.player

import com.fahrii.musicapp.data.EqualizerBands
import com.fahrii.musicapp.data.EqualizerState
import java.util.Locale
import kotlin.math.abs

/** Menyusun nilai properti `af` mpv dari EQ + normalisasi. String kosong = tanpa filter. */
internal object MpvFilters {

    /** Bass boost Android 0..1000 dipetakan ke 0..8 dB low-shelf di 100 Hz. */
    private const val MAX_BASS_DB = 8f

    fun build(eq: EqualizerState, normalize: Boolean): String {
        val parts = mutableListOf<String>()

        if (eq.enabled) {
            val gains = eq.gainsDb.takeIf { it.size == EqualizerBands.COUNT }
                ?: List(EqualizerBands.COUNT) { 0f }
            val bassDb = eq.bassBoost.coerceIn(0, EqualizerState.MAX_BASS_BOOST) /
                    EqualizerState.MAX_BASS_BOOST.toFloat() * MAX_BASS_DB

            // Preamp negatif sebesar boost terbesar, supaya tidak clipping.
            val headroom = (gains.maxOrNull() ?: 0f).coerceAtLeast(0f) + bassDb
            if (headroom > 0.05f) parts += "volume=${fmt(-headroom)}dB"

            gains.forEachIndexed { i, g ->
                if (abs(g) >= 0.05f) {
                    parts += "equalizer=f=${EqualizerBands.CENTER_HZ[i].toInt()}:t=o:w=1.5:g=${fmt(g)}"
                }
            }
            if (bassDb >= 0.05f) parts += "bass=g=${fmt(bassDb)}:f=100"
        }

        if (normalize) parts += "loudnorm=I=-14:TP=-1.5:LRA=11"

        return if (parts.isEmpty()) "" else "lavfi=[" + parts.joinToString(",") + "]"
    }

    private fun fmt(v: Float): String = String.format(Locale.ROOT, "%.1f", v)
}