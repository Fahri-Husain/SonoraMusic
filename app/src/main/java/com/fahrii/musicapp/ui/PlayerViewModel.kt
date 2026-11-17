package com.fahrii.musicapp.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fahrii.musicapp.PlaybackService
import com.fahrii.musicapp.data.Song
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
    val error: String? = null,
    /** Dari mana lagu diputar, mis. Playlist "my gweh". */
    val sourceLabel: String? = null
)

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
            replay.recordPlay(item.toSong())
        }
    }

    private val history = HistoryRepository.getInstance(app)
    private val replay = ReplayRepository.getInstance(app)
    private var lastRecordedId: String? = null

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
                    _progress.update {
                        PlaybackProgress(c.currentPosition, c.duration.coerceAtLeast(0L))
                    }
                    // Waktu dengar untuk Replay
                    c.currentMediaItem?.let { replay.addListening(it.toSong(), 500L) }
                }
                delay(500)
            }
        }
    }

    /** Putar [songs] sebagai antrian, mulai dari lagu ke-[startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int, source: String? = null) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(0, songs.lastIndex)
        val items = songs.map { it.toMediaItem() }

        // Lagu yang ditekan langsung masuk riwayat; listener di bawah tidak mencatatnya lagi
        lastRecordedId = songs[index].url
        history.record(songs[index])
        replay.recordPlay(songs[index])

        _state.update {
            it.copy(currentSong = songs[index], isBuffering = true, error = null, sourceLabel = source)
        }

        val c = controller
        if (c != null) startQueue(c, items, index) else pendingQueue = items to index
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
        controller?.seekTo(positionMs)
        _progress.update { it.copy(positionMs = positionMs) }
    }

    private fun startQueue(c: MediaController, items: List<MediaItem>, startIndex: Int) {
        c.setMediaItems(items, startIndex, C.TIME_UNSET)
        c.prepare()
        c.play()
    }

    private fun syncState(player: Player) {
        _state.update {
            it.copy(
                currentSong = player.currentMediaItem?.toSong(),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                hasNext = player.hasNextMediaItem(),
                error = player.playerError?.let { e -> "Gagal memutar: ${e.errorCodeName}" }
            )
        }
        _progress.update {
            PlaybackProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
        }
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