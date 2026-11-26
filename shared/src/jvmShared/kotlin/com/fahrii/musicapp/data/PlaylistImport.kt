package com.fahrii.musicapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Hasil impor: nama playlist, lagu-lagunya, dan catatan untuk pengguna (mis. "3 lagu tidak ditemukan"). */
data class ImportedPlaylist(
    val name: String,
    val songs: List<Song>,
    val note: String? = null
)

/** Kegagalan impor yang pesannya aman ditampilkan langsung ke pengguna. */
class ImportException(message: String) : Exception(message)

/**
 * Impor playlist dari link:
 * - YouTube / YouTube Music: playlist dibaca lewat NewPipeExtractor, lagunya sudah berupa video YouTube.
 * - Spotify: Spotify Web API sekarang butuh login pengguna, jadi daftar lagu dibaca dari halaman embed
 *   publik (open.spotify.com/embed/playlist/ID). Lagu Spotify hanya metadata (judul + artis), maka tiap
 *   lagu dicocokkan ke hasil pertama pencarian YouTube Music, sama seperti saat lagu itu diputar di Beranda.
 *
 * Hanya playlist publik yang bisa diimpor.
 */
object PlaylistImporter {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    /** Batas lagu YouTube yang diimpor sekaligus. */
    private const val MAX_YOUTUBE_SONGS = 500

    /** Jumlah pencarian YouTube Music yang berjalan bersamaan saat mencocokkan lagu Spotify. */
    private const val MATCH_PARALLEL = 3

    /** Embed Spotify hanya memuat sekitar 100 lagu pertama. */
    private const val SPOTIFY_EMBED_LIMIT = 100

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val spotifyIdRegex = Regex("^[A-Za-z0-9]{10,32}$")

