package com.fahrii.musicapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.fahrii.musicapp.data.LibraryRepository
import com.fahrii.musicapp.data.LibraryState
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository.getInstance(app)

    val state: StateFlow<LibraryState> = repo.state

    fun toggleLike(song: Song) = repo.toggleLike(song)

    fun createPlaylist(name: String, firstSong: Song? = null) =
        repo.createPlaylist(name, firstSong)

    fun deletePlaylist(id: String) = repo.deletePlaylist(id)

    fun addToPlaylist(playlistId: String, song: Song) = repo.addToPlaylist(playlistId, song)

    fun removeFromPlaylist(playlistId: String, songUrl: String) =
        repo.removeFromPlaylist(playlistId, songUrl)

}