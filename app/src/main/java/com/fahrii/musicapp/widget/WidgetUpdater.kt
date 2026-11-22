package com.fahrii.musicapp.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fahrii.musicapp.ui.asBackdrop
import com.fahrii.musicapp.ui.dominantColorOf
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * Menyalin lagu yang sedang diputar ke [WidgetStore] lalu menggambar ulang widget.
 * Dipasang di [com.fahrii.musicapp.PlaybackService] karena di situlah pemutar hidup.
 *
 * Kalau pengguna tidak punya widget di layar utama, pekerjaan berhenti lebih awal
 * (tidak mengunduh cover, tidak menghitung warna).
 */
class WidgetUpdater(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope
) : Player.Listener {

    private val appContext = context.applicationContext
    private var job: Job? = null

    fun start() {
        player.addListener(this)
        refresh()
    }

    /** Pemutar berhenti total: widget kembali ke keadaan kosong. */
    fun stop() {
        player.removeListener(this)
        job?.cancel()
        detachedScope.launch {
            WidgetStore.clear(appContext)
            SonoraWidget().updateAll(appContext)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refresh()
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = refresh()
    override fun onPlaybackStateChanged(playbackState: Int) = refresh()
    override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()

    /** Beberapa callback sering datang bersamaan (ganti lagu + mulai putar), jadi digabung jadi satu. */
    private fun refresh() {
        job?.cancel()
        job = scope.launch {
            delay(DEBOUNCE_MS)

            // Pemutar hanya boleh dibaca dari thread utama; scope ini berjalan di sana
            val item = player.currentMediaItem
            val playing = player.playWhenReady &&
                player.playbackState != Player.STATE_ENDED &&
                player.playbackState != Player.STATE_IDLE

            withContext(Dispatchers.IO) {
                if (!hasWidget()) return@withContext
                if (item == null) {
                    WidgetStore.clear(appContext)
                } else {
                    WidgetStore.write(appContext, snapshotOf(item, playing))
                }
                SonoraWidget().updateAll(appContext)
            }
        }
    }

    private suspend fun hasWidget(): Boolean =
        GlanceAppWidgetManager(appContext)
            .getGlanceIds(SonoraWidget::class.java)
            .isNotEmpty()

    /** Cover hanya dimuat ulang kalau URL-nya berbeda dari yang tersimpan. */
    private suspend fun snapshotOf(item: MediaItem, playing: Boolean): WidgetSnapshot {
        val meta = item.mediaMetadata
        val url = meta.artworkUri?.toString()
        val previous = WidgetStore.read(appContext)
        val coverFile = WidgetStore.coverFile(appContext)

        var tint = previous?.tint
        if (url != previous?.artworkUrl || !coverFile.exists()) {
            val bitmap = url?.let { loadCover(it) }
            if (bitmap != null) {
                FileOutputStream(coverFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tint = runCatching { dominantColorOf(appContext, listOf(url)) }
                    .getOrNull()
                    ?.asBackdrop(value = 0.30f, maxSaturation = 0.40f)
                    ?.toArgb()
            } else {
                coverFile.delete()
                tint = null
            }
        }

        return WidgetSnapshot(
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            isPlaying = playing,
            artworkUrl = url,
            tint = tint
        )
    }

    private suspend fun loadCover(url: String): Bitmap? = try {
        val result = appContext.imageLoader.execute(
            ImageRequest.Builder(appContext)
                .data(url)
                .size(COVER_SIZE_PX)
                .allowHardware(false)
                .build()
        )
        (result as? SuccessResult)?.drawable?.toBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val DEBOUNCE_MS = 200L
        const val COVER_SIZE_PX = 600

        // stop() dipanggil saat layanan hancur, ketika scope layanan sudah dibatalkan
        val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
