package com.fahrii.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.Playlist
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.launch

private val SheetColor = Color(0xFF242424)
private val SheetDisabled = Color.White.copy(alpha = 0.38f)

/**
 * Kerangka bottom sheet menu. [content] menerima `dismissThen`: tutup sheet dengan animasi dulu,
 * baru jalankan aksinya (mis. buka dialog atau pindah halaman).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(dismissThen: (() -> Unit) -> Unit) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismissThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetColor,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            header?.invoke()
            content(dismissThen)
        }
    }
}

@Composable
private fun SheetItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconTint: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) iconTint else SheetDisabled,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(22.dp))
        Text(
            text = text,
            color = if (enabled) Color.White else SheetDisabled,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Menu titik tiga di pojok kanan atas halaman playlist. */
@Composable
fun PlaylistOptionsSheet(
    onDismiss: () -> Unit,
    onEditTitle: () -> Unit,
    onEditThumbnail: () -> Unit,
    onAddToQueue: () -> Unit,
    onSyncToYouTubeMusic: () -> Unit,
    onDeletePlaylist: () -> Unit
) {
    ActionSheet(onDismiss = onDismiss) { dismissThen ->
        SheetItem(AppIcons.Edit, "Edit judul", onClick = { dismissThen(onEditTitle) })
        SheetItem(AppIcons.EditImage, "Edit thumbnail", onClick = { dismissThen(onEditThumbnail) })
        SheetItem(AppIcons.Queue, "Tambahkan ke daftar antri", onClick = { dismissThen(onAddToQueue) })
        SheetItem(AppIcons.Sync, "Menyinkronkan ke YouTube Music", onClick = { dismissThen(onSyncToYouTubeMusic) })
        // Hanya aktif untuk playlist yang berasal dari YouTube Music
        SheetItem(AppIcons.Refresh, "Perbarui playlist dari YouTube Music", onClick = {}, enabled = false)
        SheetItem(AppIcons.DeleteFilled, "Hapus playlist", onClick = { dismissThen(onDeletePlaylist) })
        // Baru aktif setelah playlist tersinkron ke YouTube Music
        SheetItem(AppIcons.Share, "Sync this playlist to YouTube Music first", onClick = {}, enabled = false)
    }
}

/** Menu titik tiga di setiap lagu pada halaman playlist. */
@Composable
fun SongOptionsSheet(
    song: Song,
    liked: Boolean,
    albumName: String?,
    onDismiss: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onPlayRadio: () -> Unit,
    onShare: () -> Unit
) {
    ActionSheet(
        onDismiss = onDismiss,
        header = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.18f)
                )
            }
        }
    ) { dismissThen ->
        SheetItem(AppIcons.DeleteFilled, "Menghapus lagu ini dari daftar putar", onClick = { dismissThen(onRemoveFromPlaylist) })
        SheetItem(
            icon = if (liked) AppIcons.HeartFilled else AppIcons.Heart,
            text = "Suka",
            onClick = { dismissThen(onToggleLike) },
            iconTint = if (liked) HeartColor else Color.White
        )
        SheetItem(AppIcons.Download, "Unduh", onClick = { dismissThen(onDownload) })
        // Menu ini sengaja tidak ditutup: sheet playlist muncul di atasnya (seperti bar di bawah)
        SheetItem(AppIcons.PlaylistAdd, "Tambahkan ke playlist", onClick = onAddToPlaylist)
        SheetItem(AppIcons.PlayNext, "Putar selanjutnya", onClick = { dismissThen(onPlayNext) })
        SheetItem(AppIcons.Queue, "Tambahkan ke daftar antri", onClick = { dismissThen(onAddToQueue) })
        SheetItem(AppIcons.Artist, "Artis", onClick = { dismissThen(onOpenArtist) })
        // Nama album baru muncul setelah ketemu (dicari di latar belakang saat menu dibuka)
        if (albumName != null) {
            SheetItem(AppIcons.Album, albumName, onClick = { dismissThen(onOpenAlbum) })
        }
        SheetItem(AppIcons.Radio, "Putar radio", onClick = { dismissThen(onPlayRadio) })
        SheetItem(AppIcons.Share, "Bagikan", onClick = { dismissThen(onShare) })
    }
}

/**
 * Bottom sheet "Tambahkan ke playlist". Tiap playlist berikon ceklis kalau [song] sudah ada di
 * dalamnya, dan ikon tambah kalau belum. Ketuk untuk menambah / mengeluarkan lagu; sheet tetap
 * terbuka supaya ikonnya langsung berubah.
 */
@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    onAdd: (Playlist) -> Unit,
    onRemove: (Playlist) -> Unit,
    onDismiss: () -> Unit
) {
    ActionSheet(onDismiss = onDismiss) { _ ->
        playlists.forEach { playlist ->
            val added = playlist.songs.any { it.url == song.url }
            SheetItem(
                icon = if (added) AppIcons.Check else AppIcons.PlaylistAdd,
                text = playlist.name,
                onClick = { if (added) onRemove(playlist) else onAdd(playlist) }
            )
        }
    }
}