    /**
     * Impor playlist dari [input] (link, boleh dengan teks lain di sekitarnya, mis. hasil salin dari
     * "Bagikan"). [onProgress] dipanggil dengan (selesai, total) saat mencocokkan lagu Spotify.
     * Melempar [ImportException] untuk masalah yang bisa dijelaskan ke pengguna.
     */
    suspend fun importFromLink(input: String, onProgress: (done: Int, total: Int) -> Unit): ImportedPlaylist {
        val link = extractLink(input)
            ?: throw ImportException("Tempel link playlist YouTube Music atau Spotify.")

        if (link.startsWith("spotify:", ignoreCase = true)) return importSpotify(link, onProgress)

        val host = hostOf(link)
        return when {
            host.endsWith("spotify.com") || host == "spotify.link" || host.endsWith("spotify.app.link") ->
                importSpotify(link, onProgress)

            host.endsWith("youtube.com") || host == "youtu.be" -> importYouTube(link)

            else -> throw ImportException("Link tidak dikenali. Gunakan link playlist YouTube Music atau Spotify.")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Pembantu URL (pengganti android.net.Uri)

    private fun hostOf(link: String): String =
        runCatching { URI(link).host }.getOrNull()?.lowercase().orEmpty()

    private fun pathSegments(link: String): List<String> =
        runCatching { URI(link).path }.getOrNull().orEmpty().split('/').filter { it.isNotEmpty() }

    private fun queryParam(link: String, name: String): String? {
        val query = link.substringAfter('?', "").substringBefore('#')
        for (pair in query.split('&')) {
            if (pair.substringBefore('=') == name) {
                val value = pair.substringAfter('=', "")
                return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------------------
    // YouTube / YouTube Music

    private suspend fun importYouTube(link: String): ImportedPlaylist {
        val listId = queryParam(link, "list")
            ?: throw ImportException("Link ini bukan playlist. Buka playlist-nya lalu salin linknya.")

        when {
            listId.startsWith("RD") ->
                throw ImportException("Mix/radio YouTube dibuat otomatis dan tidak bisa diimpor.")

            listId == "LM" ->
                throw ImportException("Playlist \"Liked Music\" bersifat pribadi dan tidak bisa diimpor.")
        }

        val playlist = try {
            MusicRepository.loadYouTubePlaylist(listId, MAX_YOUTUBE_SONGS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ImportException("Tidak bisa terhubung ke internet. Periksa koneksi lalu coba lagi.")
        } catch (e: Exception) {
            throw ImportException("Playlist tidak ditemukan atau bersifat pribadi.")
        }

        if (playlist.songs.isEmpty()) throw ImportException("Playlist ini kosong.")
        return playlist
    }

    // ---------------------------------------------------------------------------------------
    // Spotify

    private class SpotifyTrack(val title: String, val artist: String, val durationSeconds: Long)

    private suspend fun importSpotify(link: String, onProgress: (Int, Int) -> Unit): ImportedPlaylist {
        val (name, tracks) = withContext(Dispatchers.IO) {
            val id = resolveSpotifyPlaylistId(link)
                ?: throw ImportException("Link Spotify ini bukan playlist.")
            fetchSpotifyPlaylist(id)
        }
        if (tracks.isEmpty()) throw ImportException("Playlist ini kosong atau tidak bisa dibaca.")

        val total = tracks.size
        val done = AtomicInteger(0)
        onProgress(0, total)

        val gate = Semaphore(MATCH_PARALLEL)
        val matched: List<Song?> = coroutineScope {
            tracks.map { track ->
                async {
                    gate.withPermit {
                        val song = matchOnYouTubeMusic(track)
                        onProgress(done.incrementAndGet(), total)
                        song
                    }
                }
            }.awaitAll()
        }

        val songs = matched.filterNotNull().distinctBy { it.url }
        if (songs.isEmpty()) {
            throw ImportException("Tidak ada lagu dari playlist ini yang ditemukan di YouTube Music.")
        }

        val notes = buildList {
            val missing = total - matched.count { it != null }
            if (missing > 0) add("$missing lagu tidak ditemukan di YouTube Music.")
            if (total >= SPOTIFY_EMBED_LIMIT) add("Spotify hanya membagikan sekitar 100 lagu pertama, sisanya mungkin tidak terimpor.")
        }
        return ImportedPlaylist(name, songs, notes.joinToString(" ").ifEmpty { null })
    }

    /**
     * Cari [track] di YouTube Music. Judul dan artis tetap dari Spotify (sesuai playlist aslinya),
     * sedangkan URL, cover, dan durasi diambil dari hasil pencarian.
     * Null = memang tidak ada hasil. Kalau pencarian gagal karena jaringan/pembatasan, lagu tetap
     * disimpan dengan URL "sonora://search" supaya dicari lagi saat diputar.
     */
    private suspend fun matchOnYouTubeMusic(track: SpotifyTrack): Song? {
        val query = ChartRepository.searchQuery(track.title, track.artist)
        val hit = try {
            MusicRepository.search(query).firstOrNull() ?: return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Song(
                url = ChartRepository.searchUrl(track.title, track.artist),
                title = track.title,
                artist = track.artist,
                thumbnailUrl = null,
                durationSeconds = track.durationSeconds
            )
        }
        return Song(
            url = hit.url,
            title = track.title,
            artist = track.artist.ifBlank { hit.artist },
            thumbnailUrl = hit.thumbnailUrl,
            durationSeconds = hit.durationSeconds.takeIf { it > 0 } ?: track.durationSeconds
        )
    }

    /** ID playlist dari link open.spotify.com, URI "spotify:playlist:ID", atau link pendek spotify.link. */
    private fun resolveSpotifyPlaylistId(link: String): String? {
        if (link.startsWith("spotify:", ignoreCase = true)) {
            // Bentuk "spotify:playlist:ID"
            val parts = link.split(':')
            val id = parts.getOrNull(2)
            return if (parts.getOrNull(1).equals("playlist", ignoreCase = true) &&
                id != null && spotifyIdRegex.matches(id)
            ) id else null
        }

        idFromSpotifyUrl(link)?.let { return it }

        // Link pendek (spotify.link / spotify.app.link): ikuti pengalihan sampai ketemu link playlist-nya
        val host = hostOf(link)
        if (host == "spotify.link" || host.endsWith("spotify.app.link")) {
            val request = Request.Builder().url(link).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                idFromSpotifyUrl(response.request.url.toString())?.let { return it }
                val body = response.body?.string().orEmpty()
                Regex("open\\.spotify\\.com/(?:intl-[a-z-]+/)?playlist/([A-Za-z0-9]{10,32})")
                    .find(body)?.groupValues?.get(1)?.let { return it }
            }
        }
        return null
    }

    private fun idFromSpotifyUrl(url: String): String? {
        // Cocok untuk /playlist/ID dan /intl-id/playlist/ID
        val segments = pathSegments(url)
        val index = segments.indexOf("playlist")
        return segments.getOrNull(index + 1)?.takeIf { index >= 0 && spotifyIdRegex.matches(it) }
    }

    private fun fetchSpotifyPlaylist(id: String): Pair<String, List<SpotifyTrack>> {
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/playlist/$id")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.8")
            .build()

        val html = try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    throw ImportException("Playlist tidak ditemukan atau bersifat pribadi.")
                }
                if (!response.isSuccessful) {
                    throw ImportException("Spotify tidak bisa dibuka saat ini (HTTP ${response.code}).")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: IOException) {
            throw ImportException("Tidak bisa terhubung ke internet. Periksa koneksi lalu coba lagi.")
        }

        // Data playlist ada di <script id="__NEXT_DATA__" type="application/json">{...}</script>
        val json = Regex("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: throw spotifyFormatChanged()

        val entity = try {
            findObjectWithKey(StoreJson.parseToJsonElement(json), "trackList")
        } catch (e: Exception) {
            null
        } ?: throw spotifyFormatChanged()

        val list = entity["trackList"] as? JsonArray ?: throw spotifyFormatChanged()
        val tracks = list
            .mapNotNull { it as? JsonObject }
            .mapNotNull { t ->
                val title = t.jStr("title").trim()
                if (title.isEmpty()) return@mapNotNull null
                SpotifyTrack(
                    title = title,
                    artist = t.jStr("subtitle").replace('\u00a0', ' ').trim(),
                    // "duration" dalam milidetik
                    durationSeconds = t.jLong("duration", -1L).let { if (it > 0) it / 1000 else -1L }
                )
            }

        val name = entity.jStr("name").ifBlank { entity.jStr("title") }
            .ifBlank { "Playlist Spotify" }
        return name to tracks
    }

    private fun spotifyFormatChanged() = ImportException(
        "Spotify tidak memberikan daftar lagu untuk link ini. Pastikan playlist-nya publik. " +
                "Kalau sudah publik, format halaman Spotify mungkin berubah."
    )

    /** Cari objek JSON pertama (di kedalaman berapa pun) yang punya kunci [key]. */
    private fun findObjectWithKey(node: JsonElement?, key: String): JsonObject? {
        when (node) {
            is JsonObject -> {
                if (node.containsKey(key)) return node
                for (child in node.values) findObjectWithKey(child, key)?.let { return it }
            }

            is JsonArray -> for (child in node) findObjectWithKey(child, key)?.let { return it }

            else -> Unit
        }
        return null
    }

    // ---------------------------------------------------------------------------------------

    /** Ambil link dari teks yang ditempel (boleh ada kata lain di sekitarnya, atau tanpa "https://"). */
    private fun extractLink(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("spotify:", ignoreCase = true)) return text.split(Regex("\\s+")).first()

        Regex("https?://\\S+").find(text)?.let { return it.value.trimEnd(')', '.', ',', ';') }

        // Tanpa skema, mis. "music.youtube.com/playlist?list=..."
        Regex("(?:[a-z0-9-]+\\.)*(?:youtube\\.com|youtu\\.be|spotify\\.com|spotify\\.link)/\\S+", RegexOption.IGNORE_CASE)
            .find(text)?.let { return "https://" + it.value.trimEnd(')', '.', ',', ';') }

        return null
    }
}