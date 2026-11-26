package com.fahrii.musicapp.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fahrii.musicapp.PlaybackService
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.data.identityKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.fahrii.musicapp.data.HistoryRepository
import com.fahrii.musicapp.data.ReplayRepository

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    /** Lagu yang akan diputar kalau next/previous ditekan; null kalau tidak ada. Dipakai untuk pratinjau saat cover digeser. */
    val nextSong: Song? = null,
    val previousSong: Song? = null,
    /** Seluruh antrean sesuai urutan di pemutar, dan posisi lagu yang sedang diputar di dalamnya. */
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val error: String? = null,
    /** Dari mana lagu diputar, mis. Playlist "my gweh". */
    val sourceLabel: String? = null
)

/** Seek tidak boleh lebih dekat dari ini ke ujung lagu. */
private const val SEEK_END_MARGIN_MS = 500L

/** Berapa lama tujuan seek dipertahankan selagi pemutar belum sampai di sana. */
private const val SEEK_HOLD_MS = 1_500L

/** Posisi pemutar dianggap sudah sampai di tujuan seek kalau selisihnya sekecil ini. */
private const val SEEK_SETTLED_MS = 1_500L

data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private var controller: MediaController? = null
    private var pendingQueue: Pair<List<MediaItem>, Int>? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
            maybeAutoplay(player)
        }

        // Catat artis tiap lagu baru mulai diputar (untuk "Artis Pilihan")
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                replay.flush()
                return
            }
            val item = controller?.currentMediaItem ?: return
            if (item.mediaId == lastRecordedId) return
            lastRecordedId = item.mediaId
            history.record(item.toSong())
            replay.onSongStarted(item.toSong())
        }

        // Replay: 1 play dihitung hanya kalau lagunya sampai habis. Lanjut otomatis ke lagu
        // berikutnya (termasuk lewat crossfade, yang dilaporkan sebagai AUTO), atau lagu yang sama
        // diulang (mode ulang satu lagu), berarti lagu sebelumnya selesai. Ganti lagu manual
        // (next/previous/pilih lagu lain) memakai alasan lain, jadi tidak dihitung.
        //
        // Lagu baru dicatat di sini juga, karena status "sedang memutar" tidak berubah saat
        // transisi otomatis (tidak ada jeda), jadi onIsPlayingChanged tidak ikut terpanggil.
        // Pengecekan lastRecordedId mencegah pencatatan ganda, dan lagu yang diulang tidak dicatat lagi.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) return

            replay.songCompleted()
            if (mediaItem != null && mediaItem.mediaId != lastRecordedId) {
                lastRecordedId = mediaItem.mediaId
                history.record(mediaItem.toSong())
                replay.onSongStarted(mediaItem.toSong())
            }
        }

        // Lagu terakhir di antrian selesai (tanpa mode ulang): tidak ada transisi, hanya STATE_ENDED
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) replay.songCompleted()
        }
    }

    private val history = HistoryRepository.getInstance(app)
    private val replay = ReplayRepository.getInstance(app)
    private var lastRecordedId: String? = null

    // Putar otomatis: begitu lagu terakhir di antrean mulai diputar, ambil lagu yang mirip dan
    // sambungkan ke antrean (jadi sudah siap sebelum lagu habis, crossfade tetap jalan).
    // False untuk antrean dari playlist buatan sendiri: antrean itu berhenti di lagu terakhir.
    private var queueAllowsAutoplay = true
    private var autoplayJob: Job? = null
    private var autoplayRequestedFor: String? = null

    private val controllerFuture = MediaController.Builder(
        app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java))
    ).buildAsync()

    init {
        controllerFuture.addListener({
            try {
                val c = controllerFuture.get()
                controller = c
                c.addListener(playerListener)

                pendingQueue?.let { (items, index) ->
                    startQueue(c, items, index)
                    pendingQueue = null
                }
                syncState(c)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Gagal terhubung ke pemutar") }
            }
        }, ContextCompat.getMainExecutor(app))

        // Update posisi lagu tiap 0,5 detik selama diputar
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null && c.isPlaying) {
                    publishProgress(c)
                    // Waktu dengar untuk Replay
                    c.currentMediaItem?.let {
                        replay.addListening(it.toSong(), 500L, c.duration.coerceAtLeast(0L))
                    }
                }
                delay(500)
            }
        }
    }

    /** Putar [songs] sebagai antrian, mulai dari lagu ke-[startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int, source: String? = null, autoplay: Boolean = true) {
        if (songs.isEmpty()) return
        queueAllowsAutoplay = autoplay
        autoplayRequestedFor = null
        autoplayJob?.cancel()
        val index = startIndex.coerceIn(0, songs.lastIndex)
        val items = songs.map { it.toMediaItem() }

        // Lagu yang ditekan langsung masuk riwayat; listener di bawah tidak mencatatnya lagi
        lastRecordedId = songs[index].url
        history.record(songs[index])
        replay.onSongStarted(songs[index])

        _state.update {
            it.copy(currentSong = songs[index], isBuffering = true, error = null, sourceLabel = source)
        }

        val c = controller
        if (c != null) startQueue(c, items, index) else pendingQueue = items to index
    }

    /**
     * Tambahkan [songs] ke akhir antrian yang sedang berjalan. Kalau belum ada antrian,
     * langsung diputar sebagai antrian baru.
     */
    fun addToQueue(songs: List<Song>, source: String? = null, autoplay: Boolean = true) {
        if (songs.isEmpty()) return
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            playQueue(songs, 0, source, autoplay)
            return
        }
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    /** Sisipkan [song] tepat setelah lagu yang sedang diputar. */
    fun playNext(song: Song) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    /** Loncat ke lagu ke-[index] di antrean dan langsung memutarnya. */
    fun skipToQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.play()
    }

    /** Pindahkan lagu dari posisi [from] ke posisi [to] di antrean (dipakai saat digeser). */
    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (from !in 0 until count || to !in 0 until count || from == to) return
        c.moveMediaItem(from, to)
    }

    /** Hapus lagu ke-[index] dari antrean. Lagu yang sedang diputar tidak bisa dihapus dari sini. */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount || index == c.currentMediaItemIndex) return
        c.removeMediaItem(index)
    }

    /**
     * Pulihkan pemutar dari status error (mis. ERROR_CODE_IO_UNSPECIFIED karena koneksi putus atau
     * URL stream kedaluwarsa). Error ExoPlayer menetap sampai prepare() dipanggil lagi, jadi tulisan
     * "Gagal memutar" tidak hilang sendiri. Antrean dan lagunya tetap; stream diambil ulang lewat
     * resolver. Dipanggil saat Beranda di-refresh. Pemutar dijeda dulu supaya refresh tidak tiba-tiba
     * menyalakan musik; lagunya siap diputar dengan menekan Play. Kalau tidak sedang error, tidak
     * melakukan apa-apa.
     */
    fun recoverFromError() {
        val c = controller ?: return
        if (c.playerError == null) return
        c.pause()
        c.prepare()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        val c = controller ?: return
        c.seekToNext()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun previous() {
        val c = controller ?: return
        c.seekToPrevious()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    /** Langsung ke lagu sebelumnya di antrian (tidak mengulang lagu ini dari awal seperti [previous]). */
    fun skipToPrevious() {
        val c = controller ?: return
        c.seekToPreviousMediaItem()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun seekTo(positionMs: Long) {
        val c = controller
        // Jangan seek persis ke ujung: stream yang durasinya hanya perkiraan bisa dianggap sudah habis
        val duration = c?.duration ?: C.TIME_UNSET
        val target = if (duration != C.TIME_UNSET && duration > SEEK_END_MARGIN_MS) {
            positionMs.coerceIn(0L, duration - SEEK_END_MARGIN_MS)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        seekTargetMs = target
        seekHoldUntilMs = SystemClock.elapsedRealtime() + SEEK_HOLD_MS
        c?.seekTo(target)
        _progress.update { it.copy(positionMs = target) }
    }

    // Setelah seek, posisi yang dibaca dari pemutar bisa sesaat masih posisi lama (pemutar sedang
    // membuka ulang stream di titik baru). Selama jeda singkat itu, posisi basi tidak boleh
    // menimpa tujuan seek, kalau tidak slider terlihat loncat balik ke posisi sebelumnya.
    private var seekTargetMs = 0L
    private var seekHoldUntilMs = 0L

    private fun publishProgress(player: Player) {
        val duration = player.duration.coerceAtLeast(0L)
        var position = player.currentPosition
        if (SystemClock.elapsedRealtime() < seekHoldUntilMs) {
            if (kotlin.math.abs(position - seekTargetMs) <= SEEK_SETTLED_MS) {
                seekHoldUntilMs = 0L // pemutar sudah sampai di tujuan
            } else {
                position = seekTargetMs
            }
        }
        _progress.update { PlaybackProgress(position, duration) }
    }

    /**
     * Kalau lagu yang sedang diputar adalah yang terakhir di antrean (dan putar otomatis boleh),
     * ambil lagu terkait lalu tambahkan ke akhir antrean. Dilewati kalau mode ulang menyala,
     * karena itu artinya pengguna ingin mengulang antreannya.
     */
    private fun maybeAutoplay(player: Player) {
        // Putar otomatis selalu aktif; satu-satunya pengecualian adalah antrean yang melarangnya
        // (playlist buatan pengguna).
        if (!queueAllowsAutoplay) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF || player.hasNextMediaItem()) return
        val current = player.currentMediaItem ?: return
        if (autoplayRequestedFor == current.mediaId) return
        autoplayRequestedFor = current.mediaId

        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch {
            val related = MusicRepository.fetchRelatedSongs(current.mediaId)
            val c = controller ?: return@launch
            // Selagi menunggu, pengguna bisa saja ganti lagu atau antrean: jangan sambung kalau sudah tidak cocok
            if (!queueAllowsAutoplay || c.currentMediaItem?.mediaId != current.mediaId || c.hasNextMediaItem()) {
                autoplayRequestedFor = null
                return@launch
            }
            val queued = List(c.mediaItemCount) { i -> c.getMediaItemAt(i).toSong() }
            val queuedUrls = queued.map { it.url }.toSet()
            val queuedKeys = queued.map { it.identityKey() }.toSet()
            val fresh = related.filter { it.url !in queuedUrls && it.identityKey() !in queuedKeys }
            if (fresh.isNotEmpty()) c.addMediaItems(fresh.map { it.toMediaItem() })
        }
    }

    private fun startQueue(c: MediaController, items: List<MediaItem>, startIndex: Int) {
        c.setMediaItems(items, startIndex, C.TIME_UNSET)
        c.prepare()
        c.play()
    }

    private fun syncState(player: Player) {
        val currentIdx = player.currentMediaItemIndex
        val nextIdx = player.nextMediaItemIndex
        val prevIdx = player.previousMediaItemIndex
        val nextSong = if (nextIdx != C.INDEX_UNSET && nextIdx != currentIdx) {
            player.getMediaItemAt(nextIdx).toSong()
        } else null
        val previousSong = if (prevIdx != C.INDEX_UNSET && prevIdx != currentIdx) {
            player.getMediaItemAt(prevIdx).toSong()
        } else null
        _state.update {
            it.copy(
                currentSong = player.currentMediaItem?.toSong(),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                hasNext = player.hasNextMediaItem(),
                nextSong = nextSong,
                previousSong = previousSong,
                queue = List(player.mediaItemCount) { i -> player.getMediaItemAt(i).toSong() },
                currentIndex = currentIdx.coerceAtLeast(0),
                error = player.playerError?.let { e -> "Gagal memutar: ${e.errorCodeName}" }
            )
        }
        publishProgress(player)
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(url)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                .build()
        )
        .build()

    private fun MediaItem.toSong() = Song(
        url = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        durationSeconds = -1
    )

    override fun onCleared() {
        replay.flush()
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
    }
}