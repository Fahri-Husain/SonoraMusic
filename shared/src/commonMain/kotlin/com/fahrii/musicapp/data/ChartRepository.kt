package com.fahrii.musicapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Chart & rilis baru dari API publik Deezer (tanpa API key).
 * Deezer cuma kasih metadata, jadi lagunya dibuat dengan URL "sonora://search?q=..."
 * yang nanti dicari di YouTube Music oleh pemutar saat mau diputar.
 */
object ChartRepository {

    private const val BASE = "https://api.deezer.com"

    /** Kueri yang dicari di YouTube Music untuk memutar lagu ini (juga dipakai mengambil durasinya). */
    fun searchQuery(title: String, artist: String): String = "$title $artist".trim()

    fun searchUrl(title: String, artist: String): String =
        "sonora://search?q=" + uriEncode(searchQuery(title, artist))

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
            getData("$BASE/genre").associate { it.jStr("name").lowercase() to it.jInt("id") }
        }.getOrNull()?.also { genreIds = it }
        map?.get(name.lowercase()) ?: fallback
    }

    suspend fun newReleases(limit: Int = 50, maxAgeDays: Long = 45): List<Song> =
        withContext(Dispatchers.IO) {
            val today = java.time.LocalDate.now()
            val from = today.minusDays(maxAgeDays).toString()
            val to = today.toString()

            val artists = getData("$BASE/chart/0/artists?limit=25")
            val albums = coroutineScope {
                artists.map { artist ->
                    async {
                        runCatching {
                            getData("$BASE/artist/${artist.jLong("id")}/albums?limit=10")
                        }.getOrDefault(emptyList()).map { album ->
                            // Respons album tidak memuat nama artis; ditambahkan dari artis chart
                            JsonObject(album + ("artist" to buildJsonObject { put("name", artist.jStr("name")) }))
                        }
                    }
                }.awaitAll().flatten()
            }

            albums
                .filter { it.jStr("release_date") in from..to }
                .distinctBy { it.jLong("id") }
                .sortedByDescending { it.jStr("release_date") }
                .take(limit)
                .mapNotNull { it.toAlbumSong() }
        }

    suspend fun topArtists(limit: Int = 10): List<ArtistStat> = withContext(Dispatchers.IO) {
        getData("$BASE/chart/0/artists?limit=$limit").mapNotNull { o ->
            val name = o.jStr("name")
            if (name.isBlank()) {
                null
            } else {
                val picture = o.jStr("picture_big").ifBlank { o.jStr("picture_medium") }
                ArtistStat(name = name, plays = 0, imageUrl = picture.ifBlank { null })
            }
        }
    }

    data class ArtistRef(val id: Long, val name: String)

    suspend fun findArtist(name: String): ArtistRef? = withContext(Dispatchers.IO) {
        val wanted = name.trim().removeSuffix("- Topic").trim()
        if (wanted.isBlank()) return@withContext null
        getData("$BASE/search/artist?q=" + uriEncode(wanted) + "&limit=5")
            .firstOrNull { it.jStr("name").trim().equals(wanted, ignoreCase = true) }
            ?.let { ArtistRef(it.jLong("id"), it.jStr("name")) }
    }

    suspend fun relatedArtists(artistId: Long, limit: Int = 5): List<ArtistRef> = withContext(Dispatchers.IO) {
        getData("$BASE/artist/$artistId/related?limit=$limit").mapNotNull { o ->
            val id = o.jLong("id")
            val name = o.jStr("name")
            if (id > 0 && name.isNotBlank()) ArtistRef(id, name) else null
        }
    }

    suspend fun artistTopTracks(artistId: Long, limit: Int = 15): List<Song> = withContext(Dispatchers.IO) {
        getData("$BASE/artist/$artistId/top?limit=$limit").mapNotNull { it.toTrackSong() }
    }

    private fun getData(url: String): List<JsonObject> {
        val root = parseJsonObject(httpGetString(url, connectTimeoutMs = 15_000, readTimeoutMs = 20_000))
        if (root.containsKey("error")) {
            throw IOException(root.jObj("error")?.jStr("message")?.ifBlank { null } ?: "Deezer error")
        }
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.map { it.jsonObject }
    }

    private fun JsonObject.toTrackSong(): Song? {
        val title = jStr("title")
        if (title.isBlank()) return null
        val artist = jObj("artist")?.jStr("name").orEmpty()
        val album = jObj("album")
        val cover = album?.jStr("cover_xl").orEmpty()
            .ifBlank { album?.jStr("cover_big").orEmpty() }
            .ifBlank { album?.jStr("cover_medium").orEmpty() }
        return Song(
            url = searchUrl(title, artist),
            title = title,
            artist = artist.ifBlank { "Unknown artist" },
            thumbnailUrl = cover.ifBlank { null },
            durationSeconds = jLong("duration", -1L)
        )
    }

    /** URL entri album: dibuka sebagai halaman album, bukan diputar. */
    private fun albumEntryUrl(title: String, artist: String): String =
        "$ALBUM_ENTRY_PREFIX?title=" + uriEncode(title) + "&artist=" + uriEncode(artist)

    private fun JsonObject.toAlbumSong(): Song? {
        val title = jStr("title")
        if (title.isBlank()) return null
        val artist = jObj("artist")?.jStr("name").orEmpty()
        val cover = jStr("cover_xl")
            .ifBlank { jStr("cover_big") }
            .ifBlank { jStr("cover_medium") }
        val type = jStr("record_type").lowercase()
        val isAlbum = type.isNotBlank() && type != "single"
        return Song(
            url = if (isAlbum) albumEntryUrl(title, artist) else searchUrl(title, artist),
            title = title,
            artist = artist.ifBlank { "Unknown artist" },
            thumbnailUrl = cover.ifBlank { null },
            durationSeconds = -1L
        )
    }
}