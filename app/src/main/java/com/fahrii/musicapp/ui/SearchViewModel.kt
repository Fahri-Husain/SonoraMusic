package com.fahrii.musicapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.ArtistResult
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.SearchHistoryRepository
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    /** Artis yang cocok; ditampilkan di atas daftar lagu pada hasil pencarian (bukan di saran). */
    val artists: List<ArtistResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    /** Lagu yang cocok dengan ketikan saat ini (dimuat otomatis, ditampilkan sebagai saran). */
    val suggestions: List<Song> = emptyList(),
    val suggestionsLoading: Boolean = false
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val historyRepo = SearchHistoryRepository.getInstance(app)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Riwayat pencarian, yang terbaru di paling atas. */
    val history: StateFlow<List<String>> = historyRepo.items

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    /** Kata kunci yang hasilnya sedang ada di [SearchUiState.suggestions] dan [suggestionArtists]. */
    private var suggestionsFor: String? = null

    /**
     * Artis untuk [suggestionsFor], dimuat bersama saran tapi tidak ditampilkan di saran;
     * dipakai langsung saat pengguna menekan cari, supaya hasilnya muncul tanpa menunggu lagi.
     */
    private var suggestionArtists: List<ArtistResult> = emptyList()

    fun onQueryChange(newQuery: String) {
        _state.update { it.copy(query = newQuery) }
        scheduleSuggestions(newQuery.trim())
    }

    /** Lagu dan artis untuk [query], dimuat bersamaan. Gagal memuat artis tidak membatalkan lagu. */
    private suspend fun fetchSongsAndArtists(query: String): Pair<List<Song>, List<ArtistResult>> =
        coroutineScope {
            val songs = async { MusicRepository.search(query) }
            val artists = async {
                try {
                    MusicRepository.searchArtists(query)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
            songs.await() to artists.await()
        }

    /** Muat saran setelah pengguna berhenti mengetik sebentar, supaya tidak mencari tiap huruf. */
    private fun scheduleSuggestions(query: String) {
        suggestJob?.cancel()

        if (query.isEmpty()) {
            suggestionsFor = null
            suggestionArtists = emptyList()
            _state.update { it.copy(suggestions = emptyList(), suggestionsLoading = false) }
            return
        }
        if (query == suggestionsFor) {
            _state.update { it.copy(suggestionsLoading = false) }
            return
        }

        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            _state.update { it.copy(suggestionsLoading = true) }
            try {
                val (songs, artists) = fetchSongsAndArtists(query)
                suggestionsFor = query
                suggestionArtists = artists
                _state.update { it.copy(suggestions = songs, suggestionsLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(suggestionsLoading = false) }
            }
        }
    }

    /**
     * Cari lagu dan artis untuk kata kunci saat ini. [saveToHistory] = true untuk pencarian yang
     * diketik pengguna sendiri (bukan dari klik genre/artis di Beranda).
     */
    fun search(saveToHistory: Boolean = false) {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return

        if (saveToHistory) historyRepo.add(query)

        suggestJob?.cancel()
        searchJob?.cancel()

        // Hasil untuk kata kunci yang sama sudah dimuat sebagai saran: langsung dipakai
        if (suggestionsFor == query && _state.value.suggestions.isNotEmpty()) {
            _state.update {
                it.copy(
                    results = it.suggestions,
                    artists = suggestionArtists,
                    isLoading = false,
                    error = null,
                    hasSearched = true,
                    suggestionsLoading = false
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, suggestionsLoading = false) }
            try {
                val (songs, artists) = fetchSongsAndArtists(query)
                suggestionsFor = query
                suggestionArtists = artists
                _state.update {
                    it.copy(
                        results = songs,
                        artists = artists,
                        isLoading = false,
                        hasSearched = true,
                        suggestions = songs
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        error = e.message ?: "Gagal mencari lagu"
                    )
                }
            }
        }
    }

    /** Kosongkan kotak pencarian beserta hasil dan sarannya: kembali ke tampilan awal halaman Cari. */
    fun clearSearch() {
        searchJob?.cancel()
        suggestJob?.cancel()
        suggestionsFor = null
        suggestionArtists = emptyList()
        _state.value = SearchUiState()
    }

    fun addToHistory(text: String) = historyRepo.add(text)

    fun removeFromHistory(text: String) = historyRepo.remove(text)

    fun clearHistory() = historyRepo.clear()

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 350L
    }
}