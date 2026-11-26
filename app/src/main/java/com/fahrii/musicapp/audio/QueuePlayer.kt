package com.fahrii.musicapp.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Pemutar virtual berantrean untuk MediaSession. Tiap lagu dimainkan ExoPlayer-nya sendiri
 * ([TrackPlayer]); lagu berikutnya sudah disiapkan oleh [PrecacheManager]. Pergantian lagu =
 * mempromosikan pemutar yang sudah siap, dan crossfade hanya mengatur volume dua pemutar
 * (lihat CrossfadeManager), tanpa sinkronisasi posisi. Semua dipanggil dari main thread.
 *
 * Shuffle: SimpleBasePlayer (media3 1.5.1) belum mendukung urutan acak, jadi shuffle dilakukan
 * dengan mengacak urutan lagu SETELAH lagu yang sedang diputar; urutan asli disimpan dan
 * dipulihkan saat shuffle dimatikan.
 */
@OptIn(UnstableApi::class)
class QueuePlayer(
    context: Context,
    private val factory: TrackPlayerFactory
) : SimpleBasePlayer(Looper.getMainLooper()), AudioFocusController.Callback {

    private class Entry(val item: MediaItem) {
        // Diganti saat ulang-satu-lagu supaya dilaporkan sebagai transisi baru (alasan AUTO)
        var uid: Any = Any()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val focus = AudioFocusController(context, this)
    private val precache = PrecacheManager(factory)

    private val entries = mutableListOf<Entry>()
    private var original: MutableList<Entry>? = null // urutan asli selama shuffle menyala
    private var index = 0

    private var current: TrackPlayer? = null
    private var outgoing: TrackPlayer? = null // hanya ada selama crossfade

    private var repeat = Player.REPEAT_MODE_OFF
    private var shuffleOn = false
    private var wantPlay = false
    private var wantPlayReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var resumeOnFocusGain = false
    private var ended = false
    private var error: PlaybackException? = null
    private var retried = false
    private var autoTransitionPending = false

    // Volume akhir = userGain (fade Play/Pause) x duck (fokus) x faktor fade crossfade
    private var userGain = 1f
    private var duck = 1f
    private var fadeOut = 1f
    private var fadeIn = 1f

    /** Dipanggil kalau crossfade dibatalkan dari luar CrossfadeManager (next/seek/ubah antrean). */
    var onCrossfadeAborted: (() -> Unit)? = null

    val currentTrack: TrackPlayer? get() = current

    // ---------------------------------------------------------------- State untuk MediaSession

    override fun getState(): State {
        val cur = current?.player
        val playlist = entries.mapIndexed { i, e ->
            val b = MediaItemData.Builder(e.uid).setMediaItem(e.item).setIsSeekable(true)
            if (i == index && cur != null) {
                val d = cur.duration
                if (d != C.TIME_UNSET && d > 0L) b.setDurationUs(d * 1000L)
            }
            b.build()
        }

        val playbackState = when {
            entries.isEmpty() || error != null -> Player.STATE_IDLE
            ended -> Player.STATE_ENDED
            cur == null -> Player.STATE_BUFFERING
            cur.playbackState == Player.STATE_READY -> Player.STATE_READY
            else -> Player.STATE_BUFFERING // termasuk ENDED sesaat sebelum lagu berikutnya dimuat
        }

        val b = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(wantPlay, wantPlayReason)
            .setPlaybackState(playbackState)
            .setRepeatMode(repeat)
            .setShuffleModeEnabled(shuffleOn)
            .setAudioAttributes(AUDIO_ATTRIBUTES)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(if (entries.isEmpty()) 0 else index.coerceIn(0, entries.lastIndex))
            .setContentPositionMs { current?.player?.currentPosition?.coerceAtLeast(0L) ?: 0L }
            .setContentBufferedPositionMs { current?.player?.bufferedPosition?.coerceAtLeast(0L) ?: 0L }
            .setTotalBufferedDurationMs { current?.player?.totalBufferedDuration?.coerceAtLeast(0L) ?: 0L }

        if (playbackState == Player.STATE_IDLE) {
            b.setPlayerError(error)
        } else {
            b.setIsLoading(cur?.isLoading == true)
        }
        // Sinyal sekali pakai: dipakai SimpleBasePlayer untuk menentukan alasan transisi = AUTO
        if (autoTransitionPending) {
            autoTransitionPending = false
            b.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 0L)
        }
        return b.build()
    }

    // ---------------------------------------------------------------- Listener pemutar aktif

    private val trackListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // Jangan ganti/rilis pemutar di dalam callback-nya sendiri: tunda satu putaran
            if (playbackState == Player.STATE_ENDED) handler.post { onCurrentTrackEnded() }
            else invalidateState()
        }

        override fun onIsLoadingChanged(isLoading: Boolean) = invalidateState()

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) =
            invalidateState() // durasi lagu jadi diketahui

        // Dijeda atau dilanjutkan pihak luar (mis. headset dicabut): ikuti
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady == wantPlay) return
            wantPlay = playWhenReady
            wantPlayReason = reason
            outgoing?.player?.playWhenReady = playWhenReady
            invalidateState()
        }

        override fun onPlayerError(e: PlaybackException) {
            if (!retried) { // sekali: prepare() ulang meresolve URL stream baru
                retried = true
                current?.player?.prepare()
                return
            }
            error = e
            invalidateState()
        }
    }

    private fun onCurrentTrackEnded() {
        val cur = current ?: return
        if (cur.player.playbackState != Player.STATE_ENDED || outgoing != null) return

        if (repeat == Player.REPEAT_MODE_ONE) {
            entries[index].uid = Any() // dilaporkan sebagai transisi baru
            autoTransitionPending = true
            cur.player.seekTo(0L)
            cur.player.playWhenReady = wantPlay
        } else {
            val next = nextIndexOf(index)
            if (next != C.INDEX_UNSET) {
                autoTransitionPending = true
                loadTrack(next, 0L)
            } else {
                ended = true // antrean habis
            }
        }
        invalidateState()
    }

    // ---------------------------------------------------------------- Urutan, shuffle, precache

    private fun nextIndexOf(i: Int): Int = when {
        i + 1 < entries.size -> i + 1
        repeat == Player.REPEAT_MODE_ALL && entries.isNotEmpty() -> 0
        else -> C.INDEX_UNSET
    }

    private fun precacheTargetIndex(): Int {
        if (entries.isEmpty() || repeat == Player.REPEAT_MODE_ONE) return C.INDEX_UNSET
        val n = nextIndexOf(index)
        return if (n == index) C.INDEX_UNSET else n
    }

    private fun refreshPrecache() {
        if (outgoing != null) return // jangan bikin pemutar ketiga selama crossfade
        val e = entries.getOrNull(precacheTargetIndex())
        precache.target(e?.uid, e?.item)
    }

    /** Acak lagu setelah yang sedang diputar; matikan = pulihkan urutan asli. */
    private fun applyShuffle(enabled: Boolean) {
        shuffleOn = enabled
        if (enabled) {
            if (original != null || entries.isEmpty()) return
            original = entries.toMutableList()
            val head = entries.subList(0, index + 1).toList()
            val tail = entries.subList(index + 1, entries.size).shuffled()
            entries.clear(); entries.addAll(head); entries.addAll(tail)
        } else {
            val o = original ?: return
            val cur = entries.getOrNull(index)
            entries.clear(); entries.addAll(o); original = null
            index = if (cur == null) 0 else entries.indexOfFirst { it === cur }.coerceAtLeast(0)
        }
    }

    // ---------------------------------------------------------------- Memuat lagu

    private fun effectiveVolume() = userGain * duck

    private fun applyVolumes() {
        val v = effectiveVolume()
        outgoing?.player?.volume = v * fadeOut
        current?.player?.volume = v * fadeIn
    }

    private fun releaseTrack(t: TrackPlayer) {
        t.player.removeListener(trackListener)
        factory.release(t)
    }

    /** Jadikan lagu [newIndex] lagu aktif: pakai pemutar precache kalau ada, kalau tidak buat baru. */
    private fun loadTrack(newIndex: Int, positionMs: Long) {
        abortCrossfade()
        val old = current
        index = newIndex
        val entry = entries[newIndex]
        val t = precache.take(entry.uid) ?: factory.create(entry.item)
        current = t
        ended = false; error = null; retried = false
        fadeIn = 1f; fadeOut = 1f
        t.player.volume = effectiveVolume()
        if (positionMs > 0L) t.player.seekTo(positionMs)
        t.player.playWhenReady = wantPlay
        t.player.addListener(trackListener)
        old?.let { releaseTrack(it) } // dirilis setelah yang baru jalan, supaya jedanya sekecil mungkin
        refreshPrecache()
    }

    private fun releaseAllTracks() {
        abortCrossfade()
        current?.let { releaseTrack(it) }
        current = null
        precache.clear()
        ended = false; error = null
    }

    // ---------------------------------------------------------------- API untuk CrossfadeManager

    fun setUserGain(gain: Float) { userGain = gain; applyVolumes() }

    /** Faktor volume 0..1 untuk lagu lama ([out]) dan lagu baru ([inn]) selama crossfade. */
    fun setFade(out: Float, inn: Float) { fadeOut = out; fadeIn = inn; applyVolumes() }

    fun hasCrossfadeTarget(): Boolean = outgoing == null && precacheTargetIndex() != C.INDEX_UNSET

    /** Lagu berikutnya sudah siap diputar (precache selesai, tanpa error). */
    fun isNextReady(): Boolean {
        val e = entries.getOrNull(precacheTargetIndex()) ?: return false
        val t = precache.peek(e.uid) ?: return false
        return t.player.playbackState == Player.STATE_READY && t.player.playerError == null
    }

    /**
     * Mulai crossfade: lagu berikutnya jadi lagu aktif (UI langsung pindah), pemutar lama tetap
     * jalan sebagai [outgoing]. Mengembalikan pemutar lama, atau null kalau tidak bisa.
     */
    fun startCrossfade(): TrackPlayer? {
        val out = current ?: return null
        if (outgoing != null) return null
        val n = precacheTargetIndex()
        if (n == C.INDEX_UNSET) return null
        val incoming = precache.take(entries[n].uid) ?: return null

        out.player.removeListener(trackListener)
        outgoing = out
        index = n
        current = incoming
        ended = false; error = null; retried = false
        fadeOut = 1f; fadeIn = 0f
        applyVolumes() // lagu baru senyap SEBELUM play
        incoming.player.addListener(trackListener)
        incoming.player.playWhenReady = wantPlay
        autoTransitionPending = true
        invalidateState()
        return out
    }

    /** Crossfade selesai normal: buang pemutar lama. */
    fun finishCrossfade() {
        val out = outgoing ?: return
        outgoing = null
        factory.release(out)
        fadeOut = 1f; fadeIn = 1f
        current?.filter?.enabled = false
        applyVolumes()
        refreshPrecache()
    }

    /** Crossfade dibatalkan (next/seek/ubah antrean): lagu baru dipertahankan, lagu lama dibuang. */
    private fun abortCrossfade() {
        if (outgoing == null) return
        finishCrossfade()
        onCrossfadeAborted?.invoke()
    }

    // ---------------------------------------------------------------- Fokus audio

    override fun onFocusLoss(transient: Boolean) {
        resumeOnFocusGain = transient && wantPlay
        setWantPlay(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        invalidateState()
    }

    override fun onFocusGain() {
        if (!resumeOnFocusGain) return
        resumeOnFocusGain = false
        setWantPlay(true, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        invalidateState()
    }

    override fun onDuck(ducked: Boolean) {
        duck = if (ducked) DUCK_FACTOR else 1f
        applyVolumes()
    }

    private fun setWantPlay(value: Boolean, reason: Int) {
        wantPlay = value
        wantPlayReason = reason
        if (value) {
            focus.request()
            if (ended) { ended = false; current?.player?.seekTo(0L) }
        } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            focus.abandon()
        }
        current?.player?.playWhenReady = value
        outgoing?.player?.playWhenReady = value
    }

    // ---------------------------------------------------------------- Handler SimpleBasePlayer

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        resumeOnFocusGain = false
        setWantPlay(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (error != null) {
            error = null; retried = false
            current?.player?.prepare()
        }
        return done()
    }

    override fun handleStop(): ListenableFuture<*> {
        setWantPlay(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacksAndMessages(null)
        releaseAllTracks()
        focus.abandon()
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeat = repeatMode
        refreshPrecache()
        return done()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        applyShuffle(shuffleModeEnabled)
        refreshPrecache()
        return done()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        abortCrossfade()
        entries.clear()
        original = null
        entries.addAll(mediaItems.map { Entry(it) })
        if (entries.isEmpty()) {
            releaseAllTracks(); index = 0
            return done()
        }
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, entries.lastIndex)
        if (shuffleOn) applyShuffle(true) // lagu awal tetap di tempatnya, sisanya diacak
        loadTrack(index, if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs)
        return done()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val added = mediaItems.map { Entry(it) }
        val wasEmpty = entries.isEmpty()
        val anchor = entries.getOrNull(index - 1)
        entries.addAll(index, added)
        original?.let { o -> o.addAll(if (anchor == null) 0 else o.indexOf(anchor) + 1, added) }
        if (wasEmpty) loadTrack(0, 0L)
        else {
            if (index <= this.index) this.index += added.size
            refreshPrecache()
        }
        return done()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val removed = entries.subList(fromIndex, toIndex).toList()
        val removingCurrent = index in fromIndex until toIndex
        entries.subList(fromIndex, toIndex).clear()
        original?.removeAll(removed.toSet())
        when {
            entries.isEmpty() -> { releaseAllTracks(); index = 0 }
            removingCurrent -> loadTrack(minOf(fromIndex, entries.lastIndex), 0L)
            else -> {
                if (index >= toIndex) index -= (toIndex - fromIndex)
                refreshPrecache()
            }
        }
        return done()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val moved = entries.subList(fromIndex, toIndex).toList()
        val currentEntry = entries.getOrNull(index)
        entries.subList(fromIndex, toIndex).clear()
        entries.addAll(newIndex, moved)
        index = entries.indexOfFirst { it === currentEntry }.coerceAtLeast(0)
        refreshPrecache()
        return done()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        // INDEX_UNSET = next/previous diminta padahal tidak ada
        if (mediaItemIndex == C.INDEX_UNSET || mediaItemIndex !in entries.indices) return done()
        val pos = if (positionMs == C.TIME_UNSET) 0L else positionMs
        abortCrossfade()
        ended = false
        val cur = current
        if (mediaItemIndex == index && cur != null) cur.player.seekTo(pos)
        else loadTrack(mediaItemIndex, pos)
        return done()
    }

    private companion object {
        const val DUCK_FACTOR = 0.2f

        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val COMMANDS: Player.Commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_RELEASE
        ).build()
    }
}