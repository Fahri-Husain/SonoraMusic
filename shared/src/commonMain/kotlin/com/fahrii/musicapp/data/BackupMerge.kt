package com.fahrii.musicapp.data

import kotlin.collections.forEach
import kotlin.collections.iterator
import kotlin.collections.plusAssign
import kotlin.collections.set

/*
 * Logika penggabungan (merge) data cadangan. Sengaja dibuat murni Kotlin tanpa Android/IO supaya
 * mudah dites. Aturan utamanya: data yang SUDAH ADA di aplikasi tidak diimpor lagi, hanya yang
 * BELUM ADA yang ditambahkan. Mengimpor berkas yang sama berkali-kali tidak mengubah apa pun.
 */

/** Ringkasan satu bagian data (mis. Favorit) setelah digabung. */
data class SectionReport(
    /** Jumlah yang baru ditambahkan. */
    val added: Int = 0,
    /** Jumlah yang sudah ada di aplikasi sehingga dilewati. */
    val existing: Int = 0,
    /** Dari yang sudah ada, berapa yang angkanya diperbarui karena di cadangan lebih besar. */
    val updated: Int = 0
)

/** Hasil impor seluruh bagian; dipakai untuk memberi tahu pengguna apa yang masuk dan apa yang sudah ada. */
data class ImportReport(
    val liked: SectionReport = SectionReport(),
    /** added = playlist baru, existing = playlist yang sudah ada (isinya digabung). */
    val playlists: SectionReport = SectionReport(),
    /** Lagu di dalam playlist (baik playlist baru maupun yang digabung). */
    val playlistSongs: SectionReport = SectionReport(),
    val history: SectionReport = SectionReport(),
    val replay: SectionReport = SectionReport()
) {
    private val all get() = listOf(liked, playlists, playlistSongs, history, replay)

    /** False kalau semua isi cadangan ternyata sudah ada di aplikasi. */
    val hasNewData: Boolean get() = all.any { it.added > 0 || it.updated > 0 }
}

/** Playlist di berkas cadangan. [coverBytes] = JPEG kecil dari cover buatan pengguna (kalau ada). */
class BackupPlaylist(
    val id: String,
    val name: String,
    val songs: List<Song>,
    val createdAt: Long,
    val coverBytes: ByteArray? = null
)

class Merged<T>(val value: T, val report: SectionReport)

class LibraryMerge(
    val state: LibraryState,
    /** Cover yang perlu ditulis ke penyimpanan: pasangan (id playlist, byte JPEG). */
    val covers: List<Pair<String, ByteArray>>,
    val liked: SectionReport,
    val playlists: SectionReport,
    val playlistSongs: SectionReport
)

/** Indeks untuk mengenali lagu yang sama: URL persis sama, atau judul + artis sama. */
private class SongIndex(songs: Iterable<Song> = emptyList()) {
    private val urls = HashSet<String>()
    private val keys = HashSet<String>()

    init {
        songs.forEach { add(it) }
    }

    operator fun contains(song: Song): Boolean {
        if (song.url in urls) return true
        val key = song.usableKey() ?: return false
        return key in keys
    }

    fun add(song: Song) {
        urls += song.url
        song.usableKey()?.let { keys += it }
    }
}

/** Kunci judul + artis; null kalau salah satunya kosong (supaya lagu tanpa judul tidak dianggap sama semua). */
private fun Song.usableKey(): String? {
    val key = identityKey()
    return if (key.startsWith("|") || key.endsWith("|")) null else key
}

/**
 * Gabungkan Favorit dan playlist dari cadangan ke [current].
 * - Favorit: lagu yang sudah ada dilewati, sisanya ditaruh di bawah daftar yang sekarang.
 * - Playlist: dikenali dari id, atau (kalau id beda) dari nama yang sama dengan playlist yang
 *   sudah ada sebelum impor. Playlist yang sudah ada hanya ditambah lagu yang belum ada di dalamnya.
 * - Playlist tujuan cepat hanya diisi kalau belum dipilih.
 */
