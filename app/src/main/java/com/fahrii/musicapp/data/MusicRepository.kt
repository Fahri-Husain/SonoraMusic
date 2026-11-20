package com.fahrii.musicapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.MediaFormat

data class ArtistProfile(
    val name: String,
    val imageUrl: String?,
    val bio: String?,
    /** -1 kalau tidak diketahui. */
    val subscribers: Long
)

/** Lagu yang benar-benar diputar untuk entri "sonora://search", beserta daftar artisnya satu per satu. */
data class ResolvedSong(val song: Song, val artists: List<String>)

/** Artis di hasil pencarian: nama + foto profil. */
data class ArtistResult(
    val name: String,
    val imageUrl: String?
)

data class ArtistAlbum(
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val url: String
)

/** Album lengkap: sampul dan semua lagunya sesuai urutan tracklist. */
data class AlbumTracks(
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val songs: List<Song>
)

object MusicRepository {

    private val albumCache = ConcurrentHashMap<String, AlbumTracks>()

    private const val URL_TTL_MS = 20 * 60 * 1000L

    /** Bitrate (kbps) yang dituju untuk kualitas "Sedang". */
    private const val NORMAL_TARGET_KBPS = 128

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

    /** Artis yang cocok dengan [query] (pencarian artis YouTube Music), beserta foto profilnya. */
    suspend fun searchArtists(query: String, limit: Int = 3): List<ArtistResult> =
        withContext(Dispatchers.IO) {
            ensureInit()

            val extractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS),
                ""
            )
            extractor.fetchPage()

            extractor.initialPage.items
                .filterIsInstance<ChannelInfoItem>()
                .map { item ->
                    ArtistResult(
                        name = item.name,
                        imageUrl = hdThumbnail(item.thumbnails.lastOrNull()?.url)
                    )
                }
                .distinctBy { it.name.lowercase() }
                .take(limit)
        }

    /**
     * Album yang cocok dengan [query] (pencarian album YouTube Music), untuk bagian "Album" di
     * hasil pencarian. Hanya rilisan asli (album/single/EP); kalau tidak ada yang cocok dengan
     * penanda album, pakai semua hasil supaya bagian ini tidak kosong.
     */
    suspend fun searchAlbums(query: String, limit: Int = 5): List<ArtistAlbum> =
        withContext(Dispatchers.IO) {
            ensureInit()

            val extractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
                ""
            )
            extractor.fetchPage()

            val allItems = extractor.initialPage.items.filterIsInstance<PlaylistInfoItem>()
            val items = allItems.filter { isAlbumPlaylistUrl(it.url) }.ifEmpty { allItems }

            items
                .map { item ->
                    ArtistAlbum(
                        title = item.name,
                        artist = item.uploaderName?.takeIf { it.isNotBlank() } ?: "",
                        coverUrl = hdThumbnail(item.thumbnails.lastOrNull()?.url),
                        url = item.url
                    )
                }
                .distinctBy { it.url }
                .take(limit)
        }

    /**
     * Cari foto profil artis di YouTube Music (fallback: pencarian channel biasa).
     * Mengembalikan null kalau tidak ketemu.
     */
    suspend fun findArtistImage(name: String): String? = withContext(Dispatchers.IO) {
        ensureInit()

        fun pick(filter: String): String? {
            val extractor = ServiceList.YouTube.getSearchExtractor(name, listOf(filter), "")
            extractor.fetchPage()
            val channels = extractor.initialPage.items.filterIsInstance<ChannelInfoItem>()
            val best = channels.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: channels.firstOrNull()
            return hdThumbnail(best?.thumbnails?.lastOrNull()?.url)
        }

        runCatching { pick(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS) }.getOrNull()
            ?: runCatching { pick(YoutubeSearchQueryHandlerFactory.CHANNELS) }.getOrNull()
    }

    /**
     * Profil artis untuk halaman artis: foto, bio (dari halaman channel, fallback ke cuplikan
     * hasil pencarian), dan jumlah subscriber. Null kalau artisnya tidak ketemu.
     */
    suspend fun findArtistProfile(name: String): ArtistProfile? = withContext(Dispatchers.IO) {
        ensureInit()

        fun pick(filter: String): ChannelInfoItem? {
            val extractor = ServiceList.YouTube.getSearchExtractor(name, listOf(filter), "")
            extractor.fetchPage()
            val channels = extractor.initialPage.items.filterIsInstance<ChannelInfoItem>()
            return channels.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: channels.firstOrNull()
        }

        val best = runCatching { pick(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS) }.getOrNull()
            ?: runCatching { pick(YoutubeSearchQueryHandlerFactory.CHANNELS) }.getOrNull()

        if (best == null) {
            null
        } else {
            val bio = runCatching { ChannelInfo.getInfo(ServiceList.YouTube, best.url).description }
                .getOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?: best.description?.trim()?.takeIf { it.isNotEmpty() }

            ArtistProfile(
                name = best.name,
                imageUrl = hdThumbnail(best.thumbnails.lastOrNull()?.url),
                bio = bio,
                subscribers = best.subscriberCount
            )
        }
    }

    /** Lagu-lagu populer sebuah artis (hasil teratas pencarian lagu YouTube Music). */
    suspend fun searchArtistSongs(name: String, limit: Int = 10): List<Song> {
        val all = search(name)
        val own = all.filter { it.artist.contains(name, ignoreCase = true) }
        return own.ifEmpty { all }.take(limit)
    }

    /** Album-album sebuah artis (pencarian album YouTube Music, disaring menurut nama artis). */
    suspend fun searchArtistAlbums(name: String, limit: Int = 12): List<ArtistAlbum> =
        withContext(Dispatchers.IO) {
            ensureInit()

            val extractor = ServiceList.YouTube.getSearchExtractor(
                name,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
                ""
            )
            extractor.fetchPage()

            val allItems = extractor.initialPage.items.filterIsInstance<PlaylistInfoItem>()
            // Album/single/EP rilisan asli punya ID playlist berawalan "OLAK5uy_"; kumpulan lagu
            // biasa (playlist buatan pengguna, mix, dsb.) tidak. Kalau tidak ada satu pun yang
            // cocok (format ID berubah), pakai semua hasil supaya bagian album tidak kosong.
            val items = allItems.filter { isAlbumPlaylistUrl(it.url) }.ifEmpty { allItems }
            // Kalau nama artis di hasil tersedia, pakai untuk menyaring album artis lain
            val hasArtist = items.any { !it.uploaderName.isNullOrBlank() }
            val own = if (hasArtist) {
                items.filter { it.uploaderName?.contains(name, ignoreCase = true) == true }
            } else {
                items
            }

            own
                .map { item ->
                    ArtistAlbum(
                        title = item.name,
                        artist = item.uploaderName?.takeIf { it.isNotBlank() } ?: name,
                        coverUrl = hdThumbnail(item.thumbnails.lastOrNull()?.url),
                        url = item.url
                    )
                }
                .distinctBy { it.title.lowercase() }
                .take(limit)
        }

    private fun isAlbumPlaylistUrl(url: String): Boolean = "list=OLAK5uy_" in url

    /**
     * Playlist YouTube / YouTube Music dari ID-nya ("list=..." di link) untuk fitur impor playlist.
     * Maksimal [maxSongs] lagu; melempar exception kalau playlist tidak ada atau bersifat pribadi.
     */
    suspend fun loadYouTubePlaylist(listId: String, maxSongs: Int): ImportedPlaylist =
        withContext(Dispatchers.IO) {
            ensureInit()
            val url = "https://www.youtube.com/playlist?list=$listId"
            val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)

            val streams = info.relatedItems.filterIsInstance<StreamInfoItem>().toMutableList()
            var page = info.nextPage
            while (page != null && streams.size < maxSongs) {
                val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
                streams += more.items.filterIsInstance<StreamInfoItem>()
                page = more.nextPage
            }

            val capped = streams.size > maxSongs || (page != null && streams.size >= maxSongs)
            ImportedPlaylist(
                name = info.name?.takeIf { it.isNotBlank() } ?: "Playlist YouTube",
                songs = streams.take(maxSongs)
                    .map { item ->
                        Song(
                            url = item.url,
                            title = item.name,
                            // Kanal YouTube Music resmi bernama "Artis - Topic"
                            artist = (item.uploaderName ?: "").removeSuffix(" - Topic").ifBlank { "Unknown artist" },
                            thumbnailUrl = hdThumbnail(item.thumbnails.lastOrNull()?.url),
                            durationSeconds = item.duration
                        )
                    }
                    .distinctBy { it.url },
                note = if (capped) "Hanya $maxSongs lagu pertama yang diimpor." else null
            )
        }

    /** Ambil semua lagu dari playlist album [playlistUrl] (maksimal beberapa halaman). Null kalau kosong. */
    private fun loadAlbumPlaylist(
        playlistUrl: String,
        title: String,
        artist: String,
        coverUrl: String?
    ): AlbumTracks? {
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, playlistUrl)
        val streams = info.relatedItems.filterIsInstance<StreamInfoItem>().toMutableList()
        var page = info.nextPage
        var guard = 0
        while (page != null && guard++ < 3) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, playlistUrl, page)
            streams += more.items.filterIsInstance<StreamInfoItem>()
            page = more.nextPage
        }
        if (streams.isEmpty()) return null

        val cover = coverUrl ?: hdThumbnail(streams.first().thumbnails.lastOrNull()?.url)
        return AlbumTracks(
            title = title,
            artist = artist,
            coverUrl = cover,
            songs = streams.map { item ->
                Song(
                    url = item.url,
                    title = item.name,
                    artist = item.uploaderName?.takeIf { it.isNotBlank() } ?: artist,
                    thumbnailUrl = cover ?: hdThumbnail(item.thumbnails.lastOrNull()?.url),
                    durationSeconds = item.duration
                )
            }
        )
    }

    /**
     * Tracklist album langsung dari URL playlist albumnya (dipakai halaman artis, yang sudah
     * punya URL-nya, jadi tidak perlu mencari album lagi). Di-cache selama aplikasi hidup.
     */
    suspend fun findAlbumTracksByUrl(
        url: String,
        title: String,
        artist: String,
        coverUrl: String?
    ): AlbumTracks? = withContext(Dispatchers.IO) {
        ensureInit()
        val cacheKey = "url|$url"
        albumCache[cacheKey]?.let { return@withContext it }

        val result = loadAlbumPlaylist(url, title, artist, coverUrl) ?: return@withContext null
        albumCache[cacheKey] = result
        result
    }

    /**
     * Tracklist lengkap sebuah album: cari album di YouTube Music dari [title] + [artist],
     * lalu ambil semua lagunya. Null kalau albumnya tidak ketemu. Hasilnya di-cache selama
     * aplikasi hidup.
     */
    suspend fun findAlbumTracks(title: String, artist: String): AlbumTracks? =
        withContext(Dispatchers.IO) {
            ensureInit()

            val cacheKey = title.trim().lowercase() + "|" + artist.trim().lowercase()
            albumCache[cacheKey]?.let { return@withContext it }

            val extractor = ServiceList.YouTube.getSearchExtractor(
                "$title $artist",
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
                ""
            )
            extractor.fetchPage()
            val items = extractor.initialPage.items.filterIsInstance<PlaylistInfoItem>()

            fun sameTitle(it: PlaylistInfoItem) = it.name.trim().equals(title.trim(), ignoreCase = true)
            fun sameArtist(it: PlaylistInfoItem) =
                it.uploaderName?.contains(artist, ignoreCase = true) == true

            // Judul dari Deezer kadang beda tanda baca/huruf besar dari YouTube Music
            fun norm(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
            fun fuzzyTitle(it: PlaylistInfoItem): Boolean {
                val a = norm(it.name)
                val b = norm(title)
                return a.isNotEmpty() && b.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
            }

            val best = items.firstOrNull { sameTitle(it) && sameArtist(it) }
                ?: items.firstOrNull { sameTitle(it) }
                ?: items.firstOrNull { fuzzyTitle(it) && sameArtist(it) }
                ?: return@withContext null

            val result = loadAlbumPlaylist(
                playlistUrl = best.url,
                title = best.name,
                artist = best.uploaderName?.takeIf { it.isNotBlank() } ?: artist,
                coverUrl = hdThumbnail(best.thumbnails.lastOrNull()?.url)
            ) ?: return@withContext null
            albumCache[cacheKey] = result
            result
        }

    /**
     * Cari nama album sebuah lagu dari deskripsi videonya. Lagu resmi di YouTube Music punya
     * deskripsi otomatis berformat:
     *
     *     Provided to YouTube by <label>
     *
     *     <Judul> · <Artis>
     *
     *     <Album>
     *
     *     ℗ <tahun> ...
     *
     * Mengembalikan null kalau formatnya tidak cocok (mis. video yang diunggah sendiri).
     */
    suspend fun findAlbumName(videoUrl: String): String? = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        parseAlbumFromDescription(info.description?.content)
    }

    private val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val blockEndRegex = Regex("</(p|div)\\s*>", RegexOption.IGNORE_CASE)
    private val tagRegex = Regex("<[^>]+>")
    private val numericEntityRegex = Regex("&#(x?[0-9a-fA-F]+);")

    /**
     * NewPipe memberi deskripsi YouTube dalam bentuk HTML: baris baru jadi `<br>`, link jadi `<a>`,
     * dan karakter khusus jadi entity. Diubah dulu ke teks biasa supaya bisa dipecah per baris.
     */
    internal fun htmlToPlainText(html: String): String {
        val withoutTags = html
            .replace(brRegex, "\n")
            .replace(blockEndRegex, "\n")
            .replace(tagRegex, "")
        val decoded = numericEntityRegex.replace(withoutTags) { m ->
            val v = m.groupValues[1]
            val cp = if (v.startsWith("x", ignoreCase = true)) v.drop(1).toIntOrNull(16) else v.toIntOrNull()
            if (cp != null && cp in 1..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        return decoded
            .replace("&nbsp;", " ")
            .replace("&middot;", "·")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace('\u00a0', ' ')
    }

    /**
     * Semua artis sebuah lagu dari baris "Judul · Artis 1 · Artis 2" di deskripsi otomatis YouTube
     * Music. NewPipe hanya memberi artis pertama di hasil pencarian, jadi kolaborasi (mis. "Olivia
     * Rodrigo" + "Chappell Roan") baru lengkap di sini. Null kalau formatnya tidak cocok.
     */
    /** Semua artis video YouTube [videoUrl] dari deskripsinya. Null kalau formatnya tidak cocok. */
    suspend fun fetchArtistsByUrl(videoUrl: String): List<String>? = withContext(Dispatchers.IO) {
        ensureInit()
        parseArtistsFromDescription(StreamInfo.getInfo(ServiceList.YouTube, videoUrl).description?.content)
    }

    internal fun parseArtistsFromDescription(description: String?): List<String>? {
        if (description.isNullOrBlank()) return null
        val lines = htmlToPlainText(description).lines().map { it.trim() }
        val provided = lines.indexOfFirst { it.startsWith("Provided to YouTube by", ignoreCase = true) }
        if (provided < 0) return null
        val titleLine = (provided + 1 until lines.size).firstOrNull { " · " in lines[it] } ?: return null
        val artists = lines[titleLine].split(" · ").drop(1).map { it.trim() }.filter { it.isNotEmpty() }
        return artists.ifEmpty { null }
    }

    internal fun parseAlbumFromDescription(description: String?): String? {
        if (description.isNullOrBlank()) return null
        val lines = htmlToPlainText(description).lines().map { it.trim() }
        val provided = lines.indexOfFirst { it.startsWith("Provided to YouTube by", ignoreCase = true) }
        if (provided < 0) return null
        // Baris "Judul · Artis", lalu baris berikutnya yang tidak kosong adalah album
        val titleLine = (provided + 1 until lines.size).firstOrNull { " · " in lines[it] } ?: return null
        return lines.drop(titleLine + 1)
            .firstOrNull { it.isNotEmpty() }
            ?.takeUnless { it.startsWith("℗") || it.startsWith("Released on", ignoreCase = true) }
    }

    /**
     * Versi blocking, dipanggil dari background thread milik ExoPlayer (PlaybackService).
     * Hasilnya di-cache 20 menit supaya seek/retry nggak ambil ulang dari YouTube.
     */
    fun resolveAudioUrlBlocking(videoUrl: String, quality: AudioQuality = AudioQuality.HIGH): String {
        val now = System.currentTimeMillis()
        // Kunci cache menyertakan kualitas, supaya ganti kualitas tidak memakai URL lama
        val cacheKey = "$videoUrl|${quality.name}"
        urlCache[cacheKey]?.let { if (it.expiresAtMs > now) return it.url }

        ensureInit()

        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val candidates = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }

        // Utamakan M4A (paling kompatibel); kalau tidak ada, pakai format apa pun
        val pool = candidates.filter { it.format == MediaFormat.M4A }.ifEmpty { candidates }
        val audio = pickByQuality(pool, quality)
            ?: throw IllegalStateException("Stream audio tidak ditemukan")

        val url = audio.content
        urlCache[cacheKey] = CachedUrl(url, now + URL_TTL_MS)
        return url
    }

    /** Pilih stream sesuai [quality]; bitrate yang tidak diketahui (<= 0) dianggap paling rendah. */
    private fun <T : org.schabi.newpipe.extractor.stream.AudioStream> pickByQuality(
        streams: List<T>,
        quality: AudioQuality
    ): T? {
        if (streams.isEmpty()) return null
        val known = streams.filter { it.averageBitrate > 0 }
        if (known.isEmpty()) return streams.first()
        return when (quality) {
            AudioQuality.HIGH -> known.maxByOrNull { it.averageBitrate }
            AudioQuality.LOW -> known.minByOrNull { it.averageBitrate }
            AudioQuality.NORMAL -> known.minByOrNull { kotlin.math.abs(it.averageBitrate - NORMAL_TARGET_KBPS) }
        }
    }

    /**
     * URL stream audio untuk [songUrl] (kunci lagu di aplikasi), dengan aturan yang sama seperti
     * di PlaybackService: "sonora://search?q=..." dicari dulu di YouTube Music, link YouTube
     * langsung diambil streamnya. Dipakai pengunduh; blocking, panggil dari thread background.
     */
    fun resolvePlayableUrlBlocking(songUrl: String, quality: AudioQuality = AudioQuality.HIGH): String {
        val uri = android.net.Uri.parse(songUrl)
        return when {
            uri.scheme == "sonora" -> {
                val query = uri.getQueryParameter("q").orEmpty()
                resolveAudioUrlBlocking(findVideoUrlBlocking(query), quality)
            }
            else -> resolveAudioUrlBlocking(songUrl, quality)
        }
    }

    private val searchCache = ConcurrentHashMap<String, String>()
    private val durationCache = ConcurrentHashMap<String, Long>()

    /**
     * Durasi (detik) lagu yang akan diputar untuk [query]: hasil pertama pencarian YouTube Music,
     * sama dengan yang dipilih [findVideoUrlBlocking], jadi menit yang tampil cocok dengan yang
     * terdengar. Null kalau tidak ada hasil atau durasinya tidak diketahui. Melempar exception
     * kalau pencarian gagal.
     */
    suspend fun findSongDuration(query: String): Long? = withContext(Dispatchers.IO) {
        val cached = durationCache[query]
        if (cached != null) return@withContext cached

        ensureInit()
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        extractor.fetchPage()

        val seconds = extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .firstOrNull()
            ?.duration
            ?: -1L
        if (seconds > 0) {
            durationCache[query] = seconds
            seconds
        } else {
            null
        }
    }

    private val resolvedSongCache = ConcurrentHashMap<String, ResolvedSong>()

    /**
     * Lagu dari Beranda (Deezer) hanya membawa satu nama artis utama, padahal yang diputar adalah
     * hasil pertama pencarian YouTube Music, yang bisa saja versi kolaborasi (mis. "Olivia Rodrigo &
     * Chappell Roan"). Fungsi ini mengembalikan judul dan artis lengkap dari hasil yang sama dengan
     * yang diputar [findVideoUrlBlocking], supaya lirik dan tampilan artis cocok dengan lagu yang
     * terdengar. Null kalau lagu bukan "sonora://search" atau tidak ditemukan. Melempar exception
     * kalau pencarian gagal.
     */
    suspend fun resolveSongMeta(songUrl: String): ResolvedSong? = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(songUrl)
        if (uri.scheme != "sonora") return@withContext null
        val query = uri.getQueryParameter("q").orEmpty()
        if (query.isBlank()) return@withContext null
        resolvedSongCache[query]?.let { return@withContext it }

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
            ?: return@withContext null

        // Hasil pencarian hanya memuat artis pertama, jadi baca daftar lengkapnya dari deskripsi video.
        val allArtists = runCatching {
            parseArtistsFromDescription(StreamInfo.getInfo(ServiceList.YouTube, first.url).description?.content)
        }.getOrNull()
        val artists = allArtists
            ?: listOf((first.uploaderName ?: "").removeSuffix(" - Topic").trim()).filter { it.isNotEmpty() }
        if (artists.isEmpty()) return@withContext null

        val song = Song(
            url = first.url,
            title = first.name,
            artist = artists.joinToString(" & "),
            thumbnailUrl = hdThumbnail(first.thumbnails.lastOrNull()?.url),
            durationSeconds = first.duration
        )
        val resolved = ResolvedSong(song, artists)
        resolvedSongCache[query] = resolved
        resolved
    }

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