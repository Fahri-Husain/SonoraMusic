package com.fahrii.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.fahrii.musicapp.data.ArtistNames
import com.fahrii.musicapp.data.Song

/**
 * Nama artis lengkap [song] untuk ditampilkan. Langsung memakai nama yang sudah diketahui (atau
 * [Song.artist] dulu), lalu diganti begitu nama lengkap dari YouTube Music didapat. Karena hanya
 * dipanggil oleh baris yang sedang tampil, pencarian terjadi malas.
 */
@Composable
fun artistLabel(song: Song): String {
    val label by produceState(
        initialValue = ArtistNames.cached(song) ?: song.artist,
        key1 = song.url
    ) {
        value = ArtistNames.resolve(song)
    }
    return label
}
