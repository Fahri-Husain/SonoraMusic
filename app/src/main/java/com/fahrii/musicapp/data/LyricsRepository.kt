package com.fahrii.musicapp.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class LyricLine(val timeMs: Long, val text: String)

/**
 * Lirik tersinkron (format LRC) dari LRCLIB, API publik gratis tanpa API key: https://lrclib.net
 * Hasil kosong = lirik tidak ditemukan.
 */
object LyricsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Hanya hasil yang berhasil yang disimpan, supaya gagal koneksi bisa dicoba lagi
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    /** Lirik dengan baris lebih sedikit dari ini dianggap tidak lengkap (mis. entri LRCLIB yang cuma berisi sebaris). */
    private const val MIN_LINES = 5

    /**
     * [durationSeconds] = durasi lagu yang sedang diputar (kalau belum diketahui, isi -1).
     * Dipakai untuk memilih versi lirik yang waktunya cocok dengan lagu.
     */
    suspend fun get(title: String, artist: String, durationSeconds: Long = -1L): List<LyricLine> =
        withContext(Dispatchers.IO) {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = cleanArtist(artist)
            val key = "$cleanArtist|$cleanTitle".lowercase()

            cache[key]?.let { return@withContext it }

            val lines = runCatching { fetch(cleanTitle, cleanArtist, durationSeconds) }
                .getOrDefault(emptyList())
            if (lines.isNotEmpty()) cache[key] = lines
            lines
        }

    /**
     * Dulu fungsi ini langsung memakai hasil pertama yang punya lirik tersinkron, sehingga entri yang
     * tidak lengkap (sebaris saja) atau milik lagu/versi lain ikut terpakai. Sekarang semua hasil
     * dinilai dan hanya yang cocok dan lengkap yang dipakai; yang terbaik menang.
     */
    private fun fetch(title: String, artist: String, durationSeconds: Long): List<LyricLine> {
        val queries = listOf(
            "https://lrclib.net/api/search?track_name=${Uri.encode(title)}&artist_name=${Uri.encode(artist)}",
            "https://lrclib.net/api/search?q=${Uri.encode("$title $artist")}"
        )
        for (url in queries) {
            val results = getArray(url)
            var best: List<LyricLine> = emptyList()
            var bestScore = Int.MIN_VALUE
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                if (item.isNull("syncedLyrics")) continue
                val score = score(item, title, artist, durationSeconds) ?: continue
                if (score <= bestScore) continue
                val lines = parseLrc(item.optString("syncedLyrics"))
                if (!looksComplete(lines, item.optDouble("duration", -1.0))) continue
                best = lines
                bestScore = score
            }
            if (best.isNotEmpty()) return best
        }
        return emptyList()
    }

    /** Nilai kecocokan satu hasil LRCLIB dengan lagu yang diputar; null berarti bukan lagu yang sama. */
    private fun score(item: JSONObject, title: String, artist: String, durationSeconds: Long): Int? {
        val itemTitle = normalize(item.optString("trackName"))
        val wantTitle = normalize(title)
        if (itemTitle.isEmpty() || wantTitle.isEmpty()) return null

        var score = when {
            itemTitle == wantTitle -> 40
            itemTitle.contains(wantTitle) || wantTitle.contains(itemTitle) -> 20
            else -> return null // pencarian "q=" kadang mengembalikan lagu lain
        }
        if (artistMatches(item.optString("artistName"), artist)) score += 20

        // Durasi dipakai sebagai nilai, bukan penolak, supaya durasi pemutar yang meleset
        // tidak membuat semua lirik tertolak.
        val itemDuration = item.optDouble("duration", -1.0)
        if (durationSeconds > 0 && itemDuration > 0) {
            val diff = abs(itemDuration - durationSeconds)
            score += when {
                diff <= 2 -> 50
                diff <= 5 -> 30
                diff <= 10 -> 10
                else -> -40
            }
        }
        return score
    }

    /** Lirik dianggap lengkap kalau barisnya cukup banyak dan waktunya masuk akal untuk durasi lagu. */
    private fun looksComplete(lines: List<LyricLine>, itemDurationSec: Double): Boolean {
        if (lines.size < MIN_LINES) return false
        if (itemDurationSec > 0) {
            val lastMs = lines.last().timeMs
            if (lastMs < itemDurationSec * 1000 * 0.5) return false // lirik berhenti di tengah lagu
            if (lastMs > itemDurationSec * 1000 + 5_000) return false // waktu melebihi lagu: salah versi
        }
        return true
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    private fun artistMatches(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na.contains(nb) || nb.contains(na)) return true
        val tokens = nb.split(' ').filter { it.length >= 3 }.toSet()
        return na.split(' ').any { it in tokens }
    }

    private fun getArray(url: String): JSONArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SonoraMusic/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return JSONArray(response.body?.string().orEmpty())
        }
    }

    private val timestamp = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")

    /** "[01:23.45] teks lirik" menjadi (83450 ms, "teks lirik"). */
    private fun parseLrc(lrc: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (raw in lrc.lines()) {
            val stamps = timestamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in stamps) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fraction = m.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                result += LyricLine(minutes * 60_000 + seconds * 1_000 + fractionMs, text)
            }
        }
        return result.sortedBy { it.timeMs }
    }

    /** Judul dari YouTube sering berisi "(Official Video)", "[Lyrics]", dll. */
    private fun cleanTitle(title: String): String =
        title
            .replace(Regex("""\s*[(\[].*?[)\]]"""), "")
            .replace(Regex("""(?i)\s*-\s*(official|lyrics?|audio|video).*$"""), "")
            .trim()
            .ifBlank { title.trim() }

    private fun cleanArtist(artist: String): String =
        artist
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)\s*vevo$"""), "")
            .trim()
}