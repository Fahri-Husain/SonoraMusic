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

data class ArtistStat(
    val name: String,
    val plays: Int,
    val imageUrl: String?
)

/** Satu lagu di riwayat putar. */
data class PlayedSong(
    val song: Song,
    val plays: Int,
    val lastPlayedAt: Long
)

class HistoryRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "history.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val songFile = File(context.filesDir, "song_history.json")
    private val saveMutex = Mutex()

    private val _songs = MutableStateFlow(loadSongs())
    /** Lagu yang pernah diputar, yang terbaru di paling atas (tanpa duplikat). */
    val songs: StateFlow<List<PlayedSong>> = _songs.asStateFlow()

    private val _artists = MutableStateFlow(load())
    /** Artis yang pernah diputar, diurutkan dari yang paling sering. */
    val artists: StateFlow<List<ArtistStat>> = _artists.asStateFlow()

    /** Catat satu pemutaran: masuk ke riwayat lagu dan statistik artis. */
    fun record(song: Song) {
        recordSong(song)
        recordArtist(song)
    }

    private fun recordSong(song: Song) {
        val now = System.currentTimeMillis()
        _songs.update { list ->
            val existing = list.firstOrNull { it.song.url == song.url }
            val entry = PlayedSong(
                song = song,
                plays = (existing?.plays ?: 0) + 1,
                lastPlayedAt = now
            )
            (listOf(entry) + list.filterNot { it.song.url == song.url }).take(MAX_SONGS)
        }
        persistSongs()
    }

    fun removeSong(url: String) {
        _songs.update { list -> list.filterNot { it.song.url == url } }
        persistSongs()
    }

    private fun recordArtist(song: Song) {
        val name = song.artist.trim()
        if (name.isEmpty() || name == "Unknown artist") return

        _artists.update { list ->
            val existing = list.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val updated = if (existing == null) {
                ArtistStat(name, 1, song.thumbnailUrl)
            } else {
                existing.copy(
                    plays = existing.plays + 1,
                    imageUrl = song.thumbnailUrl ?: existing.imageUrl
                )
            }
            (list.filterNot { it === existing } + updated).sortedByDescending { it.plays }
        }
        persist()
    }

    private fun persistSongs() {
        scope.launch {
            saveMutex.withLock {
                runCatching {
                    val arr = JSONArray()
                    _songs.value.forEach { p ->
                        arr.put(JSONObject().apply {
                            put("url", p.song.url)
                            put("title", p.song.title)
                            put("artist", p.song.artist)
                            put("thumbnailUrl", p.song.thumbnailUrl ?: JSONObject.NULL)
                            put("durationSeconds", p.song.durationSeconds)
                            put("plays", p.plays)
                            put("lastPlayedAt", p.lastPlayedAt)
                        })
                    }
                    songFile.writeText(arr.toString())
                }
            }
        }
    }

    private fun loadSongs(): List<PlayedSong> = try {
        val arr = JSONArray(songFile.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PlayedSong(
                song = Song(
                    url = o.getString("url"),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    thumbnailUrl = if (o.isNull("thumbnailUrl")) null else o.getString("thumbnailUrl"),
                    durationSeconds = o.optLong("durationSeconds", -1L)
                ),
                plays = o.optInt("plays", 1),
                lastPlayedAt = o.optLong("lastPlayedAt", 0L)
            )
        }.sortedByDescending { it.lastPlayedAt }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                runCatching {
                    val arr = JSONArray()
                    _artists.value.forEach { a ->
                        arr.put(JSONObject().apply {
                            put("name", a.name)
                            put("plays", a.plays)
                            put("imageUrl", a.imageUrl ?: JSONObject.NULL)
                        })
                    }
                    file.writeText(arr.toString())
                }
            }
        }
    }

    private fun load(): List<ArtistStat> = try {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ArtistStat(
                name = o.getString("name"),
                plays = o.optInt("plays", 1),
                imageUrl = if (o.isNull("imageUrl")) null else o.getString("imageUrl")
            )
        }.sortedByDescending { it.plays }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val MAX_SONGS = 200

        @Volatile
        private var instance: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}