package com.fahrii.musicapp.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Alur ekspor/impor cadangan yang netral platform. Android dan desktop cukup menyediakan
 * stream (dari Uri atau dialog file) dan, kalau perlu, [encodeCover] untuk memperkecil cover
 * playlist buatan pengguna jadi JPEG kecil. Tanpa [encodeCover], cover tidak ikut di cadangan.
 */
class BackupService(
    private val library: LibraryRepository,
    private val history: HistoryRepository,
    private val replay: ReplayRepository,
    private val appVersion: String? = null,
    private val encodeCover: (path: String) -> ByteArray? = { null }
) {

    /** Tulis cadangan ke [out]; stream ditutup setelahnya. */
    fun export(out: OutputStream): ExportSummary {
        // Waktu dengar yang masih tertunda ikut masuk ke statistik sebelum disalin
        replay.flush()

        val lib = library.state.value
        val data = BackupData(
            liked = lib.liked,
            playlists = lib.playlists.map { p ->
                BackupPlaylist(
                    id = p.id,
                    name = p.name,
                    songs = p.songs,
                    createdAt = p.createdAt,
                    coverBytes = p.thumbnailPath?.let { runCatching { encodeCover(it) }.getOrNull() }
                )
            },
            quickPlaylistId = lib.quickPlaylistId,
            historySongs = history.songs.value,
            historyArtists = history.artists.value,
            replay = replay.stats.value
        )

        val bytes = BackupCodec.encode(data, appVersion, System.currentTimeMillis())
            .toByteArray(Charsets.UTF_8)
        try {
            out.use { it.write(bytes) }
        } catch (e: IOException) {
            throw BackupException("Gagal menyimpan berkas cadangan: ${e.localizedMessage ?: "kesalahan tulis"}")
        }
        return data.summary()
    }

    /** Baca cadangan dari [input] lalu gabungkan: yang sudah ada dilewati. */
    fun import(input: InputStream): ImportReport {
        val backup = BackupCodec.decode(readText(input))

        val libraryReport = library.mergeBackup(backup.liked, backup.playlists, backup.quickPlaylistId)
        val historyReport = history.mergeBackup(backup.historySongs, backup.historyArtists)
        val replayReport = replay.mergeBackup(backup.replay)

        return libraryReport.copy(history = historyReport, replay = replayReport)
    }

    private fun readText(input: InputStream): String {
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

    private companion object {
        const val MAX_IMPORT_BYTES = 40L * 1024 * 1024
    }
}