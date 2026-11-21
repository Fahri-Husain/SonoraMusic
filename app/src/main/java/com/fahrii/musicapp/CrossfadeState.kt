package com.fahrii.musicapp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status crossfade yang dibagikan dari [CrossfadeManager] (di PlaybackService) ke UI.
 *
 * [CrossfadeManager] hidup di service, sedangkan UI hidup di activity, tapi keduanya satu proses.
 * Jadi cukup satu penampung berstatus global, tanpa perlu bolak-balik lewat MediaController.
 */
object CrossfadeState {
    private val _isCrossfading = MutableStateFlow(false)

    /**
     * True hanya selama dua lagu benar-benar saling memudar (fase FADING). Fase sinkronisasi dan
     * serah-terima sebelumnya tidak terdengar sebagai transisi, jadi tidak dihitung.
     */
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    internal fun setCrossfading(value: Boolean) {
        _isCrossfading.value = value
    }
}
