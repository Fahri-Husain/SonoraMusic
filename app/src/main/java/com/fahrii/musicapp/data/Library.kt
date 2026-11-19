package com.fahrii.musicapp.data

import android.content.Context
import android.net.Uri
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
    val songs: List<Song>,
    /** Waktu playlist dibuat (epoch ms); 0 untuk playlist lama yang belum mencatatnya. */
    val createdAt: Long = 0L,
    /** Path file thumbnail pilihan pengguna (di penyimpanan aplikasi); null = pakai cover lagu. */
    val thumbnailPath: String? = null
)

data class LibraryState(
    val liked: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    /** Playlist tujuan cepat (bar di layar Now Playing); null = belum dipilih. */
    val quickPlaylistId: String? = null
)

class LibraryRepository private constructor(private val context: Context) {

    private val file = File(context.filesDir, "library.json")
    private val coversDir = File(context.filesDir, "playlist_covers")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    // True kalau ada playlist lama (dibuat sebelum waktu pembuatan dicatat) yang diberi tanggal di load()
    private var backfilledCreatedAt = false

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        // Simpan tanggal yang baru diisi, supaya tidak berubah lagi tiap aplikasi dibuka
        if (backfilledCreatedAt) persist()
    }

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
            songs = listOfNotNull(firstSong),
            createdAt = System.currentTimeMillis()
        )
        _state.update { it.copy(playlists = it.playlists + playlist) }
        persist()
    }

    fun deletePlaylist(id: String) {
        val thumb = _state.value.playlists.firstOrNull { it.id == id }?.thumbnailPath
        _state.update { s ->
            s.copy(
                playlists = s.playlists.filterNot { it.id == id },
                // Playlist tujuan cepat yang dihapus otomatis dilepas
                quickPlaylistId = s.quickPlaylistId.takeIf { it != id }
            )
        }
        thumb?.let { runCatching { File(it).delete() } }
        persist()
    }

    /** Pilih playlist tujuan cepat; null untuk melepas pilihan. */
    fun setQuickPlaylist(id: String?) {
        _state.update { s ->
            s.copy(quickPlaylistId = id?.takeIf { target -> s.playlists.any { it.id == target } })
        }
        persist()
    }

    fun renamePlaylist(id: String, name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        _state.update { s ->
            s.copy(playlists = s.playlists.map { p -> if (p.id == id) p.copy(name = cleanName) else p })
        }
        persist()
    }

    /**
     * Pakai gambar [uri] (hasil pilih dari galeri) sebagai thumbnail playlist. Gambar disalin ke
     * penyimpanan aplikasi supaya tetap ada walau izin akses ke galeri dicabut.
     */
    fun setPlaylistThumbnail(id: String, uri: Uri) {
        scope.launch {
            val target = runCatching {
                coversDir.mkdirs()
                val out = File(coversDir, "${id}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: return@launch
                out
            }.getOrNull() ?: return@launch

            val old = _state.value.playlists.firstOrNull { it.id == id }?.thumbnailPath
            _state.update { s ->
                s.copy(playlists = s.playlists.map { p ->
                    if (p.id == id) p.copy(thumbnailPath = target.absolutePath) else p
                })
            }
            old?.let { runCatching { File(it).delete() } }
            persist()
        }
    }

    /** Pindahkan lagu di posisi [from] ke posisi [to] (urutan kustom). */
    fun moveInPlaylist(playlistId: String, from: Int, to: Int) {
        _state.update { s ->
            s.copy(playlists = s.playlists.map { p ->
                if (p.id != playlistId || from !in p.songs.indices || to !in p.songs.indices || from == to) {
                    p
                } else {
                    val list = p.songs.toMutableList()
                    list.add(to, list.removeAt(from))
                    p.copy(songs = list)
                }
            })
        }
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
            val now = System.currentTimeMillis()
            val playlists = root.optJSONArray("playlists")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Playlist(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        songs = o.optJSONArray("songs").toSongs(),
                        // Playlist lama tidak punya waktu pembuatan: pakai waktu pertama kali dimuat
                        createdAt = o.optLong("createdAt", 0L).takeIf { it > 0L }
                            ?: now.also { backfilledCreatedAt = true },
                        thumbnailPath = if (o.isNull("thumbnailPath")) null else o.optString("thumbnailPath").ifBlank { null }
                    )
                }
            } ?: emptyList()
            val quickId = root.optString("quickPlaylistId", "")
                .takeIf { id -> playlists.any { it.id == id } }
            LibraryState(liked, playlists, quickId)
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
    quickPlaylistId?.let { put("quickPlaylistId", it) }
    put("playlists", JSONArray().also { arr ->
        playlists.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("songs", p.songs.toJsonArray())
                put("createdAt", p.createdAt)
                put("thumbnailPath", p.thumbnailPath ?: JSONObject.NULL)
            })
        }
    })
}