package com.fahrii.musicapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
