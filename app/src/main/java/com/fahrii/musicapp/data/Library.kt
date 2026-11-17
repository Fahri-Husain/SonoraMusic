package com.fahrii.musicapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song>
)

data class LibraryState(
    val liked: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

class LibraryRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "library.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun toggleLike(song: Song) {
        _state.update { s ->
            if (s.liked.any { it.url == song.url }) {
                s.copy(liked = s.liked.filterNot { it.url == song.url })
            } else {
                s.copy(liked = listOf(song) + s.liked)
            }
        }
        persist()
    }

    fun createPlaylist(name: String, firstSong: Song? = null) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            songs = listOfNotNull(firstSong)
        )
        _state.update { it.copy(playlists = it.playlists + playlist) }
        persist()
    }

    fun deletePlaylist(id: String) {
        _state.update { s -> s.copy(playlists = s.playlists.filterNot { it.id == id }) }
        persist()
    }

    fun addToPlaylist(playlistId: String, song: Song) {
        _state.update { s ->
            s.copy(playlists = s.playlists.map { p ->
                if (p.id == playlistId && p.songs.none { it.url == song.url }) {
                    p.copy(songs = p.songs + song)
                } else {
                    p
                }
            })
        }
        persist()
    }

    fun removeFromPlaylist(playlistId: String, songUrl: String) {
        _state.update { s ->
            s.copy(playlists = s.playlists.map { p ->
                if (p.id == playlistId) p.copy(songs = p.songs.filterNot { it.url == songUrl }) else p
            })
        }
        persist()
    }

    // Simpan selalu pakai state terbaru, bergantian lewat mutex supaya nggak saling timpa
    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                runCatching {
                    val tmp = File(file.parentFile, "library.json.tmp")
                    tmp.writeText(_state.value.toJson().toString())
                    if (!tmp.renameTo(file)) {
                        file.writeText(tmp.readText())
                        tmp.delete()
                    }
                }
            }
        }
    }

    private fun load(): LibraryState {
        if (!file.exists()) return LibraryState()
        return try {
            val root = JSONObject(file.readText())
            val liked = root.optJSONArray("liked").toSongs()
            val playlists = root.optJSONArray("playlists")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Playlist(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        songs = o.optJSONArray("songs").toSongs()
                    )
                }
            } ?: emptyList()
            LibraryState(liked, playlists)
        } catch (e: Exception) {
            LibraryState()
        }
    }

    companion object {
        @Volatile
        private var instance: LibraryRepository? = null

        fun getInstance(context: Context): LibraryRepository =
            instance ?: synchronized(this) {
                instance ?: LibraryRepository(context.applicationContext).also { instance = it }
            }
    }
}

private fun Song.toJson(): JSONObject = JSONObject().apply {
    put("url", url)
    put("title", title)
    put("artist", artist)
    put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
    put("durationSeconds", durationSeconds)
}

private fun JSONObject.toSong() = Song(
    url = getString("url"),
    title = getString("title"),
    artist = getString("artist"),
    thumbnailUrl = if (isNull("thumbnailUrl")) null else getString("thumbnailUrl"),
    durationSeconds = optLong("durationSeconds", -1L)
)

private fun JSONArray?.toSongs(): List<Song> {
    if (this == null) return emptyList()
    return List(length()) { i -> getJSONObject(i).toSong() }
}

private fun List<Song>.toJsonArray(): JSONArray {
    val arr = JSONArray()
    forEach { arr.put(it.toJson()) }
    return arr
}

private fun LibraryState.toJson(): JSONObject = JSONObject().apply {
    put("liked", liked.toJsonArray())
    put("playlists", JSONArray().also { arr ->
        playlists.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("songs", p.songs.toJsonArray())
            })
        }
    })
}