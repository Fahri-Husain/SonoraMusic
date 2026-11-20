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

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}