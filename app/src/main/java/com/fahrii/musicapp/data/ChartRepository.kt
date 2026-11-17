package com.fahrii.musicapp.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    suspend fun trendingTracks(limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        getData("$BASE/chart/0/tracks?limit=$limit").mapNotNull { it.toTrackSong() }
    }

    suspend fun newReleases(limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        getData("$BASE/editorial/0/releases?limit=$limit").mapNotNull { it.toAlbumSong() }
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
        val cover = album?.optString("cover_big").orEmpty()
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
        val cover = optString("cover_big").ifBlank { optString("cover_medium") }
        return Song(
            url = searchUrl(title, artist),
            title = title,
            artist = artist.ifBlank { "Unknown artist" },
            thumbnailUrl = cover.ifBlank { null },
            durationSeconds = -1L
        )
    }
}