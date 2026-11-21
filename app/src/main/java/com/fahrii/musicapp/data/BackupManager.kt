package com.fahrii.musicapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

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

/**
 * Cadangan data pengguna ke satu berkas JSON: Favorit, playlist (beserta cover buatan sendiri),
 * riwayat putar, dan statistik Replay. Lagu yang diunduh tidak ikut (ukurannya besar); setelah
 * dipulihkan, lagunya tetap bisa diputar lewat streaming dan diunduh ulang.
 *
 * Saat impor, data yang sudah ada tidak diimpor lagi; hanya yang belum ada yang ditambahkan.
 */
class BackupManager(context: Context) {

    private val context = context.applicationContext
    private val library = LibraryRepository.getInstance(this.context)
    private val history = HistoryRepository.getInstance(this.context)
    private val replay = ReplayRepository.getInstance(this.context)

    /** Tulis cadangan ke [uri] (hasil CreateDocument). */
    fun exportTo(uri: Uri): ExportSummary {
        // Waktu dengar yang masih tertunda ikut masuk ke statistik sebelum disalin
        replay.flush()

        val lib = library.state.value
        val songs = history.songs.value
        val artists = history.artists.value
        val stats = replay.stats.value

        val root = JSONObject().apply {
            put("app", APP_ID)
            put("format", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("appVersion", runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: JSONObject.NULL)

            put("liked", JSONArray().also { arr -> lib.liked.forEach { arr.put(it.toJson()) } })
            put("quickPlaylistId", lib.quickPlaylistId ?: JSONObject.NULL)
            put("playlists", JSONArray().also { arr ->
                lib.playlists.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("createdAt", p.createdAt)
                        put("songs", JSONArray().also { sa -> p.songs.forEach { sa.put(it.toJson()) } })
                        put("cover", encodeCover(p.thumbnailPath) ?: JSONObject.NULL)
                    })
                }
            })

            put("history", JSONObject().apply {
                put("songs", JSONArray().also { arr ->
                    songs.forEach { p ->
                        arr.put(p.song.toJson().apply {
                            put("plays", p.plays)
                            put("lastPlayedAt", p.lastPlayedAt)
                        })
                    }
                })
                put("artists", JSONArray().also { arr ->
                    artists.forEach { a ->
                        arr.put(JSONObject().apply {
                            put("name", a.name)
                            put("plays", a.plays)
                            put("imageUrl", a.imageUrl ?: JSONObject.NULL)
                        })
                    }
                })
            })

