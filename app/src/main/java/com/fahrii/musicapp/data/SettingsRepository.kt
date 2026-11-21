package com.fahrii.musicapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Pengaturan aplikasi (disimpan di SharedPreferences). Dipakai bersama oleh UI dan PlaybackService,
 * yang berjalan di proses yang sama, jadi perubahan dari halaman Pengaturan langsung terbaca pemutar.
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE_ENABLED, false))
    /** Crossfade menyala atau tidak. Bawaan: mati. */
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(
        prefs.getInt(KEY_CROSSFADE_SECONDS, DEFAULT_CROSSFADE_SECONDS)
            .coerceIn(MIN_CROSSFADE_SECONDS, MAX_CROSSFADE_SECONDS)
    )
    /** Lama crossfade (detik). Tetap tersimpan walau crossfade dimatikan. */
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _streamQuality = MutableStateFlow(readQuality(KEY_STREAM_QUALITY))
    /** Kualitas audio saat streaming. Berlaku mulai lagu yang diputar berikutnya. Bawaan: tinggi. */
    val streamQuality: StateFlow<AudioQuality> = _streamQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(readQuality(KEY_DOWNLOAD_QUALITY))
    /** Kualitas audio untuk lagu yang diunduh. Bawaan: tinggi. */
    val downloadQuality: StateFlow<AudioQuality> = _downloadQuality.asStateFlow()

    private val _skipSilence = MutableStateFlow(prefs.getBoolean(KEY_SKIP_SILENCE, false))
    /** Lewati bagian senyap di dalam lagu. Bawaan: mati. */
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _pauseOnUnplug = MutableStateFlow(prefs.getBoolean(KEY_PAUSE_ON_UNPLUG, true))
    /** Jeda otomatis saat headset dicabut atau Bluetooth terputus. Bawaan: nyala. */
    val pauseOnUnplug: StateFlow<Boolean> = _pauseOnUnplug.asStateFlow()

    private val _downloadWifiOnly = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, false))
    /** Unduhan baru hanya boleh jalan lewat Wi-Fi (bukan data seluler). Bawaan: mati. */
    val downloadWifiOnly: StateFlow<Boolean> = _downloadWifiOnly.asStateFlow()

    private val _shakeToSkip = MutableStateFlow(prefs.getBoolean(KEY_SHAKE_TO_SKIP, false))
    /** Goyang ponsel untuk lagu berikutnya (hanya saat aplikasi terbuka). Bawaan: mati. */
    val shakeToSkip: StateFlow<Boolean> = _shakeToSkip.asStateFlow()

    private val _doubleTapLike = MutableStateFlow(prefs.getBoolean(KEY_DOUBLE_TAP_LIKE, true))
    /** Ketuk dua kali pada cover di halaman Sedang Diputar untuk menyukai lagu. Bawaan: nyala. */
    val doubleTapLike: StateFlow<Boolean> = _doubleTapLike.asStateFlow()

    private val _lyricsFontSize = MutableStateFlow(
        runCatching { LyricsFontSize.valueOf(prefs.getString(KEY_LYRICS_FONT_SIZE, null) ?: "") }
            .getOrDefault(LyricsFontSize.MEDIUM)
    )
    /** Ukuran teks di layar lirik penuh. Bawaan: sedang (sama dengan tampilan sebelumnya). */
    val lyricsFontSize: StateFlow<LyricsFontSize> = _lyricsFontSize.asStateFlow()

    private val _lyricsBlur = MutableStateFlow(prefs.getBoolean(KEY_LYRICS_BLUR, true))
    /** Baris lirik yang jauh dari baris aktif diburamkan (gaya Apple Music). Bawaan: nyala. */
    val lyricsBlur: StateFlow<Boolean> = _lyricsBlur.asStateFlow()

    private val _sleepTimerEndAt = MutableStateFlow(0L)
    /**
     * Kapan timer tidur habis (epoch ms); 0 = timer mati. Sengaja tidak disimpan ke SharedPreferences:
     * timer hanya berlaku selama aplikasi hidup, dan PlaybackService yang menjeda pemutar saat habis.
     */
    val sleepTimerEndAt: StateFlow<Long> = _sleepTimerEndAt.asStateFlow()

    private fun readQuality(key: String): AudioQuality =
        runCatching { AudioQuality.valueOf(prefs.getString(key, null) ?: "") }
            .getOrDefault(AudioQuality.HIGH)

    fun setStreamQuality(quality: AudioQuality) {
        _streamQuality.value = quality
        prefs.edit().putString(KEY_STREAM_QUALITY, quality.name).apply()
    }

    fun setDownloadQuality(quality: AudioQuality) {
        _downloadQuality.value = quality
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality.name).apply()
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
    }

    fun setPauseOnUnplug(enabled: Boolean) {
        _pauseOnUnplug.value = enabled
        prefs.edit().putBoolean(KEY_PAUSE_ON_UNPLUG, enabled).apply()
    }

    fun setDownloadWifiOnly(enabled: Boolean) {
        _downloadWifiOnly.value = enabled
        prefs.edit().putBoolean(KEY_DOWNLOAD_WIFI_ONLY, enabled).apply()
    }

    fun setShakeToSkip(enabled: Boolean) {
        _shakeToSkip.value = enabled
        prefs.edit().putBoolean(KEY_SHAKE_TO_SKIP, enabled).apply()
    }

    fun setDoubleTapLike(enabled: Boolean) {
        _doubleTapLike.value = enabled
        prefs.edit().putBoolean(KEY_DOUBLE_TAP_LIKE, enabled).apply()
    }

    fun setLyricsFontSize(size: LyricsFontSize) {
        _lyricsFontSize.value = size
        prefs.edit().putString(KEY_LYRICS_FONT_SIZE, size.name).apply()
    }

    fun setLyricsBlur(enabled: Boolean) {
        _lyricsBlur.value = enabled
        prefs.edit().putBoolean(KEY_LYRICS_BLUR, enabled).apply()
    }

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

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, enabled).apply()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val value = seconds.coerceIn(MIN_CROSSFADE_SECONDS, MAX_CROSSFADE_SECONDS)
        _crossfadeSeconds.value = value
        prefs.edit().putInt(KEY_CROSSFADE_SECONDS, value).apply()
    }

    companion object {
        const val MIN_CROSSFADE_SECONDS = 1
        const val MAX_CROSSFADE_SECONDS = 12
        const val DEFAULT_CROSSFADE_SECONDS = 5

        private const val PREFS_NAME = "settings"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"
        private const val KEY_STREAM_QUALITY = "stream_quality"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_SKIP_SILENCE = "skip_silence"
        private const val KEY_PAUSE_ON_UNPLUG = "pause_on_unplug"
        private const val KEY_DOWNLOAD_WIFI_ONLY = "download_wifi_only"
        private const val KEY_SHAKE_TO_SKIP = "shake_to_skip"
        private const val KEY_DOUBLE_TAP_LIKE = "double_tap_like"
        private const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        private const val KEY_LYRICS_BLUR = "lyrics_blur"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}