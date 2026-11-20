package com.fahrii.musicapp.data

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Nama artis lengkap sebuah lagu. Sumber data (Deezer dan hasil pencarian YouTube Music) hanya
 * memuat artis pertama, jadi kolaborasi (mis. "Olivia Rodrigo" + "Chappell Roan") baru lengkap
 * kalau dibaca dari deskripsi video YouTube Music. Dipanggil malas, hanya untuk lagu yang tampil
 * di layar, dan hasilnya di-cache selama aplikasi hidup.
 */
object ArtistNames {

    // Daftar artis per lagu (satu elemen per artis), supaya nama yang berisi "&" tidak salah terpecah
    private val cache = ConcurrentHashMap<String, List<String>>()

    // Tiap pencarian cukup berat, jadi dibatasi beberapa sekaligus
    private val gate = Semaphore(3)

    /** Nama lengkap yang sudah pernah didapat, atau null kalau belum. */
    fun cached(song: Song): String? = cache[song.url]?.joinToString(" & ")

    /** Artis lagu satu per satu; sebelum berhasil dilengkapi, hanya [Song.artist]. */
    fun names(song: Song): List<String> = cache[song.url] ?: listOf(song.artist)

    /**
     * Nama artis untuk ditampilkan. Kalau tidak bisa dilengkapi (sudah lebih dari satu artis, lagu
     * tidak cocok, atau gagal koneksi), yang dikembalikan tetap [Song.artist]. Yang gagal koneksi
     * tidak di-cache, supaya dicoba lagi lain kali.
     */
    suspend fun resolve(song: Song): String = resolveNames(song).joinToString(" & ")

    /** Seperti [resolve], tapi berupa daftar artis satu per satu. */
    suspend fun resolveNames(song: Song): List<String> {
        cache[song.url]?.let { return it }
        if (song.isAlbumEntry() || !needsLookup(song.artist)) return listOf(song.artist)

        return gate.withPermit {
            cache[song.url]?.let { return@withPermit it }
            val result = runCatching { lookup(song) }
            if (result.isFailure) return@withPermit listOf(song.artist)
            val full = result.getOrNull() ?: listOf(song.artist)
            cache[song.url] = full
            full
        }
    }

    /** Sudah berisi lebih dari satu artis (mis. dari impor Spotify) atau tidak ada artis: tidak perlu dicari. */
    private fun needsLookup(artist: String): Boolean {
        if (artist.isBlank() || artist == "Unknown artist") return false
        return !artist.contains(',') && !artist.contains('&') && !artist.contains(" feat", ignoreCase = true)
    }

    private suspend fun lookup(song: Song): List<String>? {
        val names = if (song.url.startsWith("sonora://")) {
            val meta = MusicRepository.resolveSongMeta(song.url) ?: return null
            // Hasil pertama pencarian bisa saja lagu lain; hanya dipakai kalau judulnya sama
            if (!sameTitle(meta.song.title, song.title)) return null
            meta.artists
        } else {
            MusicRepository.fetchArtistsByUrl(song.url) ?: return null
        }
        // Artis utama dari sumber awal harus ada di daftar lengkap, kalau tidak berarti salah lagu
        val primary = normalize(song.artist.removeSuffix(" - Topic"))
        return if (primary.isNotEmpty() && normalize(names.joinToString(" ")).contains(primary)) names else null
    }

    private fun sameTitle(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        return na.isNotEmpty() && nb.isNotEmpty() && (na.contains(nb) || nb.contains(na))
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}