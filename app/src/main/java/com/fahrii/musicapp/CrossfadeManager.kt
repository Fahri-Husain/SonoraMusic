package com.fahrii.musicapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.fahrii.musicapp.audio.BiquadFilter
import com.fahrii.musicapp.audio.CrossfadeFilterAudioProcessor
import com.fahrii.musicapp.audio.DjFilterRenderersFactory
import com.fahrii.musicapp.audio.EqualizerController
import com.fahrii.musicapp.audio.LoudnessNormalizerAudioProcessor
import com.fahrii.musicapp.data.SettingsRepository
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Crossfade antar lagu dengan gaya SimpMusic: kurva equal-power (cos/sin), transisi ala DJ
 * (filter low-pass untuk lagu lama, high-pass untuk lagu baru), dan lagu pendek dilewati.
 * Aturan dan kurvanya diadaptasi dari `CrossfadeExoPlayerAdapter` SimpMusic (maxrave-dev/core,
 * GPL-3.0). Mesin dua pemutar di bawah tetap milik SonoraMusic, karena antrean, sesi media, dan
 * notifikasi di sini memakai satu ExoPlayer biasa, bukan pemutar-per-lagu seperti SimpMusic.
 *
 * ExoPlayer tidak punya crossfade bawaan, jadi dipakai dua pemutar:
 *
 * 1. Beberapa detik sebelum lagu habis, pemutar kedua ([tail]) disiapkan diam-diam untuk lagu
 *    yang sama, berhenti sedikit sebelum titik transisi.
 * 2. SYNC: sesaat sebelum titik transisi, [tail] dijalankan dalam keadaan senyap sampai posisinya
 *    sejajar dengan pemutar utama. Ini menghilangkan jeda: pemutar kedua sudah "panas" sebelum
 *    pemutar utama dipindah ke lagu berikutnya.
 * 3. HANDOFF: suara lagu lama dipindah dari pemutar utama ke [tail] lewat ramp pendek (100 ms).
 *    Isinya sama, jadi tidak terdengar. Setelah itu pemutar utama senyap dan aman dipindah ke
 *    lagu berikutnya.
 * 4. FADING: [tail] meneruskan sisa lagu lama (volume turun), pemutar utama memutar lagu baru
 *    (volume naik). Kurvanya equal-power: lagu lama cos(sudut), lagu baru sin(sudut), jadi tenaga
 *    total rata sepanjang transisi. Kalau transisi ala DJ nyala, lagu lama lewat low-pass yang
 *    turun dari 20 kHz ke 200 Hz dan lagu baru lewat high-pass yang turun dari 2 kHz ke 20 Hz,
 *    mengikuti kurva sigmoid. Setelah selesai, [tail] dibuang dan filter dimatikan. Notifikasi,
 *    sesi media, dan antrean tetap hanya memakai pemutar utama.
 *
 * Kalau syarat tidak terpenuhi (crossfade mati, ulang satu lagu, tidak ada lagu berikutnya, lagu
 * lebih pendek dari 20 detik atau 3x lama crossfade, atau [tail] tidak siap/tidak sejajar pada
 * waktunya), tidak ada yang dilakukan dan lagu berganti seperti biasa. Semuanya berjalan di
 * thread utama.
 *
 * @param djFilter filter milik pemutar utama; harus sudah dipasang di rantai audio [player]
 *   lewat [DjFilterRenderersFactory].
 */
