package com.fahrii.musicapp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pilihan kualitas audio untuk streaming dan unduhan. */
enum class AudioQuality(val label: String, val description: String) {
    LOW("Rendah", "Hemat kuota, sekitar 50 kbps"),
    NORMAL("Sedang", "Seimbang, sekitar 128 kbps"),
    HIGH("Tinggi", "Kualitas terbaik yang tersedia")
}

/** Ukuran teks lirik di layar lirik penuh. [textSp] dan [lineHeightSp] dalam satuan sp. */
enum class LyricsFontSize(val label: String, val textSp: Int, val lineHeightSp: Int) {
    SMALL("Kecil", 22, 28),
    MEDIUM("Sedang", 26, 32),
    LARGE("Besar", 30, 38)
}

/**
 * Pengaturan aplikasi. Dipakai bersama oleh UI dan pemutar, yang berjalan di proses yang sama,
 * jadi perubahan dari halaman Pengaturan langsung terbaca pemutar. Penyimpanan lewat [store]
 * (SharedPreferences di Android, file JSON di desktop).
 */
class SettingsRepository(private val store: KeyValueStore) {

    private val _crossfadeEnabled = MutableStateFlow(store.getBoolean(KEY_CROSSFADE_ENABLED, false))
    /** Crossfade menyala atau tidak. Bawaan: mati. */
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(
        store.getInt(KEY_CROSSFADE_SECONDS, DEFAULT_CROSSFADE_SECONDS)
            .coerceIn(MIN_CROSSFADE_SECONDS, MAX_CROSSFADE_SECONDS)
    )
    /** Lama crossfade (detik). Tetap tersimpan walau crossfade dimatikan. */
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _crossfadeDjMode = MutableStateFlow(store.getBoolean(KEY_CROSSFADE_DJ_MODE, true))
    /**
     * Transisi ala DJ: selama crossfade, lagu lama diredam (low-pass) dan lagu baru dibuka (high-pass).
     * Bawaan: nyala. Hanya berlaku kalau crossfade menyala, dan otomatis kembali nyala setiap kali
     * crossfade dinyalakan lagi (lihat [setCrossfadeEnabled]).
     */
    val crossfadeDjMode: StateFlow<Boolean> = _crossfadeDjMode.asStateFlow()

    private val _streamQuality = MutableStateFlow(readQuality(KEY_STREAM_QUALITY))
    /** Kualitas audio saat streaming. Berlaku mulai lagu yang diputar berikutnya. Bawaan: tinggi. */
    val streamQuality: StateFlow<AudioQuality> = _streamQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(readQuality(KEY_DOWNLOAD_QUALITY))
    /** Kualitas audio untuk lagu yang diunduh. Bawaan: tinggi. */
    val downloadQuality: StateFlow<AudioQuality> = _downloadQuality.asStateFlow()

