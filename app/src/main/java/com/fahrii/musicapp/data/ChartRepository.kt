package com.fahrii.musicapp.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Chart & rilis baru dari API publik Deezer (tanpa API key).
 * Deezer cuma kasih metadata, jadi lagunya dibuat dengan URL "sonora://search?q=..."
 * yang nanti dicari di YouTube Music oleh PlaybackService saat mau diputar.
 */
object ChartRepository {

    private const val BASE = "https://api.deezer.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun searchUrl(title: String, artist: String): String =
        "sonora://search?q=" + Uri.encode("$title $artist".trim())

    suspend fun trendingTracks(limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        getData("$BASE/chart/0/tracks?limit=$limit").mapNotNull { it.toTrackSong() }
    }

    /** Chart lagu per genre (Deezer memperbarui chart ini terus; 0 = semua genre). */
    suspend fun genreTracks(genreId: Int, limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        getData("$BASE/chart/$genreId/tracks?limit=$limit").mapNotNull { it.toTrackSong() }
    }

    private var genreIds: Map<String, Int>? = null

    /**
     * ID genre Deezer dari namanya ("Pop", "Jazz", ...). Daftar genre diambil sekali lalu disimpan
     * di memori; kalau gagal diambil, dipakai [fallback].
     */
    suspend fun genreId(name: String, fallback: Int): Int = withContext(Dispatchers.IO) {
        val map = genreIds ?: runCatching {
            getData("$BASE/genre").associate { it.optString("name").lowercase() to it.optInt("id") }
        }.getOrNull()?.also { genreIds = it }
        map?.get(name.lowercase()) ?: fallback
    }

    suspend fun newReleases(limit: Int = 50, maxAgeDays: Long = 45): List<Song> =
        withContext(Dispatchers.IO) {
            val today = java.time.LocalDate.now()
            val from = today.minusDays(maxAgeDays).toString()   // format yyyy-MM-dd
            val to = today.toString()

            val artists = getData("$BASE/chart/0/artists?limit=25")
            val albums = coroutineScope {
                artists.map { artist ->
                    async {
                        runCatching {
                            getData("$BASE/artist/${artist.optLong("id")}/albums?limit=10")
                        }.getOrDefault(emptyList()).onEach { album ->
                            // endpoint ini nggak menyertakan nama artis, jadi diisi manual
                            album.put("artist", JSONObject().put("name", artist.optString("name")))
                        }
                    }
                }.awaitAll().flatten()
            }

            albums
                .filter { it.optString("release_date") in from..to }
                .distinctBy { it.optLong("id") }
                .sortedByDescending { it.optString("release_date") }
                .take(limit)
                .mapNotNull { it.toAlbumSong() }
        }

    suspend fun topArtists(limit: Int = 10): List<ArtistStat> = withContext(Dispatchers.IO) {
        getData("$BASE/chart/0/artists?limit=$limit").mapNotNull { o ->
            val name = o.optString("name")
            if (name.isBlank()) {
                null
            } else {
                val picture = o.optString("picture_big").ifBlank { o.optString("picture_medium") }
                ArtistStat(name = name, plays = 0, imageUrl = picture.ifBlank { null })
            }
        }
    }

    private fun getData(url: String): List<JSONObject> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SonoraMusic/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val root = JSONObject(response.body?.string().orEmpty())
            // Deezer kadang membalas status 200 tapi isinya {"error": {...}}
            if (root.has("error")) {
                throw IOException(root.optJSONObject("error")?.optString("message") ?: "Deezer error")
            }
            val data = root.optJSONArray("data") ?: return emptyList()
            return List(data.length()) { data.getJSONObject(it) }
        }
    }

    private fun JSONObject.toTrackSong(): Song? {
        val title = optString("title")
        if (title.isBlank()) return null
        val artist = optJSONObject("artist")?.optString("name").orEmpty()
        val album = optJSONObject("album")
        val cover = album?.optString("cover_xl").orEmpty()
            .ifBlank { album?.optString("cover_big").orEmpty() }
            .ifBlank { album?.optString("cover_medium").orEmpty() }
        return Song(
            url = searchUrl(title, artist),
            title = title,
            artist = artist.ifBlank { "Unknown artist" },
            thumbnailUrl = cover.ifBlank { null },
            durationSeconds = optLong("duration", -1L)
        )
    }

    private fun JSONObject.toAlbumSong(): Song? {
        val title = optString("title")
        if (title.isBlank()) return null
        val artist = optJSONObject("artist")?.optString("name").orEmpty()
        val cover = optString("cover_xl")
            .ifBlank { optString("cover_big") }
            .ifBlank { optString("cover_medium") }
        return Song(
            url = searchUrl(title, artist),
            title = title,
            artist = artist.ifBlank { "Unknown artist" },
            thumbnailUrl = cover.ifBlank { null },
            durationSeconds = -1L
        )
    }
}