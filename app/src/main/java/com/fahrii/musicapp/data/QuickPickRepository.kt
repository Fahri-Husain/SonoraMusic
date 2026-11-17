package com.fahrii.musicapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Dari mana lagu satu kartu Pilihan Cepat dicari. */
private data class PickSource(
    /** Nama genre di chart Deezer; null = pakai pencarian YouTube Music saja. */
    val genreName: String? = null,
    val genreFallbackId: Int = 0,
    val queries: List<String>
)

/**
 * Isi Pilihan Cepat yang diganti tiap seminggu dan disimpan di HP.
 *
 * - Kartu bergenre yang ada di Deezer (Pop, Jazz) diambil dari chart genre itu (yang lagi tren).
 * - Kartu lain (K-Pop, Lo-Fi, Pop Punk) dicari di YouTube Music dengan kata kunci bertahun berjalan.
 * - Dari kumpulan kandidat, sebagian teratas selalu masuk, sisanya diacak dengan seed per minggu,
 *   jadi isinya ikut berubah tiap minggu walau chart-nya tidak banyak bergeser.
 * - Mix dirangkai dari kartu-kartu lain.
 */
class QuickPickRepository private constructor(context: Context) {

    data class Entry(val savedAtMs: Long, val songs: List<Song>) {
        fun isFresh(nowMs: Long): Boolean = songs.isNotEmpty() && nowMs - savedAtMs < WEEK_MS
    }

    private val file = File(context.filesDir, "quickpicks.json")
    private val saveMutex = Mutex()
    private val entries = ConcurrentHashMap<String, Entry>().apply { putAll(load()) }

    fun entry(label: String): Entry? = entries[label]

    fun songsOf(label: String): List<Song> = entries[label]?.songs.orEmpty()

    suspend fun save(label: String, songs: List<Song>, nowMs: Long) {
        entries[label] = Entry(nowMs, songs)
        withContext(Dispatchers.IO) {
            saveMutex.withLock {
                runCatching {
                    val root = JSONObject()
                    for ((key, e) in entries) {
                        root.put(key, JSONObject().apply {
                            put("savedAt", e.savedAtMs)
                            put("songs", JSONArray().also { arr -> e.songs.forEach { arr.put(it.toJson()) } })
                        })
                    }
                    file.writeText(root.toString())
                }
            }
        }
    }

    /** Ambil lagu baru untuk satu kartu (bukan Mix). Kosong kalau semua sumbernya gagal. */
    suspend fun fetchFresh(label: String, nowMs: Long): List<Song> {
        val source = sources(LocalDate.now().year)[label] ?: return emptyList()
        val seed = seedFor(label, nowMs)

        val pool = LinkedHashMap<String, Song>()
        fun add(songs: List<Song>) = songs.forEach { pool.putIfAbsent(keyOf(it), it) }

        var fromChart = false
        if (source.genreName != null) {
            val id = ChartRepository.genreId(source.genreName, source.genreFallbackId)
            val chart = runCatching { ChartRepository.genreTracks(id, CANDIDATES) }.getOrNull().orEmpty()
            add(chart)
            fromChart = chart.size >= PICK_SIZE
        }
        if (pool.size < PICK_SIZE + 5) {
            // Satu per satu supaya tidak membanjiri YouTube dengan banyak request sekaligus
            for (query in source.queries) {
                add(runCatching { MusicRepository.search(query) }.getOrNull().orEmpty())
            }
        }

        val all = pool.values.toList()
        // Chart = urutan tren yang sebenarnya, jadi bagian teratas lebih banyak dipertahankan
        val core = all.take(if (fromChart) 12 else 6)
        val rest = all.drop(core.size).shuffled(Random(seed)).take(PICK_SIZE - core.size)
        return core + rest
    }

    /** Mix: beberapa lagu dari tiap kartu lain, diacak per minggu. */
    suspend fun buildMix(others: List<List<Song>>, nowMs: Long): List<Song> {
        val seed = seedFor(MIX_LABEL, nowMs)
        val mixed = others
            .flatMapIndexed { i, songs -> songs.shuffled(Random(seed + i)).take(MIX_PER_PICK) }
            .distinctBy { keyOf(it) }
            .shuffled(Random(seed))
            .take(PICK_SIZE)
        if (mixed.size >= MIN_SONGS) return mixed

        val trending = runCatching { ChartRepository.trendingTracks(PICK_SIZE) }.getOrNull().orEmpty()
        return if (trending.isNotEmpty()) trending else mixed
    }

    private fun load(): Map<String, Entry> = runCatching {
        if (!file.exists()) return@runCatching emptyMap<String, Entry>()
        val root = JSONObject(file.readText())
        buildMap<String, Entry> {
            for (label in root.keys()) {
                val o = root.getJSONObject(label)
                val arr = o.getJSONArray("songs")
                val songs = List(arr.length()) { arr.getJSONObject(it).toSong() }
                put(label, Entry(o.optLong("savedAt"), songs))
            }
        }
    }.getOrDefault(emptyMap<String, Entry>())

    private fun Song.toJson() = JSONObject().apply {
        put("url", url)
        put("title", title)
        put("artist", artist)
        put("thumb", thumbnailUrl ?: JSONObject.NULL)
        put("dur", durationSeconds)
    }

    private fun JSONObject.toSong() = Song(
        url = getString("url"),
        title = getString("title"),
        artist = getString("artist"),
        thumbnailUrl = if (isNull("thumb")) null else optString("thumb"),
        durationSeconds = optLong("dur", -1L)
    )

    private fun keyOf(song: Song) = "${song.title}|${song.artist}".lowercase()

    /** Sama selama satu jendela seminggu, berganti saat jendela berikutnya. */
    private fun seedFor(label: String, nowMs: Long): Long =
        (nowMs / WEEK_MS) * 31 + label.hashCode()

    private fun sources(year: Int): Map<String, PickSource> = mapOf(
        "K-Pop" to PickSource(queries = listOf("K-Pop new songs $year", "K-Pop comeback $year", "K-Pop hits")),
        "Lo-Fi" to PickSource(queries = listOf("lofi chill", "lofi beats study", "lofi jazz")),
        "Pop Punk" to PickSource(queries = listOf("pop punk new $year", "pop punk hits")),
        "Pop" to PickSource("Pop", 132, listOf("pop hits $year")),
        "Jazz" to PickSource("Jazz", 129, listOf("jazz"))
    )

    companion object {
        const val MIX_LABEL = "Mix"
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        /** Jumlah lagu per kartu. */
        const val PICK_SIZE = 20

        /** Di bawah ini dianggap gagal sebagian: ditampilkan kalau kartu masih kosong, tapi tidak disimpan. */
        const val MIN_SONGS = 10

        private const val CANDIDATES = 50
        private const val MIX_PER_PICK = 4

        @Volatile
        private var instance: QuickPickRepository? = null

        fun getInstance(context: Context): QuickPickRepository =
            instance ?: synchronized(this) {
                instance ?: QuickPickRepository(context.applicationContext).also { instance = it }
            }
    }
}
