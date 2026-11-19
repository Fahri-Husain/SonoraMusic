package com.fahrii.musicapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.ArtistResult
import com.fahrii.musicapp.data.ChartRepository
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GenreUiState(
    val name: String = "",
    val songs: List<Song> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** Artis populer genre ini; foto menyusul satu per satu (imageUrl null dulu). */
    val artists: List<ArtistResult> = emptyList(),
    val albums: List<ArtistAlbum> = emptyList(),
    val albumsLoading: Boolean = true
)

/** Padanan genre di chart Deezer: nama genre + ID cadangan kalau daftar genre gagal diambil. */
private data class DeezerGenre(val name: String, val fallbackId: Int)

/**
 * Data untuk halaman genre. Genre yang ada di chart Deezer memakai chart-nya (urutan tren),
 * sisanya (K-Pop, J-Pop, Indonesia Pop, Pop Punk) dicari di YouTube Music dengan query genre itu.
 * Kunci peta = query genre di Beranda.
 *
 * Artis populer diambil dari artis di daftar lagu itu (urut peringkat), album dari pencarian album
 * dengan query genre. Tiap bagian dimuat sendiri-sendiri, jadi yang selesai duluan langsung tampil.
 */
class GenreViewModel : ViewModel() {

    private val deezerGenres = mapOf(
        "pop hits" to DeezerGenre("Pop", 132),
        "r&b soul" to DeezerGenre("R&B", 165),
        "electronic dance music" to DeezerGenre("Dance", 113),
        "hip hop" to DeezerGenre("Rap/Hip Hop", 116),
        "jazz" to DeezerGenre("Jazz", 129),
        "classical music" to DeezerGenre("Classical", 98),
        "metal" to DeezerGenre("Metal", 464),
        "rock hits" to DeezerGenre("Rock", 152)
    )

    private val _state = MutableStateFlow(GenreUiState())
    val state: StateFlow<GenreUiState> = _state.asStateFlow()

    private var loadedFor: String? = null

    fun load(name: String, query: String) {
        val key = "$name|$query"
        if (loadedFor == key) return
        loadedFor = key

        _state.value = GenreUiState(name = name)

        // Lagu -> artis populer -> foto artis
        viewModelScope.launch {
            val songs = try {
                fetchSongs(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val artists = topArtists(songs)

            _state.update {
                it.copy(
                    songs = songs,
                    loading = false,
                    failed = songs.isEmpty(),
                    artists = artists
                )
            }
            loadArtistImages(artists)
        }

        // Album jalan paralel dengan lagu
        viewModelScope.launch {
            val albums = try {
                MusicRepository.searchAlbums(query, ALBUM_COUNT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(albums = albums, albumsLoading = false) }
        }
    }

    private suspend fun fetchSongs(query: String): List<Song> {
        val deezer = deezerGenres[query]
        if (deezer != null) {
            val id = ChartRepository.genreId(deezer.name, deezer.fallbackId)
            val chart = runCatching { ChartRepository.genreTracks(id, CHART_SIZE) }.getOrNull().orEmpty()
            if (chart.size >= MIN_CHART) return chart
        }
        return runCatching { MusicRepository.search(query) }.getOrNull().orEmpty()
    }

    /**
     * Artis yang paling sering muncul di [songs]; kalau sama banyak, yang lebih dulu muncul
     * (peringkat lebih tinggi) didahulukan. Hanya artis utama ("A, B" -> "A").
     */
    private fun topArtists(songs: List<Song>): List<ArtistResult> {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        for (song in songs) {
            val artist = song.artist.substringBefore(',').removeSuffix(" - Topic").trim()
            if (artist.isEmpty() || artist == "Unknown artist") continue
            val key = artist.lowercase()
            val current = counts[key]
            counts[key] = (current?.first ?: artist) to ((current?.second ?: 0) + 1)
        }
        return counts.values
            .sortedByDescending { it.second }
            .take(ARTIST_COUNT)
            .map { ArtistResult(name = it.first, imageUrl = null) }
    }

    /** Satu per satu supaya tidak membanjiri YouTube dengan banyak request sekaligus. */
    private suspend fun loadArtistImages(artists: List<ArtistResult>) {
        for (artist in artists) {
            val image: String? = try {
                MusicRepository.findArtistImage(artist.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (image == null) continue

            _state.update { s ->
                s.copy(artists = s.artists.map { if (it.name == artist.name) it.copy(imageUrl = image) else it })
            }
        }
    }

    private companion object {
        const val CHART_SIZE = 50

        /** Chart yang terlalu pendek dianggap gagal; diganti hasil pencarian. */
        const val MIN_CHART = 10

        const val ARTIST_COUNT = 8
        const val ALBUM_COUNT = 10
    }
}