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