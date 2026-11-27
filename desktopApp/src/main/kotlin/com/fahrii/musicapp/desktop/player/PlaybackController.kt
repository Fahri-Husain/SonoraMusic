package com.fahrii.musicapp.desktop.player

import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.SettingsRepository
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.data.isAlbumEntry
import com.fahrii.musicapp.player.PlayerState
import com.fahrii.musicapp.player.QueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Antrean, auto-next, crossfade, EQ, dan normalisasi di atas dua [MpvAudioPlayer].
 * Dua pemutar dipakai bergantian supaya lagu berikutnya bisa dibunyikan sambil lagu lama memudar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackController(val settings: SettingsRepository) {

    private val playerA = MpvAudioPlayer()
    private val playerB = try {
        MpvAudioPlayer()
    } catch (e: Throwable) {
        playerA.release()
        throw e
    }

    // Satu thread logis: semua perubahan status antrean/fade berjalan berurutan.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _active = MutableStateFlow(playerA)
    private var standby: MpvAudioPlayer = playerB   // pemutar yang tidak aktif (bisa sedang memudar keluar)

    /** Status pemutar yang sedang aktif; otomatis pindah saat crossfade menukar pemutar. */
    val state: StateFlow<PlayerState> =
        _active.flatMapLatest { it.state }.stateIn(scope, SharingStarted.Eagerly, PlayerState())

    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    @Volatile private var fading = false
    private var transitionJob: Job? = null
    private var resolveJob: Job? = null

    private var prefetchJob: Job? = null
    private var prefetchIndex = -1
    @Volatile private var prefetched: Pair<Int, String>? = null

    init {
        // Auto-next (dipakai saat crossfade mati, atau crossfade gagal/tidak sempat jalan).
        scope.launch {
            state.map { it.ended }.distinctUntilChanged().filter { it }.collect {
                if (!fading) _queue.value.nextIndex(auto = true)?.let { playIndex(it) }
            }
        }

        // EQ + normalisasi: terapkan ke kedua pemutar tiap setelan berubah.
        scope.launch {
            var last = ""
            combine(settings.equalizer, settings.normalizeVolume) { eq, norm -> MpvFilters.build(eq, norm) }
                .distinctUntilChanged()
                .collect { chain ->
                    if (chain != last) {
                        playerA.setAudioFilter(chain)
                        playerB.setAudioFilter(chain)
                        last = chain
                    }
                }
        }

        // Pemantau crossfade.
        scope.launch {
            while (isActive) {
                delay(MONITOR_MS)
                runCatching { checkCrossfade() }
            }
        }
    }

    // ---------------------------------------------------------------- API untuk UI

    /** Ganti antrean dengan [songs] (entri album dibuang), lalu putar dari [startIndex]. */
    fun playList(songs: List<Song>, startIndex: Int) {
        val playable = songs.filterNot { it.isAlbumEntry() }
        if (playable.isEmpty()) return
        scope.launch {
            _queue.value = _queue.value.copy(songs = playable, index = startIndex.coerceIn(0, playable.lastIndex))
            invalidatePrefetch()
            playIndex(_queue.value.index)
        }
    }

    fun next() {
        scope.launch { _queue.value.nextIndex(auto = false)?.let { playIndex(it) } }
    }

    fun previous() {
        scope.launch { _queue.value.previousIndex()?.let { playIndex(it) } }
    }

    fun toggle() {
        scope.launch {
            val active = _active.value
            val st = active.state.value
            when {
                st.isPlaying -> {
                    active.pause()
                    if (fading) standby.pause()
                }
                st.durationMs > 0L -> {
                    active.resume()
                    if (fading) standby.resume()
                }
                // Tidak ada lagu yang termuat (habis Stop, antrean selesai, atau gagal dimuat):
                // putar ulang lagu saat ini, kecuali stream-nya sedang diambil.
                resolveJob?.isActive != true -> playIndex(_queue.value.index)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            abortTransition()
            _active.value.seekTo(positionMs)
        }
    }

    fun setVolume(percent: Int) {
        playerA.setVolume(percent)
        playerB.setVolume(percent)
    }

    fun stop() {
        scope.launch {
            abortTransition()
            invalidatePrefetch()
            resolveJob?.cancel()
            _message.value = null
            playerA.stop()
            playerB.stop()
        }
    }

    fun toggleRepeat() {
        _queue.value = _queue.value.let { it.copy(repeat = it.nextRepeat()) }
    }

    /** Siklus: mati -> tiap preset -> mati. */
    fun cycleEqualizer() {
        val presets = com.fahrii.musicapp.data.EqPreset.selectable
        val cur = settings.equalizer.value
        val idx = presets.indexOf(cur.preset)
        when {
            !cur.enabled -> {
                settings.setEqualizerPreset(presets.first())
                settings.setEqualizerEnabled(true)
            }
            idx < 0 || idx == presets.lastIndex -> settings.setEqualizerEnabled(false)
            else -> settings.setEqualizerPreset(presets[idx + 1])
        }
    }

    fun toggleNormalize() = settings.setNormalizeVolume(!settings.normalizeVolume.value)

    /** Siklus: mati -> 3 dtk -> 5 dtk -> 8 dtk -> 12 dtk -> mati. */
    fun cycleCrossfade() {
        val options = CROSSFADE_OPTIONS
        if (!settings.crossfadeEnabled.value) {
            settings.setCrossfadeSeconds(options.first())
            settings.setCrossfadeEnabled(true)
            return
        }
        val idx = options.indexOf(settings.crossfadeSeconds.value)
        if (idx < 0 || idx == options.lastIndex) settings.setCrossfadeEnabled(false)
        else settings.setCrossfadeSeconds(options[idx + 1])
    }

    fun close() {
        scope.cancel()
        playerA.release()
        playerB.release()
    }

    // ---------------------------------------------------------------- pemutaran biasa

    private fun playIndex(index: Int) {
        val song = _queue.value.songs.getOrNull(index) ?: return
        abortTransition()
        _queue.value = _queue.value.copy(index = index)
        val ready = prefetched?.takeIf { it.first == index }?.second
        invalidatePrefetch()
        resolveJob?.cancel()   // klik cepat: hanya lagu terakhir yang dipakai
        val quality = settings.streamQuality.value
        resolveJob = scope.launch {
            val url = ready ?: run {
                _message.value = "Mengambil stream…"
                runCatching {
                    withContext(Dispatchers.IO) { MusicRepository.resolvePlayableUrlBlocking(song.url, quality) }
                }.getOrElse {
                    if (it is CancellationException) throw it
                    _message.value = "Gagal mengambil stream: ${it.message}"
                    return@launch
                }
            }
            _message.value = null
            _active.value.play(url)
        }
    }

    // ---------------------------------------------------------------- crossfade

    private fun checkCrossfade() {
        if (fading || resolveJob?.isActive == true) return
        val q = _queue.value
        val nextIdx = q.nextIndex(auto = true) ?: return
        val st = _active.value.state.value
        if (!st.isPlaying || st.durationMs <= 0) return

        val fadeMs = minOf(settings.crossfadeMs(), st.durationMs / 2)
        if (fadeMs <= 0) return

        val remaining = st.durationMs - st.positionMs
        if (remaining > fadeMs + PREFETCH_AHEAD_MS) return

        if (prefetchIndex != nextIdx) startPrefetch(nextIdx)
        if (remaining <= fadeMs) {
            prefetched?.takeIf { it.first == nextIdx }?.let { startCrossfade(nextIdx, it.second, fadeMs) }
        }
    }

    private fun startPrefetch(index: Int) {
        val song = _queue.value.songs.getOrNull(index) ?: return
        prefetchJob?.cancel()
        prefetchIndex = index
        prefetched = null
        val quality = settings.streamQuality.value
        prefetchJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { MusicRepository.resolvePlayableUrlBlocking(song.url, quality) }
            }
                .onSuccess { prefetched = index to it }
                .onFailure { if (it is CancellationException) throw it }   // gagal: biarkan auto-next biasa
        }
    }

    private fun invalidatePrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetched = null
        prefetchIndex = -1
    }

    private fun startCrossfade(nextIdx: Int, url: String, fadeMs: Long) {
        val out = _active.value
        val inn = standby
        fading = true
        transitionJob = scope.launch {
            inn.setFadeGain(0f)
            inn.play(url)
            val started = withTimeoutOrNull(START_TIMEOUT_MS) {
                inn.state.first { it.isPlaying && it.positionMs > 0 }
            }
            if (started == null) {
                inn.stop()
                inn.setFadeGain(1f)
                fading = false
                // Lagu lama sudah habis duluan? Kalau begitu auto-next tadi terlewat, jadi putar manual.
                if (out.state.value.ended) playIndex(nextIdx)
                return@launch
            }

            val outState = out.state.value
            val rampMs = minOf(fadeMs, (outState.durationMs - outState.positionMs).coerceAtLeast(0L))
                .coerceAtLeast(MIN_RAMP_MS)

            // Mulai dari sini UI menampilkan lagu baru.
            _queue.value = _queue.value.copy(index = nextIdx)
            _active.value = inn
            standby = out
            invalidatePrefetch()

            val t0 = System.nanoTime()
            while (true) {
                val t = (((System.nanoTime() - t0) / 1_000_000.0) / rampMs).toFloat().coerceIn(0f, 1f)
                out.setFadeGain(cos(t * PI / 2).toFloat())   // equal-power: total daya konstan
                inn.setFadeGain(sin(t * PI / 2).toFloat())
                if (t >= 1f) break
                delay(RAMP_STEP_MS)
            }
            out.stop()
            out.setFadeGain(1f)
            inn.setFadeGain(1f)
            fading = false
        }
    }

    /** Batalkan crossfade yang sedang berjalan (kalau ada) dan kembalikan volume kedua pemutar. */
    private fun abortTransition() {
        transitionJob?.cancel()
        transitionJob = null
        if (fading) standby.stop()   // standby = yang memudar keluar, atau yang baru mulai dimuat
        fading = false
        playerA.setFadeGain(1f)
        playerB.setFadeGain(1f)
    }

    private companion object {
        val CROSSFADE_OPTIONS = listOf(3, 5, 8, 12)
        const val MONITOR_MS = 200L
        const val PREFETCH_AHEAD_MS = 20_000L
        const val START_TIMEOUT_MS = 4_000L
        const val MIN_RAMP_MS = 500L
        const val RAMP_STEP_MS = 40L
    }
}