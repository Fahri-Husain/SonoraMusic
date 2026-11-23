package com.fahrii.musicapp.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.ArtistNames
import com.fahrii.musicapp.data.ArtistStat
import com.fahrii.musicapp.data.ChartRepository
import com.fahrii.musicapp.data.HistoryRepository
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.QuickPickRepository
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.data.identityKey
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
    val forYou: List<Song> = emptyList(),
    val forYouEligible: Boolean = false,
    val forYouLoading: Boolean = false,
    val newReleases: List<Song> = emptyList(),
    val trending: List<Song> = emptyList(),
    val isLoading: Boolean = true,

    val isRefreshing: Boolean = false,

    val quickPicksLoading: Boolean = true,
    val error: String? = null
)

private const val HOME_PREFETCH_COUNT = 10

private const val PULL_REFRESH_MIN_MS = 1_000L

private const val HOME_VISIBLE_COUNT = 4

private const val FOR_YOU_MIN_PLAYS = 10

private const val FOR_YOU_EXCLUDE_PLAYS = 3

private const val FOR_YOU_ARTISTS = 5

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
    private var forYouJob: Job? = null
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

            val visible = releaseSongs.take(HOME_VISIBLE_COUNT) + trendingSongs.take(HOME_VISIBLE_COUNT)
            val offscreen = releaseSongs.drop(HOME_VISIBLE_COUNT).take(HOME_PREFETCH_COUNT - HOME_VISIBLE_COUNT) +
                    trendingSongs.drop(HOME_VISIBLE_COUNT).take(HOME_PREFETCH_COUNT - HOME_VISIBLE_COUNT)
            ArtistNames.prefetch(visible, timeoutMs = 6_000)
            viewModelScope.launch { ArtistNames.prefetch(offscreen, timeoutMs = 60_000) }

            if (pull) {

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

        loadQuickPicks(shuffle = pull)
        loadForYou(shuffle = pull)
    }

    /**
     * Baris Untukmu: lagu populer artis yang paling sering didengar plus artis yang mirip. Baris ini
     * hanya ada kalau riwayat dengar sudah cukup. Diambil seminggu sekali dan disimpan di HP; saat
     * tarik-untuk-refresh, isinya diacak ulang dari kandidat tersimpan tanpa internet.
     */
    private fun loadForYou(shuffle: Boolean) {
        if (forYouJob?.isActive == true) return

        val app = getApplication<Application>()
        val history = HistoryRepository.getInstance(app)
        val favorites = history.artists.value
        val eligible = favorites.isNotEmpty() && favorites.sumOf { it.plays } >= FOR_YOU_MIN_PLAYS
        if (!eligible) {
            _state.update { it.copy(forYouEligible = false, forYou = emptyList(), forYouLoading = false) }
            return
        }

        val repo = QuickPickRepository.getInstance(app)
        val label = QuickPickRepository.FOR_YOU_LABEL
        _state.update {
            it.copy(
                forYouEligible = true,
                forYouLoading = true,
                forYou = if (it.forYou.isEmpty()) repo.songsOf(label) else it.forYou
            )
        }

        forYouJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entry = repo.entry(label)
            if (entry != null && entry.isFresh(now)) {
                if (!shuffle) return@launch
                val reshuffled = repo.reshuffle(label, now)
                if (reshuffled != null) {
                    _state.update { it.copy(forYou = reshuffled) }
                    return@launch
                }
            }

            val oftenPlayed = history.songs.value
                .filter { it.plays >= FOR_YOU_EXCLUDE_PLAYS }
                .map { it.song.identityKey() }
                .toSet()
            val fetched = repo.fetchForYou(
                artistNames = favorites.take(FOR_YOU_ARTISTS).map { it.name },
                isExcluded = { it.identityKey() in oftenPlayed },
                nowMs = now
            )
            if (fetched.songs.size < QuickPickRepository.MIN_SONGS) return@launch

            repo.save(label, fetched.songs, now, fetched.pool, fetched.coreSize)
            _state.update { it.copy(forYou = fetched.songs) }
        }
        forYouJob?.invokeOnCompletion { _state.update { it.copy(forYouLoading = false) } }
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
     * Pilihan Cepat diambil dari sumber tren seminggu sekali dan disimpan di HP. Yang tersimpan
     * ditampilkan dulu, lalu kartu yang sudah lewat seminggu (atau belum pernah ada) diambil ulang.
     *
     * Kalau [shuffle] (tarik-untuk-refresh), kartu yang masih segar tidak diambil ulang dari internet:
     * lagu teratasnya tetap dan sisanya diacak ulang dari kandidat yang tersimpan. Mix selalu
     * dirangkai ulang, dan juga kalau ada kartu lain yang baru diambil ulang.
     */
    private fun loadQuickPicks(shuffle: Boolean = false) {
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

            _state.value.quickPicks.forEach { p ->
                if (p.songs.isEmpty()) show(p.label, repo.songsOf(p.label))
            }

            val labels = _state.value.quickPicks.map { it.label }
            val mix = QuickPickRepository.MIX_LABEL
            val others = labels.filter { it != mix }
            var refetched = false

            for (label in others) {
                val entry = repo.entry(label)
                if (entry != null && entry.isFresh(now)) {
                    if (!shuffle) continue

                    val reshuffled = repo.reshuffle(label, now)
                    if (reshuffled != null) {
                        show(label, reshuffled)
                        continue
                    }

                }

                val fetched = repo.fetchFresh(label, now)
                if (fetched.songs.size < QuickPickRepository.MIN_SONGS) {

                    if (repo.songsOf(label).isEmpty() && fetched.songs.isNotEmpty()) show(label, fetched.songs)
                    continue
                }
                repo.save(label, fetched.songs, now, fetched.pool, fetched.coreSize)
                show(label, fetched.songs)
                refetched = true
            }

            if (labels.contains(mix)) {
                val mixFresh = repo.entry(mix)?.isFresh(now) == true
                if (shuffle || refetched || !mixFresh) {
                    val salt = if (shuffle) now else 0L
                    val songs = repo.buildMix(others.map { repo.poolOf(it) }, now, salt)
                    if (songs.size >= QuickPickRepository.MIN_SONGS) {
                        repo.save(mix, songs, now)
                        show(mix, songs)
                    } else if (repo.songsOf(mix).isEmpty() && songs.isNotEmpty()) {
                        show(mix, songs)
                    }
                }
            }
        }

        quickPicksJob?.invokeOnCompletion { _state.update { it.copy(quickPicksLoading = false) } }
    }
}