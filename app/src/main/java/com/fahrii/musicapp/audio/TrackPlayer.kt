package com.fahrii.musicapp.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** Satu lagu = satu ExoPlayer, dengan filter DJ dan penyama volume miliknya sendiri. */
@OptIn(UnstableApi::class)
class TrackPlayer(
    val player: ExoPlayer,
    val filter: CrossfadeFilterAudioProcessor,
    val normalizer: LoudnessNormalizerAudioProcessor
)

@OptIn(UnstableApi::class)
class TrackPlayerFactory(
    private val context: Context,
    private val dataSourceFactory: DataSource.Factory,
    private val equalizer: EqualizerController?,
    initialNormalize: Boolean,
    initialSkipSilence: Boolean
) {
    private val live = mutableSetOf<TrackPlayer>()
    private var normalize = initialNormalize
    private var skipSilence = initialSkipSilence

    /** Buat pemutar untuk [item] dan langsung prepare (tanpa play): inilah "precache". */
    fun create(item: MediaItem): TrackPlayer {
        val filter = CrossfadeFilterAudioProcessor()
        val normalizer = LoudnessNormalizerAudioProcessor().also { it.enabled = normalize }
        val player = ExoPlayer.Builder(context, DjFilterRenderersFactory(context, filter, normalizer))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false // fokus dipegang AudioFocusController, bukan per pemutar
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.skipSilenceEnabled = skipSilence
        equalizer?.attach(player)
        player.setMediaItem(item)
        player.prepare()
        return TrackPlayer(player, filter, normalizer).also { live += it }
    }

    fun release(track: TrackPlayer) {
        live -= track
        equalizer?.detach(track.player) // harus sebelum release()
        track.player.release()
    }

    fun setNormalize(enabled: Boolean) {
        normalize = enabled
        live.forEach { it.normalizer.enabled = enabled }
    }

    fun setSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        live.forEach { it.player.skipSilenceEnabled = enabled }
    }
}