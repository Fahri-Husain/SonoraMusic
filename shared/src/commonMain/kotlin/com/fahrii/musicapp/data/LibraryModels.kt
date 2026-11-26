package com.fahrii.musicapp.data

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song>,
    /** Waktu playlist dibuat (epoch ms); 0 untuk playlist lama yang belum mencatatnya. */
    val createdAt: Long = 0L,
    /** Path file thumbnail pilihan pengguna (di penyimpanan aplikasi); null = pakai cover lagu. */
    val thumbnailPath: String? = null
)

data class LibraryState(
    val liked: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    /** Playlist tujuan cepat (bar di layar Now Playing); null = belum dipilih. */
    val quickPlaylistId: String? = null
)