fun mergeLibrary(
    current: LibraryState,
    incomingLiked: List<Song>,
    incomingPlaylists: List<BackupPlaylist>,
    incomingQuickPlaylistId: String?,
    now: Long
): LibraryMerge {
    // --- Favorit ---
    val likedIndex = SongIndex(current.liked)
    val newLiked = ArrayList<Song>()
    var likedExisting = 0
    for (song in incomingLiked) {
        if (song in likedIndex) {
            likedExisting++
        } else {
            newLiked += song
            likedIndex.add(song)
        }
    }

    // --- Playlist ---
    val originals = current.playlists
    val playlists = originals.toMutableList() // indeks 0 until originals.size tetap milik playlist lama
    val covers = ArrayList<Pair<String, ByteArray>>()
    val localIdOf = HashMap<String, String>() // id di cadangan -> id di aplikasi
    var newPlaylists = 0
    var existingPlaylists = 0
    var coversApplied = 0
    var songsAdded = 0
    var songsExisting = 0

    for (backup in incomingPlaylists) {
        val byId = playlists.indexOfFirst { it.id == backup.id }
        val byName = if (byId >= 0) -1 else originals.indexOfFirst {
            it.name.trim().equals(backup.name.trim(), ignoreCase = true)
        }
        val index = if (byId >= 0) byId else byName

        if (index >= 0) {
            val target = playlists[index]
            val songIndex = SongIndex(target.songs)
            val toAdd = ArrayList<Song>()
            for (song in backup.songs) {
                if (song in songIndex) {
                    songsExisting++
                } else {
                    toAdd += song
                    songIndex.add(song)
                }
            }
            songsAdded += toAdd.size
            playlists[index] = target.copy(songs = target.songs + toAdd)
            existingPlaylists++
            localIdOf[backup.id] = target.id
            if (target.thumbnailPath == null && backup.coverBytes != null) {
                covers += target.id to backup.coverBytes
                coversApplied++
            }
        } else {
            val songIndex = SongIndex()
            val songs = ArrayList<Song>()
            for (song in backup.songs) {
                if (song in songIndex) {
                    songsExisting++
                } else {
                    songs += song
                    songIndex.add(song)
                }
            }
            songsAdded += songs.size
            playlists += Playlist(
                id = backup.id,
                name = backup.name.trim().ifEmpty { "Playlist impor" },
                songs = songs,
                createdAt = backup.createdAt.takeIf { it > 0L } ?: now
            )
            newPlaylists++
            localIdOf[backup.id] = backup.id
            if (backup.coverBytes != null) covers += backup.id to backup.coverBytes
        }
    }

    val quickId = current.quickPlaylistId
        ?: incomingQuickPlaylistId?.let { localIdOf[it] }

    return LibraryMerge(
        state = current.copy(
            liked = current.liked + newLiked,
            playlists = playlists,
            quickPlaylistId = quickId
        ),
        covers = covers,
        liked = SectionReport(added = newLiked.size, existing = likedExisting),
        playlists = SectionReport(added = newPlaylists, existing = existingPlaylists, updated = coversApplied),
        playlistSongs = SectionReport(added = songsAdded, existing = songsExisting)
    )
}

/**
 * Gabungkan riwayat lagu. Lagu yang sama (judul + artis) tidak dibuat ganda; jumlah putar dan
 * waktu terakhir diputar diambil yang terbesar, bukan dijumlahkan, supaya mengimpor berkas yang
 * sama berulang kali tidak menggelembungkan angkanya.
 */
