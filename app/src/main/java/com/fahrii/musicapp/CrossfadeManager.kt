package com.fahrii.musicapp

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.fahrii.musicapp.audio.BiquadFilter
import com.fahrii.musicapp.audio.QueuePlayer
import com.fahrii.musicapp.audio.TrackPlayer
import com.fahrii.musicapp.data.SettingsRepository
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Crossfade gaya SimpMusic: tiap lagu punya ExoPlayer sendiri dan lagu berikutnya sudah siap
 * (precache) sebelum transisi, jadi crossfade murni mengatur volume dua pemutar, tanpa
 * sinkronisasi posisi. Lagu lama turun mengikuti cos² dan lagu baru naik mengikuti sin²
 * (jumlahnya selalu 1), jadi lagu baru naik pelan di awal dan terdengar sebagai fade in.
 * Kemajuan fade dihitung dari jam dinding, dan berhenti saat dijeda.
 *
 * Kalau syarat tidak terpenuhi (crossfade mati, ulang satu lagu, tidak ada lagu berikutnya,
 * lagu lebih pendek dari max(20 detik, 3x lama fade), atau lagu berikutnya belum siap pada
 * waktunya), tidak ada yang dilakukan dan lagu berganti seperti biasa.
 *
 * Label "Crossfading" di UI menyala [LABEL_LEAD_MS] sebelum fade (lagu lama masih tampil), lalu
 * menyambung ke fase fade tanpa putus.
 */
