package com.fahrii.musicapp

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fahrii.musicapp.data.DownloadRepository
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import androidx.media3.datasource.DefaultHttpDataSource

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var crossfade: CrossfadeManager? = null
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

        val player = ExoPlayer.Builder(this)
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

        mediaSession = MediaSession.Builder(this, player).build()

        // Lewati bagian senyap mengikuti Pengaturan, termasuk saat diubah ketika lagu sedang diputar
        settingsScope.launch {
            settings.skipSilence.collect { player.skipSilenceEnabled = it }
        }

        // Crossfade antar lagu (menyala lewat Pengaturan); memakai sumber data yang sama dengan pemutar utama
        crossfade = CrossfadeManager(this, player, dataSourceFactory, SettingsRepository.getInstance(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        settingsScope.cancel()
        crossfade?.release()
        crossfade = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}