fun mergeHistorySongs(
    current: List<PlayedSong>,
    incoming: List<PlayedSong>,
    maxSongs: Int
): Merged<List<PlayedSong>> {
    val byKey = LinkedHashMap<String, PlayedSong>()
    current.forEach { byKey.putIfAbsent(it.song.identityKey(), it) }

    val addedKeys = HashSet<String>()
    var existing = 0
    var updated = 0
    for (item in incoming) {
        val key = item.song.identityKey()
        val have = byKey[key]
        if (have == null) {
            byKey[key] = item
            addedKeys += key
        } else {
            existing++
            if (key in addedKeys) continue // duplikat di dalam cadangan itu sendiri
            val plays = maxOf(have.plays, item.plays)
            val last = maxOf(have.lastPlayedAt, item.lastPlayedAt)
            if (plays != have.plays || last != have.lastPlayedAt) {
                byKey[key] = have.copy(plays = plays, lastPlayedAt = last)
                updated++
            }
        }
    }

    val result = byKey.values.sortedByDescending { it.lastPlayedAt }.take(maxSongs)
    val surviving = result.mapTo(HashSet()) { it.song.identityKey() }
    return Merged(
        value = result,
        report = SectionReport(added = addedKeys.count { it in surviving }, existing = existing, updated = updated)
    )
}

/** Gabungkan statistik artis di riwayat: nama sama (tanpa beda huruf besar/kecil) diambil putar terbanyak. */
fun mergeArtistStats(current: List<ArtistStat>, incoming: List<ArtistStat>): List<ArtistStat> {
    val byName = LinkedHashMap<String, ArtistStat>()
    current.forEach { byName.putIfAbsent(it.name.trim().lowercase(), it) }
    for (item in incoming) {
        val key = item.name.trim().lowercase()
        if (key.isEmpty()) continue
        val have = byName[key]
        byName[key] = if (have == null) {
            item
        } else {
            have.copy(plays = maxOf(have.plays, item.plays), imageUrl = have.imageUrl ?: item.imageUrl)
        }
    }
    return byName.values.sortedByDescending { it.plays }
}

/**
 * Gabungkan statistik Replay per bulan. Lagu dikenali dari URL (sama seperti ReplayRepository),
 * artis dari nama. Jumlah putar dan lama dengar diambil yang terbesar, bukan dijumlahkan.
 */
fun mergeReplay(
    current: Map<String, MonthStats>,
    incoming: Map<String, MonthStats>
): Merged<Map<String, MonthStats>> {
    val result = LinkedHashMap(current)
    var added = 0
    var existing = 0
    var updated = 0

    for ((month, inMonth) in incoming) {
        val cur = result[month] ?: MonthStats()

        val songs = cur.songs.toMutableList()
        val songPos = HashMap<String, Int>()
        songs.forEachIndexed { i, s -> songPos.putIfAbsent(s.song.url, i) }
        for (item in inMonth.songs) {
            val pos = songPos[item.song.url]
            if (pos == null) {
                songs += item
                songPos[item.song.url] = songs.lastIndex
                added++
            } else {
                existing++
                val have = songs[pos]
                val plays = maxOf(have.plays, item.plays)
                val ms = maxOf(have.listenedMs, item.listenedMs)
                if (plays != have.plays || ms != have.listenedMs) {
                    songs[pos] = have.copy(
                        song = have.song.copy(thumbnailUrl = have.song.thumbnailUrl ?: item.song.thumbnailUrl),
                        plays = plays,
                        listenedMs = ms
                    )
                    updated++
                }
            }
        }

        val artists = cur.artists.toMutableList()
        val artistPos = HashMap<String, Int>()
        artists.forEachIndexed { i, a -> artistPos.putIfAbsent(a.name.trim().lowercase(), i) }
        for (item in inMonth.artists) {
            val key = item.name.trim().lowercase()
            if (key.isEmpty()) continue
            val pos = artistPos[key]
            if (pos == null) {
                artists += item
                artistPos[key] = artists.lastIndex
            } else {
                val have = artists[pos]
                artists[pos] = have.copy(
                    plays = maxOf(have.plays, item.plays),
                    listenedMs = maxOf(have.listenedMs, item.listenedMs),
                    imageUrl = have.imageUrl ?: item.imageUrl
                )
            }
        }

        result[month] = MonthStats(songs, artists)
    }

    return Merged(result, SectionReport(added = added, existing = existing, updated = updated))
}
