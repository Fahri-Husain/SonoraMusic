package com.fahrii.musicapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Status unduhan satu lagu, dipakai menu titik tiga untuk menentukan label "Unduh". */
enum class DownloadStatus { None, Downloading, Downloaded }

/** Lagu yang sudah diunduh: metadata + lokasi file audio dan cover di penyimpanan aplikasi. */
data class DownloadedSong(
    val song: Song,
    val audioPath: String,
    val coverPath: String?,
    val sizeBytes: Long,
    val downloadedAt: Long
)

/**
 * Mengunduh audio lagu ke penyimpanan aplikasi supaya bisa diputar tanpa internet.
 *
 * - File audio ada di `filesDir/downloads/`, indeksnya di `downloads.json` (pola sama dengan [LibraryRepository]).
 * - Kunci lagu = [Song.url], sama dengan Favorit dan playlist, jadi lagu yang sama dikenali di mana pun.
 * - [localFile] dipanggil PlaybackService: kalau lagu sudah diunduh, pemutar membuka file lokal, bukan stream.
 */
class DownloadRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "downloads")
    private val indexFile = File(appContext.filesDir, "downloads.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    // Maksimal 2 unduhan berjalan bersamaan; sisanya antre (mis. saat "unduh semua" satu playlist)
    private val slots = Semaphore(2)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloaded = MutableStateFlow(load())

    /** Semua lagu yang sudah diunduh, terbaru di atas. */
    val downloaded: StateFlow<List<DownloadedSong>> = _downloaded.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())

    /** URL lagu yang sedang diunduh atau menunggu giliran. */
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Pesan singkat untuk Toast ("Mengunduh ...", "Gagal mengunduh ..."). */
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        // Sisa unduhan yang terputus (proses aplikasi dimatikan) dibersihkan
        runCatching { dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() } }
    }

    /** File audio lokal untuk [songUrl], atau null kalau lagu belum diunduh. Aman dipanggil dari thread mana pun. */
    fun localFile(songUrl: String): File? {
        val entry = _downloaded.value.firstOrNull { it.song.url == songUrl } ?: return null
        return File(entry.audioPath).takeIf { it.isFile && it.length() > 0 }
    }

    fun statusOf(songUrl: String): DownloadStatus = when {
        _downloaded.value.any { it.song.url == songUrl } -> DownloadStatus.Downloaded
        songUrl in _downloading.value -> DownloadStatus.Downloading
        else -> DownloadStatus.None
    }

    /** Unduh satu lagu. Diabaikan kalau lagu itu sudah diunduh atau sedang berjalan. */
    fun download(song: Song) {
        if (statusOf(song.url) != DownloadStatus.None) return
        if (blockedByWifiOnly()) return
        _downloading.update { it + song.url }
        _events.tryEmit("Mengunduh \"${song.title}\"")

        scope.launch {
            val result = slots.withPermit { runCatching { downloadBlocking(song) } }
            _downloading.update { it - song.url }
            result.onSuccess { entry ->
                _downloaded.update { list -> listOf(entry) + list.filterNot { it.song.url == song.url } }
                persist()
                _events.tryEmit("\"${song.title}\" selesai diunduh")
            }.onFailure {
                _events.tryEmit("Gagal mengunduh \"${song.title}\"")
            }
        }
    }

    /** Unduh banyak lagu sekaligus (mis. satu playlist); yang sudah diunduh dilewati. */
    fun downloadAll(songs: List<Song>) {
        val todo = songs.distinctBy { it.url }.filter { statusOf(it.url) == DownloadStatus.None }
        if (todo.isEmpty()) {
            _events.tryEmit("Semua lagu sudah diunduh")
            return
        }
        if (blockedByWifiOnly()) return
        _events.tryEmit("Mengunduh ${todo.size} lagu")
        todo.forEach { song ->
            _downloading.update { it + song.url }
            scope.launch {
                val result = slots.withPermit { runCatching { downloadBlocking(song) } }
                _downloading.update { it - song.url }
                result.onSuccess { entry ->
                    _downloaded.update { list -> listOf(entry) + list.filterNot { it.song.url == song.url } }
                    persist()
                }.onFailure {
                    _events.tryEmit("Gagal mengunduh \"${song.title}\"")
                }
            }
        }
    }

    /** Hapus semua lagu yang sudah diunduh (file audio, cover, dan indeksnya). */
    fun removeAll() {
        val all = _downloaded.value
        if (all.isEmpty()) return
        _downloaded.value = emptyList()
        all.forEach { entry ->
            runCatching { File(entry.audioPath).delete() }
            entry.coverPath?.let { runCatching { File(it).delete() } }
        }
        persist()
    }

    /**
     * True (dan memberi tahu lewat Toast) kalau unduhan sedang dibatasi Wi-Fi saja tetapi perangkat
     * memakai data seluler. Jaringan yang tidak dikenal dianggap boleh, supaya unduhan tidak macet.
     */
    private fun blockedByWifiOnly(): Boolean {
        if (!SettingsRepository.getInstance(appContext).downloadWifiOnly.value) return false
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        val onMobileOnly = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (onMobileOnly) {
            _events.tryEmit("Unduhan hanya lewat Wi-Fi. Sambungkan Wi-Fi atau matikan pembatasan di Pengaturan")
        }
        return onMobileOnly
    }

    fun remove(songUrl: String) {
        val entry = _downloaded.value.firstOrNull { it.song.url == songUrl } ?: return
        _downloaded.update { list -> list.filterNot { it.song.url == songUrl } }
        runCatching { File(entry.audioPath).delete() }
        entry.coverPath?.let { runCatching { File(it).delete() } }
        persist()
    }

    // ---------------------------------------------------------------------------------------

    private fun downloadBlocking(song: Song): DownloadedSong {
        dir.mkdirs()
        val name = fileKey(song.url)
        val audio = File(dir, "$name.audio")
        val part = File(dir, "$name.audio.part")

        try {
            val streamUrl = MusicRepository.resolvePlayableUrlBlocking(
                song.url,
                SettingsRepository.getInstance(appContext).downloadQuality.value
            )
            fetchToFile(streamUrl, part)
            if (part.length() <= 0L) throw IOException("File kosong")
            if (audio.exists()) audio.delete()
            if (!part.renameTo(audio)) throw IOException("Gagal menyimpan file")
        } catch (e: Exception) {
            part.delete()
            throw e
        }

        // Cover disimpan juga supaya tetap tampil saat offline (gagal = cukup pakai ikon bawaan)
        val cover = song.thumbnailUrl
            ?.takeIf { it.startsWith("http") }
            ?.let { url ->
                runCatching {
                    val target = File(dir, "$name.jpg")
                    fetchToFile(url, target)
                    target.takeIf { it.length() > 0 }
                }.getOrNull()
            }

        return DownloadedSong(
            song = song,
            audioPath = audio.absolutePath,
            coverPath = cover?.absolutePath,
            sizeBytes = audio.length(),
            downloadedAt = System.currentTimeMillis()
        )
    }

    /**
     * Unduh [url] ke [target] per potongan 1 MB (header Range). YouTube sering membatasi
     * kecepatan unduhan satu permintaan penuh, sedangkan potongan kecil jalan normal.
     * Kalau server tidak mendukung Range (balasan 200), seluruh isi dibaca sekali jalan.
     */
    private fun fetchToFile(url: String, target: File) {
        target.delete()
        var start = 0L
        var total = -1L

        target.outputStream().buffered().use { out ->
            while (total < 0 || start < total) {
                val end = start + CHUNK_BYTES - 1
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=$start-$end")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Respons kosong")

                    if (response.code == 200) {
                        // Server mengabaikan Range: ini seluruh file, tulis lalu selesai
                        if (start != 0L) throw IOException("Server tidak mendukung Range")
                        body.byteStream().copyTo(out)
                        return
                    }

                    // 206: total ukuran ada di "Content-Range: bytes 0-1048575/3456789"
                    if (total < 0) {
                        total = response.header("Content-Range")
                            ?.substringAfterLast('/')
                            ?.toLongOrNull()
                            ?: throw IOException("Ukuran file tidak diketahui")
                    }
                    val written = body.byteStream().copyTo(out)
                    if (written <= 0L) throw IOException("Potongan kosong")
                    start += written
                }
            }
        }
    }

    private fun fileKey(songUrl: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(songUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                runCatching {
                    val arr = JSONArray()
                    _downloaded.value.forEach { d ->
                        arr.put(JSONObject().apply {
                            put("url", d.song.url)
                            put("title", d.song.title)
                            put("artist", d.song.artist)
                            put("thumbnailUrl", d.song.thumbnailUrl ?: JSONObject.NULL)
                            put("durationSeconds", d.song.durationSeconds)
                            put("audioPath", d.audioPath)
                            put("coverPath", d.coverPath ?: JSONObject.NULL)
                            put("sizeBytes", d.sizeBytes)
                            put("downloadedAt", d.downloadedAt)
                        })
                    }
                    val tmp = File(indexFile.parentFile, "downloads.json.tmp")
                    tmp.writeText(JSONObject().put("songs", arr).toString())
                    if (!tmp.renameTo(indexFile)) {
                        indexFile.writeText(tmp.readText())
                        tmp.delete()
                    }
                }
            }
        }
    }

    private fun load(): List<DownloadedSong> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONObject(indexFile.readText()).optJSONArray("songs") ?: return emptyList()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DownloadedSong(
                    song = Song(
                        url = o.getString("url"),
                        title = o.getString("title"),
                        artist = o.getString("artist"),
                        thumbnailUrl = if (o.isNull("thumbnailUrl")) null else o.getString("thumbnailUrl"),
                        durationSeconds = o.optLong("durationSeconds", -1L)
                    ),
                    audioPath = o.getString("audioPath"),
                    coverPath = if (o.isNull("coverPath")) null else o.optString("coverPath").ifBlank { null },
                    sizeBytes = o.optLong("sizeBytes", 0L),
                    downloadedAt = o.optLong("downloadedAt", 0L)
                )
            }
                // File yang hilang (mis. dihapus lewat "Hapus data") tidak ditampilkan lagi
                .filter { File(it.audioPath).isFile }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val CHUNK_BYTES = 1L * 1024 * 1024
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

        @Volatile
        private var instance: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * Lagu-lagu yang sudah diunduh untuk layar Diunduh. Cover memakai file lokal kalau ada,
 * supaya gambarnya tetap muncul tanpa internet.
 */
fun List<DownloadedSong>.toDisplaySongs(): List<Song> = map { d ->
    val localCover = d.coverPath?.takeIf { File(it).isFile }?.let { Uri.fromFile(File(it)).toString() }
    if (localCover != null) d.song.copy(thumbnailUrl = localCover) else d.song
}