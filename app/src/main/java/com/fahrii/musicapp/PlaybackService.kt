package com.fahrii.musicapp

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.fahrii.musicapp.audio.CrossfadeFilterAudioProcessor
import com.fahrii.musicapp.audio.DjFilterRenderersFactory
import com.fahrii.musicapp.audio.EqualizerController
import com.fahrii.musicapp.data.DownloadRepository
import com.fahrii.musicapp.data.LibraryRepository
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.SettingsRepository
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.widget.WidgetUpdater
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import androidx.media3.datasource.DefaultHttpDataSource

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var library: LibraryRepository? = null

    // Status Hati yang sedang tampil di notifikasi; null = belum pernah diatur
    private var likedShown: Boolean? = null

    // Tombol tambahan di notifikasi: Hati di kiri, Acak di kanan (Prev, Play, Next tetap bawaan)
    private val likeCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private var crossfade: CrossfadeManager? = null
    private var equalizer: EqualizerController? = null
    private var widgetUpdater: WidgetUpdater? = null
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        val downloads = DownloadRepository.getInstance(this)
        val settings = SettingsRepository.getInstance(this)

        // Ubah link YouTube di antrian jadi URL stream audio, tepat saat lagu mau diputar
        val resolver = ResolvingDataSource.Resolver { dataSpec ->
            val uri = dataSpec.uri

            // Lagu yang sudah diunduh diputar dari file lokal: tanpa internet, tanpa ambil stream
            downloads.localFile(uri.toString())?.let { file ->
                return@Resolver dataSpec.withUri(Uri.fromFile(file))
            }

            try {
                when {
                    // Lagu dari Beranda: cari dulu di YouTube Music, lalu ambil stream audionya
                    uri.scheme == "sonora" -> {
                        val query = uri.getQueryParameter("q").orEmpty()
                        val videoUrl = MusicRepository.findVideoUrlBlocking(query)
                        val streamUrl = MusicRepository.resolveAudioUrlBlocking(videoUrl, settings.streamQuality.value)
                        dataSpec.withUri(Uri.parse(streamUrl))
                    }

                    // Lagu dari hasil search YouTube
                    uri.host?.endsWith("youtube.com") == true &&
                            uri.getQueryParameter("v") != null -> {
                        val streamUrl = MusicRepository.resolveAudioUrlBlocking(uri.toString(), settings.streamQuality.value)
                        dataSpec.withUri(Uri.parse(streamUrl))
                    }

                    else -> dataSpec
                }
            } catch (e: Exception) {
                throw IOException("Gagal mengambil stream audio", e)
            }
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            resolver
        )

        // Filter low/high-pass untuk transisi ala DJ; hanya aktif selama crossfade (lihat CrossfadeManager)
        val djFilter = CrossfadeFilterAudioProcessor()

        val player = ExoPlayer.Builder(this, DjFilterRenderersFactory(this, djFilter))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val lib = LibraryRepository.getInstance(this)
        library = lib

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .setCallback(object : MediaSession.Callback {
                // Sama seperti bawaan (semua pengontrol diterima), ditambah dua perintah kustom
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(likeCommand)
                        .add(shuffleCommand)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_TOGGLE_LIKE -> toggleLikeCurrent(session.player)
                        ACTION_TOGGLE_SHUFFLE ->
                            session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .setCustomLayout(customLayout(liked = false))
            .build()

        // Ikon Hati ikut berubah saat lagu ganti atau daftar Favorit berubah (dari app maupun notifikasi)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshCustomLayout()
            }
        })
        settingsScope.launch { lib.state.collect { refreshCustomLayout() } }

        // Lewati bagian senyap mengikuti Pengaturan, termasuk saat diubah ketika lagu sedang diputar
        settingsScope.launch {
            settings.skipSilence.collect { player.skipSilenceEnabled = it }
        }

        // Jeda otomatis saat headset dicabut atau Bluetooth terputus: selalu aktif, tidak ada pengaturannya
        player.setHandleAudioBecomingNoisy(true)

        // Timer tidur: saat waktunya habis, pemutar dijeda. collectLatest membatalkan hitungan
        // lama otomatis kalau timer diganti atau dimatikan.
        settingsScope.launch {
            settings.sleepTimerEndAt.collectLatest { endAt ->
                if (endAt <= 0L) return@collectLatest
                val wait = endAt - System.currentTimeMillis()
                if (wait <= 0L) {
                    // Sudah lewat sebelum layanan sempat berjalan: jangan menjeda lagu yang baru diputar
                    settings.cancelSleepTimer()
                    return@collectLatest
                }
                delay(wait)
                player.pause()
                settings.cancelSleepTimer()
            }
        }

        // Widget layar utama ikut lagu yang sedang diputar
        widgetUpdater = WidgetUpdater(this, player, settingsScope).also { it.start() }

        // Equalizer + bass boost mengikuti Pengaturan. Didaftarkan juga ke pemutar kedua milik crossfade
        // (lewat parameter di bawah) supaya lagu yang memudar keluar tetap terdengar sama.
        val eqController = EqualizerController(settings, settingsScope).also { it.attach(player) }
        equalizer = eqController

        // Crossfade antar lagu (menyala lewat Pengaturan); memakai sumber data yang sama dengan pemutar utama
        crossfade = CrossfadeManager(this, player, dataSourceFactory, settings, djFilter, eqController)
    }

    /**
     * Dibuka saat notifikasi pemutar diketuk. Memakai launch intent app, sama seperti mengetuk
     * ikonnya di launcher, jadi app yang masih hidup dibawa ke depan, bukan dibuka dobel.
     */
    private fun openAppIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun customLayout(liked: Boolean): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder()
            .setDisplayName(if (liked) "Hapus dari Favorit" else "Suka")
            .setIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(likeCommand)
            .build(),
        CommandButton.Builder()
            .setDisplayName("Acak")
            .setIconResId(R.drawable.ic_shuffle_small)
            .setSessionCommand(shuffleCommand)
            .build()
    )

    /** Pasang ulang tombol notifikasi hanya kalau status Hati lagu yang sedang diputar berubah. */
    private fun refreshCustomLayout() {
        val session = mediaSession ?: return
        val url = session.player.currentMediaItem?.mediaId
        val liked = url != null && library?.state?.value?.liked?.any { it.url == url } == true
        if (liked == likedShown) return
        likedShown = liked
        session.setCustomLayout(customLayout(liked))
    }

    /** Suka/batal suka lagu yang sedang diputar, ke daftar Favorit yang sama dengan di app. */
    private fun toggleLikeCurrent(player: Player) {
        val item = player.currentMediaItem ?: return
        library?.toggleLike(
            Song(
                url = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                durationSeconds = -1
            )
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    private companion object {
        const val ACTION_TOGGLE_LIKE = "com.fahrii.musicapp.TOGGLE_LIKE"
        const val ACTION_TOGGLE_SHUFFLE = "com.fahrii.musicapp.TOGGLE_SHUFFLE"
    }

    override fun onDestroy() {
        widgetUpdater?.stop()
        widgetUpdater = null
        settingsScope.cancel()
        crossfade?.release()
        crossfade = null
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}