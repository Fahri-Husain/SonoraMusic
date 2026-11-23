package com.fahrii.musicapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class ForYouArtist(
    val ref: ChartRepository.ArtistRef?,
    val tracks: List<Song>,
    val related: List<ChartRepository.ArtistRef>
)

/** Dari mana lagu satu kartu Pilihan Cepat dicari. */
private data class PickSource(
    /** Nama genre di chart Deezer; null = pakai pencarian YouTube Music saja. */
    val genreName: String? = null,
    val genreFallbackId: Int = 0,
    val queries: List<String>
)

/**
 * Isi Pilihan Cepat yang diambil seminggu sekali dan disimpan di HP.
 *
 * - Kartu bergenre yang ada di Deezer (Pop, Jazz) diambil dari chart genre itu (yang lagi tren).
 * - Kartu lain (K-Pop, Lo-Fi, Pop Punk) dicari di YouTube Music dengan kata kunci bertahun berjalan.
 * - Dari kumpulan kandidat, sebagian teratas selalu masuk, sisanya diacak dengan seed per minggu,
 *   jadi isinya ikut berubah tiap minggu walau chart-nya tidak banyak bergeser.
 * - Seluruh kandidat (pool) ikut disimpan, jadi tarik-untuk-refresh bisa mengganti bagian "sisa"
 *   tanpa internet ([reshuffle]); lagu teratas chart tetap.
 * - Mix dirangkai dari kartu-kartu lain dan dirangkai ulang tiap tarik-untuk-refresh.
 */
class QuickPickRepository private constructor(context: Context) {

    /**
     * @param songs lagu yang tampil di kartu.
     * @param pool seluruh kandidat, urut dari yang paling tren; bahan untuk mengacak ulang tanpa internet.
     * @param coreSize jumlah lagu teratas dari [pool] yang selalu tampil. [NO_POOL] = data lama tanpa pool.
     */
    data class Entry(
        val savedAtMs: Long,
        val songs: List<Song>,
        val pool: List<Song> = songs,
        val coreSize: Int = NO_POOL
    ) {
        fun isFresh(nowMs: Long): Boolean = songs.isNotEmpty() && nowMs - savedAtMs < WEEK_MS
        fun hasPool(): Boolean = coreSize >= 0
    }

    /** Hasil pengambilan satu kartu: yang tampil, seluruh kandidatnya, dan berapa teratas yang tetap. */
    data class Fetched(val songs: List<Song>, val pool: List<Song>, val coreSize: Int) {
        companion object {
            val EMPTY = Fetched(emptyList(), emptyList(), 0)
        }
    }

    private val file = File(context.filesDir, "quickpicks.json")
    private val saveMutex = Mutex()
    private val entries = ConcurrentHashMap<String, Entry>().apply { putAll(load()) }

    fun entry(label: String): Entry? = entries[label]

    fun songsOf(label: String): List<Song> = entries[label]?.songs.orEmpty()

    /** Seluruh kandidat satu kartu (untuk data lama tanpa pool, sama dengan yang tampil). */
    fun poolOf(label: String): List<Song> = entries[label]?.pool.orEmpty()

    suspend fun save(
        label: String,
        songs: List<Song>,
        nowMs: Long,
        pool: List<Song> = songs,
        coreSize: Int = NO_POOL
    ) {
        entries[label] = Entry(nowMs, songs, pool, coreSize)
        persist()
    }

    /**
     * Ganti bagian "sisa" satu kartu dari kandidat yang sudah tersimpan, tanpa internet.
     * Lagu teratas chart ([Entry.coreSize]) tetap; sisanya diutamakan lagu yang belum tampil
     * supaya perubahannya terlihat. Waktu simpan tidak diubah, jadi jadwal ambil ulang mingguan
     * tetap jalan. Mengembalikan null kalau kartu belum punya pool (data lama).
     */
    suspend fun reshuffle(label: String, nowMs: Long): List<Song>? {
        val e = entries[label] ?: return null
        if (!e.hasPool()) return null

        val core = e.pool.take(e.coreSize)
        val shown = e.songs.map { keyOf(it) }.toSet()
        val (seen, unseen) = e.pool.drop(core.size).partition { keyOf(it) in shown }
        val rng = Random(nowMs + label.hashCode())
        val rest = (unseen.shuffled(rng) + seen.shuffled(rng)).take((PICK_SIZE - core.size).coerceAtLeast(0))

        val songs = core + rest
        entries[label] = e.copy(songs = songs)
        persist()
        return songs
    }

    private suspend fun persist() {
        withContext(Dispatchers.IO) {
            saveMutex.withLock {
                runCatching {
                    val root = JSONObject()
                    for ((key, e) in entries) {
                        root.put(key, JSONObject().apply {
                            put("savedAt", e.savedAtMs)
                            put("songs", e.songs.toJsonArray())
                            if (e.hasPool()) {
                                put("pool", e.pool.toJsonArray())
                                put("core", e.coreSize)
                            }
                        })
                    }
                    file.writeText(root.toString())
                }
            }
        }
    }

