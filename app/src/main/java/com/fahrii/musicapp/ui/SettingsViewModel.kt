package com.fahrii.musicapp.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.fahrii.musicapp.data.AudioQuality
import com.fahrii.musicapp.data.BackupException
import com.fahrii.musicapp.data.BackupManager
import com.fahrii.musicapp.data.BackupOutcome
import com.fahrii.musicapp.data.DownloadRepository
import com.fahrii.musicapp.data.DownloadedSong
import com.fahrii.musicapp.data.EqPreset
import com.fahrii.musicapp.data.EqualizerState
import com.fahrii.musicapp.data.LyricsFontSize
import com.fahrii.musicapp.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository.getInstance(app)
    private val downloads = DownloadRepository.getInstance(app)
    private val context: Application = app

    val crossfadeEnabled: StateFlow<Boolean> = repo.crossfadeEnabled
    val crossfadeSeconds: StateFlow<Int> = repo.crossfadeSeconds
    val crossfadeDjMode: StateFlow<Boolean> = repo.crossfadeDjMode

    val streamQuality: StateFlow<AudioQuality> = repo.streamQuality
    val downloadQuality: StateFlow<AudioQuality> = repo.downloadQuality
    val skipSilence: StateFlow<Boolean> = repo.skipSilence
    val normalizeVolume: StateFlow<Boolean> = repo.normalizeVolume
    val downloadWifiOnly: StateFlow<Boolean> = repo.downloadWifiOnly
    val shakeToSkip: StateFlow<Boolean> = repo.shakeToSkip
    val albumTheme: StateFlow<Boolean> = repo.albumTheme
    val lyricsFontSize: StateFlow<LyricsFontSize> = repo.lyricsFontSize
    val lyricsBlur: StateFlow<Boolean> = repo.lyricsBlur
    val sleepTimerEndAt: StateFlow<Long> = repo.sleepTimerEndAt
    val equalizer: StateFlow<EqualizerState> = repo.equalizer

    /** Lagu yang sudah diunduh; jumlah dan ukurannya ditampilkan di bagian Unduhan dan Penyimpanan. */
    val downloaded: StateFlow<List<DownloadedSong>> = downloads.downloaded

    private val _cacheBytes = MutableStateFlow(0L)
    /** Ukuran cache aplikasi (gambar sampul dan berkas sementara), tidak termasuk lagu yang diunduh. */
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val backupManager = BackupManager(app)

    private val _backupBusy = MutableStateFlow(false)
    /** True selama ekspor/impor cadangan berjalan; dipakai untuk mencegah ketukan ganda. */
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _backupOutcome = MutableStateFlow<BackupOutcome?>(null)
    /** Hasil ekspor/impor terakhir, ditampilkan sebagai dialog sampai ditutup. */
    val backupOutcome: StateFlow<BackupOutcome?> = _backupOutcome.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun setStreamQuality(quality: AudioQuality) = repo.setStreamQuality(quality)

    fun setDownloadQuality(quality: AudioQuality) = repo.setDownloadQuality(quality)

    fun setSkipSilence(enabled: Boolean) = repo.setSkipSilence(enabled)

    fun setNormalizeVolume(enabled: Boolean) = repo.setNormalizeVolume(enabled)

    fun setEqualizerEnabled(enabled: Boolean) = repo.setEqualizerEnabled(enabled)

    fun setEqualizerPreset(preset: EqPreset) = repo.setEqualizerPreset(preset)

    fun setEqualizerBand(index: Int, db: Float) = repo.setEqualizerBand(index, db)

    fun setBassBoost(strength: Int) = repo.setBassBoost(strength)

    fun resetEqualizer() = repo.resetEqualizer()

    fun setDownloadWifiOnly(enabled: Boolean) = repo.setDownloadWifiOnly(enabled)

    fun setShakeToSkip(enabled: Boolean) = repo.setShakeToSkip(enabled)

    fun setAlbumTheme(enabled: Boolean) = repo.setAlbumTheme(enabled)

    fun setLyricsFontSize(size: LyricsFontSize) = repo.setLyricsFontSize(size)

    fun setLyricsBlur(enabled: Boolean) = repo.setLyricsBlur(enabled)

    fun setCrossfadeEnabled(enabled: Boolean) = repo.setCrossfadeEnabled(enabled)

    fun setCrossfadeSeconds(seconds: Int) = repo.setCrossfadeSeconds(seconds)

    fun setCrossfadeDjMode(enabled: Boolean) = repo.setCrossfadeDjMode(enabled)

    /** [minutes] <= 0 mematikan timer tidur. */
    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) repo.cancelSleepTimer() else repo.startSleepTimer(minutes)
    }

    fun removeAllDownloads() = downloads.removeAll()

    /** Simpan cadangan data ke [uri] (berkas JSON pilihan pengguna). */
    fun exportBackup(uri: Uri) = runBackup { BackupOutcome.Exported(backupManager.exportTo(uri)) }

    /** Gabungkan cadangan dari [uri]; data yang sudah ada dilewati, hanya yang baru yang masuk. */
    fun importBackup(uri: Uri) = runBackup { BackupOutcome.Imported(backupManager.importFrom(uri)) }

    fun dismissBackupOutcome() {
        _backupOutcome.value = null
    }

    private fun runBackup(block: () -> BackupOutcome) {
        if (_backupBusy.value) return
        _backupBusy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _backupOutcome.value = try {
                block()
            } catch (e: BackupException) {
                BackupOutcome.Failed(e.message ?: "Cadangan gagal diproses")
            } catch (e: Exception) {
                BackupOutcome.Failed("Terjadi kesalahan: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
            _backupBusy.value = false
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheBytes.value = dirSize(context.cacheDir)
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            // Cache gambar dibersihkan lewat Coil (bukan hapus folder) supaya indeksnya tetap konsisten
            val loader = context.imageLoader
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
            context.cacheDir.listFiles()?.forEach { child ->
                if (child.name != COIL_CACHE_DIR) child.deleteRecursively()
            }
            _cacheBytes.value = dirSize(context.cacheDir)
        }
    }

    private fun dirSize(dir: File): Long =
        runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    private companion object {
        /** Folder cache bawaan Coil di dalam cacheDir. */
        const val COIL_CACHE_DIR = "image_cache"
    }
}