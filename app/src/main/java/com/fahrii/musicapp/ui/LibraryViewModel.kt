package com.fahrii.musicapp.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.AlbumRepository
import com.fahrii.musicapp.data.ArtistImageRepository
import com.fahrii.musicapp.data.DownloadRepository
import com.fahrii.musicapp.data.toDisplaySongs
import com.fahrii.musicapp.data.HistoryRepository
import com.fahrii.musicapp.data.ImportException
import com.fahrii.musicapp.data.PlaylistImporter
import com.fahrii.musicapp.data.LibraryRepository
import com.fahrii.musicapp.data.LibraryState
import com.fahrii.musicapp.data.MonthStats
import com.fahrii.musicapp.data.ReplayRepository
import com.fahrii.musicapp.data.PlayedSong
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.data.getInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tahap impor playlist dari link; ditampilkan oleh dialog Import playlist. */
sealed interface PlaylistImportState {
    data object Idle : PlaylistImportState

    /** [total] = 0 selagi playlist masih diambil; setelahnya jumlah lagu yang dicocokkan ([done]/[total]). */
    data class Running(val done: Int = 0, val total: Int = 0) : PlaylistImportState

    data class Done(val playlistId: String, val name: String, val songCount: Int, val note: String?) :
        PlaylistImportState

    data class Failed(val message: String) : PlaylistImportState
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository.getInstance(app)

    private val historyRepo = HistoryRepository.getInstance(app)
    private val replayRepo = ReplayRepository.getInstance(app)
    private val artistImageRepo = ArtistImageRepository.getInstance(app)
    private val albumRepo = AlbumRepository.getInstance(app)
    private val downloadRepo = DownloadRepository.getInstance(app)

    /** Lagu yang sudah diunduh (terbaru di atas), untuk halaman Koleksi > Diunduh. */
    val downloadedSongs: StateFlow<List<Song>> = downloadRepo.downloaded
        .map { it.toDisplaySongs() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, downloadRepo.downloaded.value.toDisplaySongs())

    /** URL lagu yang sudah diunduh. */
    val downloadedUrls: StateFlow<Set<String>> = downloadRepo.downloaded
        .map { list -> list.map { it.song.url }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, downloadRepo.downloaded.value.map { it.song.url }.toSet())

    /** URL lagu yang sedang diunduh atau menunggu giliran. */
    val downloadingUrls: StateFlow<Set<String>> = downloadRepo.downloading

    /** Pesan singkat untuk Toast (mulai, selesai, gagal). */
    val downloadEvents: SharedFlow<String> = downloadRepo.events

    private val _importState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)

    /** Kemajuan impor playlist dari link (YouTube Music / Spotify). */
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    private var importJob: Job? = null

    /** Impor playlist dari [link]; hasilnya masuk ke daftar playlist dan dilaporkan lewat [importState]. */
    fun importPlaylist(link: String) {
        if (importJob?.isActive == true) return
        _importState.value = PlaylistImportState.Running()

        importJob = viewModelScope.launch {
            try {
                val result = PlaylistImporter.importFromLink(link) { done, total ->
                    _importState.update { current ->
                        // Jangan menimpa hasil akhir/pembatalan kalau ada callback yang telat
                        if (current is PlaylistImportState.Running) PlaylistImportState.Running(done, total) else current
                    }
                }
                val id = repo.createPlaylistWithSongs(result.name, result.songs)
                _importState.value = PlaylistImportState.Done(id, result.name, result.songs.size, result.note)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                _importState.value = PlaylistImportState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                _importState.value = PlaylistImportState.Failed(
                    "Gagal mengimpor playlist. Periksa koneksi internet lalu coba lagi."
                )
            }
        }
    }

    /** Batalkan impor yang sedang berjalan (jika ada) dan tutup statusnya. */
    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importState.value = PlaylistImportState.Idle
    }

    /** Dipanggil setelah hasil impor ditampilkan, supaya tidak muncul lagi. */
    fun consumeImport() {
        _importState.value = PlaylistImportState.Idle
    }

    fun downloadSong(song: Song) = downloadRepo.download(song)

    fun downloadSongs(songs: List<Song>) = downloadRepo.downloadAll(songs)

    fun removeDownload(songUrl: String) = downloadRepo.remove(songUrl)

    /** Foto profil artis (kunci: nama artis huruf kecil). */
    val artistImages: StateFlow<Map<String, String>> = artistImageRepo.images

    fun requestArtistImage(name: String) = artistImageRepo.request(name)

    /** Nama album tiap lagu (kunci: URL lagu) untuk "Your Top Albums". */
    val songAlbums: StateFlow<Map<String, String>> = albumRepo.albums

    fun requestSongAlbum(song: Song) = albumRepo.request(song)

    /** Statistik dengar per bulan (kunci "2026-03") untuk layar Replay. */
    val replayStats: StateFlow<Map<String, MonthStats>> = replayRepo.stats

    val state: StateFlow<LibraryState> = repo.state

    /** Riwayat lagu yang diputar, terbaru di atas. */
    val playHistory: StateFlow<List<PlayedSong>> = historyRepo.songs

    fun removeFromHistory(songUrl: String) = historyRepo.removeSong(songUrl)

    fun clearPlayHistory() = historyRepo.clearSongs()

    fun toggleLike(song: Song) = repo.toggleLike(song)

    fun createPlaylist(name: String, firstSong: Song? = null) =
        repo.createPlaylist(name, firstSong)

    fun deletePlaylist(id: String) = repo.deletePlaylist(id)

    fun addToPlaylist(playlistId: String, song: Song) = repo.addToPlaylist(playlistId, song)

    fun removeFromPlaylist(playlistId: String, songUrl: String) =
        repo.removeFromPlaylist(playlistId, songUrl)

    fun setQuickPlaylist(id: String?) = repo.setQuickPlaylist(id)

    fun renamePlaylist(id: String, name: String) = repo.renamePlaylist(id, name)

    fun setPlaylistThumbnail(id: String, uri: Uri) = repo.setPlaylistThumbnail(id) {
        getApplication<Application>().contentResolver.openInputStream(uri)
    }

    fun moveInPlaylist(playlistId: String, from: Int, to: Int) =
        repo.moveInPlaylist(playlistId, from, to)

}