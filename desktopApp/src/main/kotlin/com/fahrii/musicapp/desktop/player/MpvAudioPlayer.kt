package com.fahrii.musicapp.desktop.player

import com.fahrii.musicapp.player.AudioPlayer
import com.fahrii.musicapp.player.PlayerState
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Pemutar desktop berbasis libmpv (tanpa video, hanya audio). */
class MpvAudioPlayer : AudioPlayer {

    private val lib: MpvLib = MpvLoader.load()
    private val ctx: Pointer =
        lib.mpv_create() ?: throw IllegalStateException("mpv_create gagal: libmpv tidak bisa membuat instance")

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    @Volatile private var released = false
    @Volatile private var loaded = false      // sudah ada perintah play untuk lagu ini
    @Volatile private var started = false     // posisi sudah pernah terbaca (lagu benar-benar mulai)
    @Volatile private var loadedAtNs = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pollJob: Job

    init {
        try {
            option("vid", "no")                       // audio saja
            option("idle", "yes")                     // inti mpv tetap hidup setelah lagu habis
            option("ytdl", "no")                      // URL stream sudah dari NewPipe
            option("terminal", "no")
            option("osc", "no")
            option("input-default-bindings", "no")
            option("input-vo-keyboard", "no")
            option("audio-client-name", "Sonora Music")
            option("user-agent", USER_AGENT)          // sama dengan yang dipakai pengunduh

            val rc = lib.mpv_initialize(ctx)
            check(rc >= 0) { "mpv_initialize gagal: ${lib.mpv_error_string(rc)}" }
        } catch (e: Throwable) {
            lib.mpv_terminate_destroy(ctx)
            throw e
        }
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    override fun play(url: String) {
        if (released) return
        started = false
        loaded = true
        loadedAtNs = System.nanoTime()
        _state.update { it.copy(isPlaying = false, positionMs = 0L, durationMs = 0L, ended = false, error = null) }
        command("loadfile", url, "replace")
        setProperty("pause", "no")
    }

    override fun pause() = setProperty("pause", "yes")

    override fun resume() = setProperty("pause", "no")

    override fun toggle() {
        if (_state.value.isPlaying) pause() else resume()
    }

    override fun seekTo(positionMs: Long) {
        command("seek", (positionMs.coerceAtLeast(0L) / 1000.0).toString(), "absolute")
    }

    override fun setVolume(percent: Int) {
        val v = percent.coerceIn(0, 100)
        setProperty("volume", v.toString())
        _state.update { it.copy(volume = v) }
    }

    override fun stop() {
        loaded = false
        command("stop")
        _state.update { PlayerState(volume = it.volume) }
    }

    override fun release() {
        if (released) return
        released = true
        runBlocking { pollJob.cancelAndJoin() }   // pastikan tidak ada panggilan mpv yang masih jalan
        lib.mpv_terminate_destroy(ctx)
    }

    // ---------------------------------------------------------------------------------------

    private fun refresh() {
        if (released) return
        val idle = property("idle-active") == "yes"
        val paused = property("pause") == "yes"
        val pos = property("time-pos")?.toDoubleOrNull()
        val dur = property("duration")?.toDoubleOrNull()
        if (pos != null) started = true

        val sinceLoadMs = (System.nanoTime() - loadedAtNs) / 1_000_000L
        val ended = loaded && idle && started
        // Tepat setelah loadfile, idle-active bisa sempat "yes"; beri jeda sebelum dianggap gagal
        val failed = loaded && idle && !started && sinceLoadMs > LOAD_GRACE_MS

        _state.update {
            it.copy(
                isPlaying = loaded && !idle && !paused,
                positionMs = ((pos ?: 0.0) * 1000).toLong(),
                durationMs = ((dur ?: 0.0) * 1000).toLong(),
                ended = ended,
                error = if (failed) "Gagal memutar lagu (stream ditolak atau tidak bisa dibuka)" else null
            )
        }
    }

    private fun option(name: String, value: String) {
        val rc = lib.mpv_set_option_string(ctx, name, value)
        if (rc < 0) println("mpv: opsi $name gagal diatur: ${lib.mpv_error_string(rc)}")
    }

    private fun setProperty(name: String, value: String) {
        if (released) return
        val rc = lib.mpv_set_property_string(ctx, name, value)
        if (rc < 0) println("mpv: properti $name gagal diatur: ${lib.mpv_error_string(rc)}")
    }

    private fun command(vararg args: String) {
        if (released) return
        val full = arrayOfNulls<String>(args.size + 1)   // diakhiri null, sesuai API C
        args.forEachIndexed { i, a -> full[i] = a }
        val rc = lib.mpv_command(ctx, full)
        if (rc < 0) println("mpv: perintah ${args.firstOrNull()} gagal: ${lib.mpv_error_string(rc)}")
    }

    private fun property(name: String): String? {
        if (released) return null
        val p = lib.mpv_get_property_string(ctx, name) ?: return null
        return try {
            p.getString(0, "UTF-8")
        } finally {
            lib.mpv_free(p)
        }
    }

    private companion object {
        const val POLL_MS = 300L
        const val LOAD_GRACE_MS = 2_500L
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}