package com.fahrii.musicapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Nama artis lengkap sebuah lagu. Sumber data (Deezer dan hasil pencarian YouTube Music) hanya
 * memuat artis pertama, jadi kolaborasi (mis. "Olivia Rodrigo" + "Chappell Roan") baru lengkap
 * kalau dibaca dari deskripsi video YouTube Music. Dipanggil malas, hanya untuk lagu yang tampil
 * di layar. Hasilnya di-cache di memori dan juga disimpan ke file, supaya waktu aplikasi dibuka
 * lagi nama kolaborasi langsung tampil tanpa menunggu pencarian ulang.
 */
object ArtistNames {

    // Daftar artis per lagu (satu elemen per artis), supaya nama yang berisi "&" tidak salah terpecah
    private val cache = ConcurrentHashMap<String, List<String>>()
    private const val MAX_STORED = 3000

    // Hasil pencarian yang berhasil (cocok dengan lagunya); hanya ini yang disimpan ke file
    private val store = ConcurrentHashMap<String, List<String>>()
    private var file: File? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    // Tiap pencarian cukup berat, jadi dibatasi beberapa sekaligus
    private val gate = Semaphore(3)

    /**
     * Muat cache dari file. Panggil sekali saat aplikasi dibuka, sebelum layar pertama tampil,
     * supaya [cached] sudah terisi sejak komposisi pertama.
     */
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "artist_names.json")
        file = f
        runCatching {
            val obj = JSONObject(f.readText())
            obj.keys().forEach { url ->
                val arr = obj.getJSONArray(url)
                val list = List(arr.length()) { arr.getString(it) }
                if (list.isNotEmpty()) {
                    store[url] = list
                    cache[url] = list
                }
            }
        }
    }

    private fun persist() {
        val f = file ?: return
        ioScope.launch {
            saveMutex.withLock {
                runCatching {
                    val obj = JSONObject()
                    store.forEach { (url, names) -> obj.put(url, JSONArray(names)) }
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    tmp.writeText(obj.toString())
                    tmp.renameTo(f)
                }
            }
        }
    }

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

    /**
     * Cari nama lengkap banyak lagu sekaligus, menunggu paling lama [timeoutMs]. Dipanggil sebelum
     * daftar lagu ditampilkan, supaya nama kolaborasi sudah ada di cache begitu baris pertama
     * tampil. Yang belum selesai saat waktu habis tetap dilengkapi lewat [artistLabel] seperti biasa.
     */
    suspend fun prefetch(songs: List<Song>, timeoutMs: Long) {
        val todo = songs.filter { !it.isAlbumEntry() && cache[it.url] == null }
        if (todo.isEmpty()) return
        withTimeoutOrNull(timeoutMs) {
            coroutineScope { todo.map { async { runCatching { resolveNames(it) } } }.awaitAll() }
        }
    }

    /** Seperti [resolve], tapi berupa daftar artis satu per satu. */
    suspend fun resolveNames(song: Song): List<String> {
        cache[song.url]?.let { return it }
        if (song.isAlbumEntry() || !needsLookup(song.artist)) return listOf(song.artist)

        return gate.withPermit {
            cache[song.url]?.let { return@withPermit it }
            val result = runCatching { lookup(song) }
            if (result.isFailure) return@withPermit listOf(song.artist)
            val found = result.getOrNull()
            val full = found ?: listOf(song.artist)
            cache[song.url] = full
            if (found != null && store.size < MAX_STORED) {
                store[song.url] = found
                persist()
            }
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