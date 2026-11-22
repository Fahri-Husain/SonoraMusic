package com.fahrii.musicapp.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.ArtistNames
import com.fahrii.musicapp.data.ArtistStat
import com.fahrii.musicapp.data.ChartRepository
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.QuickPickRepository
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.data.isAlbumEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    // True selama tarik-untuk-muat-ulang berjalan; isi Beranda tetap terlihat (tanpa skeleton)
    val isRefreshing: Boolean = false,
    // True selama kartu Pilihan Cepat masih diambil; dipakai untuk menampilkan skeleton
    val quickPicksLoading: Boolean = true,
    val error: String? = null
)

// Jumlah lagu per baris Beranda yang tampil (sama dengan HOME_ROW_LIMIT di HomeScreen)
private const val HOME_PREFETCH_COUNT = 10

// Lama minimum tarik-untuk-muat-ulang berjalan (ms), supaya animasinya sempat terlihat
private const val PULL_REFRESH_MIN_MS = 1_000L

// Jumlah kartu per baris yang langsung terlihat tanpa menggeser
private const val HOME_VISIBLE_COUNT = 4

class HomeViewModel(app: Application) : AndroidViewModel(app) {


    private val chartArtists = MutableStateFlow<List<ArtistStat>>(emptyList())

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Artis paling sering didengar di dunia (chart global Deezer). */
    val artists: StateFlow<List<ArtistStat>> = chartArtists.asStateFlow()

    private val artistSubscribers = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Jumlah subscriber tiap artis pilihan (kunci = nama artis), terisi satu per satu begitu didapat. */
    val subscribers: StateFlow<Map<String, Long>> = artistSubscribers.asStateFlow()

    private var quickPicksJob: Job? = null
    private var durationsJob: Job? = null

    init {
        refresh()
    }

    fun refresh() = load(pull = false)

    /**
     * Tarik-untuk-muat-ulang di Beranda. Sama seperti [refresh] (skeleton tampil selagi memuat),
     * tapi ikon tarik tetap berputar sampai selesai, ada waktu tampil minimum supaya animasinya
     * terlihat walau datanya cepat, dan kalau pengambilan gagal daftar lama tidak dikosongkan.
     */
    fun pullRefresh() {
        val s = _state.value
        if (s.isLoading || s.isRefreshing) return
        load(pull = true)
    }

    private fun load(pull: Boolean) {
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            _state.update { it.copy(isLoading = true, isRefreshing = pull, error = null) }

            val releases = async { runCatching { ChartRepository.newReleases() } }
            val trending = async { runCatching { ChartRepository.trendingTracks() } }
            val artistChart = async { runCatching { ChartRepository.topArtists() } }

            val r = releases.await()
            val t = trending.await()
            artistChart.await().getOrNull()?.let { list ->
                val top = list.take(3)
                chartArtists.value = top
                loadSubscribers(top)
            }

            val releaseSongs = r.getOrNull() ?: if (pull) _state.value.newReleases else emptyList()
            val trendingSongs = t.getOrNull() ?: if (pull) _state.value.trending else emptyList()
            // Lengkapi nama artis kolaborasi untuk kartu yang langsung terlihat di layar sebelum daftar
            // muncul, supaya namanya tidak berubah di depan mata. Sisanya (di luar layar, perlu digeser)
            // dilengkapi di latar belakang tanpa menahan tampilan.
            val visible = releaseSongs.take(HOME_VISIBLE_COUNT) + trendingSongs.take(HOME_VISIBLE_COUNT)
            val offscreen = releaseSongs.drop(HOME_VISIBLE_COUNT).take(HOME_PREFETCH_COUNT - HOME_VISIBLE_COUNT) +
                    trendingSongs.drop(HOME_VISIBLE_COUNT).take(HOME_PREFETCH_COUNT - HOME_VISIBLE_COUNT)
            ArtistNames.prefetch(visible, timeoutMs = 6_000)
            viewModelScope.launch { ArtistNames.prefetch(offscreen, timeoutMs = 60_000) }

            if (pull) {
                // Datanya bisa datang sangat cepat; tahan sebentar supaya skeleton dan ikon terlihat
                val remaining = PULL_REFRESH_MIN_MS - (SystemClock.elapsedRealtime() - startedAt)
                if (remaining > 0) delay(remaining)
            }

            _state.update {
                it.copy(
                    newReleases = releaseSongs,
                    trending = trendingSongs,
                    isLoading = false,
                    isRefreshing = false,
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
     * Rilis Baru dibuat dari album, yang datanya tidak menyertakan durasi. Dipanggil saat daftar
     * lengkapnya dibuka: durasi tiap lagu diambil dari hasil pertama pencarian YouTube Music (lagu
     * yang sama dengan yang diputar), beberapa sekaligus karena tiap pencarian cukup berat, lalu
     * diisikan satu per satu begitu didapat. Yang sudah punya durasi dilewati.
     */
    fun loadNewReleaseDurations() {
        if (durationsJob?.isActive == true) return
        val pending = _state.value.newReleases.filter { it.durationSeconds <= 0 && !it.isAlbumEntry() }
        if (pending.isEmpty()) return

        durationsJob = viewModelScope.launch {
            val gate = Semaphore(2)
            pending.forEach { song ->
                launch {
                    gate.withPermit {
                        val query = ChartRepository.searchQuery(song.title, song.artist)
                        var seconds: Long? = null
                        // Sekali coba ulang kalau gagal (mis. koneksi putus sebentar)
                        for (attempt in 0..1) {
                            val result = runCatching { MusicRepository.findSongDuration(query) }
                            if (result.isSuccess) {
                                seconds = result.getOrNull()
                                break
                            }
                            delay(1500)
                        }
                        val found = seconds ?: return@withPermit
                        _state.update { s ->
                            s.copy(newReleases = s.newReleases.map {
                                if (it.url == song.url) it.copy(durationSeconds = found) else it
                            })
                        }
                    }
                }
            }
        }
    }

    /** Ambil jumlah subscriber tiap artis (profil yang sama dengan halaman artis), paralel. */
    private fun loadSubscribers(artists: List<ArtistStat>) {
        artists.forEach { artist ->
            if ((artistSubscribers.value[artist.name] ?: -1L) >= 0) return@forEach
            viewModelScope.launch {
                val count = runCatching { MusicRepository.findArtistProfile(artist.name)?.subscribers }
                    .getOrNull()
                if (count != null && count >= 0) {
                    artistSubscribers.update { it + (artist.name to count) }
                }
            }
        }
    }

    /**
     * Pilihan Cepat diganti seminggu sekali dan disimpan di HP. Yang tersimpan ditampilkan dulu,
     * lalu kartu yang sudah lewat seminggu (atau belum pernah ada) diambil ulang dari sumber tren.
     */
    private fun loadQuickPicks() {
        if (quickPicksJob?.isActive == true) return
        _state.update { it.copy(quickPicksLoading = true) }
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
        // Selesai (berhasil atau gagal): skeleton tidak boleh menyala selamanya
        quickPicksJob?.invokeOnCompletion { _state.update { it.copy(quickPicksLoading = false) } }
    }
}