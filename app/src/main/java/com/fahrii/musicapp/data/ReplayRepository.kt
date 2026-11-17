package com.fahrii.musicapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import java.time.LocalDate

data class MonthSong(val song: Song, val plays: Int, val listenedMs: Long)

data class MonthArtist(
    val name: String,
    val plays: Int,
    val listenedMs: Long,
    val imageUrl: String?
)

data class MonthStats(
    val songs: List<MonthSong> = emptyList(),
    val artists: List<MonthArtist> = emptyList()
) {
    val totalMs: Long get() = songs.sumOf { it.listenedMs }
}

/**
 * Statistik dengar per bulan untuk layar Replay. Kunci bulan berformat "2026-03".
 * Jumlah play bertambah tiap lagu mulai diputar; waktu dengar dikumpulkan sambil lagu
 * berjalan dan dijumlahkan dari semua lagu dalam satu bulan.
 */
class ReplayRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "replay_stats.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private var delayedSave: Job? = null

    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<Map<String, MonthStats>> = _stats.asStateFlow()

    // Waktu dengar ditampung dulu supaya tidak memicu update tiap 0,5 detik
    private var pendingSong: Song? = null
    private var pendingMs = 0L
    private var lastStartedUrl: String? = null

    /** Lagu mulai diputar (ditekan atau lanjut otomatis): tambah 1 play. */
    fun recordPlay(song: Song) {
        flush()
        lastStartedUrl = song.url
        bump(song, plays = 1, ms = 0L)
        saveNow()
    }

    fun addListening(song: Song, ms: Long) {
        // Jaga-jaga kalau lagu berganti otomatis tanpa tercatat sebagai play
        if (lastStartedUrl != song.url) recordPlay(song)
        val current = pendingSong
        if (current != null && current.url != song.url) flush()
        pendingSong = song
        pendingMs += ms
        if (pendingMs >= FLUSH_EVERY_MS) flush()
    }

    /** Masukkan waktu dengar yang tertunda ke statistik (dipanggil saat pause / ganti lagu). */
    fun flush() {
        val song = pendingSong ?: return
        val ms = pendingMs
        pendingSong = null
        pendingMs = 0L
        if (ms > 0) {
            bump(song, plays = 0, ms = ms)
            saveLater()
        }
    }

    private fun bump(song: Song, plays: Int, ms: Long) {
        val key = currentKey()
        _stats.update { all ->
            val month = all[key] ?: MonthStats()

            val songs = month.songs.toMutableList()
            val si = songs.indexOfFirst { it.song.url == song.url }
            if (si >= 0) {
                val old = songs[si]
                songs[si] = old.copy(
                    song = song.copy(thumbnailUrl = song.thumbnailUrl ?: old.song.thumbnailUrl),
                    plays = old.plays + plays,
                    listenedMs = old.listenedMs + ms
                )
            } else {
                songs.add(MonthSong(song, plays, ms))
            }

            val artists = month.artists.toMutableList()
            val name = song.artist.trim()
            if (name.isNotEmpty() && name != "Unknown artist") {
                val ai = artists.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                if (ai >= 0) {
                    val old = artists[ai]
                    artists[ai] = old.copy(
                        plays = old.plays + plays,
                        listenedMs = old.listenedMs + ms,
                        imageUrl = song.thumbnailUrl ?: old.imageUrl
                    )
                } else {
                    artists.add(MonthArtist(name, plays, ms, song.thumbnailUrl))
                }
            }

            all + (key to MonthStats(songs, artists))
        }
    }

    private fun currentKey(): String {
        val d = LocalDate.now()
        return "%04d-%02d".format(d.year, d.monthValue)
    }

    private fun saveNow() {
        delayedSave?.cancel()
        scope.launch { save() }
    }

    private fun saveLater() {
        if (delayedSave?.isActive == true) return
        delayedSave = scope.launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    private suspend fun save() {
        saveMutex.withLock {
            runCatching {
                val root = JSONObject()
                _stats.value.forEach { (key, month) ->
                    root.put(key, JSONObject().apply {
                        put("songs", JSONArray().also { arr ->
                            month.songs.forEach { s ->
                                arr.put(JSONObject().apply {
                                    put("url", s.song.url)
                                    put("title", s.song.title)
                                    put("artist", s.song.artist)
                                    put("thumbnailUrl", s.song.thumbnailUrl ?: JSONObject.NULL)
                                    put("durationSeconds", s.song.durationSeconds)
                                    put("plays", s.plays)
                                    put("listenedMs", s.listenedMs)
                                })
                            }
                        })
                        put("artists", JSONArray().also { arr ->
                            month.artists.forEach { a ->
                                arr.put(JSONObject().apply {
                                    put("name", a.name)
                                    put("plays", a.plays)
                                    put("listenedMs", a.listenedMs)
                                    put("imageUrl", a.imageUrl ?: JSONObject.NULL)
                                })
                            }
                        })
                    })
                }
                val tmp = File(file.parentFile, "replay_stats.json.tmp")
                tmp.writeText(root.toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }
        }
    }

    private fun load(): Map<String, MonthStats> {
        if (!file.exists()) return emptyMap()
        return try {
            val root = JSONObject(file.readText())
            val result = LinkedHashMap<String, MonthStats>()
            root.keys().forEach { key ->
                val m = root.getJSONObject(key)
                val songsArr = m.optJSONArray("songs") ?: JSONArray()
                val artistsArr = m.optJSONArray("artists") ?: JSONArray()
                result[key] = MonthStats(
                    songs = List(songsArr.length()) { i ->
                        val o = songsArr.getJSONObject(i)
                        MonthSong(
                            song = Song(
                                url = o.getString("url"),
                                title = o.getString("title"),
                                artist = o.getString("artist"),
                                thumbnailUrl = if (o.isNull("thumbnailUrl")) null else o.getString("thumbnailUrl"),
                                durationSeconds = o.optLong("durationSeconds", -1L)
                            ),
                            plays = o.optInt("plays", 0),
                            listenedMs = o.optLong("listenedMs", 0L)
                        )
                    },
                    artists = List(artistsArr.length()) { i ->
                        val o = artistsArr.getJSONObject(i)
                        MonthArtist(
                            name = o.getString("name"),
                            plays = o.optInt("plays", 0),
                            listenedMs = o.optLong("listenedMs", 0L),
                            imageUrl = if (o.isNull("imageUrl")) null else o.getString("imageUrl")
                        )
                    }
                )
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val FLUSH_EVERY_MS = 5_000L
        private const val SAVE_DELAY_MS = 15_000L

        @Volatile
        private var instance: ReplayRepository? = null

        fun getInstance(context: Context): ReplayRepository =
            instance ?: synchronized(this) {
                instance ?: ReplayRepository(context.applicationContext).also { instance = it }
            }
    }
}