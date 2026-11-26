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
import java.io.InputStream
import java.io.File
import java.util.UUID


class LibraryRepository(dir: File) {

    private val file = File(dir, "library.json")
    private val coversDir = File(dir, "playlist_covers")
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

    /** Buat playlist berisi [songs] (hasil impor dari link) dan kembalikan id-nya. */
    fun createPlaylistWithSongs(name: String, songs: List<Song>): String {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Playlist impor" },
            songs = songs.distinctBy { it.url },
            createdAt = System.currentTimeMillis()
        )
        _state.update { it.copy(playlists = it.playlists + playlist) }
        persist()
        return playlist.id
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
    fun setPlaylistThumbnail(id: String, openImage: () -> InputStream?) {
        scope.launch {
            val target = runCatching {
                coversDir.mkdirs()
                val out = File(coversDir, "${id}_${System.currentTimeMillis()}.jpg")
                openImage()?.use { input ->
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

    /**
     * Gabungkan Favorit dan playlist dari berkas cadangan. Yang sudah ada dilewati, hanya yang
     * belum ada yang ditambahkan (lihat [mergeLibrary]). Cover playlist ikut dipulihkan.
     */
    fun mergeBackup(
        liked: List<Song>,
        playlists: List<BackupPlaylist>,
        quickPlaylistId: String?
    ): ImportReport {
        var merge: LibraryMerge? = null
        val now = System.currentTimeMillis()
        _state.update { current ->
            mergeLibrary(current, liked, playlists, quickPlaylistId, now).also { merge = it }.state
        }
        val result = merge!!
        persist()

        if (result.covers.isNotEmpty()) {
            coversDir.mkdirs()
            for ((id, bytes) in result.covers) {
                val out = File(coversDir, "${id}_${System.currentTimeMillis()}.jpg")
                if (runCatching { out.writeBytes(bytes) }.isFailure) continue
                _state.update { s ->
                    s.copy(playlists = s.playlists.map { p ->
                        if (p.id == id && p.thumbnailPath == null) p.copy(thumbnailPath = out.absolutePath) else p
                    })
                }
            }
            persist()
        }
        return ImportReport(
            liked = result.liked,
            playlists = result.playlists,
            playlistSongs = result.playlistSongs
        )
    }

    // Simpan selalu pakai state terbaru, bergantian lewat mutex supaya nggak saling timpa
    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                runCatching {
                    val tmp = File(file.parentFile, "library.json.tmp")
                    tmp.writeText(StoreJson.encodeToString(LibraryDto.serializer(), _state.value.toDto()))
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
            val root = StoreJson.decodeFromString(LibraryDto.serializer(), file.readText())
            val now = System.currentTimeMillis()
            val playlists = root.playlists.map { p ->
                Playlist(
                    id = p.id,
                    name = p.name,
                    songs = p.songs.map { it.toSong() },
                    // Playlist lama tidak punya waktu pembuatan: pakai waktu pertama kali dimuat
                    createdAt = p.createdAt.takeIf { it > 0L }
                        ?: now.also { backfilledCreatedAt = true },
                    thumbnailPath = p.thumbnailPath?.ifBlank { null }
                )
            }
            val quickId = root.quickPlaylistId?.takeIf { id -> playlists.any { it.id == id } }
            LibraryState(root.liked.map { it.toSong() }, playlists, quickId)
        } catch (e: Exception) {
            LibraryState()
        }
    }

    companion object
}

@Serializable
private class SongDto(
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = -1L
)

@Serializable
private class PlaylistDto(
    val id: String,
    val name: String,
    val songs: List<SongDto> = emptyList(),
    val createdAt: Long = 0L,
    val thumbnailPath: String? = null
)

@Serializable
private class LibraryDto(
    val liked: List<SongDto> = emptyList(),
    val quickPlaylistId: String? = null,
    val playlists: List<PlaylistDto> = emptyList()
)

private fun SongDto.toSong() = Song(url, title, artist, thumbnailUrl, durationSeconds)

private fun Song.toDto() = SongDto(url, title, artist, thumbnailUrl, durationSeconds)

private fun LibraryState.toDto() = LibraryDto(
    liked = liked.map { it.toDto() },
    quickPlaylistId = quickPlaylistId,
    playlists = playlists.map { p ->
        PlaylistDto(p.id, p.name, p.songs.map { it.toDto() }, p.createdAt, p.thumbnailPath)
    }
)