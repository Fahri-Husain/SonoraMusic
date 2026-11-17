package com.fahrii.musicapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.fahrii.musicapp.data.ArtistImageRepository
import com.fahrii.musicapp.data.HistoryRepository
import com.fahrii.musicapp.data.LibraryRepository
import com.fahrii.musicapp.data.LibraryState
import com.fahrii.musicapp.data.MonthStats
import com.fahrii.musicapp.data.ReplayRepository
import com.fahrii.musicapp.data.PlayedSong
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository.getInstance(app)

    private val historyRepo = HistoryRepository.getInstance(app)
    private val replayRepo = ReplayRepository.getInstance(app)
    private val artistImageRepo = ArtistImageRepository.getInstance(app)

    /** Foto profil artis (kunci: nama artis huruf kecil). */
    val artistImages: StateFlow<Map<String, String>> = artistImageRepo.images

    fun requestArtistImage(name: String) = artistImageRepo.request(name)

    /** Statistik dengar per bulan (kunci "2026-03") untuk layar Replay. */
    val replayStats: StateFlow<Map<String, MonthStats>> = replayRepo.stats

    val state: StateFlow<LibraryState> = repo.state

    /** Riwayat lagu yang diputar, terbaru di atas. */
    val playHistory: StateFlow<List<PlayedSong>> = historyRepo.songs

    fun removeFromHistory(songUrl: String) = historyRepo.removeSong(songUrl)

    fun toggleLike(song: Song) = repo.toggleLike(song)

    fun createPlaylist(name: String, firstSong: Song? = null) =
        repo.createPlaylist(name, firstSong)

    fun deletePlaylist(id: String) = repo.deletePlaylist(id)

    fun addToPlaylist(playlistId: String, song: Song) = repo.addToPlaylist(playlistId, song)

    fun removeFromPlaylist(playlistId: String, songUrl: String) =
        repo.removeFromPlaylist(playlistId, songUrl)

}