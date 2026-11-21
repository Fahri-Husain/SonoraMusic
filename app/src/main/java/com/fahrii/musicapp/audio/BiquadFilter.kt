package com.fahrii.musicapp.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Filter biquad IIR (low-pass atau high-pass) untuk transisi crossfade ala DJ.
 *
 * Diadaptasi dari SimpMusic (maxrave-dev/core, `BiquadFilter.kt`), lisensi GPL-3.0.
 * Berdasarkan Audio EQ Cookbook karya Robert Bristow-Johnson.
 *
 * - Lagu lama: low-pass, cutoff turun dari 20 kHz ke ~200 Hz (treble hilang, suara meredup).
 * - Lagu baru: high-pass, cutoff turun dari 2 kHz ke 20 Hz (bass terisi pelan-pelan).
 *
 * Dua tahap Butterworth dikaskade, jadi orde ke-4 (24 dB/oktaf), sama curamnya dengan filter di
 * mixer DJ profesional. Tiap kanal (kiri/kanan) punya status filter sendiri.
 */
class BiquadFilter {

    enum class FilterType { LOW_PASS, HIGH_PASS }

    // Koefisien tahap 1 dan 2 (identik: dua tahap Butterworth yang sama dikaskade)
    @Volatile private var b0 = 1.0
    @Volatile private var b1 = 0.0
    @Volatile private var b2 = 0.0
    @Volatile private var a1 = 0.0
    @Volatile private var a2 = 0.0

    // Status per kanal: [x1, x2, y1, y2] untuk tahap 1 lalu tahap 2
    private val left = DoubleArray(8)
    private val right = DoubleArray(8)

    /**
     * Hitung ulang koefisien.
     *
     * @param cutoffHz frekuensi cutoff dalam Hz.
     * @param sampleRate sample rate dalam Hz (mis. 44100, 48000).
     * @param q faktor kualitas per tahap; 0,707 (Butterworth) = passband paling rata.
     */
    fun updateCoefficients(
        cutoffHz: Float,
        sampleRate: Int,
        q: Float = BUTTERWORTH_Q,
        type: FilterType
    ) {
        val cutoff = cutoffHz.coerceIn(20f, (sampleRate / 2f) - 1f)
        val omega = 2.0 * PI * cutoff / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        val nb0: Double
        val nb1: Double
        val nb2: Double
        when (type) {
            FilterType.LOW_PASS -> {
                nb0 = (1.0 - cosOmega) / 2.0
                nb1 = 1.0 - cosOmega
                nb2 = nb0
            }
            FilterType.HIGH_PASS -> {
                nb0 = (1.0 + cosOmega) / 2.0
                nb1 = -(1.0 + cosOmega)
                nb2 = nb0
            }
        }
        val a0 = 1.0 + alpha
        b0 = nb0 / a0
        b1 = nb1 / a0
        b2 = nb2 / a0
        a1 = (-2.0 * cosOmega) / a0
        a2 = (1.0 - alpha) / a0
    }

    /** Proses satu sampel kanal kiri (atau mono) lewat kedua tahap. */
    fun processLeft(input: Double): Double = process(left, input)

    /** Proses satu sampel kanal kanan lewat kedua tahap. */
    fun processRight(input: Double): Double = process(right, input)

    private fun process(s: DoubleArray, input: Double): Double {
        // Tahap 1
        val mid = b0 * input + b1 * s[0] + b2 * s[1] - a1 * s[2] - a2 * s[3]
        s[1] = s[0]; s[0] = input
        s[3] = s[2]; s[2] = mid

        // Tahap 2
        val out = b0 * mid + b1 * s[4] + b2 * s[5] - a1 * s[6] - a2 * s[7]
        s[5] = s[4]; s[4] = mid
        s[7] = s[6]; s[6] = out
        return out
    }

    /** Kosongkan riwayat kedua tahap. Panggil saat stream baru dimulai atau filter dimatikan. */
    fun reset() {
        left.fill(0.0)
        right.fill(0.0)
    }

    companion object {
        /** Faktor Q Butterworth: passband rata, tanpa resonansi di cutoff. */
        const val BUTTERWORTH_Q = 0.707f
    }
}
