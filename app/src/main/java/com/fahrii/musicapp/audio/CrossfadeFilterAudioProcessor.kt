package com.fahrii.musicapp.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [AudioProcessor] Media3 yang menerapkan filter biquad low-pass / high-pass secara real-time.
 * Dipakai untuk transisi crossfade ala DJ ([com.fahrii.musicapp.CrossfadeManager]).
 *
 * Diadaptasi dari SimpMusic (maxrave-dev/core, `CrossfadeFilterAudioProcessor.kt`), lisensi GPL-3.0.
 *
 * - [enabled], [cutoffFrequencyHz], dan [filterType] boleh diubah kapan saja dari thread mana pun.
 * - Saat [enabled] false, audio diteruskan apa adanya.
 * - Koefisien hanya dihitung ulang kalau cutoff atau jenis filter berubah.
 * - Mendukung PCM 16-bit mono dan stereo; format lain dibiarkan lewat tanpa diproses.
 */
@OptIn(UnstableApi::class)
class CrossfadeFilterAudioProcessor : BaseAudioProcessor() {

    /** Filter aktif atau tidak. Mematikannya juga mengosongkan riwayat filter. */
    @Volatile
    var enabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) filter.reset()
            }
        }

    /** Cutoff saat ini (Hz); diubah terus selama animasi crossfade. */
    @Volatile
    var cutoffFrequencyHz: Float = 20000f
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    /** LOW_PASS untuk lagu yang keluar, HIGH_PASS untuk lagu yang masuk. */
    @Volatile
    var filterType: BiquadFilter.FilterType = BiquadFilter.FilterType.LOW_PASS
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    private val filter = BiquadFilter()

    @Volatile
    private var coefficientsDirty = true

    private var sampleRate = 0
    private var channelCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        coefficientsDirty = true
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)

        if (!enabled || sampleRate == 0 || (channelCount != 1 && channelCount != 2)) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        if (coefficientsDirty) {
            filter.updateCoefficients(
                cutoffHz = cutoffFrequencyHz,
                sampleRate = sampleRate,
                type = filterType
            )
            coefficientsDirty = false
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())
        if (channelCount == 1) {
            while (inputBuffer.remaining() >= 2) {
                output.putShort(toPcm(filter.processLeft(toUnit(inputBuffer.short))))
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                val left = filter.processLeft(toUnit(inputBuffer.short))
                val right = filter.processRight(toUnit(inputBuffer.short))
                output.putShort(toPcm(left))
                output.putShort(toPcm(right))
            }
        }
        output.flip()
    }

    private fun toUnit(sample: Short): Double = sample.toDouble() / Short.MAX_VALUE

    private fun toPcm(value: Double): Short =
        (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()

    override fun onFlush() {
        super.onFlush()
        filter.reset()
    }

    override fun onReset() {
        super.onReset()
        enabled = false
        cutoffFrequencyHz = 20000f
        filterType = BiquadFilter.FilterType.LOW_PASS
        filter.reset()
    }
}

/**
 * Pabrik renderer yang menyisipkan [filter] ke rantai audio pemutar. Rantai bawaan Media3
 * (lewati senyap, ubah kecepatan) tetap ikut, jadi pengaturan "Lewati bagian senyap" tetap jalan.
 * Satu filter untuk satu pemutar: pemutar utama dan pemutar kedua di CrossfadeManager masing-masing
 * punya filter sendiri.
 *
 * [normalizer] (penyama volume antar lagu) dipasang *sebelum* filter DJ: ia harus mengukur audio asli,
 * bukan audio yang sudah diredam filter saat crossfade, kalau tidak ia akan "melawan" sapuan filter.
 */
@OptIn(UnstableApi::class)
class DjFilterRenderersFactory(
    context: Context,
    private val filter: CrossfadeFilterAudioProcessor,
    private val normalizer: LoudnessNormalizerAudioProcessor? = null
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setAudioProcessors(listOfNotNull<AudioProcessor>(normalizer, filter).toTypedArray())
            .build()
}