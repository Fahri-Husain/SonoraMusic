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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crossfade antar lagu. ExoPlayer tidak punya crossfade bawaan, jadi dipakai dua pemutar:
 *
 * 1. Beberapa detik sebelum lagu habis, pemutar kedua ([tail]) disiapkan diam-diam untuk lagu
 *    yang sama, dimulai dari titik transisi.
 * 2. Saat waktunya tiba, [tail] meneruskan sisa lagu lama (volume turun), sementara pemutar
 *    utama pindah ke lagu berikutnya (volume naik). Kurvanya equal-power supaya terdengar rata.
 * 3. Setelah selesai, [tail] dibuang. Notifikasi, sesi media, dan antrean tetap hanya memakai
 *    pemutar utama.
 *
 * Kalau syarat tidak terpenuhi (crossfade mati, ulang satu lagu, tidak ada lagu berikutnya, lagu
 * terlalu pendek, atau [tail] belum siap pada waktunya), tidak ada yang dilakukan dan lagu
 * berganti seperti biasa. Semuanya berjalan di thread utama.
 */
@OptIn(UnstableApi::class)
class CrossfadeManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val dataSourceFactory: DataSource.Factory,
    private val settings: SettingsRepository
) {
    private val handler = Handler(Looper.getMainLooper())

    // Pemutar kedua: memutar sisa lagu lama selama lagu baru masuk
    private var tail: ExoPlayer? = null
    private var tailMediaId: String? = null

    private var fading = false
    private var fadeDurationMs = 0L
    private var fadeElapsedMs = 0L
    private var lastFadeTickMs = 0L
    private var fadeStartedAtMs = 0L

    // True selama kita sendiri memindahkan pemutar utama ke lagu berikutnya
    private var selfSeeking = false
    private var released = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) scheduleTick()
        }

        // Lagu lama yang sedang memudar ikut dijeda/dilanjutkan bersama pemutar utama
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (fading) tail?.playWhenReady = playWhenReady
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
            if (fading) stepFade() else checkTrigger()

            when {
                fading -> handler.postDelayed(this, FADE_TICK_MS)
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
        fading = false
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
        if (fading) finishFade() else releaseTail()
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
        when {
            // Masih jauh dari ujung lagu
            remaining > fadeMs + PREPARE_AHEAD_MS -> releaseTail()

            // Sudah dekat: siapkan pemutar kedua (sekali) supaya siap saat waktunya tiba
            remaining > fadeMs -> if (tail == null) prepareTail(item, duration - fadeMs)

            // Waktunya crossfade, tapi hanya kalau pemutar kedua sudah siap; kalau tidak, biarkan
            // lagu berganti seperti biasa
            remaining > MIN_REMAINING_MS && tail?.playbackState == Player.STATE_READY -> startFade()
            remaining <= MIN_REMAINING_MS -> releaseTail()
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
        p.setMediaItem(item, startMs)
        p.playWhenReady = false
        p.prepare()
        tail = p
        tailMediaId = item.mediaId
    }

    private fun startFade() {
        val t = tail ?: return
        val position = player.currentPosition
        val remaining = (player.duration - position).coerceAtLeast(MIN_REMAINING_MS)

        // 1. Pemutar kedua meneruskan lagu lama dari titik ini
        t.seekTo(position)
        t.volume = 1f
        t.playWhenReady = true

        // 2. Pemutar utama pindah ke lagu berikutnya, mulai dari senyap
        player.volume = 0f
        selfSeeking = true
        try {
            player.seekToNextMediaItem()
        } finally {
            selfSeeking = false
        }

        fading = true
        fadeDurationMs = remaining // sisa lagu lama: fade selesai tepat saat lagu lama habis
        fadeElapsedMs = 0L
        lastFadeTickMs = SystemClock.elapsedRealtime()
        fadeStartedAtMs = lastFadeTickMs
    }

    // ---- Fade ----

    private fun stepFade() {
        val now = SystemClock.elapsedRealtime()
        // Waktu fade hanya berjalan selagi pengguna tidak menjeda
        if (player.playWhenReady) fadeElapsedMs += now - lastFadeTickMs
        lastFadeTickMs = now

        val progress = (fadeElapsedMs.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
        val angle = progress * HALF_PI
        player.volume = sin(angle)
        tail?.volume = cos(angle)

        if (progress >= 1f) finishFade()
    }

    private fun finishFade() {
        fading = false
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
        const val FADE_TICK_MS = 50L

        // Pemutar kedua mulai disiapkan sekian ms sebelum titik crossfade
        const val PREPARE_AHEAD_MS = 8_000L

        // Crossfade lebih pendek dari ini tidak terdengar, jadi dilewati
        const val MIN_FADE_MS = 1_000L

        // Terlalu dekat ke ujung lagu: tidak jadi crossfade
        const val MIN_REMAINING_MS = 600L

        // Event dalam jangka ini setelah crossfade dimulai dianggap berasal dari kita
        const val OWN_EVENT_WINDOW_MS = 500L

        val HALF_PI = (Math.PI / 2).toFloat()
    }
}
