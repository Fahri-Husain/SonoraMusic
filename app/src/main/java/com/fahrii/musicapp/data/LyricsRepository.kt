package com.fahrii.musicapp.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
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

    suspend fun get(title: String, artist: String): List<LyricLine> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitle(title)
        val cleanArtist = cleanArtist(artist)
        val key = "$cleanArtist|$cleanTitle".lowercase()

        cache[key]?.let { return@withContext it }

        val lines = runCatching { fetch(cleanTitle, cleanArtist) }.getOrDefault(emptyList())
        if (lines.isNotEmpty()) cache[key] = lines
        lines
    }

    private fun fetch(title: String, artist: String): List<LyricLine> {
        val queries = listOf(
            "https://lrclib.net/api/search?track_name=${Uri.encode(title)}&artist_name=${Uri.encode(artist)}",
            "https://lrclib.net/api/search?q=${Uri.encode("$title $artist")}"
        )
        for (url in queries) {
            val results = getArray(url)
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                if (item.isNull("syncedLyrics")) continue
                val lines = parseLrc(item.optString("syncedLyrics"))
                if (lines.isNotEmpty()) return lines
            }
        }
        return emptyList()
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
