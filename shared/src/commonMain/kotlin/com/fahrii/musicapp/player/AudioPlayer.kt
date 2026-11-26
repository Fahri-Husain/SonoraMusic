package com.fahrii.musicapp.player

import kotlinx.coroutines.flow.StateFlow

/** Keadaan pemutar yang bisa diamati UI. Posisi dan durasi dalam milidetik. */
data class PlayerState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 100,
    /** True setelah lagu habis diputar sampai akhir. */
    val ended: Boolean = false,
    /** Pesan kalau lagu gagal dimuat; null kalau tidak ada masalah. */
    val error: String? = null
)

/** Antarmuka pemutar audio. Android nanti memakai Media3, desktop memakai libmpv. */
interface AudioPlayer {
    val state: StateFlow<PlayerState>

    /** Putar [url] (stream atau file lokal) dan langsung mulai. */
    fun play(url: String)
    fun pause()
    fun resume()
    fun toggle()
    fun seekTo(positionMs: Long)

    /** 0 sampai 100. */
    fun setVolume(percent: Int)
    fun stop()
    fun release()
}