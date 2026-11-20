package com.fahrii.musicapp.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.fahrii.musicapp.data.AudioQuality
import com.fahrii.musicapp.data.SettingsRepository
import kotlinx.coroutines.delay
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
    ),
    DOWNLOADS(
        dialogTitle = "Hapus semua unduhan?",
        dialogMessage = "Semua lagu yang sudah diunduh akan dihapus dari perangkat. " +
                "Lagu di Favorit dan playlist tetap ada dan bisa diputar lewat streaming.",
        doneMessage = "Semua unduhan dihapus"
    )
}

private const val GITHUB_URL = "https://github.com/Fahri-Husain/SonoraMusic"
private const val ISSUES_URL = "$GITHUB_URL/issues"

/** Pustaka pihak ketiga yang dipakai, untuk dialog Lisensi open source: nama, lisensi. */
private val OpenSourceLibraries = listOf(
    "Jetpack Compose, Navigation, dan Media3 (ExoPlayer)" to "Apache License 2.0",
    "OkHttp" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
    "Haze" to "Apache License 2.0",
    "NewPipe Extractor" to "GNU GPL v3.0",
    "Phosphor Icons (adamglin)" to "MIT License",
    "Backdrop (Kyant0)" to "Lihat repositorinya"
)

/** Pengaturan kualitas audio mana yang sedang dipilih lewat dialog. */
private enum class QualityTarget(val dialogTitle: String) {
    STREAM("Kualitas streaming"),
    DOWNLOAD("Kualitas unduhan")
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
    streamQuality: AudioQuality,
    downloadQuality: AudioQuality,
    skipSilence: Boolean,
    onStreamQualityChange: (AudioQuality) -> Unit,
    onDownloadQualityChange: (AudioQuality) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    pauseOnUnplug: Boolean,
    onPauseOnUnplugChange: (Boolean) -> Unit,
    sleepTimerEndAt: Long,
    onSleepTimerSet: (Int) -> Unit,
    downloadWifiOnly: Boolean,
    onDownloadWifiOnlyChange: (Boolean) -> Unit,
    downloadedCount: Int,
    downloadedBytes: Long,
    cacheBytes: Long,
    onClearCache: () -> Unit,
    onClearDownloads: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearPlayHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<ClearTarget?>(null) }
    var qualityDialog by remember { mutableStateOf<QualityTarget?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    // Sisa waktu timer tidur, diperbarui tiap 15 detik selama halaman terbuka
    val nowMs by produceState(System.currentTimeMillis(), sleepTimerEndAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val sleepRemainingMin =
        if (sleepTimerEndAt > 0L) ((sleepTimerEndAt - nowMs + 59_999L) / 60_000L).toInt() else 0

    // Status izin notifikasi; dibaca ulang setiap kembali dari pengaturan sistem
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    DisposableEffect(context) {
        val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>()
            .firstOrNull()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

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
            SettingsSection(title = "Kualitas Audio") {
                SettingsItem(
                    icon = AppIcons.Radio,
                    title = "Kualitas streaming",
                    subtitle = "${streamQuality.label} · ${streamQuality.description}",
                    onClick = { qualityDialog = QualityTarget.STREAM }
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.Download,
                    title = "Kualitas unduhan",
                    subtitle = "${downloadQuality.label} · ${downloadQuality.description}",
                    onClick = { qualityDialog = QualityTarget.DOWNLOAD }
                )
            }

            SettingsSection(title = "Pemutaran") {
                SettingsItem(
                    icon = AppIcons.Sync,
                    title = "Crossfade",
                    subtitle = "Lagu saling memudar saat berganti",
                    trailing = {
                        Switch(
                            checked = crossfadeEnabled,
                            onCheckedChange = onCrossfadeEnabledChange,
                            colors = sonoraSwitchColors()
                        )
                    }
                )
                if (crossfadeEnabled) {
                    CrossfadeDuration(seconds = crossfadeSeconds, onChange = onCrossfadeSecondsChange)
                }
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.SkipNext,
                    title = "Lewati bagian senyap",
                    subtitle = "Langsung loncat saat ada jeda hening di dalam lagu",
                    trailing = {
                        Switch(
                            checked = skipSilence,
                            onCheckedChange = onSkipSilenceChange,
                            colors = sonoraSwitchColors()
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.Pause,
                    title = "Jeda saat headset dicabut",
                    subtitle = "Musik berhenti otomatis saat headset dicabut atau Bluetooth terputus",
                    trailing = {
                        Switch(
                            checked = pauseOnUnplug,
                            onCheckedChange = onPauseOnUnplugChange,
                            colors = sonoraSwitchColors()
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.Refresh,
                    title = "Timer tidur",
                    subtitle = if (sleepRemainingMin > 0)
                        "Musik berhenti sekitar $sleepRemainingMin menit lagi"
                    else
                        "Mati · hentikan musik otomatis setelah waktu tertentu",
                    onClick = { showSleepDialog = true }
                )
                SettingsDivider()
                // Belum ada fungsinya: baris ini hanya menandai tempatnya di Pengaturan
                SettingsItem(
                    icon = AppIcons.Shuffle,
                    title = "AutoMix",
                    subtitle = "Segera hadir: transisi ala DJ yang menyesuaikan tempo lagu",
                    modifier = Modifier.alpha(0.5f),
                    trailing = {
                        Switch(
                            checked = false,
                            onCheckedChange = null,
                            enabled = false,
                            colors = sonoraSwitchColors()
                        )
                    }
                )
            }

            SettingsSection(title = "Unduhan dan Penyimpanan") {
                SettingsItem(
                    icon = AppIcons.Radio,
                    title = "Unduh hanya lewat Wi-Fi",
                    subtitle = "Cegah unduhan baru memakai data seluler",
                    trailing = {
                        Switch(
                            checked = downloadWifiOnly,
                            onCheckedChange = onDownloadWifiOnlyChange,
                            colors = sonoraSwitchColors()
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.Library,
                    title = "Lagu terunduh",
                    subtitle = if (downloadedCount == 0) "Belum ada lagu yang diunduh"
                    else "$downloadedCount lagu · ${Formatter.formatShortFileSize(context, downloadedBytes)}"
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.DeleteFilled,
                    title = "Hapus semua unduhan",
                    subtitle = "Kosongkan lagu yang tersimpan di perangkat",
                    onClick = {
                        if (downloadedCount > 0) confirm = ClearTarget.DOWNLOADS
                        else Toast.makeText(context, "Belum ada lagu yang diunduh", Toast.LENGTH_SHORT).show()
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.Trash,
                    title = "Hapus cache",
                    subtitle = "${Formatter.formatShortFileSize(context, cacheBytes)} · gambar sampul dan berkas sementara",
                    onClick = {
                        onClearCache()
                        Toast.makeText(context, "Cache dibersihkan", Toast.LENGTH_SHORT).show()
                    }
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

            SettingsSection(title = "Notifikasi") {
                SettingsItem(
                    icon = AppIcons.MusicNote,
                    title = "Notifikasi pemutar",
                    subtitle = if (notificationsEnabled)
                        "Kontrol musik tampil di bilah notifikasi dan layar kunci. Ketuk untuk mengatur"
                    else
                        "Dimatikan, jadi kontrol musik tidak tampil. Ketuk untuk mengaktifkan",
                    onClick = { openNotificationSettings(context) },
                    trailing = {
                        Text(
                            text = if (notificationsEnabled) "Aktif" else "Mati",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Sonora.TextSecondary
                        )
                    }
                )
            }

            SettingsSection(title = "Tentang") {
                SettingsItem(
                    icon = AppIcons.MusicNote,
                    title = "SonoraMusic",
                    subtitle = "Versi ${versionName ?: "-"}"
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.Share,
                    title = "Kode sumber",
                    subtitle = "github.com/Fahri-Husain/SonoraMusic",
                    onClick = { openUrl(context, GITHUB_URL) }
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.Edit,
                    title = "Laporkan masalah",
                    subtitle = "Buka halaman Issues di GitHub",
                    onClick = { openUrl(context, ISSUES_URL) }
                )
                SettingsDivider()
                SettingsItem(
                    icon = AppIcons.Queue,
                    title = "Lisensi open source",
                    subtitle = "Pustaka pihak ketiga yang dipakai aplikasi ini",
                    onClick = { showLicenses = true }
                )
            }

            // Supaya item terakhir tidak tertutup mini player dan navbar yang mengambang
            Spacer(Modifier.height(LocalBottomInset.current + 24.dp))
        }
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Timer tidur") },
            text = {
                Column {
                    Text(
                        text = "Musik berhenti otomatis setelah waktu yang dipilih.",
                        fontSize = 13.sp,
                        color = Sonora.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        Text(
                            text = "$minutes menit",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSleepTimerSet(minutes)
                                    showSleepDialog = false
                                    Toast.makeText(
                                        context,
                                        "Musik berhenti dalam $minutes menit",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row {
                    if (sleepRemainingMin > 0) {
                        TextButton(onClick = {
                            onSleepTimerSet(0)
                            showSleepDialog = false
                            Toast.makeText(context, "Timer tidur dimatikan", Toast.LENGTH_SHORT).show()
                        }) { Text("Matikan timer") }
                    }
                    TextButton(onClick = { showSleepDialog = false }) { Text("Tutup") }
                }
            }
        )
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Lisensi open source") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "SonoraMusic dibangun dengan pustaka sumber terbuka berikut. " +
                                "Teks lisensi lengkap ada di repositori masing-masing.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Sonora.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OpenSourceLibraries.forEach { (name, license) ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(text = name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(text = license, fontSize = 13.sp, color = Sonora.TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLicenses = false }) { Text("Tutup") } }
        )
    }

    qualityDialog?.let { target ->
        val current = if (target == QualityTarget.STREAM) streamQuality else downloadQuality
        AlertDialog(
            onDismissRequest = { qualityDialog = null },
            title = { Text(target.dialogTitle) },
            text = {
                Column {
                    AudioQuality.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (target == QualityTarget.STREAM) onStreamQualityChange(option)
                                    else onDownloadQualityChange(option)
                                    qualityDialog = null
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == current, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(text = option.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = option.description,
                                    fontSize = 13.sp,
                                    color = Sonora.TextSecondary
                                )
                            }
                        }
                    }
                    Text(
                        text = if (target == QualityTarget.STREAM)
                            "Berlaku mulai lagu berikutnya yang diputar."
                        else
                            "Hanya berlaku untuk unduhan baru; lagu yang sudah diunduh tidak berubah.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Sonora.TextSecondary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { qualityDialog = null }) { Text("Tutup") } }
        )
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
                        ClearTarget.DOWNLOADS -> onClearDownloads()
                    }
                    Toast.makeText(context, target.doneMessage, Toast.LENGTH_SHORT).show()
                    confirm = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } }
        )
    }
}

/** Buka halaman pengaturan notifikasi aplikasi ini di pengaturan sistem. */
private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "Tidak bisa membuka pengaturan notifikasi", Toast.LENGTH_SHORT).show()
    }
}

/** Buka tautan di browser. */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "Tidak ada aplikasi untuk membuka tautan", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Warna Switch: saat nonaktif, bulatan (thumb) putih supaya jelas terlihat di dalam pill;
 * saat aktif, pill putih dengan bulatan hitam (sesuai tema).
 */
@Composable
private fun sonoraSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.Black,
    checkedTrackColor = Color.White,
    checkedBorderColor = Color.White,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Sonora.Border,
    uncheckedBorderColor = Sonora.Border,
    disabledUncheckedThumbColor = Color.White,
    disabledUncheckedTrackColor = Sonora.Border,
    disabledUncheckedBorderColor = Sonora.Border
)

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