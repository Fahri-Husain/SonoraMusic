package com.fahrii.musicapp.data


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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.time.LocalDate
import kotlin.collections.forEach

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
 * Jumlah play bertambah hanya saat sebuah lagu selesai diputar sampai habis (salah ketuk atau
 * di-skip tidak dihitung); waktu dengar dikumpulkan sambil lagu berjalan dan dijumlahkan dari
 * semua lagu dalam satu bulan.
 */
class ReplayRepository(dir: File) {

    private val file = File(dir, "replay_stats.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private var delayedSave: Job? = null

    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<Map<String, MonthStats>> = _stats.asStateFlow()

    // Waktu dengar ditampung dulu supaya tidak memicu update tiap 0,5 detik
    private var pendingSong: Song? = null
    private var pendingMs = 0L

    // Lagu yang sedang diputar dan berapa lama sudah didengar sejak mulai (atau sejak ulangan
    // terakhir). Dipakai untuk memutuskan apakah lagu itu benar-benar selesai didengarkan.
    private var currentSong: Song? = null
    private var currentListenedMs = 0L
    private var currentDurationMs = 0L

    /**
     * Lagu mulai diputar (ditekan atau lanjut otomatis). Belum menambah play: play baru dihitung
     * di [songCompleted] saat lagunya selesai.
     */
    fun onSongStarted(song: Song) {
        flush()
        currentSong = song
        currentListenedMs = 0L
        currentDurationMs = 0L
    }

    /**
     * Lagu yang sedang diputar sudah sampai habis (lanjut otomatis ke lagu berikutnya, diulang,
     * atau antrian selesai): tambah 1 play. Kalau lagunya hanya di-seek ke ujung dan ditonton
     * sebentar (kurang dari separuh durasi), tidak dihitung.
     */
    fun songCompleted() {
        val song = currentSong ?: return
        flush()

        val enough = if (currentDurationMs > 0) {
            currentListenedMs >= currentDurationMs / 2
        } else {
            currentListenedMs >= MIN_LISTEN_WITHOUT_DURATION_MS
        }
        // Siap untuk putaran berikutnya (mis. mode ulang satu lagu); juga mencegah dihitung dua kali
        currentListenedMs = 0L
        if (!enough) return

        bump(song, plays = 1, ms = 0L)
        saveNow()
    }

    fun addListening(song: Song, ms: Long, durationMs: Long = 0L) {
        // Lagu berganti tanpa lewat onSongStarted (mis. lanjut otomatis): mulai sesi baru
        if (currentSong?.url != song.url) onSongStarted(song)
        if (durationMs > 0) currentDurationMs = durationMs
        currentListenedMs += ms

        val pending = pendingSong
        if (pending != null && pending.url != song.url) flush()
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

    /** Gabungkan statistik dari berkas cadangan tanpa menggandakan angka (lihat [mergeReplay]). */
    fun mergeBackup(incoming: Map<String, MonthStats>): SectionReport {
        flush()
        var merged: Merged<Map<String, MonthStats>>? = null
        _stats.update { current -> mergeReplay(current, incoming).also { merged = it }.value }
        saveNow()
        return merged!!.report
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
                val dto = _stats.value.mapValues { (_, month) ->
                    MonthDto(
                        songs = month.songs.map { s ->
                            MonthSongDto(
                                url = s.song.url,
                                title = s.song.title,
                                artist = s.song.artist,
                                thumbnailUrl = s.song.thumbnailUrl,
                                durationSeconds = s.song.durationSeconds,
                                plays = s.plays,
                                listenedMs = s.listenedMs
                            )
                        },
                        artists = month.artists.map { a ->
                            MonthArtistDto(
                                name = a.name,
                                plays = a.plays,
                                listenedMs = a.listenedMs,
                                imageUrl = a.imageUrl
                            )
                        }
                    )
                }
                val tmp = File(file.parentFile, "replay_stats.json.tmp")
                tmp.writeText(StoreJson.encodeToString(replaySerializer, dto))
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
            StoreJson.decodeFromString(replaySerializer, file.readText())
                .mapValuesTo(LinkedHashMap<String, MonthStats>()) { (_, m) ->
                    MonthStats(
                        songs = m.songs.map { o ->
                            MonthSong(
                                song = Song(o.url, o.title, o.artist, o.thumbnailUrl, o.durationSeconds),
                                plays = o.plays,
                                listenedMs = o.listenedMs
                            )
                        },
                        artists = m.artists.map { a ->
                            MonthArtist(a.name, a.plays, a.listenedMs, a.imageUrl)
                        }
                    )
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val FLUSH_EVERY_MS = 5_000L
        private const val SAVE_DELAY_MS = 15_000L

        /** Kalau durasi lagu tidak diketahui, lagu dianggap selesai didengar setelah selama ini. */
        private const val MIN_LISTEN_WITHOUT_DURATION_MS = 30_000L
    }
}

@Serializable
private class MonthSongDto(
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = -1L,
    val plays: Int = 0,
    val listenedMs: Long = 0L
)

@Serializable
private class MonthArtistDto(
    val name: String,
    val plays: Int = 0,
    val listenedMs: Long = 0L,
    val imageUrl: String? = null
)

@Serializable
private class MonthDto(
    val songs: List<MonthSongDto> = emptyList(),
    val artists: List<MonthArtistDto> = emptyList()
)

private val replaySerializer = MapSerializer(String.serializer(), MonthDto.serializer())
