package com.fahrii.musicapp.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.Song

/**
 * Baris paling atas di menu lagu yang khas tiap layar, mis. "Hapus dari riwayat" atau
 * "Menghapus lagu ini dari daftar putar". Layar yang tidak punya aksi seperti ini cukup tidak mengirimnya.
 */
class SongMenuRemove(val label: String, val onRemove: () -> Unit)

internal class SongMenuTarget(val song: Song, val remove: SongMenuRemove?)

/**
 * Pengendali menu titik tiga di setiap lagu. Layar cukup memanggil [show] saat titik tiga diketuk;
 * bottom sheet-nya digambar satu kali oleh [SongMenuSheets] di AppRoot.
 */
@Stable
class SongMenuController {
    internal var target by mutableStateOf<SongMenuTarget?>(null)
        private set

    fun show(song: Song, remove: SongMenuRemove? = null) {
        target = SongMenuTarget(song, remove)
    }

    internal fun dismiss() {
        target = null
    }
}

val LocalSongMenu = staticCompositionLocalOf<SongMenuController> {
    error("SongMenuController belum disediakan. Pasang lewat CompositionLocalProvider di AppRoot.")
}

/**
 * Menggambar menu lagu (dan sheet "Tambahkan ke playlist") untuk lagu yang dibuka lewat [controller].
 * State perpustakaan dan nama album hanya dikumpulkan selama menu terbuka, jadi layar di belakangnya
 * tidak ikut recompose.
 */
@Composable
fun SongMenuSheets(
    controller: SongMenuController,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (ArtistAlbum) -> Unit
) {
    controller.target?.let { target ->
        SongMenuContent(
            target = target,
            onDismiss = controller::dismiss,
            libraryViewModel = libraryViewModel,
            playerViewModel = playerViewModel,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum
        )
    }
}

@Composable
private fun SongMenuContent(
    target: SongMenuTarget,
    onDismiss: () -> Unit,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (ArtistAlbum) -> Unit
) {
    val song = target.song
    val context = LocalContext.current
    val library by libraryViewModel.state.collectAsState()
    val songAlbums by libraryViewModel.songAlbums.collectAsState()
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    // Cari nama album lagu ini; hasilnya muncul sebagai baris album di menu
    LaunchedEffect(song) { libraryViewModel.requestSongAlbum(song) }

    SongOptionsSheet(
        song = song,
        liked = library.liked.any { it.url == song.url },
        albumName = songAlbums[song.url]?.trim()?.takeIf { it.isNotEmpty() },
        onDismiss = onDismiss,
        remove = target.remove,
        onToggleLike = { libraryViewModel.toggleLike(song) },
        onDownload = { toast("Fitur unduhan belum tersedia") },
        onAddToPlaylist = { addToPlaylistSong = song },
        onPlayNext = {
            playerViewModel.playNext(song)
            toast("Akan diputar selanjutnya")
        },
        onAddToQueue = {
            playerViewModel.addToQueue(listOf(song))
            toast("Ditambahkan ke daftar antri")
        },
        onOpenArtist = { onOpenArtist(song.artist) },
        onOpenAlbum = {
            val album = songAlbums[song.url]?.trim().orEmpty()
            if (album.isNotEmpty()) {
                onOpenAlbum(ArtistAlbum(title = album, artist = song.artist, coverUrl = null, url = ""))
            }
        },
        onPlayRadio = { toast("Fitur radio belum tersedia") },
        onShare = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}\n${song.url}")
            }
            context.startActivity(Intent.createChooser(send, "Bagikan"))
        }
    )

    addToPlaylistSong?.let { pending ->
        AddToPlaylistSheet(
            song = pending,
            playlists = library.playlists,
            onAdd = { libraryViewModel.addToPlaylist(it.id, pending) },
            onRemove = { libraryViewModel.removeFromPlaylist(it.id, pending.url) },
            onDismiss = { addToPlaylistSong = null }
        )
    }
}