@OptIn(UnstableApi::class)
class CrossfadeManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val dataSourceFactory: DataSource.Factory,
    private val settings: SettingsRepository,
    private val djFilter: CrossfadeFilterAudioProcessor,
    // Equalizer pemutar utama; pemutar kedua didaftarkan juga supaya suaranya tidak loncat saat fade
    private val equalizer: EqualizerController? = null,
    // Penyama volume pemutar utama; gain lagu yang sedang diputar diwariskan ke pemutar kedua
    private val normalizer: LoudnessNormalizerAudioProcessor? = null,
    // Pengali volume dari fade Play/Pause (lihat FadingPlayer). Semua volume crossfade dikalikan ini,
    // supaya fade Play/Pause tidak ditimpa crossfade dan sebaliknya.
    private val userGain: () -> Float = { 1f }
) {
    private enum class Phase { IDLE, SYNC, HANDOFF, FADING }

    private val handler = Handler(Looper.getMainLooper())

    // Pemutar kedua: memutar sisa lagu lama selama lagu baru masuk
    private var tail: ExoPlayer? = null
    private var tailMediaId: String? = null

    // Filter di rantai audio pemutar kedua (low-pass untuk lagu yang keluar)
    private var tailFilter: CrossfadeFilterAudioProcessor? = null

    // Penyama volume di rantai audio pemutar kedua. Pemutar kedua memutar sisa lagu lama dari tengah,
    // jadi gainnya diwarisi dari pemutar utama dan dibekukan, bukan diukur ulang dari nol.
    private var tailNormalizer: LoudnessNormalizerAudioProcessor? = null

    // Transisi ala DJ dipakai untuk crossfade yang sedang disiapkan/berjalan. Dibaca sekali saat
    // sinkronisasi dimulai, jadi mengubah pengaturan di tengah transisi tidak memotongnya.
    private var djActive = false

    // Posisi pemutar utama (ms) saat pemutar kedua harus mulai dijalankan
    private var tailTriggerPosMs = 0L
    private var tailPlayStartPosMs = 0L

    // Kompensasi jeda start pemutar kedua. Dipelajari dari selisih yang terukur tiap lagu,
    // karena jeda ini beda-beda antar perangkat dan antar format stream.
    private var tailCompensationMs = TAIL_COMPENSATION_MS
    private var tailCompUsedMs = TAIL_COMPENSATION_MS

    // Selisih posisi (pemutar kedua dikurangi pemutar utama) selama SYNC, dihaluskan
    private var offsetAvgMs = 0f
    private var offsetSamples = 0
    private var resyncCount = 0
    // Jendela sampel selisih; yang dipakai adalah median-nya (kebal terhadap lonjakan sesaat
    // posisi stream) dan sampel diambil berjarak SYNC_SAMPLE_INTERVAL_MS supaya tidak saling berkorelasi
    private val offsetWindow = FloatArray(SYNC_MIN_SAMPLES)
    private var lastSampleAtMs = 0L

    // Lama crossfade yang berlaku untuk lagu ini
    private var currentFadeMs = 0L

    // Untuk log: pesan terakhir (supaya tidak dobel tiap tick) dan kapan pemutar kedua terakhir disiapkan
    private var lastNote = ""
    private var lastPrepareAtMs = 0L

    private var phase = Phase.IDLE
    private val fading: Boolean get() = phase == Phase.FADING
    private var phaseStartedAtMs = 0L

    private var fadeDurationMs = 0L

    // Posisi awal masing-masing pemutar saat fade mulai. Kemajuan fade dibaca dari posisi putar
    // yang sebenarnya (jam audio), bukan dari stopwatch, supaya selalu sinkron dengan suara.
    private var outgoingStartMs = 0L
    private var incomingStartMs = 0L
    private var fadeStartedAtMs = 0L

    // Kemajuan fade hanya boleh naik (posisi yang dilaporkan kadang melompat mundur sedikit)
    private var incomingProgress = 0f
    private var outgoingProgress = 0f

    // True selama kita sendiri memindahkan pemutar utama ke lagu berikutnya
    private var selfSeeking = false
    private var released = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) scheduleTick()
        }

        // Pemutar kedua ikut dijeda/dilanjutkan bersama pemutar utama
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (phase != Phase.IDLE) tail?.playWhenReady = playWhenReady
        }

        // Ganti lagu yang bukan dari kita (mis. pengguna menekan next): batalkan crossfade
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isOwnEvent()) interrupted()
        }

        // Pengguna menggeser posisi: crossfade yang sedang berjalan dibatalkan, yang tertunda disiapkan ulang
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (isOwnEvent()) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                interrupted()
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (released) return
            when (phase) {
                Phase.FADING -> stepFade()
                Phase.HANDOFF -> stepHandoff()
                Phase.SYNC -> stepSync()
                Phase.IDLE -> checkTrigger()
            }

            when {
                // Selama ada pemutar kedua yang siap atau crossfade berjalan, cek rapat supaya
                // titik mulainya tepat
                phase != Phase.IDLE || (tail != null && player.isPlaying) ->
                    handler.postDelayed(this, FAST_TICK_MS)
                player.isPlaying -> handler.postDelayed(this, POLL_MS)
                // Selain itu berhenti; dihidupkan lagi oleh onIsPlayingChanged(true)
            }
        }
    }

    init {
        player.addListener(listener)
        if (player.isPlaying) scheduleTick()
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        phase = Phase.IDLE
        // Service dilepas di tengah fade: jangan tinggalkan UI dengan label dan warna yang menyala terus
        CrossfadeState.setCrossfading(false)
        endDjFilters()
        releaseTail()
    }

    private fun scheduleTick() {
        if (released) return
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    /** Event yang datang dari pemindahan lagu oleh kita sendiri, bukan dari pengguna. */
    private fun isOwnEvent(): Boolean =
        selfSeeking || (fading && SystemClock.elapsedRealtime() - fadeStartedAtMs < OWN_EVENT_WINDOW_MS)

    /** Catat alasan keputusan ke logcat (tag Crossfade); pesan yang sama berturut-turut dicatat sekali. */
    private fun note(msg: String) {
        if (msg == lastNote) return
        lastNote = msg
        Log.d(TAG, msg)
    }

    private fun skipReason(item: MediaItem?, duration: Long): String = when {
        item == null -> "tidak ada lagu"
        duration == C.TIME_UNSET || duration <= 0L -> "durasi belum diketahui"
        player.isCurrentMediaItemLive -> "stream live"
        player.repeatMode == Player.REPEAT_MODE_ONE -> "ulang satu lagu"
        !player.hasNextMediaItem() -> "tidak ada lagu berikutnya"
        else -> "tidak diketahui"
    }

    private fun interrupted() {
        // Setelah seek/next, pemutar kedua baru boleh disiapkan lagi tanpa menunggu jeda retry;
        // penting kalau pengguna menggeser ke dekat akhir lagu (waktunya sudah mepet)
        lastPrepareAtMs = 0L
        when (phase) {
            Phase.FADING -> finishFade()
            Phase.SYNC, Phase.HANDOFF -> abortSync("dibatalkan pengguna (next/seek)")
            Phase.IDLE -> releaseTail()
        }
    }

    // ---- Persiapan dan pemicu ----

    private fun checkTrigger() {
        // Pengaman: volume dan filter DJ tidak boleh tertinggal kalau tidak sedang crossfade
        val idleVolume = userGain()
        if (player.volume != idleVolume) player.volume = idleVolume
        if (djFilter.enabled) djFilter.enabled = false

        val crossfadeMs = settings.crossfadeMs()
        val item = player.currentMediaItem
        val duration = player.duration

        val eligible = crossfadeMs > 0L &&
                item != null &&
                duration != C.TIME_UNSET && duration > 0L &&
                player.isPlaying &&
                !player.isCurrentMediaItemLive &&
                player.repeatMode != Player.REPEAT_MODE_ONE &&
                player.hasNextMediaItem()
        if (!eligible || item == null) {
            if (crossfadeMs > 0L && player.isPlaying) {
                note("[${item?.mediaId}] dilewati: ${skipReason(item, duration)}")
            }
            releaseTail()
            return
        }
        val id = item.mediaId

        // Lagu pendek (aturan SimpMusic): lagu harus cukup panjang supaya kedua sisi transisi masih
        // enak didengar. Pada fade 5 detik, lagu 20 detik sudah separuhnya habis untuk fade, dan
        // fade yang lebih panjang akan menelan lagu itu seluruhnya; makanya batasnya ikut lama fade.
        if (duration < maxOf(MIN_CROSSFADE_TRACK_MS, crossfadeMs * 3)) {
            note("[$id] dilewati: lagu terlalu pendek")
            releaseTail()
            return
        }
        val fadeMs = crossfadeMs
        currentFadeMs = fadeMs

        if (tail != null && tailMediaId != item.mediaId) releaseTail()

        val remaining = duration - player.currentPosition
        val syncAtRemaining = fadeMs + SYNC_LEAD_MS
        when {
            // Masih jauh dari ujung lagu
            remaining > syncAtRemaining + PREPARE_AHEAD_MS -> releaseTail()

            // Sudah dekat: siapkan pemutar kedua supaya siap saat waktunya tiba. Kalau pemutar
            // kedua gagal (mis. error jaringan), coba lagi dengan jeda, selama masih ada waktu.
            remaining > syncAtRemaining -> {
                tail?.playerError?.let {
                    note("[$id] pemutar kedua error: ${it.errorCodeName}, akan dicoba ulang")
                    releaseTail()
                }
                if (tail == null &&
                    SystemClock.elapsedRealtime() - lastPrepareAtMs > RETRY_PREPARE_MS
                ) {
                    note("[$id] menyiapkan pemutar kedua (sisa ${remaining / 1000}s)")
                    prepareTail(item, duration - syncAtRemaining)
                }
            }

            // Terlalu dekat ke ujung lagu: tidak jadi crossfade
            remaining <= MIN_START_REMAINING_MS -> {
                note("[$id] dilewati: pemutar kedua belum siap sampai lagu hampir habis")
                releaseTail()
            }

            // Waktunya sinkronisasi, tapi hanya kalau pemutar kedua sudah siap; kalau tidak,
            // biarkan lagu berganti seperti biasa
            else -> {
                val t = tail ?: run {
                    // Biasanya terjadi setelah seek ke dekat akhir lagu: jendela persiapan normal
                    // sudah lewat. Selama sisa lagu masih cukup, siapkan terlambat dengan titik
                    // mulai yang diberi ruang untuk buffering stream.
                    if (remaining >= LATE_PREPARE_MIN_REMAINING_MS &&
                        SystemClock.elapsedRealtime() - lastPrepareAtMs > LATE_PREPARE_RETRY_MS
                    ) {
                        note("[$id] menyiapkan pemutar kedua terlambat (sisa ${remaining}ms)")
                        prepareTail(item, player.currentPosition + LATE_PREPARE_AHEAD_MS)
                    } else {
                        note("[$id] dilewati: pemutar kedua tidak ada saat waktunya tiba")
                    }
                    return
                }
                if (t.playbackState != Player.STATE_READY) {
                    note("[$id] menunggu pemutar kedua siap (state=${t.playbackState})")
                    return
                }
                val lateBy = player.currentPosition - tailTriggerPosMs
                when {
                    lateBy < 0L -> Unit // belum sampai titiknya
                    lateBy > MAX_SYNC_LATE_MS -> reseekTail(t) // siap terlambat: susul ke posisi sekarang
                    else -> startSync(t)
                }
            }
        }
    }

    /** [triggerPosMs]: posisi pemutar utama saat pemutar kedua harus mulai dijalankan. */
    private fun prepareTail(item: MediaItem, triggerPosMs: Long) {
        tailCompUsedMs = tailCompensationMs
        val startMs = triggerPosMs + tailCompUsedMs.toLong()
        val filter = CrossfadeFilterAudioProcessor()
        val tailNorm = LoudnessNormalizerAudioProcessor()
        inheritNormalizer(tailNorm)
        val p = ExoPlayer.Builder(context, DjFilterRenderersFactory(context, filter, tailNorm))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false // fokus audio milik pemutar utama
            )
            .build()
        p.volume = 0f
        equalizer?.attach(p)
        p.setMediaItem(item, startMs)
        p.playWhenReady = false
        p.prepare()
        tail = p
        tailFilter = filter
        tailNormalizer = tailNorm
        tailMediaId = item.mediaId
        lastPrepareAtMs = SystemClock.elapsedRealtime()
        tailTriggerPosMs = triggerPosMs
    }

    /**
     * Samakan penyama volume pemutar kedua dengan pemutar utama: aktif atau tidaknya, dan gain lagu yang
     * sedang diputar, lalu bekukan. Dipanggil saat pemutar kedua dibuat (supaya audio yang sudah
     * ter-buffer lebih dulu pun memakai gain yang benar) dan lagi saat sinkronisasi dimulai, karena
     * gain pemutar utama bisa bergeser sedikit di antaranya.
     */
    private fun inheritNormalizer(target: LoudnessNormalizerAudioProcessor) {
        val main = normalizer ?: return
        target.enabled = main.enabled
        target.seedGainDb(main.currentGainDb)
        target.frozen = true
    }

    /** Pemutar kedua siap terlambat: pindahkan ke sedikit di depan posisi sekarang dan tunggu lagi. */
    private fun reseekTail(t: ExoPlayer) {
        tailTriggerPosMs = player.currentPosition + RESEEK_AHEAD_MS
        t.seekTo(tailTriggerPosMs + tailCompUsedMs.toLong())
    }

    // ---- SYNC: pemutar kedua bersuara senyap, menyusul pemutar utama ----

    private fun startSync(t: ExoPlayer) {
        t.volume = 0f
        tailNormalizer?.let { inheritNormalizer(it) }
        // Low-pass lagu lama dinyalakan sekarang, selagi pemutar kedua masih senyap, di cutoff
        // terbuka lebar (20 kHz). Dengan begitu riwayat filternya sudah terisi saat suaranya
        // diambil alih; kalau baru dinyalakan di tengah lagu yang sedang bersuara keras, awal
        // filter dari keadaan kosong bisa terdengar sebagai "tik".
        djActive = settings.crossfadeDjMode()
        if (djActive) {
            tailFilter?.apply {
                filterType = BiquadFilter.FilterType.LOW_PASS
                cutoffFrequencyHz = LPF_START_HZ
                enabled = true
            }
        }
        tailPlayStartPosMs = t.currentPosition
        offsetAvgMs = 0f
        offsetSamples = 0
        lastSampleAtMs = 0L
        resyncCount = 0
        t.playWhenReady = player.playWhenReady
        phase = Phase.SYNC
        phaseStartedAtMs = SystemClock.elapsedRealtime()
        // Label "Crossfading" sudah muncul di lagu lama; dipadamkan lagi oleh abortSync atau fade mulai
        CrossfadeState.setPreparing(true)
    }

    private fun stepSync() {
        val t = tail
        val item = player.currentMediaItem
        if (t == null || item == null || item.mediaId != tailMediaId ||
            !player.hasNextMediaItem() || player.repeatMode == Player.REPEAT_MODE_ONE
        ) {
            abortSync("antrean/mode ulang berubah saat sinkronisasi")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - phaseStartedAtMs > SYNC_TIMEOUT_MS) {
            abortSync(
                "sinkronisasi kehabisan waktu (tailPlaying=${t.isPlaying}, " +
                        "jalan=${t.currentPosition - tailPlayStartPosMs}ms, sampel=$offsetSamples)"
            )
            return
        }

        // Tunggu sampai pemutar kedua benar-benar bersuara dan posisinya stabil
        if (!t.isPlaying || t.currentPosition - tailPlayStartPosMs < SYNC_MIN_RUN_MS) return

        // Selisih posisi dua salinan lagu lama (positif = pemutar kedua lebih maju). Kalau
        // melenceng lebih dari puluhan ms, sebagian lagu terdengar dua kali (gema/dobel).
        if (now - lastSampleAtMs < SYNC_SAMPLE_INTERVAL_MS) return
        lastSampleAtMs = now
        val offset = (t.currentPosition - player.currentPosition).toFloat()
        offsetWindow[offsetSamples % SYNC_MIN_SAMPLES] = offset
        offsetSamples++
        if (offsetSamples < SYNC_MIN_SAMPLES) return
        offsetAvgMs = offsetWindow.sortedArray()[SYNC_MIN_SAMPLES / 2]

        // Jeda start pemutar kedua yang sebenarnya = kompensasi yang dipakai - selisih terukur
        val measuredDelayMs = tailCompUsedMs - offsetAvgMs
        learnCompensation(measuredDelayMs)

        // Setelah semua geser ulang terpakai, toleransi sedikit dilonggarkan: selisih sekecil ini
        // hanya berlangsung selama ramp handoff 100 ms dan tidak terdengar sebagai gema
        val tolerance = if (resyncCount >= MAX_RESYNC) FINAL_ALIGN_TOLERANCE_MS else ALIGN_TOLERANCE_MS
        when {
            abs(offsetAvgMs) <= tolerance -> {
                Log.d(TAG, "[${item.mediaId}] sinkron: selisih=${offsetAvgMs.toInt()}ms, kompensasi=${tailCompensationMs.toInt()}ms")
                phase = Phase.HANDOFF
                phaseStartedAtMs = now
            }
            // Belum sejajar: geser pemutar kedua sekali lagi memakai jeda yang baru terukur
            resyncCount < MAX_RESYNC && abs(offsetAvgMs) <= RESYNC_MAX_OFFSET_MS -> {
                resyncCount++
                Log.d(TAG, "[${item.mediaId}] geser ulang #$resyncCount: selisih=${offsetAvgMs.toInt()}ms")
                // Koreksi teredam (bukan penuh): selisih terukur sebagian adalah jitter stream, jadi
                // mengejarnya 100% membuat posisi melampaui target lalu berayun ke arah sebaliknya.
                // Putaran berikutnya mengukur terhadap kompensasi yang baru dipakai ini.
                tailCompUsedMs = (tailCompUsedMs - offsetAvgMs * RESYNC_GAIN)
                    .coerceIn(0f, MAX_COMPENSATION_MS)
                val target = player.currentPosition + tailCompUsedMs.toLong()
                t.seekTo(target)
                tailPlayStartPosMs = target
                offsetSamples = 0
                lastSampleAtMs = 0L
            }
            // Tetap tidak sejajar: lebih baik berganti lagu biasa daripada terdengar dobel
            else -> abortSync("selisih posisi terlalu besar (${offsetAvgMs.toInt()}ms)")
        }
    }

    private fun learnCompensation(measuredDelayMs: Float) {
        tailCompensationMs = (tailCompensationMs * 0.5f + measuredDelayMs * 0.5f)
            .coerceIn(0f, MAX_COMPENSATION_MS)
    }

    // ---- HANDOFF: suara lagu lama pindah dari pemutar utama ke pemutar kedua ----

    private fun stepHandoff() {
        val t = tail
        if (t == null) {
            abortSync("pemutar kedua hilang saat handoff")
            return
        }
        // Isi kedua pemutar sama, jadi ramp linier menjaga level tetap rata
        val x = ((SystemClock.elapsedRealtime() - phaseStartedAtMs).toFloat() / HANDOFF_MS)
            .coerceIn(0f, 1f)
        val gain = userGain()
        player.volume = (1f - x) * gain
        t.volume = x * gain
        if (x >= 1f) beginFade(t)
    }

    private fun abortSync(reason: String) {
        Log.d(TAG, "crossfade dibatalkan, lagu pindah biasa: $reason")
        phase = Phase.IDLE
        CrossfadeState.setCrossfading(false)
        player.volume = userGain()
        endDjFilters()
        releaseTail()
    }

    /**
     * Dipanggil saat fade Play/Pause mengubah gain. Di luar crossfade, pemutar utama satu-satunya
     * sumber suara, jadi volumenya langsung diikutkan. Selama crossfade, langkah fade (tiap 16 ms)
     * yang menerapkannya bersama kurva crossfade.
     */
    fun applyUserGain(gain: Float) {
        if (!released && phase == Phase.IDLE) player.volume = gain
    }

    // ---- Fade ----

    private fun beginFade(t: ExoPlayer) {
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) {
            abortSync("durasi hilang saat fade akan mulai")
            return
        }
        val now = SystemClock.elapsedRealtime()
        val outgoingPos = t.currentPosition
        val remaining = (duration - outgoingPos).coerceAtLeast(MIN_REMAINING_MS)

        // Pemutar kedua sudah memegang suara lagu lama; pemutar utama senyap, aman dipindah
        t.volume = userGain()
        player.volume = 0f
        // High-pass lagu baru dinyalakan selagi pemutar utama masih senyap, sebelum pindah lagu,
        // jadi lagu baru sudah lewat filter sejak sampel pertamanya (cutoff awal 2 kHz).
        if (djActive) {
            djFilter.filterType = BiquadFilter.FilterType.HIGH_PASS
            djFilter.cutoffFrequencyHz = HPF_START_HZ
            djFilter.enabled = true
        }
        // Status crossfade dinyalakan SEBELUM lagu dipindah, supaya saat UI menerima lagu baru,
        // ia sudah tahu ini crossfade dan memakai transisi panjang (bukan transisi ganti lagu biasa).
        CrossfadeState.start(remaining.coerceAtMost(currentFadeMs.coerceAtLeast(MIN_REMAINING_MS)))
        selfSeeking = true
        try {
            player.seekToNextMediaItem()
        } finally {
            selfSeeking = false
        }

        phase = Phase.FADING
        CrossfadeState.setCrossfading(true)
        // Panjang fade sesuai pengaturan (tidak lebih panjang walau sinkronisasi dimulai lebih awal)
        fadeDurationMs = remaining.coerceAtMost(currentFadeMs.coerceAtLeast(MIN_REMAINING_MS))
        Log.d(TAG, "fade mulai: ${fadeDurationMs}ms (sisa lagu lama ${remaining}ms)")
        outgoingStartMs = outgoingPos
        incomingStartMs = player.currentPosition
        incomingProgress = 0f
        outgoingProgress = 0f
        fadeStartedAtMs = now
    }

    private fun stepFade() {
        // Kemajuan tiap lagu dihitung dari posisi putarnya sendiri. Jadi:
        // - lagu baru baru naik setelah benar-benar terdengar (stream lambat tidak bikin lonjakan),
        // - jeda/lanjut ikut otomatis karena posisi berhenti bergerak.
        incomingProgress = maxOf(incomingProgress, progress(player.currentPosition - incomingStartMs))

        val outgoingPlayer = tail
        val outgoingEnded = outgoingPlayer == null || outgoingPlayer.playbackState == Player.STATE_ENDED
        outgoingProgress = if (outgoingEnded) {
            1f
        } else {
            // Lagu lama tidak boleh turun lebih cepat daripada naiknya lagu baru. Kalau lagu baru
            // telat bersuara (buffering/pindah lagu), lagu lama menunggu dulu, jadi tidak ada
            // lubang senyap di tengah transisi.
            maxOf(
                outgoingProgress,
                minOf(progress(outgoingPlayer!!.currentPosition - outgoingStartMs), incomingProgress)
            )
        }

        // Equal-power seperti SimpMusic: lagu lama cos(sudut), lagu baru sin(sudut), sudut naik
        // linear dari 0 ke 90 derajat. cos^2 + sin^2 = 1, jadi tenaga total tetap rata dan lagu
        // lama tidak "mati" di tengah sebelum lagu baru terisi.
        val gain = userGain()
        player.volume = sin(incomingProgress * HALF_PI) * gain
        outgoingPlayer?.volume = cos(outgoingProgress * HALF_PI) * gain

        // Sapuan filter ala DJ berjalan bersama volume. Sumbu waktunya sigmoid (datar di awal dan
        // akhir, curam di tengah, seperti crossfader mixer DJ); sumbu frekuensinya eksponensial
        // karena telinga mendengar frekuensi secara logaritmik.
        if (djActive) {
            tailFilter?.cutoffFrequencyHz =
                exponentialInterpolate(LPF_START_HZ, LPF_END_HZ, sigmoid(outgoingProgress))
            djFilter.cutoffFrequencyHz =
                exponentialInterpolate(HPF_START_HZ, HPF_END_HZ, sigmoid(incomingProgress))
        }

        if (incomingProgress >= 1f) finishFade()
    }

    private fun progress(playedMs: Long): Float =
        (playedMs.toFloat() / fadeDurationMs).coerceIn(0f, 1f)

    /** Kurva S: 1 / (1 + e^(-k (t - 0,5))). Dengan k = 6, sapuan terjadi sekitar 8%-92% durasi. */
    private fun sigmoid(t: Float): Float = 1f / (1f + exp(-DJ_FILTER_SIGMOID_K * (t - 0.5f)))

    /** Interpolasi eksponensial antara dua frekuensi; terdengar merata karena telinga logaritmik. */
    private fun exponentialInterpolate(start: Float, end: Float, t: Float): Float {
        if (start <= 0f || end <= 0f) return end
        return exp(ln(start) + (ln(end) - ln(start)) * t)
    }

    private fun finishFade() {
        Log.d(TAG, "fade selesai")
        phase = Phase.IDLE
        CrossfadeState.setCrossfading(false)
        endDjFilters()
        releaseTail()
        player.volume = userGain()
    }

    /** Matikan filter DJ di kedua pemutar; mematikan filter juga mengosongkan riwayatnya. */
    private fun endDjFilters() {
        djFilter.enabled = false
        tailFilter?.enabled = false
        djActive = false
    }

    private fun releaseTail() {
        tail?.let { equalizer?.detach(it) }
        tail?.release()
        tail = null
        tailFilter?.enabled = false
        tailFilter = null
        tailNormalizer = null
        tailMediaId = null
    }

    private companion object {
        const val TAG = "Crossfade"
        const val POLL_MS = 200L

        // Jeda minimal sebelum pemutar kedua yang gagal disiapkan ulang
        const val RETRY_PREPARE_MS = 2_000L

        // Berapa kali pemutar kedua boleh digeser ulang kalau belum sejajar
        const val MAX_RESYNC = 2
        // ~60 langkah volume per detik; 50 ms (20/detik) terdengar bergelombang
        const val FAST_TICK_MS = 16L

        // Pemutar kedua mulai disiapkan sekian ms sebelum titik sinkronisasi
        const val PREPARE_AHEAD_MS = 15_000L

        // Persiapan terlambat (mis. setelah seek ke dekat akhir lagu): syarat sisa lagu minimal,
        // jarak titik mulai dari posisi sekarang (waktu buffering stream), dan jeda antar percobaan
        const val LATE_PREPARE_MIN_REMAINING_MS = 3_000L
        const val LATE_PREPARE_AHEAD_MS = 1_500L
        const val LATE_PREPARE_RETRY_MS = 1_000L

        // Lagu yang lebih pendek dari ini (atau dari 3x lama crossfade, mana yang lebih besar)
        // tidak di-crossfade. Sama dengan MIN_CROSSFADE_TRACK_MS di SimpMusic.
        const val MIN_CROSSFADE_TRACK_MS = 20_000L

        // Transisi ala DJ (nilai sama dengan SimpMusic). Kecuratan kurva S: makin besar makin tajam.
        const val DJ_FILTER_SIGMOID_K = 6f

        // Low-pass lagu lama: terbuka lebar lalu meredam sampai tinggal dentuman bass
        const val LPF_START_HZ = 20000f
        const val LPF_END_HZ = 200f

        // High-pass lagu baru: mulai dari 2 kHz supaya lagu baru cepat terisi, lalu terbuka penuh
        const val HPF_START_HZ = 2000f
        const val HPF_END_HZ = 20f

        // Sisa lagu minimal untuk masih memulai sinkronisasi
        const val MIN_START_REMAINING_MS = 900L

        // Sisa lagu minimal untuk lamanya fade
        const val MIN_REMAINING_MS = 600L

        // Sinkronisasi dimulai sekian ms sebelum fade (waktu bagi pemutar kedua untuk "panas")
        const val SYNC_LEAD_MS = 2_600L

        // Perkiraan jeda antara pemutar kedua dijalankan dan suaranya keluar. Pemutar kedua
        // dimulai sebesar ini lebih maju supaya sejajar dengan pemutar utama. Kalau masih terdengar
        // gema tipis di awal crossfade, ubah nilai ini (mis. 20-80).
        const val TAIL_COMPENSATION_MS = 40f
        const val MAX_COMPENSATION_MS = 400f

        // Pemutar kedua harus sudah berjalan sekian ms sebelum suaranya diambil alih
        const val SYNC_MIN_RUN_MS = 350L

        // Jumlah pengukuran selisih posisi sebelum diambil keputusan
        const val SYNC_MIN_SAMPLES = 11

        // Jarak minimal antar sampel selisih (tick 16 ms terlalu rapat dan saling berkorelasi)
        const val SYNC_SAMPLE_INTERVAL_MS = 25L

        // Batas waktu menunggu pemutar kedua bersuara; lewat dari ini crossfade dibatalkan
        const val SYNC_TIMEOUT_MS = 3_600L

        // Pemutar kedua siap lebih dari ini setelah titiknya: susul ulang
        const val MAX_SYNC_LATE_MS = 250L
        const val RESEEK_AHEAD_MS = 1_000L

        // Selisih posisi maksimal dua salinan lagu lama sebelum suaranya dipindah (di atas ini
        // terdengar dobel); sampai RESYNC_MAX_OFFSET_MS masih dicoba digeser ulang
        const val ALIGN_TOLERANCE_MS = 40f
        const val FINAL_ALIGN_TOLERANCE_MS = 50f

        // Seberapa besar selisih terukur dikoreksi pada geser ulang (1 = penuh, cenderung berayun)
        const val RESYNC_GAIN = 0.8f
        const val RESYNC_MAX_OFFSET_MS = 600f

        // Lama ramp pemindahan suara lagu lama ke pemutar kedua
        const val HANDOFF_MS = 100L

        // Event dalam jangka ini setelah crossfade dimulai dianggap berasal dari kita
        const val OWN_EVENT_WINDOW_MS = 500L

        val HALF_PI = (Math.PI / 2).toFloat()
    }
}