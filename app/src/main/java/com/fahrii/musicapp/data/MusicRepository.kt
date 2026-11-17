package com.fahrii.musicapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.MediaFormat

object MusicRepository {

    private const val URL_TTL_MS = 20 * 60 * 1000L

    private data class CachedUrl(val url: String, val expiresAtMs: Long)

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()

    @Volatile
    private var initialized = false

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(DownloaderImpl.getInstance())
                    initialized = true
                }
            }
        }
    }

    private fun hdThumbnail(url: String?): String? {
        if (url == null) return null
        return when {
            "googleusercontent.com" in url || "ggpht.com" in url ->
                url.replace(Regex("=(w\\d+-h\\d+|s\\d+).*$"), "=w800-h800-l90-rj")
            else -> url
        }
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()

        val extractor = ServiceList.YouTube.getSearchExtractor(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        extractor.fetchPage()

        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                Song(
                    url = item.url,
                    title = item.name,
                    artist = item.uploaderName ?: "Unknown artist",
                    thumbnailUrl = hdThumbnail(item.thumbnails.lastOrNull()?.url),
                    durationSeconds = item.duration
                )
            }
    }

    /**
     * Versi blocking, dipanggil dari background thread milik ExoPlayer (PlaybackService).
     * Hasilnya di-cache 20 menit supaya seek/retry nggak ambil ulang dari YouTube.
     */
    fun resolveAudioUrlBlocking(videoUrl: String): String {
        val now = System.currentTimeMillis()
        urlCache[videoUrl]?.let { if (it.expiresAtMs > now) return it.url }

        ensureInit()

        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val candidates = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }

        val audio = candidates
            .filter { it.format == MediaFormat.M4A }
            .maxByOrNull { it.averageBitrate }
            ?: candidates.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("Stream audio tidak ditemukan")

        val url = audio.content
        urlCache[videoUrl] = CachedUrl(url, now + URL_TTL_MS)
        return url
    }

    private val searchCache = ConcurrentHashMap<String, String>()

    /**
     * Cari lagu di YouTube Music dan kembalikan URL video hasil pertama.
     * Dipanggil dari thread background milik player (untuk lagu dari Beranda).
     */
    fun findVideoUrlBlocking(query: String): String {
        searchCache[query]?.let { return it }
        ensureInit()

        val extractor = ServiceList.YouTube.getSearchExtractor(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        extractor.fetchPage()

        val first = extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .firstOrNull()
            ?: throw IllegalStateException("Lagu tidak ditemukan: $query")

        searchCache[query] = first.url
        return first.url
    }
}