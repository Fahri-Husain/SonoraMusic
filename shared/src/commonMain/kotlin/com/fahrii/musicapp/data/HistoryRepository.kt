package com.fahrii.musicapp.data

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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

class HistoryRepository(dir: File) {

    private val file = File(dir, "history.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val songFile = File(dir, "song_history.json")
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
        val key = songKey(song)
        _songs.update { list ->
            // Lagu yang sama dikenali dari judul + artis, bukan URL: URL-nya beda tergantung
            // sumbernya (Pilihan Cepat, album, atau pencarian), padahal lagunya satu.
            val existing = list.firstOrNull { songKey(it.song) == key }
            val entry = PlayedSong(
                song = song,
                plays = (existing?.plays ?: 0) + 1,
                lastPlayedAt = now
            )
            (listOf(entry) + list.filterNot { songKey(it.song) == key }).take(MAX_SONGS)
        }
        persistSongs()
    }

    fun removeSong(url: String) {
        _songs.update { list ->
            val key = list.firstOrNull { it.song.url == url }?.let { songKey(it.song) }
            list.filterNot { it.song.url == url || (key != null && songKey(it.song) == key) }
        }
        persistSongs()
    }

    /** Kosongkan riwayat lagu (Riwayat dan Paling Sering Diputar). Statistik Replay tidak ikut terhapus. */
    fun clearSongs() {
        _songs.value = emptyList()
        persistSongs()
    }

    /** Gabungkan riwayat dari berkas cadangan tanpa membuat entri ganda (lihat [mergeHistorySongs]). */
    fun mergeBackup(songs: List<PlayedSong>, artists: List<ArtistStat>): SectionReport {
        var merged: Merged<List<PlayedSong>>? = null
        _songs.update { current ->
            mergeHistorySongs(current, songs, MAX_SONGS).also { merged = it }.value
        }
        persistSongs()
        _artists.update { current -> mergeArtistStats(current, artists) }
        persist()
        return merged!!.report
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
                    val dto = _songs.value.map { p ->
                        PlayedSongDto(
                            url = p.song.url,
                            title = p.song.title,
                            artist = p.song.artist,
                            thumbnailUrl = p.song.thumbnailUrl,
                            durationSeconds = p.song.durationSeconds,
                            plays = p.plays,
                            lastPlayedAt = p.lastPlayedAt
                        )
                    }
                    songFile.writeText(
                        StoreJson.encodeToString(ListSerializer(PlayedSongDto.serializer()), dto)
                    )
                }
            }
        }
    }


    private fun loadSongs(): List<PlayedSong> = try {
        val loaded = StoreJson
            .decodeFromString(ListSerializer(PlayedSongDto.serializer()), songFile.readText())
            .map { o ->
                PlayedSong(
                    song = Song(o.url, o.title, o.artist, o.thumbnailUrl, o.durationSeconds),
                    plays = o.plays,
                    lastPlayedAt = o.lastPlayedAt
                )
            }
            .sortedByDescending { it.lastPlayedAt }
        mergeDuplicates(loaded)
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Gabungkan entri ganda yang sudah tersimpan sebelum perbaikan ini: pemutaran dijumlahkan,
     * dan lagu yang dipakai adalah yang paling baru diputar. [list] sudah urut terbaru dulu.
     */
    private fun mergeDuplicates(list: List<PlayedSong>): List<PlayedSong> =
        list.groupBy { songKey(it.song) }
            .values
            .map { group -> group.first().copy(plays = group.sumOf { it.plays }) }
            .sortedByDescending { it.lastPlayedAt }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                runCatching {
                    val dto = _artists.value.map { a ->
                        ArtistStatDto(name = a.name, plays = a.plays, imageUrl = a.imageUrl)
                    }
                    file.writeText(
                        StoreJson.encodeToString(ListSerializer(ArtistStatDto.serializer()), dto)
                    )
                }
            }
        }
    }

    private fun load(): List<ArtistStat> = try {
        StoreJson
            .decodeFromString(ListSerializer(ArtistStatDto.serializer()), file.readText())
            .map { ArtistStat(name = it.name, plays = it.plays, imageUrl = it.imageUrl) }
            .sortedByDescending { it.plays }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val MAX_SONGS = 200

        /** Kunci identitas lagu: lihat [identityKey]. */
        private fun songKey(song: Song): String = song.identityKey()
    }
}

@Serializable
private class PlayedSongDto(
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = -1L,
    val plays: Int = 1,
    val lastPlayedAt: Long = 0L
)

@Serializable
private class ArtistStatDto(
    val name: String,
    val plays: Int = 1,
    val imageUrl: String? = null
)
