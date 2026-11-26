package com.fahrii.musicapp.audio

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * Pembungkus pemutar yang membuat Play/Pause terasa seperti Spotify:
 * - Pause: volume turun halus dulu, baru benar-benar dijeda (tidak mati mendadak).
 * - Play: pemutar jalan dari volume nol, lalu naik halus (tidak langsung keras).
 *
 * Dipasang sebagai pemutar milik MediaSession, jadi semua jalur kontrol ikut mendapat efeknya:
 * tombol di app, notifikasi, widget, kontrol Bluetooth/headset, dan timer tidur. Jalur yang
 * memang harus berhenti seketika (fokus audio hilang, headset dicabut) memanggil ExoPlayer
 * langsung, jadi tidak lewat sini dan tetap berhenti tanpa fade.
 *
 * Volume akhir = [gain] x volume milik crossfade. Crossfade ikut membaca [gain] (lihat parameter
 * `userGain` di CrossfadeManager), jadi keduanya tidak saling menimpa. Semua pemanggilan harus
 * dari main thread.
 */
@OptIn(UnstableApi::class)
class FadingPlayer(private val exo: Player) : ForwardingPlayer(exo) {

    /** Pengali volume dari fade Play/Pause, 0..1. */
    @Volatile
    var gain = 1f
        private set

    /**
     * Penerap gain ke pemutar. Diisi CrossfadeManager supaya selama crossfade berjalan, volume
     * tetap dipegang satu pihak saja. Kalau null, gain langsung diterapkan ke volume pemutar.
     */
    var gainApplier: ((Float) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())

    // Posisi fade pada skala "tingkat" 0..1; gain = tingkat^2 supaya turun/naiknya terdengar rata
    // (telinga mendengar volume secara logaritmik, ramp linier terasa terlalu cepat di ujung).
    private var level = 1f

    private var ramping = false
    private var rampFrom = 1f
    private var rampTo = 1f
    private var rampStartedAt = 0L
    private var rampDurationMs = 0L

    // Setelah fade-out selesai, pemutar dijeda sungguhan
    private var pauseWhenDone = false

    // Play sudah diminta tapi suara belum keluar (buffering): fade-in menunggu suara benar-benar mulai
    private var fadeInPending = false

