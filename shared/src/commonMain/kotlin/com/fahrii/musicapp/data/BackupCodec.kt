package com.fahrii.musicapp.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/** Galat yang pesannya aman ditampilkan langsung ke pengguna. */
class BackupException(message: String) : Exception(message)

/** Isi singkat berkas cadangan yang baru dibuat. */
data class ExportSummary(
    val liked: Int,
    val playlists: Int,
    val historySongs: Int,
    val replayMonths: Int
)

/** Hasil terakhir operasi cadangan, ditampilkan sebagai dialog di Pengaturan. */
sealed interface BackupOutcome {
    data class Exported(val summary: ExportSummary) : BackupOutcome
    data class Imported(val report: ImportReport) : BackupOutcome
    data class Failed(val message: String) : BackupOutcome
}

/** Seluruh isi berkas cadangan dalam bentuk objek. */
internal class BackupData(
    val liked: List<Song>,
    val playlists: List<BackupPlaylist>,
    val quickPlaylistId: String?,
    val historySongs: List<PlayedSong>,
    val historyArtists: List<ArtistStat>,
    val replay: Map<String, MonthStats>
) {
    fun summary() = ExportSummary(
        liked = liked.size,
        playlists = playlists.size,
        historySongs = historySongs.size,
        replayMonths = replay.size
    )
}

/** Bangun dan baca teks JSON berkas cadangan. Murni Kotlin, tanpa Android. */
internal object BackupCodec {
    const val APP_ID = "SonoraMusic"
    const val FORMAT_VERSION = 1
    private val MONTH_KEY = Regex("\\d{4}-\\d{2}")

    // ------------------------------------------------------------ tulis

