package com.fahrii.musicapp.data

import kotlin.math.ln

/**
 * Pita equalizer yang tampil di UI. Jumlah band Equalizer bawaan Android beda-beda tiap perangkat
 * (umumnya 5, kadang 6 atau 10), jadi pengaturan disimpan di 5 pita "virtual" ini lalu dipetakan
 * ke band perangkat berdasarkan frekuensi tengahnya (lihat [EqualizerBands.gainAt]).
 */
object EqualizerBands {
    const val COUNT = 5
    const val MIN_DB = -12f
    const val MAX_DB = 12f

    /** Frekuensi tengah tiap pita (Hz). */
    val CENTER_HZ = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

    val LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3,6 kHz", "14 kHz")

    /**
     * Penguatan (dB) di frekuensi [hz], diinterpolasi linear pada skala logaritmik di antara dua
     * pita terdekat. Di luar jangkauan, dipakai nilai pita paling pinggir.
     */
    fun gainAt(gainsDb: List<Float>, hz: Float): Float {
        if (gainsDb.size != COUNT) return 0f
        if (hz <= CENTER_HZ.first()) return gainsDb.first()
        if (hz >= CENTER_HZ.last()) return gainsDb.last()
        for (i in 0 until COUNT - 1) {
            val lo = CENTER_HZ[i]
            val hi = CENTER_HZ[i + 1]
            if (hz in lo..hi) {
                val t = (ln(hz) - ln(lo)) / (ln(hi) - ln(lo))
                return gainsDb[i] + (gainsDb[i + 1] - gainsDb[i]) * t
            }
        }
        return 0f
    }
}

/** Preset siap pakai. Urutan [gainsDb] mengikuti [EqualizerBands.CENTER_HZ]. */
enum class EqPreset(val label: String, val gainsDb: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS("Bass", listOf(6f, 4f, 1f, 0f, 0f)),
    VOCAL("Vokal", listOf(-2f, -1f, 3f, 3f, 1f)),
    TREBLE("Treble", listOf(0f, 0f, 0f, 3f, 6f)),
    ROCK("Rock", listOf(5f, 3f, -1f, 3f, 5f)),
    POP("Pop", listOf(-1f, 2f, 4f, 2f, -1f)),
    ELECTRONIC("Elektronik", listOf(5f, 3f, 0f, 2f, 4f)),
    JAZZ("Jazz", listOf(3f, 1f, -1f, 2f, 4f)),

    /**
     * Suara teredam seperti di dalam air: frekuensi tinggi dipangkas dalam-dalam, bass sedikit
     * dinaikkan. Pita 14 kHz sampai -12 dB (batas bawah slider), jadi efeknya terasa jelas.
     */
    UNDERWATER("Underwater", listOf(3f, 2f, -3f, -8f, -12f)),

    /** Dipakai otomatis begitu pengguna menggeser pita sendiri; tidak ada nilai bawaannya. */
    CUSTOM("Kustom", listOf(0f, 0f, 0f, 0f, 0f));

    companion object {
        /** Preset yang bisa dipilih dari daftar (tanpa [CUSTOM]). */
        val selectable: List<EqPreset> = entries.filter { it != CUSTOM }
    }
}

/**
 * Kondisi equalizer saat ini.
 * @param bassBoost kekuatan bass boost 0..1000 (satuan BassBoost Android).
 */
data class EqualizerState(
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val gainsDb: List<Float> = EqPreset.FLAT.gainsDb,
    val bassBoost: Int = 0
) {
    companion object {
        const val MAX_BASS_BOOST = 1000
    }
}