package com.fahrii.musicapp.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fahrii.musicapp.R

/** URL semua lagu yang sudah diunduh. Disediakan di AppRoot, dibaca oleh [ArtistLine] di setiap baris lagu. */
val LocalDownloadedUrls = compositionLocalOf<Set<String>> { emptySet() }

/**
 * Baris nama artis di bawah judul lagu. Kalau lagu [songUrl] sudah diunduh, ikon unduhan tampil
 * di sebelah kiri nama artis; kalau belum, [content] dipakai apa adanya (tata letak tidak berubah).
 * [content] menerima modifier yang harus dipasang di Text nama artis.
 */
@Composable
fun ArtistLine(songUrl: String, content: @Composable (Modifier) -> Unit) {
    if (songUrl !in LocalDownloadedUrls.current) {
        content(Modifier)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_downloaded),
            contentDescription = "Terunduh",
            tint = Color.Unspecified, // ikonnya sudah putih
            // Gambarnya punya ruang kosong ±10% di tepi, jadi dibuat 17dp agar tampak seukuran 14dp
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(4.dp))
        content(Modifier.weight(1f))
    }
}