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
import com.fahrii.musicapp.data.MusicRepository
import java.io.IOException
import androidx.media3.datasource.DefaultHttpDataSource

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Ubah link YouTube di antrian jadi URL stream audio, tepat saat lagu mau diputar
        val resolver = ResolvingDataSource.Resolver { dataSpec ->
            val uri = dataSpec.uri
            try {
                when {
                    // Lagu dari Beranda: cari dulu di YouTube Music, lalu ambil stream audionya
                    uri.scheme == "sonora" -> {
                        val query = uri.getQueryParameter("q").orEmpty()
                        val videoUrl = MusicRepository.findVideoUrlBlocking(query)
                        val streamUrl = MusicRepository.resolveAudioUrlBlocking(videoUrl)
                        dataSpec.withUri(Uri.parse(streamUrl))
                    }

                    // Lagu dari hasil search YouTube
                    uri.host?.endsWith("youtube.com") == true &&
                            uri.getQueryParameter("v") != null -> {
                        val streamUrl = MusicRepository.resolveAudioUrlBlocking(uri.toString())
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}