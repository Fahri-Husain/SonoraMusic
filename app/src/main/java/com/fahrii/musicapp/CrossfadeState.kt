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

    /** True hanya selama dua lagu benar-benar saling memudar. */
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    private val _isPreparing = MutableStateFlow(false)

    /**
     * True beberapa detik sebelum fade, saat UI masih menampilkan lagu lama dan transisi sudah
     * pasti terjadi. Hanya dipakai untuk memunculkan label "Crossfading" lebih awal di lagu lama;
     * warna pelangi bar progres tetap menunggu [isCrossfading].
     */
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _fadeDurationMs = MutableStateFlow(0L)

    /** Lama fade yang sedang/baru berjalan (ms). Dipakai UI supaya animasi latar selaras dengan audio. */
    val fadeDurationMs: StateFlow<Long> = _fadeDurationMs.asStateFlow()

    internal fun setCrossfading(value: Boolean) {
        _isCrossfading.value = value
        // Fade dimulai atau berakhir: fase persiapan pasti sudah selesai
        _isPreparing.value = false
    }

    internal fun setPreparing(value: Boolean) {
        _isPreparing.value = value
    }

    /** Dipanggil tepat sebelum lagu dipindah, supaya UI sudah tahu ini crossfade saat lagu berganti. */
    internal fun start(durationMs: Long) {
        _fadeDurationMs.value = durationMs
        _isCrossfading.value = true
        _isPreparing.value = false
    }
}