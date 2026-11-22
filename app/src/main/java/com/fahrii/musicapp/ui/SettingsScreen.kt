package com.fahrii.musicapp.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.fahrii.musicapp.data.AudioQuality
import com.fahrii.musicapp.data.BackupOutcome
import com.fahrii.musicapp.data.ImportReport
import com.fahrii.musicapp.data.LyricsFontSize
import com.fahrii.musicapp.data.SettingsRepository
import kotlinx.coroutines.delay
import java.time.LocalDate
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
    crossfadeDjMode: Boolean,
    onCrossfadeDjModeChange: (Boolean) -> Unit,
    streamQuality: AudioQuality,
    downloadQuality: AudioQuality,
    skipSilence: Boolean,
    onStreamQualityChange: (AudioQuality) -> Unit,
    onDownloadQualityChange: (AudioQuality) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    shakeToSkip: Boolean,
    onShakeToSkipChange: (Boolean) -> Unit,
    lyricsFontSize: LyricsFontSize,
    onLyricsFontSizeChange: (LyricsFontSize) -> Unit,
    lyricsBlur: Boolean,
    onLyricsBlurChange: (Boolean) -> Unit,
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
    backupBusy: Boolean,
    backupOutcome: BackupOutcome?,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onDismissBackupOutcome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<ClearTarget?>(null) }
    var qualityDialog by remember { mutableStateOf<QualityTarget?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    // Pemilih berkas bawaan sistem: tidak butuh izin penyimpanan
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportBackup(uri) }

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
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            Text(
                text = "Setting",
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            CrossfadeHero(
                enabled = crossfadeEnabled,
                seconds = crossfadeSeconds,
                onEnabledChange = onCrossfadeEnabledChange,
                onSecondsChange = onCrossfadeSecondsChange
            )

            SettingsSection(title = "Pemutaran") {
                // Transisi ala DJ hanya ada selama Crossfade menyala: muncul (sudah aktif, lihat
                // SettingsRepository.setCrossfadeEnabled) dan bisa dimatikan sendiri, lalu hilang
                // dari baris begitu Crossfade dimatikan. Garis pemisahnya ikut di dalam blok ini
                // supaya tidak tertinggal saat barisnya tersembunyi.
                AnimatedVisibility(
                    visible = crossfadeEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        SettingsItem(
                            icon = SettingsIcons.Sliders,
                            title = "Transisi ala DJ",
                            subtitle = "Lagu lama diredam, lagu baru dibuka bertahap",
                            trailing = {
                                SonoraSwitch(
                                    checked = crossfadeDjMode,
                                    onCheckedChange = onCrossfadeDjModeChange,
                                    label = "Transisi ala DJ"
                                )
                            }
                        )
                        SettingsDivider()
                    }
                }
                SettingsItem(
                    icon = SettingsIcons.SkipSilence,
                    title = "Lewati bagian senyap",
                    trailing = {
                        SonoraSwitch(
                            checked = skipSilence,
                            onCheckedChange = onSkipSilenceChange,
                            label = "Lewati bagian senyap"
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Waveform,
                    title = "Kualitas streaming",
                    onClick = { qualityDialog = QualityTarget.STREAM },
                    trailing = { ValueWithChevron(streamQuality.label) }
                )
            }

            SettingsSection(title = "Kontrol") {
                SettingsItem(
                    icon = SettingsIcons.Phone,
                    title = "Goyang untuk ganti lagu",
                    subtitle = "Hanya saat aplikasi terbuka",
                    trailing = {
                        SonoraSwitch(
                            checked = shakeToSkip,
                            onCheckedChange = onShakeToSkipChange,
                            label = "Goyang untuk ganti lagu"
                        )
                    }
                )
            }

            SettingsSection(title = "Lirik") {
                SettingsItem(
                    icon = SettingsIcons.TextSize,
                    title = "Ukuran lirik",
                    trailing = {
                        SegmentedControl(
                            options = LyricsFontSize.entries,
                            selected = lyricsFontSize,
                            label = { it.label },
                            onSelect = onLyricsFontSizeChange
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Blur,
                    title = "Blur lirik",
                    subtitle = "Baris jauh dari baris aktif jadi buram (Android 12+)",
                    trailing = {
                        SonoraSwitch(
                            checked = lyricsBlur,
                            onCheckedChange = onLyricsBlurChange,
                            label = "Blur lirik"
                        )
                    }
                )
            }

            SettingsSection(title = "Lainnya") {
                SettingsItem(
                    icon = SettingsIcons.Moon,
                    title = "Timer tidur",
                    onClick = { showSleepDialog = true },
                    trailing = {
                        ValueWithChevron(if (sleepRemainingMin > 0) "$sleepRemainingMin mnt" else "Mati")
                    }
                )
                SettingsDivider()
                // Belum ada fungsinya: baris ini hanya menandai tempatnya di Setting
                SettingsItem(
                    icon = SettingsIcons.Pulse,
                    title = "AutoMix",
                    subtitle = "Transisi mengikuti tempo lagu",
                    trailing = { SoonBadge() }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Bell,
                    title = "Notifikasi pemutar",
                    subtitle = if (notificationsEnabled) null
                    else "Kontrol musik tidak tampil. Ketuk untuk mengaktifkan",
                    onClick = { openNotificationSettings(context) },
                    trailing = { ValueWithChevron(if (notificationsEnabled) "Aktif" else "Mati") }
                )
            }

            SettingsSection(title = "Unduhan dan penyimpanan") {
                SettingsItem(
                    icon = SettingsIcons.Waveform,
                    title = "Kualitas unduhan",
                    onClick = { qualityDialog = QualityTarget.DOWNLOAD },
                    trailing = { ValueWithChevron(downloadQuality.label) }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Wifi,
                    title = "Unduh hanya lewat Wi-Fi",
                    trailing = {
                        SonoraSwitch(
                            checked = downloadWifiOnly,
                            onCheckedChange = onDownloadWifiOnlyChange,
                            label = "Unduh hanya lewat Wi-Fi"
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Download,
                    title = "Lagu terunduh",
                    subtitle = if (downloadedCount == 0) null
                    else Formatter.formatShortFileSize(context, downloadedBytes),
                    trailing = {
                        ValueWithChevron(
                            value = if (downloadedCount == 0) "Belum ada" else "$downloadedCount lagu",
                            showChevron = false
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Eraser,
                    title = "Hapus cache",
                    onClick = {
                        onClearCache()
                        Toast.makeText(context, "Cache dibersihkan", Toast.LENGTH_SHORT).show()
                    },
                    trailing = {
                        ValueWithChevron(
                            value = Formatter.formatShortFileSize(context, cacheBytes),
                            showChevron = false
                        )
                    }
                )
            }

            SettingsSection(title = "Cadangan data") {
                SettingsItem(
                    icon = SettingsIcons.Upload,
                    title = "Ekspor cadangan",
                    subtitle = if (backupBusy) "Memproses…" else null,
                    onClick = {
                        if (!backupBusy) exportLauncher.launch("SonoraMusic-backup-${LocalDate.now()}.json")
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Download,
                    title = "Impor cadangan",
                    subtitle = if (backupBusy) "Memproses…" else null,
                    onClick = {
                        if (!backupBusy) importLauncher.launch(arrayOf("*/*"))
                    }
                )
            }

            SettingsSection(title = "Zona hati-hati") {
                SettingsItem(
                    icon = SettingsIcons.Trash,
                    title = "Hapus semua unduhan",
                    danger = true,
                    onClick = {
                        if (downloadedCount > 0) confirm = ClearTarget.DOWNLOADS
                        else Toast.makeText(context, "Belum ada lagu yang diunduh", Toast.LENGTH_SHORT).show()
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Search,
                    title = "Hapus riwayat pencarian",
                    danger = true,
                    onClick = { confirm = ClearTarget.SEARCH }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Clock,
                    title = "Hapus riwayat putar",
                    danger = true,
                    onClick = { confirm = ClearTarget.PLAY }
                )
            }

            SettingsSection(title = "Tentang") {
                SettingsItem(
                    icon = SettingsIcons.Note,
                    title = "SonoraMusic",
                    subtitle = "Versi ${versionName ?: "-"}"
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Share,
                    title = "Kode sumber",
                    subtitle = "github.com/Fahri-Husain/SonoraMusic",
                    onClick = { openUrl(context, GITHUB_URL) }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.Pencil,
                    title = "Laporkan masalah",
                    subtitle = "Buka halaman Issues di GitHub",
                    onClick = { openUrl(context, ISSUES_URL) }
                )
                SettingsDivider()
                SettingsItem(
                    icon = SettingsIcons.ListPlus,
                    title = "Lisensi open source",
                    subtitle = "Pustaka pihak ketiga yang dipakai aplikasi ini",
                    onClick = { showLicenses = true }
                )
            }

            // Supaya item terakhir tidak tertutup mini player dan navbar yang mengambang
            Spacer(Modifier.height(LocalBottomInset.current))
        }
    }

    backupOutcome?.let { outcome ->
        val (title, message) = backupDialogText(outcome)
        AlertDialog(
            onDismissRequest = onDismissBackupOutcome,
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = message, fontSize = 14.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = { TextButton(onClick = onDismissBackupOutcome) { Text("OK") } }
        )
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

/** Judul dan isi dialog hasil ekspor/impor cadangan. */
private fun backupDialogText(outcome: BackupOutcome): Pair<String, String> = when (outcome) {
    is BackupOutcome.Failed -> "Cadangan gagal" to outcome.message
    is BackupOutcome.Exported -> {
        val s = outcome.summary
        "Cadangan tersimpan" to ("Yang dicadangkan:\n" +
                "• ${s.liked} lagu favorit\n" +
                "• ${s.playlists} playlist\n" +
                "• ${s.historySongs} lagu di riwayat putar\n" +
                "• ${s.replayMonths} bulan statistik Replay\n\n" +
                "Lagu yang sudah diunduh tidak ikut dicadangkan.")
    }
    is BackupOutcome.Imported -> importDialogText(outcome.report)
}

/**
 * Ringkasan impor. Kalau semua isi berkas sudah ada di aplikasi, pengguna diberi tahu bahwa
 * tidak ada yang diimpor lagi; kalau sebagian, dipisah antara yang baru masuk dan yang dilewati.
 */
private fun importDialogText(r: ImportReport): Pair<String, String> {
    val existing = buildList {
        if (r.liked.existing > 0) add("${r.liked.existing} lagu favorit")
        if (r.playlists.existing > 0) add("${r.playlists.existing} playlist")
        if (r.playlistSongs.existing > 0) add("${r.playlistSongs.existing} lagu di dalam playlist")
        if (r.history.existing > 0) add("${r.history.existing} lagu di riwayat putar")
        if (r.replay.existing > 0) add("${r.replay.existing} data Replay")
    }
    val existingBlock = existing.joinToString("\n") { "• $it" }

    if (!r.hasNewData) {
        val body = if (existing.isEmpty()) {
            "Berkas ini tidak berisi data."
        } else {
            "Berkas ini tidak berisi data baru, jadi tidak ada yang diimpor lagi.\n\n" +
                    "Sudah ada di aplikasi:\n$existingBlock"
        }
        return "Semua data sudah ada" to body
    }

    val added = buildList {
        if (r.liked.added > 0) add("${r.liked.added} lagu favorit")
        if (r.playlists.added > 0) add("${r.playlists.added} playlist")
        if (r.playlistSongs.added > 0) add("${r.playlistSongs.added} lagu di dalam playlist")
        if (r.history.added > 0) add("${r.history.added} lagu di riwayat putar")
        if (r.replay.added > 0) add("${r.replay.added} data Replay")
        if (r.playlists.updated > 0) add("${r.playlists.updated} cover playlist")
        val stats = r.history.updated + r.replay.updated
        if (stats > 0) add("$stats angka statistik diperbarui (di cadangan lebih besar)")
    }
    val body = buildString {
        append("Ditambahkan:\n")
        append(added.joinToString("\n") { "• $it" })
        if (existing.isNotEmpty()) {
            append("\n\nDilewati karena sudah ada:\n")
            append(existingBlock)
        }
    }
    return "Impor selesai" to body
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

// ---- Warna dan komponen halaman Setting (mengikuti usulan tampilan Setting) ----

private val SectionLabel = Color(0xFF6E6E76)
private val Hairline = Color.White.copy(alpha = 0.06f)
private val ChipBackground = Color.White.copy(alpha = 0.05f)
private val ChipBorder = Color.White.copy(alpha = 0.07f)
private val ChipIcon = Color(0xFFD6D6DB)
private val Danger = Color(0xFFEE8A98)
private val ChevronColor = Color(0xFF55555C)
private val Ink = Color(0xFFF2F2F4)
private val ControlOffTrack = Color(0xFF2E2E33)
private val SliderTick = Color(0xFF5A5A62)
private val SegmentedBackground = Color(0xFF222226)

private val SliderThumbRadius = 10.dp

/**
 * Kartu paling atas: saklar Crossfade. Selama menyala, di bawahnya tampil durasi, slider, dan
 * gambaran singkat cara kerjanya; saat mati, semuanya menyusut dan hanya baris saklar yang tersisa.
 */
@Composable
private fun CrossfadeHero(
    enabled: Boolean,
    seconds: Int,
    onEnabledChange: (Boolean) -> Unit,
    onSecondsChange: (Int) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Sonora.Surface)
            .drawBehind {
                // Kilau tipis dari pojok kiri atas
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset.Zero,
                        radius = size.width * 0.66f
                    )
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Crossfade",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = "Lagu saling memudar saat berganti",
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = Sonora.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            SonoraSwitch(checked = enabled, onCheckedChange = onEnabledChange, label = "Crossfade")
        }

        AnimatedVisibility(
            visible = enabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 2.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Durasi", fontSize = 13.sp, color = Sonora.TextSecondary)
                    Text(text = "$seconds dtk", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                CrossfadeSlider(value = seconds, onValueChange = onSecondsChange)
                CrossfadeFlow(modifier = Modifier.padding(top = 18.dp))
            }
        }
    }
}

/** Dua pil bergradasi yang menggambarkan lagu lama memudar dan lagu baru muncul. */
@Composable
private fun CrossfadeFlow(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(44.dp)) {
        FlowPill(
            text = "Lagu lama memudar",
            fadesToEnd = true,
            modifier = Modifier.align(Alignment.TopStart)
        )
        FlowPill(
            text = "Lagu baru muncul",
            fadesToEnd = false,
            modifier = Modifier.align(Alignment.TopEnd).offset(y = 26.dp)
        )
    }
}

@Composable
private fun FlowPill(text: String, fadesToEnd: Boolean, modifier: Modifier = Modifier) {
    val glow = Color.White.copy(alpha = 0.22f)
    Box(
        modifier = modifier
            .fillMaxWidth(0.66f)
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    if (fadesToEnd) listOf(glow, Color.Transparent) else listOf(Color.Transparent, glow)
                )
            ),
        contentAlignment = if (fadesToEnd) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

/**
 * Slider durasi crossfade: garis tipis dengan satu titik per detik dan bulatan putih. Digambar
 * sendiri (bukan Slider Material3) supaya persis mengikuti usulan tampilan.
 */
@Composable
private fun CrossfadeSlider(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val min = SettingsRepository.MIN_CROSSFADE_SECONDS
    val max = SettingsRepository.MAX_CROSSFADE_SECONDS
    val currentOnChange by rememberUpdatedState(onValueChange)
    var widthPx by remember { mutableIntStateOf(0) }
    val thumbRadiusPx = with(LocalDensity.current) { SliderThumbRadius.toPx() }

    // Detik terdekat dari posisi sentuh; bulatan bergerak di antara x = r dan x = lebar - r
    fun valueAt(x: Float): Int {
        val usable = (widthPx - 2 * thumbRadiusPx).coerceAtLeast(1f)
        val fraction = ((x - thumbRadiusPx) / usable).coerceIn(0f, 1f)
        return (min + fraction * (max - min)).roundToInt()
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(min, max) {
                detectTapGestures { offset -> currentOnChange(valueAt(offset.x)) }
            }
            .pointerInput(min, max) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> currentOnChange(valueAt(offset.x)) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        currentOnChange(valueAt(change.position.x))
                    }
                )
            }
            .semantics {
                contentDescription = "Durasi crossfade"
                stateDescription = "$value detik"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = min.toFloat()..max.toFloat(),
                    steps = max - min - 1
                )
                setProgress { target ->
                    currentOnChange(target.roundToInt().coerceIn(min, max))
                    true
                }
            }
    ) {
        val centerY = size.height / 2f
        val radius = SliderThumbRadius.toPx()
        val usable = size.width - 2 * radius
        val fraction = (value - min).toFloat() / (max - min)
        val thumbX = radius + usable * fraction
        val trackHeight = 4.dp.toPx()
        val trackTop = centerY - trackHeight / 2f
        val corner = CornerRadius(trackHeight / 2f)

        drawRoundRect(
            color = ControlOffTrack,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = corner
        )
        drawRoundRect(
            color = Ink,
            topLeft = Offset(0f, trackTop),
            size = Size(thumbX, trackHeight),
            cornerRadius = corner
        )
        val count = max - min + 1
        for (i in 0 until count) {
            drawCircle(
                color = SliderTick,
                radius = 1.25.dp.toPx(),
                center = Offset(radius + usable * i / (count - 1), centerY)
            )
        }
        drawCircle(color = Ink, radius = radius, center = Offset(thumbX, centerY))
    }
}

/**
 * Saklar pil 46x28: mati = jalur abu gelap dengan bulatan putih, nyala = jalur putih dengan bulatan
 * gelap. Area ketuknya diperluas ke ukuran minimal sentuhan tanpa mengubah tampilan.
 */
@Composable
private fun SonoraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val track by animateColorAsState(if (checked) Ink else ControlOffTrack, tween(200), label = "switchTrack")
    val thumb by animateColorAsState(if (checked) Sonora.Background else Ink, tween(200), label = "switchThumb")
    val thumbX by animateDpAsState(if (checked) 21.dp else 3.dp, tween(200), label = "switchThumbX")

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = label }
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(track)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbX, y = 3.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(thumb)
            )
        }
    }
}