    /** Ambil lagu baru untuk satu kartu (bukan Mix). Kosong kalau semua sumbernya gagal. */
    suspend fun fetchFresh(label: String, nowMs: Long): Fetched {
        val source = sources(LocalDate.now().year)[label] ?: return Fetched.EMPTY
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

            for (query in source.queries) {
                add(runCatching { MusicRepository.search(query) }.getOrNull().orEmpty())
            }
        }

        val all = pool.values.toList()

        val coreSize = minOf(all.size, if (fromChart) 12 else 6)
        val core = all.take(coreSize)
        val rest = all.drop(coreSize).shuffled(Random(seed)).take(PICK_SIZE - coreSize)
        return Fetched(core + rest, all, coreSize)
    }

    /**
     * Isi baris Untukmu: lagu populer artis favorit pengguna, ditambah lagu dari artis yang mirip.
     * Lagu yang lolos [isExcluded] (mis. yang sudah sering diputar) dibuang. Lagu teratas dari artis
     * favorit selalu tampil, sisanya diacak per minggu dari seluruh kandidat.
     */
    suspend fun fetchForYou(
        artistNames: List<String>,
        isExcluded: (Song) -> Boolean,
        nowMs: Long
    ): Fetched {
        val seed = seedFor(FOR_YOU_LABEL, nowMs)

        val favorites = coroutineScope {
            artistNames.map { name ->
                async {
                    val ref = runCatching { ChartRepository.findArtist(name) }.getOrNull()
                    if (ref == null) {
                        ForYouArtist(null, emptyList(), emptyList())
                    } else {
                        ForYouArtist(
                            ref,
                            runCatching { ChartRepository.artistTopTracks(ref.id, FOR_YOU_PER_ARTIST) }
                                .getOrNull().orEmpty(),
                            runCatching { ChartRepository.relatedArtists(ref.id, 4) }
                                .getOrNull().orEmpty()
                        )
                    }
                }
            }.awaitAll()
        }

        val knownIds = favorites.mapNotNull { it.ref?.id }.toSet()
        val relatedRefs = favorites.take(3)
            .flatMap { it.related.take(2) }
            .filter { it.id !in knownIds }
            .distinctBy { it.id }

        val relatedTracks = coroutineScope {
            relatedRefs.map { ref ->
                async {
                    runCatching { ChartRepository.artistTopTracks(ref.id, FOR_YOU_RELATED_PER_ARTIST) }
                        .getOrNull().orEmpty()
                }
            }.awaitAll()
        }

        val seen = HashSet<String>()
        fun accept(song: Song): Boolean = !isExcluded(song) && seen.add(keyOf(song))

        val favoritePool = interleave(favorites.map { it.tracks }).filter { accept(it) }
        val relatedPool = interleave(relatedTracks).filter { accept(it) }
        val all = favoritePool + relatedPool

        val coreSize = minOf(favoritePool.size, FOR_YOU_CORE)
        val core = all.take(coreSize)
        val rest = all.drop(coreSize).shuffled(Random(seed)).take(PICK_SIZE - coreSize)
        return Fetched(core + rest, all, coreSize)
    }

    private fun interleave(lists: List<List<Song>>): List<Song> {
        val result = ArrayList<Song>()
        var index = 0
        while (lists.any { index < it.size }) {
            lists.forEach { list -> if (index < list.size) result.add(list[index]) }
            index++
        }
        return result
    }

    /**
     * Mix: beberapa lagu dari tiap kartu lain, diacak per minggu. [salt] diisi (mis. waktu sekarang)
     * kalau Mix mau dirangkai ulang dengan hasil berbeda, misalnya saat tarik-untuk-refresh.
     */
    suspend fun buildMix(others: List<List<Song>>, nowMs: Long, salt: Long = 0L): List<Song> {
        val seed = seedFor(MIX_LABEL, nowMs) + salt
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
                val savedAt = o.optLong("savedAt")
                val songs = o.getJSONArray("songs").toSongs()
                val pool = o.optJSONArray("pool")?.toSongs()
                val core = o.optInt("core", NO_POOL)
                put(
                    label,
                    if (pool != null && core >= 0) Entry(savedAt, songs, pool, core)
                    else Entry(savedAt, songs)
                )
            }
        }
    }.getOrDefault(emptyMap<String, Entry>())

    private fun JSONArray.toSongs(): List<Song> = List(length()) { getJSONObject(it).toSong() }

    private fun List<Song>.toJsonArray(): JSONArray =
        JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }

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
        const val FOR_YOU_LABEL = "Untukmu"

        /** Penanda data lama yang belum menyimpan pool kandidat. */
        const val NO_POOL = -1
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        /** Jumlah lagu per kartu. */
        const val PICK_SIZE = 20

        /** Di bawah ini dianggap gagal sebagian: ditampilkan kalau kartu masih kosong, tapi tidak disimpan. */
        const val MIN_SONGS = 10

        private const val CANDIDATES = 50
        private const val MIX_PER_PICK = 4
        private const val FOR_YOU_PER_ARTIST = 15
        private const val FOR_YOU_RELATED_PER_ARTIST = 6
        private const val FOR_YOU_CORE = 6

        @Volatile
        private var instance: QuickPickRepository? = null

        fun getInstance(context: Context): QuickPickRepository =
            instance ?: synchronized(this) {
                instance ?: QuickPickRepository(context.applicationContext).also { instance = it }
            }
    }
}