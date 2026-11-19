package com.fahrii.musicapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.fahrii.musicapp.data.SettingsRepository
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Crossfade antar lagu. ExoPlayer tidak punya crossfade bawaan, jadi dipakai dua pemutar:
 *
 * 1. Beberapa detik sebelum lagu habis, pemutar kedua ([tail]) disiapkan diam-diam untuk lagu
 *    yang sama, berhenti sedikit sebelum titik transisi.
 * 2. SYNC: sesaat sebelum titik transisi, [tail] dijalankan dalam keadaan senyap sampai posisinya
 *    sejajar dengan pemutar utama. Ini menghilangkan jeda: pemutar kedua sudah "panas" sebelum
 *    pemutar utama dipindah ke lagu berikutnya.
 * 3. HANDOFF: suara lagu lama dipindah dari pemutar utama ke [tail] lewat ramp pendek (100 ms).
 *    Isinya sama, jadi tidak terdengar. Setelah itu pemutar utama senyap dan aman dipindah ke
 *    lagu berikutnya.
 * 4. FADING: [tail] meneruskan sisa lagu lama (volume turun), pemutar utama memutar lagu baru
 *    (volume naik). Kurvanya equal-power dengan ujung yang landai, jadi awal lagu baru tidak
 *    "muncul tiba-tiba". Setelah selesai, [tail] dibuang. Notifikasi, sesi media, dan antrean
 *    tetap hanya memakai pemutar utama.
 *
 * Kalau syarat tidak terpenuhi (crossfade mati, ulang satu lagu, tidak ada lagu berikutnya, lagu
 * terlalu pendek, atau [tail] tidak siap/tidak sejajar pada waktunya), tidak ada yang dilakukan dan
 * lagu berganti seperti biasa. Semuanya berjalan di thread utama.
 */
