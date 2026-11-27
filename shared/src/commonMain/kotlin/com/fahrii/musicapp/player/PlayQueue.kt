package com.fahrii.musicapp.player

import com.fahrii.musicapp.data.Song

enum class RepeatMode { OFF, ALL, ONE }

/** Antrean lagu. Immutable: tiap perubahan menghasilkan salinan baru. */
data class QueueState(
    val songs: List<Song> = emptyList(),
    val index: Int = -1,
    val repeat: RepeatMode = RepeatMode.OFF
) {
    val current: Song? get() = songs.getOrNull(index)

    /**
     * Indeks lagu berikutnya, atau null kalau antrean habis.
     * [auto] = true saat lagu selesai sendiri (mode "ulang satu" mengulang lagu yang sama);
     * tombol Next manual tetap pindah ke lagu berikutnya.
     */
    fun nextIndex(auto: Boolean): Int? {
        if (songs.isEmpty() || index < 0) return null
        if (auto && repeat == RepeatMode.ONE) return index
        if (index + 1 < songs.size) return index + 1
        return if (repeat != RepeatMode.OFF) 0 else null
    }

    /** Indeks lagu sebelumnya; di lagu pertama, lagu diulang dari awal (atau lompat ke akhir kalau mode ulang-semua). */
    fun previousIndex(): Int? {
        if (songs.isEmpty() || index < 0) return null
        if (index > 0) return index - 1
        return if (repeat == RepeatMode.ALL) songs.lastIndex else 0
    }

    fun nextRepeat(): RepeatMode = when (repeat) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }
}