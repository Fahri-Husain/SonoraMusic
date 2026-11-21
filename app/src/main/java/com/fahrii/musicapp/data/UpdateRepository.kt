package com.fahrii.musicapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Rilis terbaru di GitHub: [tag] mis. "v1.1", [body] berisi catatan perubahan (Markdown). */
data class ReleaseInfo(
    val tag: String,
    val publishedAt: String?,
    val body: String,
    /** Halaman rilis di GitHub (tempat APK-nya diunduh). */
    val releaseUrl: String
)

/** Cek rilis terbaru SonoraMusic di GitHub Releases. */
object UpdateRepository {

    private const val REPO = "Fahri-Husain/SonoraMusic"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Rilis terbaru (bukan draft/pre-release), atau null kalau belum ada rilis atau gagal koneksi. */
    suspend fun latestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SonoraMusic")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string() ?: return@withContext null)
            val tag = json.optString("tag_name").ifBlank { return@withContext null }
            ReleaseInfo(
                tag = tag,
                publishedAt = json.optString("published_at").ifBlank { null },
                body = json.optString("body"),
                releaseUrl = json.optString("html_url").ifBlank { "https://github.com/$REPO/releases/latest" }
            )
        }
    }

    /** True kalau versi [remoteTag] (mis. "v1.2") lebih baru dari [localVersionName] (mis. "1.1-debug"). */
    fun isNewer(remoteTag: String, localVersionName: String): Boolean {
        val remote = parseVersion(remoteTag) ?: return false
        val local = parseVersion(localVersionName) ?: return false
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /** Ambil angka versi dari teks seperti "v1.2.3" atau "1.1-debug" -> [1, 2, 3] / [1, 1]. */
    private fun parseVersion(text: String): List<Int>? =
        Regex("""\d+(\.\d+)*""").find(text)?.value?.split('.')?.mapNotNull { it.toIntOrNull() }
}