@OptIn(UnstableApi::class)
class CrossfadeManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val dataSourceFactory: DataSource.Factory,
    private val settings: SettingsRepository
) {
    private enum class Phase { IDLE, SYNC, HANDOFF, FADING }

    private val handler = Handler(Looper.getMainLooper())

    // Pemutar kedua: memutar sisa lagu lama selama lagu baru masuk
    private var tail: ExoPlayer? = null
    private var tailMediaId: String? = null

    // Posisi (ms) tempat pemutar kedua mulai bersuara, sudah termasuk TAIL_COMPENSATION_MS
    private var tailStartMs = 0L
    private var tailPlayStartPosMs = 0L

    private var phase = Phase.IDLE
    private val fading: Boolean get() = phase == Phase.FADING
    private var phaseStartedAtMs = 0L

    private var fadeDurationMs = 0L
    private var lastFadeTickMs = 0L

    // Posisi awal masing-masing pemutar saat fade mulai. Kemajuan fade dibaca dari posisi putar
    // yang sebenarnya (jam audio), bukan dari stopwatch, supaya selalu sinkron dengan suara.
    private var outgoingStartMs = 0L
    private var incomingStartMs = 0L
    private var fadeStartedAtMs = 0L

    // Kemajuan fade hanya boleh naik (posisi yang dilaporkan kadang melompat mundur sedikit)
    private var incomingProgress = 0f
    private var outgoingProgress = 0f

    // True selama kita sendiri memindahkan pemutar utama ke lagu berikutnya
    private var selfSeeking = false
    private var released = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) scheduleTick()
        }

        // Pemutar kedua ikut dijeda/dilanjutkan bersama pemutar utama
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (phase != Phase.IDLE) tail?.playWhenReady = playWhenReady
        }

        // Ganti lagu yang bukan dari kita (mis. pengguna menekan next): batalkan crossfade
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isOwnEvent()) interrupted()
        }

        // Pengguna menggeser posisi: crossfade yang sedang berjalan dibatalkan, yang tertunda disiapkan ulang
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (isOwnEvent()) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                interrupted()
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (released) return
            when (phase) {
                Phase.FADING -> stepFade()
                Phase.HANDOFF -> stepHandoff()
                Phase.SYNC -> stepSync()
                Phase.IDLE -> checkTrigger()
            }

            when {
                // Selama ada pemutar kedua yang siap atau crossfade berjalan, cek rapat supaya
                // titik mulainya tepat
                phase != Phase.IDLE || (tail != null && player.isPlaying) ->
                    handler.postDelayed(this, FAST_TICK_MS)
                player.isPlaying -> handler.postDelayed(this, POLL_MS)
                // Selain itu berhenti; dihidupkan lagi oleh onIsPlayingChanged(true)
            }
        }
    }

    init {
        player.addListener(listener)
        if (player.isPlaying) scheduleTick()
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        phase = Phase.IDLE
        releaseTail()
    }

    private fun scheduleTick() {
        if (released) return
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    /** Event yang datang dari pemindahan lagu oleh kita sendiri, bukan dari pengguna. */
    private fun isOwnEvent(): Boolean =
        selfSeeking || (fading && SystemClock.elapsedRealtime() - fadeStartedAtMs < OWN_EVENT_WINDOW_MS)

    private fun interrupted() {
        when (phase) {
            Phase.FADING -> finishFade()
            Phase.SYNC, Phase.HANDOFF -> abortSync()
            Phase.IDLE -> releaseTail()
        }
    }

    // ---- Persiapan dan pemicu ----

    private fun checkTrigger() {
        // Pengaman: volume tidak boleh tertinggal di bawah 1 kalau tidak sedang crossfade
        if (player.volume != 1f) player.volume = 1f

        val crossfadeMs = settings.crossfadeMs()
        val item = player.currentMediaItem
        val duration = player.duration

        val eligible = crossfadeMs > 0L &&
                item != null &&
                duration != C.TIME_UNSET && duration > 0L &&
                player.isPlaying &&
                !player.isCurrentMediaItemLive &&
                player.repeatMode != Player.REPEAT_MODE_ONE &&
                player.hasNextMediaItem()
        if (!eligible || item == null) {
            releaseTail()
            return
        }

        // Lagu pendek: crossfade tidak boleh lebih dari sepertiga lagu
        val fadeMs = minOf(crossfadeMs, duration / 3)
        if (fadeMs < MIN_FADE_MS) {
            releaseTail()
            return
        }

        if (tail != null && tailMediaId != item.mediaId) releaseTail()

        val remaining = duration - player.currentPosition
        val syncAtRemaining = fadeMs + SYNC_LEAD_MS
        when {
            // Masih jauh dari ujung lagu
            remaining > syncAtRemaining + PREPARE_AHEAD_MS -> releaseTail()

            // Sudah dekat: siapkan pemutar kedua (sekali) supaya siap saat waktunya tiba
            remaining > syncAtRemaining ->
                if (tail == null) prepareTail(item, duration - syncAtRemaining + TAIL_COMPENSATION_MS)

            // Terlalu dekat ke ujung lagu: tidak jadi crossfade
            remaining <= MIN_START_REMAINING_MS -> releaseTail()

            // Waktunya sinkronisasi, tapi hanya kalau pemutar kedua sudah siap; kalau tidak,
            // biarkan lagu berganti seperti biasa
            else -> {
                val t = tail ?: return
                if (t.playbackState != Player.STATE_READY) return
                val lateBy = player.currentPosition - (tailStartMs - TAIL_COMPENSATION_MS)
                when {
                    lateBy < 0L -> Unit // belum sampai titiknya
                    lateBy > MAX_SYNC_LATE_MS -> reseekTail(t) // siap terlambat: susul ke posisi sekarang
                    else -> startSync(t)
                }
            }
        }
    }

    private fun prepareTail(item: MediaItem, startMs: Long) {
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false // fokus audio milik pemutar utama
            )
            .build()
        p.volume = 0f
        p.setMediaItem(item, startMs)
        p.playWhenReady = false
        p.prepare()
        tail = p
        tailMediaId = item.mediaId
        tailStartMs = startMs
    }

    /** Pemutar kedua siap terlambat: pindahkan ke sedikit di depan posisi sekarang dan tunggu lagi. */
    private fun reseekTail(t: ExoPlayer) {
        tailStartMs = player.currentPosition + RESEEK_AHEAD_MS
        t.seekTo(tailStartMs)
    }

    // ---- SYNC: pemutar kedua bersuara senyap, menyusul pemutar utama ----

    private fun startSync(t: ExoPlayer) {
        t.volume = 0f
        tailPlayStartPosMs = t.currentPosition
        t.playWhenReady = player.playWhenReady
        phase = Phase.SYNC
        phaseStartedAtMs = SystemClock.elapsedRealtime()
    }

    private fun stepSync() {
        val t = tail
        val item = player.currentMediaItem
        if (t == null || item == null || item.mediaId != tailMediaId ||
            !player.hasNextMediaItem() || player.repeatMode == Player.REPEAT_MODE_ONE
        ) {
            abortSync()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - phaseStartedAtMs > SYNC_TIMEOUT_MS) {
            abortSync()
            return
        }

        // Tunggu sampai pemutar kedua benar-benar bersuara (posisinya sudah bergerak)
        if (!t.isPlaying || t.currentPosition - tailPlayStartPosMs < SYNC_MIN_RUN_MS) return

        // Dua salinan harus berjalan hampir bersamaan; kalau melenceng jauh akan terdengar gema
        if (abs(t.currentPosition - player.currentPosition) > MAX_SYNC_OFFSET_MS) {
            abortSync()
            return
        }

        phase = Phase.HANDOFF
        phaseStartedAtMs = now
    }

    // ---- HANDOFF: suara lagu lama pindah dari pemutar utama ke pemutar kedua ----

    private fun stepHandoff() {
        val t = tail
        if (t == null) {
            abortSync()
            return
        }
        // Isi kedua pemutar sama, jadi ramp linier menjaga level tetap rata
        val x = ((SystemClock.elapsedRealtime() - phaseStartedAtMs).toFloat() / HANDOFF_MS)
            .coerceIn(0f, 1f)
        player.volume = 1f - x
        t.volume = x
        if (x >= 1f) beginFade(t)
    }

    private fun abortSync() {
        phase = Phase.IDLE
        player.volume = 1f
        releaseTail()
    }

    // ---- Fade ----

    private fun beginFade(t: ExoPlayer) {
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) {
            abortSync()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val outgoingPos = t.currentPosition
        val remaining = (duration - outgoingPos).coerceAtLeast(MIN_REMAINING_MS)

        // Pemutar kedua sudah memegang suara lagu lama; pemutar utama senyap, aman dipindah
        t.volume = 1f
        player.volume = 0f
        selfSeeking = true
        try {
            player.seekToNextMediaItem()
        } finally {
            selfSeeking = false
        }

        phase = Phase.FADING
        fadeDurationMs = remaining // sisa lagu lama: fade selesai tepat saat lagu lama habis
        outgoingStartMs = outgoingPos
        incomingStartMs = player.currentPosition
        incomingProgress = 0f
        outgoingProgress = 0f
        lastFadeTickMs = now
        fadeStartedAtMs = now
    }

    private fun stepFade() {
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastFadeTickMs).coerceAtLeast(0L)
        lastFadeTickMs = now

        // Kemajuan tiap lagu dihitung dari posisi putarnya sendiri. Jadi:
        // - lagu baru baru naik setelah benar-benar terdengar (stream lambat tidak bikin lonjakan),
        // - jeda/lanjut ikut otomatis karena posisi berhenti bergerak.
        incomingProgress = maxOf(incomingProgress, progress(player.currentPosition - incomingStartMs))

        val outgoingPlayer = tail
        val outgoingEnded = outgoingPlayer == null || outgoingPlayer.playbackState == Player.STATE_ENDED
        outgoingProgress = if (outgoingEnded) {
            1f
        } else {
            // Lagu lama tidak boleh turun lebih cepat daripada naiknya lagu baru. Kalau lagu baru
            // telat bersuara (buffering/pindah lagu), lagu lama menunggu dulu, jadi tidak ada
            // lubang senyap di tengah transisi.
            maxOf(
                outgoingProgress,
                minOf(progress(outgoingPlayer!!.currentPosition - outgoingStartMs), incomingProgress)
            )
        }

        // Equal-power (sin/cos) dengan ujung landai: lagu baru naik pelan dari nol dan lagu lama
        // turun pelan ke nol. Titik tengah tetap sama (-3 dB masing-masing), tenaga total rata.
        player.volume = smooth(player.volume, sin(shape(incomingProgress) * HALF_PI), dt)
        outgoingPlayer?.let { it.volume = smooth(it.volume, cos(shape(outgoingProgress) * HALF_PI), dt) }

        if (incomingProgress >= 1f) finishFade()
    }

    private fun progress(playedMs: Long): Float =
        (playedMs.toFloat() / fadeDurationMs).coerceIn(0f, 1f)

    // Smoothstep: kemiringan nol di kedua ujung, sehingga tidak ada "ketukan" di awal/akhir fade
    private fun shape(x: Float): Float = x * x * (3f - 2f * x)

    // Penghalus bergantung waktu (bukan per tick), supaya hasilnya sama walau tick sempat telat
    private fun smooth(current: Float, target: Float, dtMs: Long): Float {
        val alpha = 1f - exp(-dtMs / SMOOTH_TAU_MS)
        return current + (target - current) * alpha
    }

    private fun finishFade() {
        phase = Phase.IDLE
        releaseTail()
        player.volume = 1f
    }

    private fun releaseTail() {
        tail?.release()
        tail = null
        tailMediaId = null
    }

    private companion object {
        const val POLL_MS = 200L
        // ~60 langkah volume per detik; 50 ms (20/detik) terdengar bergelombang
        const val FAST_TICK_MS = 16L

        // Konstanta waktu penghalus volume (ms); kecil = cepat mengikuti target
        const val SMOOTH_TAU_MS = 24f

        // Pemutar kedua mulai disiapkan sekian ms sebelum titik sinkronisasi
        const val PREPARE_AHEAD_MS = 8_000L

        // Crossfade lebih pendek dari ini tidak terdengar, jadi dilewati
        const val MIN_FADE_MS = 1_000L

        // Sisa lagu minimal untuk masih memulai sinkronisasi
        const val MIN_START_REMAINING_MS = 900L

        // Sisa lagu minimal untuk lamanya fade
        const val MIN_REMAINING_MS = 600L

        // Sinkronisasi dimulai sekian ms sebelum fade (waktu bagi pemutar kedua untuk "panas")
        const val SYNC_LEAD_MS = 350L

        // Perkiraan jeda antara pemutar kedua dijalankan dan suaranya keluar. Pemutar kedua
        // dimulai sebesar ini lebih maju supaya sejajar dengan pemutar utama. Kalau masih terdengar
        // gema tipis di awal crossfade, ubah nilai ini (mis. 20-80).
        const val TAIL_COMPENSATION_MS = 40L

        // Pemutar kedua harus sudah berjalan sekian ms sebelum suaranya diambil alih
        const val SYNC_MIN_RUN_MS = 80L

        // Batas waktu menunggu pemutar kedua bersuara; lewat dari ini crossfade dibatalkan
        const val SYNC_TIMEOUT_MS = 1_500L

        // Pemutar kedua siap lebih dari ini setelah titiknya: susul ulang
        const val MAX_SYNC_LATE_MS = 250L
        const val RESEEK_AHEAD_MS = 700L

        // Selisih posisi maksimal dua salinan lagu lama sebelum suaranya dipindah
        const val MAX_SYNC_OFFSET_MS = 200L

        // Lama ramp pemindahan suara lagu lama ke pemutar kedua
        const val HANDOFF_MS = 100L

        // Event dalam jangka ini setelah crossfade dimulai dianggap berasal dari kita
        const val OWN_EVENT_WINDOW_MS = 500L

        val HALF_PI = (Math.PI / 2).toFloat()
    }
}