@OptIn(UnstableApi::class)
class CrossfadeManager(
    private val queue: QueuePlayer,
    private val settings: SettingsRepository
) {
    private val handler = Handler(Looper.getMainLooper())

    private var fading = false
    private var fadeMs = 0L
    private var elapsedMs = 0L
    private var lastTickAt = 0L
    private var dj = false
    private var outgoing: TrackPlayer? = null
    private var preparingShown = false
    private var released = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) schedule()
            else if (!fading) showPreparing(false) // dijeda: jangan biarkan label menggantung
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (released) return
            if (fading) stepFade() else checkTrigger()
            when {
                fading -> handler.postDelayed(this, FAST_TICK_MS)
                queue.isPlaying -> handler.postDelayed(this, POLL_MS)
                // selain itu berhenti; dihidupkan lagi oleh onIsPlayingChanged(true)
            }
        }
    }

    init {
        queue.addListener(listener)
        // Next/seek/ubah antrean di tengah fade: QueuePlayer sudah membuang pemutar lama
        queue.onCrossfadeAborted = {
            fading = false
            outgoing = null
            preparingShown = false
            CrossfadeState.setCrossfading(false)
        }
        if (queue.isPlaying) schedule()
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        queue.removeListener(listener)
        queue.onCrossfadeAborted = null
        if (fading) CrossfadeState.setCrossfading(false) else showPreparing(false)
        fading = false
        outgoing = null
    }

    private fun schedule() {
        if (released) return
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    /** Nyalakan/matikan label persiapan di UI; hanya menyentuh state kalau nilainya berubah. */
    private fun showPreparing(show: Boolean) {
        if (show == preparingShown) return
        preparingShown = show
        CrossfadeState.setPreparing(show)
    }

    /**
     * Sisa waktu lagu aktif (ms) kalau lagu ini akan di-crossfade ke lagu berikutnya, atau null
     * kalau tidak: crossfade mati, dijeda, ulang satu lagu, siaran langsung, lagu terlalu pendek,
     * tidak ada lagu berikutnya, atau lagu berikutnya belum siap.
     */
    private fun eligibleRemainingMs(setting: Long): Long? {
        if (setting <= 0L || !queue.isPlaying) return null
        val player = queue.currentTrack?.player ?: return null
        if (queue.repeatMode == Player.REPEAT_MODE_ONE || player.isCurrentMediaItemLive) return null

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return null
        // Lagu pendek tidak di-crossfade (aturan SimpMusic, batasnya ikut lama fade)
        if (duration < maxOf(MIN_CROSSFADE_TRACK_MS, setting * 3)) return null

        if (!queue.hasCrossfadeTarget() || !queue.isNextReady()) return null
        return duration - player.currentPosition
    }

    private fun checkTrigger() {
        val setting = settings.crossfadeMs()
        val remaining = eligibleRemainingMs(setting)

        // Label muncul di lagu lama beberapa detik sebelum fade, hanya kalau transisi memang akan terjadi
        showPreparing(remaining != null && remaining <= setting + LABEL_LEAD_MS)

        if (remaining == null || remaining > setting) return // belum waktunya
        if (remaining < MIN_FADE_MS) return                   // sudah terlalu mepet: ganti lagu biasa
        begin(minOf(setting, remaining))
    }

    private fun begin(durationMs: Long) {
        // Status dinyalakan SEBELUM lagu dipindah, supaya UI memakai transisi panjang saat lagu berganti.
        // start() juga mematikan label persiapan di saat yang sama, jadi labelnya tidak berkedip.
        preparingShown = false
        CrossfadeState.start(durationMs)
        val out = queue.startCrossfade()
        if (out == null) {
            CrossfadeState.setCrossfading(false)
            return
        }
        outgoing = out
        fadeMs = durationMs
        elapsedMs = 0L
        lastTickAt = SystemClock.elapsedRealtime()
        dj = settings.crossfadeDjMode()
        fading = true

        if (dj) {
            out.filter.apply {
                filterType = BiquadFilter.FilterType.LOW_PASS
                cutoffFrequencyHz = LPF_START_HZ
                enabled = true
            }
            queue.currentTrack?.filter?.apply {
                filterType = BiquadFilter.FilterType.HIGH_PASS
                cutoffFrequencyHz = HPF_START_HZ
                enabled = true
            }
        }
    }

    private fun stepFade() {
        val now = SystemClock.elapsedRealtime()
        if (queue.playWhenReady) elapsedMs += now - lastTickAt // jam fade berhenti saat dijeda
        lastTickAt = now

        val p = (elapsedMs.toFloat() / fadeMs).coerceIn(0f, 1f)
        val angle = p * HALF_PI
        val c = cos(angle)
        val s = sin(angle)
        // Dikuadratkan: lagu baru naik pelan di awal (terdengar sebagai fade in), lagu lama turun pelan.
        // c² + s² = 1, jadi jumlah volume keduanya tetap 1 sepanjang transisi
        queue.setFade(c * c, s * s)

        if (dj) {
            val sig = sigmoid(p)
            outgoing?.filter?.cutoffFrequencyHz = exponentialInterpolate(LPF_START_HZ, LPF_END_HZ, sig)
            queue.currentTrack?.filter?.cutoffFrequencyHz = exponentialInterpolate(HPF_START_HZ, HPF_END_HZ, sig)
        }
        if (p >= 1f) finish()
    }

    private fun finish() {
        fading = false
        outgoing = null
        queue.finishCrossfade() // buang pemutar lama, matikan filter pemutar baru
        CrossfadeState.setCrossfading(false)
    }

    /** Kurva S: datar di awal dan akhir, curam di tengah, seperti crossfader mixer DJ. */
    private fun sigmoid(t: Float): Float = 1f / (1f + exp(-DJ_FILTER_SIGMOID_K * (t - 0.5f)))

    /** Interpolasi eksponensial; terdengar merata karena telinga mendengar frekuensi secara logaritmik. */
    private fun exponentialInterpolate(start: Float, end: Float, t: Float): Float {
        if (start <= 0f || end <= 0f) return end
        return exp(ln(start) + (ln(end) - ln(start)) * t)
    }

    private companion object {
        const val POLL_MS = 50L
        const val FAST_TICK_MS = 16L
        const val MIN_CROSSFADE_TRACK_MS = 20_000L
        const val MIN_FADE_MS = 1_000L

        /** Berapa lama sebelum fade label "Crossfading" sudah tampil di lagu lama. Ubah sesuai selera. */
        const val LABEL_LEAD_MS = 3_000L

        const val DJ_FILTER_SIGMOID_K = 6f
        const val LPF_START_HZ = 20000f
        const val LPF_END_HZ = 200f
        const val HPF_START_HZ = 2000f
        const val HPF_END_HZ = 20f
        val HALF_PI = (Math.PI / 2).toFloat()
    }
}