            put("replay", JSONObject().also { obj ->
                stats.forEach { (month, m) ->
                    obj.put(month, JSONObject().apply {
                        put("songs", JSONArray().also { arr ->
                            m.songs.forEach { s ->
                                arr.put(s.song.toJson().apply {
                                    put("plays", s.plays)
                                    put("listenedMs", s.listenedMs)
                                })
                            }
                        })
                        put("artists", JSONArray().also { arr ->
                            m.artists.forEach { a ->
                                arr.put(JSONObject().apply {
                                    put("name", a.name)
                                    put("plays", a.plays)
                                    put("listenedMs", a.listenedMs)
                                    put("imageUrl", a.imageUrl ?: JSONObject.NULL)
                                })
                            }
                        })
                    })
                }
            })
        }

        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupException("Tidak bisa menulis ke lokasi yang dipilih")
        try {
            out.use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            throw BackupException("Gagal menyimpan berkas cadangan: ${e.localizedMessage ?: "kesalahan tulis"}")
        }

        return ExportSummary(
            liked = lib.liked.size,
            playlists = lib.playlists.size,
            historySongs = songs.size,
            replayMonths = stats.size
        )
    }

    /** Baca cadangan dari [uri] (hasil OpenDocument) lalu gabungkan: yang sudah ada dilewati. */
    fun importFrom(uri: Uri): ImportReport {
        val backup = parse(readText(uri))

        val libraryReport = library.mergeBackup(backup.liked, backup.playlists, backup.quickPlaylistId)
        val historyReport = history.mergeBackup(backup.historySongs, backup.historyArtists)
        val replayReport = replay.mergeBackup(backup.replay)

        return libraryReport.copy(history = historyReport, replay = replayReport)
    }

    // ---------------------------------------------------------------- baca

    private class ParsedBackup(
        val liked: List<Song>,
        val playlists: List<BackupPlaylist>,
        val quickPlaylistId: String?,
        val historySongs: List<PlayedSong>,
        val historyArtists: List<ArtistStat>,
        val replay: Map<String, MonthStats>
    )

    private fun readText(uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("Berkas tidak bisa dibuka")
        val bytes = try {
            input.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) {
                        throw BackupException("Berkas terlalu besar untuk berkas cadangan SonoraMusic")
                    }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } catch (e: IOException) {
            throw BackupException("Gagal membaca berkas: ${e.localizedMessage ?: "kesalahan baca"}")
        }
        return String(bytes, Charsets.UTF_8).trimStart('\uFEFF')
    }

    private fun parse(text: String): ParsedBackup {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException("Berkas ini bukan cadangan SonoraMusic yang valid")
        }
        if (root.optString("app") != APP_ID) {
            throw BackupException("Berkas ini bukan cadangan SonoraMusic")
        }
        val format = root.optInt("format", 0)
        if (format < 1) throw BackupException("Format berkas cadangan tidak dikenali")
        if (format > FORMAT_VERSION) {
            throw BackupException("Cadangan ini dibuat oleh SonoraMusic versi lebih baru. Perbarui aplikasinya dulu")
        }

        val playlists = root.optJSONArray("playlists").mapObjects { o ->
            BackupPlaylist(
                id = o.getString("id"),
                name = o.getString("name"),
                songs = o.optJSONArray("songs").toSongsSafe(),
                createdAt = o.optLong("createdAt", 0L),
                coverBytes = o.optStringOrNull("cover")?.let {
                    runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()?.takeIf { b -> b.isNotEmpty() }
                }
            )
        }

        val historyObj = root.optJSONObject("history")
        val historySongs = historyObj?.optJSONArray("songs").mapObjects { o ->
            PlayedSong(
                song = o.toSong(),
                plays = o.optInt("plays", 1).coerceAtLeast(1),
                lastPlayedAt = o.optLong("lastPlayedAt", 0L)
            )
        }
        val historyArtists = historyObj?.optJSONArray("artists").mapObjects { o ->
            ArtistStat(
                name = o.getString("name"),
                plays = o.optInt("plays", 1).coerceAtLeast(1),
                imageUrl = o.optStringOrNull("imageUrl")
            )
        }

        val replay = LinkedHashMap<String, MonthStats>()
        root.optJSONObject("replay")?.let { obj ->
            obj.keys().forEach { month ->
                if (!MONTH_KEY.matches(month)) return@forEach
                val m = obj.optJSONObject(month) ?: return@forEach
                replay[month] = MonthStats(
                    songs = m.optJSONArray("songs").mapObjects { o ->
                        MonthSong(
                            song = o.toSong(),
                            plays = o.optInt("plays", 0).coerceAtLeast(0),
                            listenedMs = o.optLong("listenedMs", 0L).coerceAtLeast(0L)
                        )
                    },
                    artists = m.optJSONArray("artists").mapObjects { o ->
                        MonthArtist(
                            name = o.getString("name"),
                            plays = o.optInt("plays", 0).coerceAtLeast(0),
                            listenedMs = o.optLong("listenedMs", 0L).coerceAtLeast(0L),
                            imageUrl = o.optStringOrNull("imageUrl")
                        )
                    }
                )
            }
        }

        return ParsedBackup(
            liked = root.optJSONArray("liked").toSongsSafe(),
            playlists = playlists,
            quickPlaylistId = root.optStringOrNull("quickPlaylistId"),
            historySongs = historySongs,
            historyArtists = historyArtists,
            replay = replay
        )
    }

    /** Ubah tiap elemen objek lewat [transform]; elemen yang rusak dilewati, tidak menggagalkan seluruh impor. */
    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val result = ArrayList<T>(length())
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            runCatching { transform(o) }.getOrNull()?.let { result += it }
        }
        return result
    }

    private fun JSONArray?.toSongsSafe(): List<Song> = mapObjects { it.toSong() }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    // ---------------------------------------------------------------- cover

    /** Cover buatan pengguna diperkecil dan disimpan sebagai teks Base64 di dalam berkas cadangan. */
    private fun encodeCover(path: String?): String? {
        if (path == null) return null
        return runCatching {
            if (!File(path).exists()) return@runCatching null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > COVER_MAX_PX * 2) sample *= 2

            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val longest = max(bitmap.width, bitmap.height)
            val scaled = if (longest > COVER_MAX_PX) {
                val ratio = COVER_MAX_PX.toFloat() / longest
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                    (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private companion object {
        const val APP_ID = "SonoraMusic"
        const val FORMAT_VERSION = 1
        const val MAX_IMPORT_BYTES = 40L * 1024 * 1024
        const val COVER_MAX_PX = 600
        val MONTH_KEY = Regex("\\d{4}-\\d{2}")
    }
}
