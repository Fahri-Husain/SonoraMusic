package com.fahrii.musicapp.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * [AudioProcessor] yang menyamakan loudness antar lagu, supaya lagu yang masuk tidak terasa tiba-tiba
 * jauh lebih keras atau lebih pelan dari lagu sebelumnya (dan crossfade terdengar rata).
 *
 * Cara kerja:
 * - Loudness lagu diukur dari daya rata-rata (RMS) sampel yang sedang diputar. Bagian nyaris senyap
 *   (di bawah [GATE_DB]) tidak dihitung, jadi intro sepi atau jeda tidak menipu pengukuran.
 * - Dari situ dihitung gain supaya levelnya mendekati [TARGET_DB], dibatasi antara [MIN_GAIN_DB] dan
 *   [MAX_GAIN_DB]. Menaikkan volume dibatasi lebih kecil karena berisiko pecah (clipping).
 * - Gain bergerak halus (dB per detik terbatas) dan diterapkan linear di dalam tiap buffer, jadi
 *   tidak ada lonjakan volume atau bunyi "tik". Di awal lagu geraknya lebih cepat, setelah pengukuran
 *   stabil geraknya sangat pelan.
 * - Limiter lembut di atas -1 dBFS menahan puncak supaya suara yang dinaikkan tidak pecah.
 *
 * Ini perkiraan berbasis RMS, bukan pengukuran LUFS penuh (tanpa K-weighting), tapi cukup untuk
 * menyamakan lagu keras dan lagu pelan.
 *
 * - [enabled] boleh diubah kapan saja dari thread mana pun. Saat dimatikan, gain turun halus ke 0 dB
 *   lalu audio diteruskan apa adanya.
 * - Mendukung PCM 16-bit mono dan stereo; format lain dilewatkan tanpa diproses.
 * - Letakkan *sebelum* filter DJ di rantai audio, supaya sapuan filter saat crossfade tidak ikut
 *   terukur sebagai perubahan loudness.
 */
@OptIn(UnstableApi::class)
class LoudnessNormalizerAudioProcessor : BaseAudioProcessor() {

    /** Penyama volume aktif atau tidak. */
    @Volatile
    var enabled: Boolean = false

    /**
     * Kalau true, gain dipertahankan di nilai sekarang dan tidak mengukur ulang. Dipakai pemutar kedua
     * crossfade: ia memutar sisa lagu lama dari tengah, jadi pengukurannya sendiri tidak mewakili lagu itu.
     */
    @Volatile
    var frozen: Boolean = false

    /** Gain yang sedang diterapkan (dB). Dibaca CrossfadeManager untuk dipindahkan ke pemutar kedua. */
    @Volatile
    var currentGainDb: Float = 0f
        private set

    /** Mulai dari gain tertentu, mis. gain lagu yang sedang diputar pemutar utama. */
    fun seedGainDb(gainDb: Float) {
        currentGainDb = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }

    private var sampleRate = 0
    private var channelCount = 0

    // Status pengukuran; hanya disentuh dari thread pemutaran
    private var energy = 0.0
    private var gatedSeconds = 0.0
    private var haveEstimate = false
    private var restartPending = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount != 1 && inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)

        val isEnabled = enabled
        val startGain = currentGainDb

        // Mati dan gain sudah kembali ke 0 dB: teruskan apa adanya
        if ((!isEnabled && startGain == 0f) || sampleRate == 0 || channelCount == 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val frames = remaining / (2 * channelCount)
        if (frames == 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val dtSec = frames.toDouble() / sampleRate

        if (restartPending) {
            energy = 0.0
            gatedSeconds = 0.0
            haveEstimate = false
            restartPending = false
        }

        if (isEnabled && !frozen) measure(inputBuffer, frames * channelCount, dtSec)

        val desiredGain = when {
            !isEnabled -> 0f
            frozen || !haveEstimate -> startGain
            else -> (TARGET_DB - 10.0 * log10(energy + 1e-12)).toFloat()
                .coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }
        val slew = if (!isEnabled || gatedSeconds < FAST_PHASE_S) FAST_SLEW_DB_PER_S else SLOW_SLEW_DB_PER_S
        val maxStep = (slew * dtSec).toFloat()
        val endGain = startGain + (desiredGain - startGain).coerceIn(-maxStep, maxStep)
        currentGainDb = endGain

        val g0 = dbToLinear(startGain)
        val g1 = dbToLinear(endGain)
        val source = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frames) {
            val gain = g0 + (g1 - g0) * f / frames
            for (c in 0 until channelCount) {
                val x = source.short / 32768f * gain
                output.putShort(toPcm(softLimit(x)))
            }
        }
        // Sisa byte yang tidak membentuk satu frame utuh diteruskan apa adanya
        output.put(source)
        output.flip()
    }

    /** Perbarui perkiraan loudness dari [samples] sampel di [buffer] (tanpa menggeser posisinya). */
    private fun measure(buffer: ByteBuffer, samples: Int, dtSec: Double) {
        val src = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        for (i in 0 until samples) {
            val s = src.short / 32768.0
            sum += s * s
        }
        val meanSquare = sum / samples
        if (10.0 * log10(meanSquare + 1e-12) <= GATE_DB) return

        // Rata-rata berjalan: bobot awalnya penuh supaya perkiraan cepat terisi, lalu mengecil
        // sampai jendela WINDOW_S detik
        val weight = dtSec / (gatedSeconds + dtSec)
        energy += (meanSquare - energy) * weight
        gatedSeconds = min(gatedSeconds + dtSec, WINDOW_S)
        haveEstimate = true
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    /** Di bawah ambang diteruskan apa adanya; di atasnya dilengkungkan halus mendekati 1,0. */
    private fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= LIMIT_THRESHOLD) return x
        val shaped = LIMIT_THRESHOLD +
                (1f - LIMIT_THRESHOLD) * tanh((a - LIMIT_THRESHOLD) / (1f - LIMIT_THRESHOLD))
        return if (x < 0f) -shaped else shaped
    }

    private fun toPcm(x: Float): Short = (x.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()

    // Seek, ganti lagu, atau akhir lagu: pengukuran dimulai lagi. Gain sengaja tidak di-nol-kan,
    // supaya volume tidak melompat; ia bergerak halus ke nilai baru begitu loudness terukur.
    override fun onFlush() {
        super.onFlush()
        restartPending = true
    }

    override fun onQueueEndOfStream() {
        super.onQueueEndOfStream()
        restartPending = true
    }

    override fun onReset() {
        super.onReset()
        currentGainDb = 0f
        restartPending = true
    }

    private companion object {
        /** Level sasaran (dBFS, RMS). Kira-kira setara -14 LUFS, patokan umum layanan streaming. */
        const val TARGET_DB = -14.0

        /** Blok yang lebih pelan dari ini dianggap senyap dan tidak ikut diukur. */
        const val GATE_DB = -50.0

        /** Batas gain: lagu keras boleh diredam lebih jauh daripada lagu pelan dinaikkan. */
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 6f

        /** Panjang jendela perkiraan loudness (detik). */
        const val WINDOW_S = 20.0

        /** Selama detik-detik awal pengukuran gain boleh bergerak cepat, lalu sangat pelan. */
        const val FAST_PHASE_S = 4.0
        const val FAST_SLEW_DB_PER_S = 4f
        const val SLOW_SLEW_DB_PER_S = 0.5f

        /** Ambang limiter lembut, sekitar -1 dBFS. */
        const val LIMIT_THRESHOLD = 0.89f
    }
}