    private val _skipSilence = MutableStateFlow(store.getBoolean(KEY_SKIP_SILENCE, false))
    /** Lewati bagian senyap di dalam lagu. Bawaan: mati. */
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(store.getBoolean(KEY_NORMALIZE_VOLUME, false))
    /** Samakan volume antar lagu (lagu keras diredam, lagu pelan dinaikkan sedikit). Bawaan: mati. */
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    private val _downloadWifiOnly = MutableStateFlow(store.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, false))
    /** Unduhan baru hanya boleh jalan lewat Wi-Fi (bukan data seluler). Bawaan: mati. */
    val downloadWifiOnly: StateFlow<Boolean> = _downloadWifiOnly.asStateFlow()

    private val _shakeToSkip = MutableStateFlow(store.getBoolean(KEY_SHAKE_TO_SKIP, false))
    /** Goyang ponsel untuk lagu berikutnya (hanya saat aplikasi terbuka). Bawaan: mati. */
    val shakeToSkip: StateFlow<Boolean> = _shakeToSkip.asStateFlow()

    private val _albumTheme = MutableStateFlow(store.getBoolean(KEY_ALBUM_THEME, true))
    /** Warna latar dan aksen app mengikuti sampul lagu yang sedang diputar. Bawaan: nyala. */
    val albumTheme: StateFlow<Boolean> = _albumTheme.asStateFlow()

    private val _lyricsFontSize = MutableStateFlow(
        runCatching { LyricsFontSize.valueOf(store.getString(KEY_LYRICS_FONT_SIZE) ?: "") }
            .getOrDefault(LyricsFontSize.MEDIUM)
    )
    /** Ukuran teks di layar lirik penuh. Bawaan: sedang. */
    val lyricsFontSize: StateFlow<LyricsFontSize> = _lyricsFontSize.asStateFlow()

    private val _lyricsBlur = MutableStateFlow(store.getBoolean(KEY_LYRICS_BLUR, true))
    /** Baris lirik yang jauh dari baris aktif diburamkan (gaya Apple Music). Bawaan: nyala. */
    val lyricsBlur: StateFlow<Boolean> = _lyricsBlur.asStateFlow()

    private val _equalizer = MutableStateFlow(readEqualizer())
    /**
     * Equalizer + bass boost. Dibaca pemutar dan langsung diterapkan, termasuk saat pita digeser
     * ketika lagu sedang diputar. Bawaan: mati.
     */
    val equalizer: StateFlow<EqualizerState> = _equalizer.asStateFlow()

    // Menggeser pita memicu puluhan perubahan per detik; tulis ke penyimpanan sekali setelah berhenti
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var persistEqualizerJob: Job? = null

    private val _sleepTimerEndAt = MutableStateFlow(0L)
    /**
     * Kapan timer tidur habis (epoch ms); 0 = timer mati. Sengaja tidak disimpan: timer hanya
     * berlaku selama aplikasi hidup, dan pemutar yang menjeda saat habis.
     */
    val sleepTimerEndAt: StateFlow<Long> = _sleepTimerEndAt.asStateFlow()

    private fun readEqualizer(): EqualizerState {
        val preset = runCatching { EqPreset.valueOf(store.getString(KEY_EQ_PRESET) ?: "") }
            .getOrDefault(EqPreset.FLAT)
        val saved = store.getString(KEY_EQ_GAINS)
            ?.split(',')
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == EqualizerBands.COUNT }
        val gains = (if (preset == EqPreset.CUSTOM) saved else preset.gainsDb)
            ?: EqPreset.FLAT.gainsDb
        return EqualizerState(
            enabled = store.getBoolean(KEY_EQ_ENABLED, false),
            preset = if (preset == EqPreset.CUSTOM && saved == null) EqPreset.FLAT else preset,
            gainsDb = gains.map { it.coerceIn(EqualizerBands.MIN_DB, EqualizerBands.MAX_DB) },
            bassBoost = store.getInt(KEY_EQ_BASS, 0).coerceIn(0, EqualizerState.MAX_BASS_BOOST)
        )
    }

    private fun writeEqualizer(state: EqualizerState) {
        store.putBoolean(KEY_EQ_ENABLED, state.enabled)
        store.putString(KEY_EQ_PRESET, state.preset.name)
        store.putString(KEY_EQ_GAINS, state.gainsDb.joinToString(","))
        store.putInt(KEY_EQ_BASS, state.bassBoost)
    }

    private fun updateEqualizer(transform: (EqualizerState) -> EqualizerState) {
        _equalizer.update(transform)
        persistEqualizerJob?.cancel()
        persistEqualizerJob = ioScope.launch {
            delay(EQ_PERSIST_DELAY_MS)
            writeEqualizer(_equalizer.value)
        }
    }

    private fun readQuality(key: String): AudioQuality =
        runCatching { AudioQuality.valueOf(store.getString(key) ?: "") }
            .getOrDefault(AudioQuality.HIGH)

    fun setStreamQuality(quality: AudioQuality) {
        _streamQuality.value = quality
        store.putString(KEY_STREAM_QUALITY, quality.name)
    }

    fun setDownloadQuality(quality: AudioQuality) {
        _downloadQuality.value = quality
        store.putString(KEY_DOWNLOAD_QUALITY, quality.name)
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        store.putBoolean(KEY_SKIP_SILENCE, enabled)
    }

    fun setNormalizeVolume(enabled: Boolean) {
        _normalizeVolume.value = enabled
        store.putBoolean(KEY_NORMALIZE_VOLUME, enabled)
    }

    fun setDownloadWifiOnly(enabled: Boolean) {
        _downloadWifiOnly.value = enabled
        store.putBoolean(KEY_DOWNLOAD_WIFI_ONLY, enabled)
    }

    fun setShakeToSkip(enabled: Boolean) {
        _shakeToSkip.value = enabled
        store.putBoolean(KEY_SHAKE_TO_SKIP, enabled)
    }

    fun setAlbumTheme(enabled: Boolean) {
        _albumTheme.value = enabled
        store.putBoolean(KEY_ALBUM_THEME, enabled)
    }

    fun setLyricsFontSize(size: LyricsFontSize) {
        _lyricsFontSize.value = size
        store.putString(KEY_LYRICS_FONT_SIZE, size.name)
    }

    fun setLyricsBlur(enabled: Boolean) {
        _lyricsBlur.value = enabled
        store.putBoolean(KEY_LYRICS_BLUR, enabled)
    }

    fun setEqualizerEnabled(enabled: Boolean) = updateEqualizer { it.copy(enabled = enabled) }

    /** Pilih preset; pita ikut berubah ke nilai preset. */
    fun setEqualizerPreset(preset: EqPreset) = updateEqualizer {
        if (preset == EqPreset.CUSTOM) it.copy(preset = preset)
        else it.copy(preset = preset, gainsDb = preset.gainsDb)
    }

    /** Geser satu pita ke [db]; preset otomatis menjadi Kustom. */
    fun setEqualizerBand(index: Int, db: Float) = updateEqualizer { state ->
        if (index !in 0 until EqualizerBands.COUNT) return@updateEqualizer state
        val gains = state.gainsDb.toMutableList()
        gains[index] = db.coerceIn(EqualizerBands.MIN_DB, EqualizerBands.MAX_DB)
        state.copy(preset = EqPreset.CUSTOM, gainsDb = gains)
    }

    fun setBassBoost(strength: Int) =
        updateEqualizer { it.copy(bassBoost = strength.coerceIn(0, EqualizerState.MAX_BASS_BOOST)) }

    /** Kembalikan semua pita ke Flat dan matikan bass boost (status nyala/mati tidak berubah). */
    fun resetEqualizer() =
        updateEqualizer { it.copy(preset = EqPreset.FLAT, gainsDb = EqPreset.FLAT.gainsDb, bassBoost = 0) }

    /** Nyalakan timer tidur [minutes] menit dari sekarang. */
    fun startSleepTimer(minutes: Int) {
        _sleepTimerEndAt.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun cancelSleepTimer() {
        _sleepTimerEndAt.value = 0L
    }

    /** Lama crossfade yang berlaku sekarang dalam milidetik; 0 kalau crossfade mati. */
    fun crossfadeMs(): Long =
        if (_crossfadeEnabled.value) _crossfadeSeconds.value * 1000L else 0L

    /**
     * Menyalakan atau mematikan crossfade. Setiap kali crossfade dinyalakan (dari mati), Transisi
     * ala DJ ikut diaktifkan otomatis; pengguna tetap bisa mematikannya sendiri sesudahnya.
     */
    fun setCrossfadeEnabled(enabled: Boolean) {
        if (enabled && !_crossfadeEnabled.value) setCrossfadeDjMode(true)
        _crossfadeEnabled.value = enabled
        store.putBoolean(KEY_CROSSFADE_ENABLED, enabled)
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val value = seconds.coerceIn(MIN_CROSSFADE_SECONDS, MAX_CROSSFADE_SECONDS)
        _crossfadeSeconds.value = value
        store.putInt(KEY_CROSSFADE_SECONDS, value)
    }

    /** Transisi ala DJ yang berlaku sekarang: nyala hanya kalau crossfade juga nyala. */
    fun crossfadeDjMode(): Boolean = _crossfadeEnabled.value && _crossfadeDjMode.value

    fun setCrossfadeDjMode(enabled: Boolean) {
        _crossfadeDjMode.value = enabled
        store.putBoolean(KEY_CROSSFADE_DJ_MODE, enabled)
    }

    companion object {
        const val MIN_CROSSFADE_SECONDS = 1
        const val MAX_CROSSFADE_SECONDS = 30
        const val DEFAULT_CROSSFADE_SECONDS = 5

        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"
        private const val KEY_CROSSFADE_DJ_MODE = "crossfade_dj_mode"
        private const val KEY_STREAM_QUALITY = "stream_quality"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_SKIP_SILENCE = "skip_silence"
        private const val KEY_NORMALIZE_VOLUME = "normalize_volume"
        private const val KEY_DOWNLOAD_WIFI_ONLY = "download_wifi_only"
        private const val KEY_SHAKE_TO_SKIP = "shake_to_skip"
        private const val KEY_ALBUM_THEME = "album_theme"
        private const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        private const val KEY_LYRICS_BLUR = "lyrics_blur"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val KEY_EQ_GAINS = "eq_gains"
        private const val KEY_EQ_BASS = "eq_bass_boost"
        private const val EQ_PERSIST_DELAY_MS = 300L
    }
}