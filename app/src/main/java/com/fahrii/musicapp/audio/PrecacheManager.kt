package com.fahrii.musicapp.audio

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Menyiapkan SATU pemutar untuk lagu berikutnya (sudah prepare, belum play).
 * Kuncinya uid entri antrean, bukan mediaId, karena lagu yang sama bisa muncul dua kali.
 */
@OptIn(UnstableApi::class)
class PrecacheManager(private val factory: TrackPlayerFactory) {

    private var uid: Any? = null
    private var track: TrackPlayer? = null
    private var listener: Player.Listener? = null

    /** Atur target. Target yang sama dibiarkan; target berbeda membuang yang lama. */
    fun target(newUid: Any?, item: MediaItem?) {
        if (newUid != null && newUid == uid) return
        clear()
        if (newUid == null || item == null) return

        val t = factory.create(item)
        var attempts = 0
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // prepare() ulang membuka stream lagi, jadi URL di-resolve ulang oleh ResolvingDataSource
                if (attempts++ < MAX_RETRY) t.player.prepare()
            }
        }
        t.player.addListener(l)
        uid = newUid
        track = t
        listener = l
    }

    fun peek(forUid: Any): TrackPlayer? = if (forUid == uid) track else null

    /** Ambil pemutar yang sudah disiapkan; setelah ini pemiliknya QueuePlayer. Null kalau gagal/tidak ada. */
    fun take(forUid: Any): TrackPlayer? {
        if (forUid != uid) return null
        val t = track ?: return null
        listener?.let { t.player.removeListener(it) }
        track = null; uid = null; listener = null
        if (t.player.playerError != null) {
            factory.release(t)
            return null
        }
        return t
    }

    fun clear() {
        track?.let { t ->
            listener?.let { t.player.removeListener(it) }
            factory.release(t)
        }
        track = null; uid = null; listener = null
    }

    private companion object {
        const val MAX_RETRY = 2
    }
}