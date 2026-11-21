package com.fahrii.musicapp.data

data class Song(
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long
)

/** Awalan URL untuk entri yang sebenarnya album (mis. di Rilis Baru), bukan lagu yang bisa diputar. */
const val ALBUM_ENTRY_PREFIX = "sonora://album"

/** True kalau entri ini sebuah album: kalau diklik, buka halaman albumnya, jangan diputar. */
fun Song.isAlbumEntry(): Boolean = url.startsWith(ALBUM_ENTRY_PREFIX)

/** Rujukan album untuk membuka halaman album; tracklist-nya dicari dari judul + artis. */
fun Song.toAlbumRef(): ArtistAlbum =
    ArtistAlbum(title = title, artist = artist, coverUrl = thumbnailUrl, url = "")

private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

/**
 * Kunci identitas lagu: judul + artis, tanpa beda huruf besar/kecil, tanda baca, atau " - Topic".
 * URL lagu yang sama bisa beda tergantung sumbernya (Pilihan Cepat, album, atau pencarian),
 * jadi lagu dikenali dari judul + artisnya.
 */
fun Song.identityKey(): String {
    fun norm(s: String) = s.lowercase()
        .removeSuffix("- topic")
        .replace(NON_ALNUM, "")
    return norm(title) + "|" + norm(artist)
}