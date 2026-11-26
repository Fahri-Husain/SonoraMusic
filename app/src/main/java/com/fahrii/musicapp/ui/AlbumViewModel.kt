package com.fahrii.musicapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.AlbumRepository
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.data.getInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumUiState(
    val title: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    val songs: List<Song> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false
)

/** Data untuk halaman album: judul/artis/sampul langsung tampil, tracklist menyusul setelah dimuat. */
class AlbumViewModel(app: Application) : AndroidViewModel(app) {

    private val albumRepo = AlbumRepository.getInstance(app)

    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    private var loadedFor: String? = null

    /** [url] = URL playlist album (kalau ada); kalau kosong, album dicari lewat [title] + [artist]. */
    fun load(url: String, title: String, artist: String, coverUrl: String?) {
        val key = url.ifBlank { "$title|$artist" }
        if (loadedFor == key) return
        loadedFor = key

        _state.value = AlbumUiState(title = title, artist = artist, coverUrl = coverUrl)

        viewModelScope.launch {
            val album = try {
                if (url.isNotBlank()) {
                    MusicRepository.findAlbumTracksByUrl(url, title, artist, coverUrl)
                } else {
                    MusicRepository.findAlbumTracks(title, artist)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (album == null) {
                _state.update { it.copy(loading = false, failed = true) }
            } else {
                // Semua lagu di sini pasti anggota album ini: catat supaya Replay ("Your Top Albums")
                // dan hasil pencarian langsung tahu albumnya tanpa mencari lewat deskripsi video
                albumRepo.record(album.songs.map { it.url }, album.title.ifBlank { title })
                _state.value = AlbumUiState(
                    title = album.title.ifBlank { title },
                    artist = album.artist.ifBlank { artist },
                    coverUrl = album.coverUrl ?: coverUrl,
                    songs = album.songs,
                    loading = false
                )
            }
        }
    }
}