/** Pilihan satu dari beberapa, dalam satu pil (dipakai untuk Ukuran lirik). */
@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SegmentedBackground)
            .padding(2.dp)
            .selectableGroup()
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                if (isSelected) Ink else Color.Transparent,
                tween(150),
                label = "segmentBackground"
            )
            Text(
                text = label(option),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) Sonora.Background else Sonora.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** Nilai pengaturan di sisi kanan baris, dengan tanda panah kalau barisnya membuka pilihan. */
@Composable
private fun ValueWithChevron(value: String, showChevron: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = value, fontSize = 14.sp, color = Sonora.TextSecondary)
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = ChevronColor
            )
        }
    }
}

/** Lencana "Segera" untuk fitur yang belum tersedia. */
@Composable
private fun SoonBadge() {
    Text(
        text = "Segera",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        color = Color(0xFF9A9AA2),
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** Judul bagian (huruf kapital kecil renggang) + kartu yang membungkus item-itemnya. */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.3.sp,
            color = SectionLabel,
            modifier = Modifier.padding(start = 8.dp, bottom = 9.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Sonora.Surface)
                .border(1.dp, Hairline, shape),
            content = content
        )
    }
}

/**
 * Satu baris pengaturan. [onClick] null = tidak bisa diketuk (informasi, atau yang diketuk hanya
 * [trailing]-nya). [danger] mewarnai baris untuk tindakan menghapus.
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val chipShape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(chipShape)
                .background(if (danger) Danger.copy(alpha = 0.08f) else ChipBackground)
                .border(1.dp, if (danger) Danger.copy(alpha = 0.16f) else ChipBorder, chipShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (danger) Danger else ChipIcon
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.1).sp,
                color = if (danger) Danger else Color.Unspecified
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = Sonora.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Garis tipis antar item, dimulai sejajar teks (melewati kotak ikon). */
@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 64.dp)
            .height(1.dp)
            .background(Hairline)
    )
}