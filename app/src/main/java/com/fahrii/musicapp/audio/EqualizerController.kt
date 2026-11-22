package com.fahrii.musicapp.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.fahrii.musicapp.data.EqualizerBands
import com.fahrii.musicapp.data.EqualizerState
import com.fahrii.musicapp.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Memasang Equalizer dan Bass Boost bawaan Android ke pemutar, mengikuti [SettingsRepository.equalizer].
 *
 * Efek audio Android menempel ke *audio session*, bukan ke pemutar. Karena crossfade memakai pemutar
 * kedua (lihat [com.fahrii.musicapp.CrossfadeManager]), setiap pemutar harus didaftarkan lewat
 * [attach]; kalau tidak, lagu yang sedang memudar keluar akan terdengar tanpa equalizer dan suaranya
 * "loncat" di tengah transisi.
 *
 * Semua pemanggilan harus dari main thread (sama seperti ExoPlayer).
 */
@OptIn(UnstableApi::class)
class EqualizerController(
    settings: SettingsRepository,
    scope: CoroutineScope
) {
    /** Sepasang efek untuk satu audio session. Salah satunya bisa null kalau perangkat tidak mendukung. */
    private class Effects(val equalizer: Equalizer?, val bassBoost: BassBoost?) {
        fun release() {
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
        }
    }

    private class Binding(val listener: Player.Listener, var sessionId: Int, var effects: Effects?)

    private val bindings = HashMap<ExoPlayer, Binding>()
    private var state: EqualizerState = settings.equalizer.value
    private val collectJob: Job = scope.launch {
        settings.equalizer.collect { newState ->
            state = newState
            bindings.values.forEach { binding -> binding.effects?.let { apply(it, newState) } }
        }
    }

    /** Pasang efek ke [player] dan ikuti kalau audio session-nya berganti. Aman dipanggil dua kali. */
    fun attach(player: ExoPlayer) {
        if (bindings.containsKey(player)) return
        val binding = Binding(
            listener = object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    bind(player, audioSessionId)
                }
            },
            sessionId = 0,
            effects = null
        )
        bindings[player] = binding
        player.addListener(binding.listener)
        bind(player, player.audioSessionId)
    }

    /** Lepas efek milik [player]. Panggil *sebelum* player.release(). */
    fun detach(player: ExoPlayer) {
        val binding = bindings.remove(player) ?: return
        player.removeListener(binding.listener)
        binding.effects?.release()
        binding.effects = null
    }

    /** Lepas semua efek dan berhenti mengikuti pengaturan. */
    fun release() {
        collectJob.cancel()
        bindings.keys.toList().forEach { detach(it) }
    }

    private fun bind(player: ExoPlayer, sessionId: Int) {
        val binding = bindings[player] ?: return
        // 0 = "belum ada session". Jangan pernah memasang efek ke session 0: itu mixer global.
        if (sessionId <= 0) {
            binding.effects?.release()
            binding.effects = null
            binding.sessionId = 0
            return
        }
        if (sessionId == binding.sessionId && binding.effects != null) return

        binding.effects?.release()
        binding.sessionId = sessionId
        binding.effects = createEffects(sessionId)?.also { apply(it, state) }
    }

    private fun createEffects(sessionId: Int): Effects? {
        // Beberapa perangkat/ROM tidak punya atau menolak efek ini; jangan sampai memutus pemutaran
        val eq = runCatching { Equalizer(0, sessionId) }
            .onFailure { Log.w(TAG, "Equalizer tidak tersedia", it) }
            .getOrNull()
        val bass = runCatching { BassBoost(0, sessionId) }
            .onFailure { Log.w(TAG, "BassBoost tidak tersedia", it) }
            .getOrNull()
        return if (eq == null && bass == null) null else Effects(eq, bass)
    }

    private fun apply(effects: Effects, state: EqualizerState) {
        effects.equalizer?.let { eq ->
            runCatching {
                val range = eq.bandLevelRange // [min, max] dalam milibel (1 dB = 100 mB)
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                for (band in 0 until eq.numberOfBands) {
                    val hz = eq.getCenterFreq(band.toShort()) / 1000f // milliHertz -> Hz
                    val levelMb = (EqualizerBands.gainAt(state.gainsDb, hz) * 100f)
                        .roundToInt()
                        .coerceIn(minMb, maxMb)
                    eq.setBandLevel(band.toShort(), levelMb.toShort())
                }
                eq.enabled = state.enabled
            }.onFailure { Log.w(TAG, "Gagal mengatur Equalizer", it) }
        }
        effects.bassBoost?.let { bass ->
            runCatching {
                if (bass.strengthSupported) bass.setStrength(state.bassBoost.toShort())
                bass.enabled = state.enabled && state.bassBoost > 0
            }.onFailure { Log.w(TAG, "Gagal mengatur BassBoost", it) }
        }
    }

    private companion object {
        const val TAG = "EqualizerController"
    }
}
