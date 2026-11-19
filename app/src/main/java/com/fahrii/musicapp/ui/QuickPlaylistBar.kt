package com.fahrii.musicapp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tinggi bar (di luar inset navigation bar); dipakai layar lain untuk menyisakan ruang. */
val QuickPlaylistBarHeight = 60.dp

private val BarColor = Color(0xFF242424)

/**
 * Bar playlist tujuan di bagian bawah layar.
 *
 * - Ikon kiri: ceklis kalau lagu sudah ada di playlist, ikon tambah-ke-playlist kalau belum.
 *   Ketuk ikon untuk menambah/menghapus lagu dari playlist.
 * - Nama playlist: ketuk untuk mengganti playlist tujuan.
 */
@Composable
fun QuickPlaylistBar(
    playlistName: String?,
    inPlaylist: Boolean,
    onToggle: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(BarColor)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(QuickPlaylistBarHeight - 12.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = inPlaylist, label = "quickPlaylistIcon") { added ->
                    Icon(
                        imageVector = if (added) AppIcons.Check else AppIcons.PlaylistAdd,
                        contentDescription = if (added) "Hapus dari playlist" else "Tambah ke playlist",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onPick),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = playlistName ?: "Pilih playlist",
                    color = Color.White.copy(alpha = if (playlistName == null) 0.6f else 1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