    // True hanya saat kita sendiri yang menjeda pemutar, untuk membedakannya dari jeda pihak lain
    private var selfPause = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && fadeInPending) {
                fadeInPending = false
                startRamp(1f, FADE_IN_MS)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                // Pemutar dinyalakan pihak lain (tidak lewat kita) saat volume masih 0: naikkan
                // supaya tidak senyap selamanya
                if (!ramping && !fadeInPending && level < 1f) startRamp(1f, FADE_IN_MS)
            } else if (!selfPause) {
                // Dijeda pihak lain (fokus audio, headset dicabut, error): batalkan fade yang
                // sedang berjalan dan kembalikan volume ke normal
                cancelRamp()
                pauseWhenDone = false
                fadeInPending = false
                setLevel(1f)
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!ramping) return
            val t = ((SystemClock.elapsedRealtime() - rampStartedAt).toFloat() / rampDurationMs)
                .coerceIn(0f, 1f)
            setLevel(rampFrom + (rampTo - rampFrom) * t)
            if (t >= 1f) finishRamp() else handler.postDelayed(this, TICK_MS)
        }
    }

    // Pendengar milik sesi/pengontrol; dicatat sendiri supaya status "dijeda" bisa diumumkan
    // lebih awal dari pemutar aslinya (lihat announce)
    private val clients = CopyOnWriteArrayList<Player.Listener>()

    init {
        exo.addListener(listener)
    }

    override fun addListener(listener: Player.Listener) {
        clients.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        clients.remove(listener)
        super.removeListener(listener)
    }

    // Selama fade-out, suara masih ada tapi status yang dilaporkan sudah "dijeda". Kalau tidak,
    // UI (yang langsung menampilkan Play saat Pause ditekan) dikembalikan lagi ke Pause oleh
    // status sesi, lalu berubah ke Play lagi setelah fade selesai: tombol terlihat berkedip.
    override fun getPlayWhenReady(): Boolean = if (pauseWhenDone) false else super.getPlayWhenReady()

    override fun isPlaying(): Boolean = if (pauseWhenDone) false else super.isPlaying()

    /** Umumkan perubahan status play/pause ke sesi dan pengontrol. Dipanggil setelah [pauseWhenDone] diubah. */
    private fun announce(playWhenReady: Boolean) {
        val events = Player.Events(
            FlagSet.Builder()
                .addAll(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_IS_PLAYING_CHANGED)
                .build()
        )
        for (client in clients) {
            client.onPlayWhenReadyChanged(
                playWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            client.onIsPlayingChanged(isPlaying)
            client.onEvents(this, events)
        }
    }

    override fun play() = setPlayWhenReady(true)

    override fun pause() = setPlayWhenReady(false)

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) requestPlay() else requestPause()
    }

    override fun release() {
        cancelRamp()
        exo.removeListener(listener)
        super.release()
    }

    private fun requestPlay() {
        val pauseWasPending = pauseWhenDone
        pauseWhenDone = false

        if (exo.playWhenReady) {
            // Sudah berjalan. Kalau tadi sedang fade-out (Pause ditekan lalu Play), balik naik
            // dari volume saat ini, tanpa loncatan.
            if (pauseWasPending) announce(true)
            if (!fadeInPending && level < 1f) startRamp(1f, FADE_IN_MS)
            return
        }

        // Mulai dari senyap, lalu naik setelah suara benar-benar keluar
        cancelRamp()
        setLevel(0f)
        fadeInPending = true
        exo.play()
        if (fadeInPending && exo.isPlaying) {
            fadeInPending = false
            startRamp(1f, FADE_IN_MS)
        }
    }

    private fun requestPause() {
        fadeInPending = false

        // Belum ada suara yang bisa diturunkan (sudah dijeda, atau masih buffering): jeda biasa
        if (!exo.playWhenReady || !exo.isPlaying) {
            cancelRamp()
            pauseWhenDone = false
            exo.pause()
            return
        }

        if (pauseWhenDone) return // fade-out sudah berjalan
        pauseWhenDone = true
        announce(false) // UI langsung menampilkan Play, suaranya menyusul turun
        startRamp(0f, FADE_OUT_MS)
    }

    /** Durasi dihitung sebanding jarak yang ditempuh, jadi membalik arah di tengah fade tetap mulus. */
    private fun startRamp(to: Float, fullDurationMs: Long) {
        handler.removeCallbacks(ticker)
        rampFrom = level
        rampTo = to
        rampDurationMs = (abs(to - level) * fullDurationMs).toLong()
        if (rampDurationMs <= 0L) {
            ramping = true
            finishRamp()
            return
        }
        rampStartedAt = SystemClock.elapsedRealtime()
        ramping = true
        handler.post(ticker)
    }

    private fun finishRamp() {
        handler.removeCallbacks(ticker)
        ramping = false
        setLevel(rampTo)
        if (pauseWhenDone) {
            pauseWhenDone = false
            // Volume sudah 0 saat dijeda, jadi tidak ada bunyi tersisa; Play berikutnya mulai dari 0
            selfPause = true
            try {
                exo.pause()
            } finally {
                selfPause = false
            }
        }
    }

    private fun cancelRamp() {
        handler.removeCallbacks(ticker)
        ramping = false
    }

    private fun setLevel(value: Float) {
        level = value.coerceIn(0f, 1f)
        gain = level * level
        val applier = gainApplier
        if (applier != null) applier(gain) else exo.volume = gain
    }

    private companion object {
        // Fade-out sedikit lebih pendek supaya tombol Pause tetap terasa responsif
        const val FADE_OUT_MS = 350L
        const val FADE_IN_MS = 400L

        // ~60 langkah volume per detik, sama seperti fade crossfade
        const val TICK_MS = 16L
    }
}