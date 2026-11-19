package com.fahrii.musicapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahrii.musicapp.data.SettingsRepository
import kotlin.math.roundToInt

/** Data yang bisa dihapus dari halaman Pengaturan; menentukan teks dialog konfirmasinya. */
private enum class ClearTarget(
    val dialogTitle: String,
    val dialogMessage: String,
    val doneMessage: String
) {
    SEARCH(
        dialogTitle = "Hapus riwayat pencarian?",
        dialogMessage = "Semua pencarian terakhir di halaman Cari akan dihapus.",
        doneMessage = "Riwayat pencarian dihapus"
    ),
    PLAY(
        dialogTitle = "Hapus riwayat putar?",
        dialogMessage = "Daftar lagu di Riwayat dan Paling Sering Diputar akan dihapus. " +
                "Statistik Replay tidak ikut terhapus.",
        doneMessage = "Riwayat putar dihapus"
    )
}

/**
 * Halaman Pengaturan, dibuka dari roda gigi di header Beranda.
 * Isinya dikelompokkan per bagian lewat [SettingsSection] dan [SettingsItem], jadi menambah
 * pengaturan baru cukup menambah satu item di bagian yang sesuai.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    crossfadeEnabled: Boolean,
    crossfadeSeconds: Int,
    onCrossfadeEnabledChange: (Boolean) -> Unit,
    onCrossfadeSecondsChange: (Int) -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearPlayHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<ClearTarget?>(null) }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.BackArrow, contentDescription = "Kembali", modifier = Modifier.size(22.dp))
            }
            Text(
                text = "Pengaturan",
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SettingsSection(title = "Pemutaran") {
                SettingsItem(
                    icon = AppIcons.Sync,
                    title = "Crossfade",
                    subtitle = "Lagu saling memudar saat berganti",
                    trailing = {
                        Switch(checked = crossfadeEnabled, onCheckedChange = onCrossfadeEnabledChange)
                    }
                )
                if (crossfadeEnabled) {
                    CrossfadeDuration(seconds = crossfadeSeconds, onChange = onCrossfadeSecondsChange)
                }
                SettingsDivider()
                // Belum ada fungsinya: baris ini hanya menandai tempatnya di Pengaturan
                SettingsItem(
                    icon = AppIcons.Shuffle,
                    title = "AutoMix",
                    subtitle = "Segera hadir: transisi ala DJ yang menyesuaikan tempo lagu",
                    modifier = Modifier.alpha(0.5f),
                    trailing = { Switch(checked = false, onCheckedChange = null, enabled = false) }
                )
            }

            SettingsSection(title = "Riwayat") {
                SettingsItem(
                    icon = AppIcons.Search,
                    title = "Hapus riwayat pencarian",
                    subtitle = "Kosongkan pencarian terakhir di halaman Cari",
                    onClick = { confirm = ClearTarget.SEARCH }
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.History,
                    title = "Hapus riwayat putar",
                    subtitle = "Kosongkan daftar Riwayat dan Paling Sering Diputar",
                    onClick = { confirm = ClearTarget.PLAY }
                )
            }

            SettingsSection(title = "Tentang") {
                SettingsItem(
                    icon = AppIcons.MusicNote,
                    title = "SonoraMusic",
                    subtitle = "Versi ${versionName ?: "-"}"
                )
            }

            // Supaya item terakhir tidak tertutup mini player dan navbar yang mengambang
            Spacer(Modifier.height(LocalBottomInset.current + 24.dp))
        }
    }

    confirm?.let { target ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(target.dialogTitle) },
            text = { Text(target.dialogMessage) },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        ClearTarget.SEARCH -> onClearSearchHistory()
                        ClearTarget.PLAY -> onClearPlayHistory()
                    }
                    Toast.makeText(context, target.doneMessage, Toast.LENGTH_SHORT).show()
                    confirm = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } }
        )
    }
}

/** Judul bagian + kartu yang membungkus item-itemnya. */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Sonora.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Sonora.Surface),
        content = content
    )
}

/** Satu baris pengaturan. [onClick] null = hanya informasi (tidak bisa diketuk). */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Sonora.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Sonora.TextSecondary
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Slider durasi crossfade, tampil di bawah baris Crossfade kalau menyala. */
@Composable
private fun CrossfadeDuration(seconds: Int, onChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp, end = 16.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Durasi",
                fontSize = 13.sp,
                color = Sonora.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(text = "$seconds dtk", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = SettingsRepository.MIN_CROSSFADE_SECONDS.toFloat()..
                    SettingsRepository.MAX_CROSSFADE_SECONDS.toFloat(),
            steps = SettingsRepository.MAX_CROSSFADE_SECONDS - SettingsRepository.MIN_CROSSFADE_SECONDS - 1
        )
    }
}

/** Garis tipis antar item, dimulai sejajar teks (melewati kotak ikon). */
@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp)
            .height(1.dp)
            .background(Sonora.Border.copy(alpha = 0.5f))
    )
}