package com.fahrii.musicapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.ArtistImageRepository
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.data.getInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistUiState(
    val name: String = "",
    val imageUrl: String? = null,
    val bio: String? = null,
    /** -1 kalau tidak diketahui. */
    val subscribers: Long = -1L,
    val songs: List<Song> = emptyList(),
    val albums: List<ArtistAlbum> = emptyList(),
    val songsLoading: Boolean = true,
    val albumsLoading: Boolean = true
)

/**
 * Data untuk halaman artis. Profil, lagu, dan album dimuat terpisah dan paralel,
 * jadi bagian yang sudah siap langsung tampil tanpa menunggu yang lain.
 */
class ArtistViewModel(app: Application) : AndroidViewModel(app) {

    private val imageRepo = ArtistImageRepository.getInstance(app)

    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    private var loadedFor: String? = null

    fun load(name: String) {
        if (loadedFor == name) return
        loadedFor = name

        // Foto yang sudah di-cache dari Replay langsung dipakai, jadi layar tidak kosong dulu
        _state.value = ArtistUiState(
            name = name,
            imageUrl = imageRepo.images.value[ArtistImageRepository.keyOf(name)]
        )

        viewModelScope.launch {
            val profile = safe { MusicRepository.findArtistProfile(name) }
            if (profile != null) {
                _state.update {
                    it.copy(
                        imageUrl = profile.imageUrl ?: it.imageUrl,
                        bio = profile.bio,
                        subscribers = profile.subscribers
                    )
                }
            }
        }

        viewModelScope.launch {
            val songs = safe { MusicRepository.searchArtistSongs(name) } ?: emptyList()
            _state.update { it.copy(songs = songs, songsLoading = false) }
        }

        viewModelScope.launch {
            val albums = safe { MusicRepository.searchArtistAlbums(name) } ?: emptyList()
            _state.update { it.copy(albums = albums, albumsLoading = false) }
        }
    }

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
