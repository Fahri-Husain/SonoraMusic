package com.fahrii.musicapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.ArtistStat
import com.fahrii.musicapp.data.ChartRepository
import com.fahrii.musicapp.data.QuickPickRepository
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickPick(
    val label: String,
    val subtitle: String,
    val query: String,
    val songs: List<Song> = emptyList()
)

data class HomeUiState(
    val quickPicks: List<QuickPick> = listOf(
        QuickPick("K-Pop", "Hits K-Pop pilihan", "K-Pop hits"),
        QuickPick("Lo-Fi", "Santai untuk fokus", "lofi chill"),
        QuickPick("Pop Punk", "Energi pop punk", "pop punk"),
        QuickPick("Pop", "Pop terpopuler", "pop hits"),
        QuickPick("Jazz", "Jazz untuk bersantai", "jazz"),
        QuickPick("Mix", "Campuran lagu populer", "top hits")
    ),
    val newReleases: List<Song> = emptyList(),
    val trending: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {


    private val chartArtists = MutableStateFlow<List<ArtistStat>>(emptyList())

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Artis paling sering didengar di dunia (chart global Deezer). */
    val artists: StateFlow<List<ArtistStat>> = chartArtists.asStateFlow()

    private var quickPicksJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val releases = async { runCatching { ChartRepository.newReleases() } }
            val trending = async { runCatching { ChartRepository.trendingTracks() } }
            val artistChart = async { runCatching { ChartRepository.topArtists() } }

            val r = releases.await()
            val t = trending.await()
            artistChart.await().getOrNull()?.let { chartArtists.value = it.take(3) }

            _state.update {
                it.copy(
                    newReleases = r.getOrDefault(emptyList()),
                    trending = t.getOrDefault(emptyList()),
                    isLoading = false,
                    error = if (r.isFailure && t.isFailure) {
                        "Gagal memuat. Periksa koneksi internet."
                    } else {
                        null
                    }
                )
            }
        }
        loadQuickPicks()
    }

    /**
     * Pilihan Cepat diganti seminggu sekali dan disimpan di HP. Yang tersimpan ditampilkan dulu,
     * lalu kartu yang sudah lewat seminggu (atau belum pernah ada) diambil ulang dari sumber tren.
     */
    private fun loadQuickPicks() {
        if (quickPicksJob?.isActive == true) return
        quickPicksJob = viewModelScope.launch {
            val repo = QuickPickRepository.getInstance(getApplication<Application>())
            val now = System.currentTimeMillis()

            fun show(label: String, songs: List<Song>) {
                _state.update { s ->
                    s.copy(quickPicks = s.quickPicks.map { p ->
                        if (p.label == label) p.copy(songs = songs) else p
                    })
                }
            }

            // Tampilkan dulu yang tersimpan, supaya kartu tidak kosong selama menunggu
            _state.value.quickPicks.forEach { p ->
                if (p.songs.isEmpty()) show(p.label, repo.songsOf(p.label))
            }

            val labels = _state.value.quickPicks.map { it.label }
            val mix = QuickPickRepository.MIX_LABEL
            // Mix paling akhir karena bahannya diambil dari kartu lain
            for (label in labels.filter { it != mix } + labels.filter { it == mix }) {
                if (repo.entry(label)?.isFresh(now) == true) continue

                val songs = if (label == mix) {
                    repo.buildMix(labels.filter { it != mix }.map { repo.songsOf(it) }, now)
                } else {
                    repo.fetchFresh(label, now)
                }

                if (songs.size < QuickPickRepository.MIN_SONGS) {
                    // Terlalu sedikit (mungkin gagal sebagian): jangan menimpa yang lama dan jangan
                    // disimpan sebagai pilihan minggu ini, supaya dicoba lagi lain kali
                    if (repo.songsOf(label).isEmpty() && songs.isNotEmpty()) show(label, songs)
                    continue
                }
                repo.save(label, songs, now)
                show(label, songs)
            }
        }
    }
}