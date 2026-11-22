package com.fahrii.musicapp.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fahrii.musicapp.MainActivity
import com.fahrii.musicapp.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Widget layar utama bergaya piringan hitam: status dan judul lagu di kiri, piringan dengan cover
 * sebagai label di kanan. Ketuk piringan untuk putar/jeda, ketuk bagian lain untuk membuka app.
 */
class SonoraWidget : GlanceAppWidget() {

    // Ukuran asli widget dibutuhkan supaya bitmap latar pas dan tidak gepeng
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore.read(context)
        val cover = withContext(Dispatchers.IO) {
            WidgetStore.coverFile(context)
                .takeIf { snapshot != null && it.exists() }
                ?.let { BitmapFactory.decodeFile(it.path) }
        }
        provideContent { WidgetContent(context, snapshot, cover) }
    }
}

class SonoraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SonoraWidget()
}

private val TextPrimary = ColorProvider(Color(0xFFFFFFFF))
private val TextMuted = ColorProvider(Color(0xB3FFFFFF))

@Composable
private fun WidgetContent(context: Context, snapshot: WidgetSnapshot?, cover: Bitmap?) {
    val size = LocalSize.current
    // Batasi kerapatan piksel: bitmap widget yang terlalu besar ditolak sistem
    val scale = min(context.resources.displayMetrics.density, 2.5f)
    val widthPx = (size.width.value * scale).toInt().coerceAtLeast(1)
    val heightPx = (size.height.value * scale).toInt().coerceAtLeast(1)

    val art = remember(widthPx, heightPx, snapshot, cover) {
        VinylArt.render(
            width = widthPx,
            height = heightPx,
            cover = cover,
            tint = snapshot?.tint,
            playing = snapshot?.isPlaying == true
        )
    }

    // Widget hampir persegi: piringan saja, tanpa teks
    val compact = size.width.value < size.height.value * 1.5f
    val textWidth = (size.width - size.height).coerceAtLeast(80.dp)

    val status = when {
        snapshot == null -> "Sonora"
        snapshot.isPlaying -> "Sedang diputar"
        else -> "Dijeda"
    }
    val artist = if (snapshot == null) "Ketuk untuk membuka" else snapshot.artist
    val title = snapshot?.title ?: "Belum ada lagu"

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Image(
            provider = ImageProvider(art),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )

        Row(modifier = GlanceModifier.fillMaxSize()) {
            if (!compact) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(textWidth)
                        .padding(start = 22.dp, top = 18.dp, bottom = 18.dp)
                ) {
                    Text(
                        text = status,
                        maxLines = 1,
                        style = TextStyle(
                            color = TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            maxLines = 1,
                            style = TextStyle(color = TextMuted, fontSize = 14.sp)
                        )
                    }
                    Text(
                        text = title,
                        maxLines = 2,
                        style = TextStyle(
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Area piringan: putar/jeda kalau ada lagu, kalau tidak jatuh ke "buka app"
            val vinylTap = GlanceModifier.defaultWeight().fillMaxHeight()
            Box(
                modifier = if (snapshot != null) {
                    vinylTap.clickable(actionRunCallback<TogglePlaybackAction>())
                } else {
                    vinylTap
                }
            ) {}
        }
    }
}

/** Putar/jeda lewat sesi media yang sama dengan notifikasi, jadi status ikut sinkron dengan app. */
class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().awaitResult()
        try {
            // Antrean kosong (layanan baru dimulai ulang): tidak ada yang bisa diputar
            if (controller.mediaItemCount == 0) return
            if (controller.isPlaying) controller.pause() else controller.play()
        } finally {
            controller.release()
        }
    }
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, MoreExecutors.directExecutor())
        cont.invokeOnCancellation { cancel(false) }
    }