    fun encode(data: BackupData, appVersion: String?, exportedAt: Long): String {
        val root = buildJsonObject {
            put("app", APP_ID)
            put("format", FORMAT_VERSION)
            put("exportedAt", exportedAt)
            put("appVersion", appVersion)

            putJsonArray("liked") { data.liked.forEach { add(it.toBackupJson()) } }
            put("quickPlaylistId", data.quickPlaylistId)
            putJsonArray("playlists") {
                data.playlists.forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id)
                        put("name", p.name)
                        put("createdAt", p.createdAt)
                        putJsonArray("songs") { p.songs.forEach { add(it.toBackupJson()) } }
                        put("cover", p.coverBytes?.let { Base64.Default.encode(it) })
                    })
                }
            }

            putJsonObject("history") {
                putJsonArray("songs") {
                    data.historySongs.forEach { p ->
                        add(p.song.toBackupJson {
                            put("plays", p.plays)
                            put("lastPlayedAt", p.lastPlayedAt)
                        })
                    }
                }
                putJsonArray("artists") {
                    data.historyArtists.forEach { a ->
                        add(buildJsonObject {
                            put("name", a.name)
                            put("plays", a.plays)
                            put("imageUrl", a.imageUrl)
                        })
                    }
                }
            }

            putJsonObject("replay") {
                data.replay.forEach { (month, m) ->
                    putJsonObject(month) {
                        putJsonArray("songs") {
                            m.songs.forEach { s ->
                                add(s.song.toBackupJson {
                                    put("plays", s.plays)
                                    put("listenedMs", s.listenedMs)
                                })
                            }
                        }
                        putJsonArray("artists") {
                            m.artists.forEach { a ->
                                add(buildJsonObject {
                                    put("name", a.name)
                                    put("plays", a.plays)
                                    put("listenedMs", a.listenedMs)
                                    put("imageUrl", a.imageUrl)
                                })
                            }
                        }
                    }
                }
            }
        }
        return StoreJson.encodeToString(JsonElement.serializer(), root)
    }

    private fun Song.toBackupJson(extra: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        buildJsonObject {
            put("url", url)
            put("title", title)
            put("artist", artist)
            put("thumbnailUrl", thumbnailUrl)
            put("durationSeconds", durationSeconds)
            extra()
        }

    // ------------------------------------------------------------ baca

    fun decode(text: String): BackupData {
        val root: JsonObject = try {
            StoreJson.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw BackupException("Berkas ini bukan cadangan SonoraMusic yang valid")

        if (root.prim("app")?.content != APP_ID) {
            throw BackupException("Berkas ini bukan cadangan SonoraMusic")
        }
        val format = root.optInt("format", 0)
        if (format < 1) throw BackupException("Format berkas cadangan tidak dikenali")
        if (format > FORMAT_VERSION) {
            throw BackupException("Cadangan ini dibuat oleh SonoraMusic versi lebih baru. Perbarui aplikasinya dulu")
        }

        val playlists = root["playlists"].mapObjects { o ->
            BackupPlaylist(
                id = o.req("id"),
                name = o.req("name"),
                songs = o["songs"].mapObjects { it.toBackupSong() },
                createdAt = o.optLong("createdAt", 0L),
                coverBytes = o.optStringOrNull("cover")?.let {
                    runCatching { Base64.Mime.decode(it) }.getOrNull()?.takeIf { b -> b.isNotEmpty() }
                }
            )
        }

        val historyObj = root["history"] as? JsonObject
        val historySongs = historyObj?.get("songs").mapObjects { o ->
            PlayedSong(
                song = o.toBackupSong(),
                plays = o.optInt("plays", 1).coerceAtLeast(1),
                lastPlayedAt = o.optLong("lastPlayedAt", 0L)
            )
        }
        val historyArtists = historyObj?.get("artists").mapObjects { o ->
            ArtistStat(
                name = o.req("name"),
                plays = o.optInt("plays", 1).coerceAtLeast(1),
                imageUrl = o.optStringOrNull("imageUrl")
            )
        }

        val replay = LinkedHashMap<String, MonthStats>()
        (root["replay"] as? JsonObject)?.let { obj ->
            for ((month, el) in obj) {
                if (!MONTH_KEY.matches(month)) continue
                val m = el as? JsonObject ?: continue
                replay[month] = MonthStats(
                    songs = m["songs"].mapObjects { o ->
                        MonthSong(
                            song = o.toBackupSong(),
                            plays = o.optInt("plays", 0).coerceAtLeast(0),
                            listenedMs = o.optLong("listenedMs", 0L).coerceAtLeast(0L)
                        )
                    },
                    artists = m["artists"].mapObjects { o ->
                        MonthArtist(
                            name = o.req("name"),
                            plays = o.optInt("plays", 0).coerceAtLeast(0),
                            listenedMs = o.optLong("listenedMs", 0L).coerceAtLeast(0L),
                            imageUrl = o.optStringOrNull("imageUrl")
                        )
                    }
                )
            }
        }

        return BackupData(
            liked = root["liked"].mapObjects { it.toBackupSong() },
            playlists = playlists,
            quickPlaylistId = root.optStringOrNull("quickPlaylistId"),
            historySongs = historySongs,
            historyArtists = historyArtists,
            replay = replay
        )
    }

    private fun JsonObject.toBackupSong() = Song(
        url = req("url"),
        title = req("title"),
        artist = req("artist"),
        thumbnailUrl = prim("thumbnailUrl")?.content,
        durationSeconds = optLong("durationSeconds", -1L)
    )

    /** Ubah tiap elemen objek lewat [transform]; elemen yang rusak dilewati, tidak menggagalkan seluruh impor. */
    private fun <T> JsonElement?.mapObjects(transform: (JsonObject) -> T): List<T> {
        val arr = this as? JsonArray ?: return emptyList()
        val result = ArrayList<T>(arr.size)
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            runCatching { transform(o) }.getOrNull()?.let { result += it }
        }
        return result
    }

    private fun JsonObject.prim(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun JsonObject.req(key: String): String =
        prim(key)?.content ?: throw IllegalArgumentException("Kunci $key tidak ada")

    private fun JsonObject.optStringOrNull(key: String): String? =
        prim(key)?.content?.ifBlank { null }

    private fun JsonObject.optInt(key: String, default: Int): Int =
        prim(key)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() } ?: default

    private fun JsonObject.optLong(key: String, default: Long): Long =
        prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: default
}