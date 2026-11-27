package com.fahrii.musicapp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import com.fahrii.musicapp.desktop.player.PlaybackController

/** Ikon sementara: lingkaran teal berlubang (mirip piringan). Nanti diganti file .ico. */
private object TrayIconPainter : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        drawCircle(Color(0xFF80CBC4))
        drawCircle(Color(0xFF121212), radius = size.minDimension / 6f)
    }
}

@Composable
internal fun ApplicationScope.SonoraTray(
    controller: PlaybackController,
    onShow: () -> Unit,
    onQuit: () -> Unit
) {
    if (!isTraySupported) return

    val state by controller.state.collectAsState()
    val queue by controller.queue.collectAsState()
    val trayState = rememberTrayState()

    Tray(
        icon = TrayIconPainter,
        state = trayState,
        tooltip = queue.current?.let { "${it.title} — ${it.artist}".take(120) } ?: "Sonora Music",
        onAction = onShow,
        menu = {
            Item("Buka Sonora Music", onClick = onShow)
            Separator()
            Item(if (state.isPlaying) "Jeda" else "Putar", onClick = controller::toggle)
            Item("Berikutnya", onClick = controller::next)
            Item("Sebelumnya", onClick = controller::previous)
            Separator()
            Item("Keluar", onClick = onQuit)
        }
    )
}