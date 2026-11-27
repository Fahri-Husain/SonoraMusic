package com.fahrii.musicapp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.fahrii.musicapp.data.AudioQuality
import com.fahrii.musicapp.data.MusicRepository
import com.fahrii.musicapp.data.Song
import com.fahrii.musicapp.desktop.player.MpvAudioPlayer
import com.fahrii.musicapp.player.AudioPlayer
import com.fahrii.musicapp.player.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.DisposableEffect
import com.fahrii.musicapp.data.EqualizerState
import com.fahrii.musicapp.data.isAlbumEntry
import com.fahrii.musicapp.desktop.player.PlaybackController
import com.fahrii.musicapp.player.QueueState
import com.fahrii.musicapp.player.RepeatMode
import com.fahrii.musicapp.data.FileKeyValueStore
import com.fahrii.musicapp.data.SettingsRepository
import java.io.File
import androidx.compose.runtime.DisposableEffect
import com.fahrii.musicapp.desktop.player.GlobalMediaKeys
import androidx.compose.ui.window.isTraySupported

private val Bg = Color(0xFF121212)
private val Panel = Color(0xFF1E1E1E)
private val Accent = Color(0xFF80CBC4)
private val Dim = Color(0xFF9E9E9E)

fun main() = application {
    val controllerResult = remember {
        runCatching {
            PlaybackController(SettingsRepository(FileKeyValueStore(File(AppDirs.data, "settings.json"))))
        }
    }
    val controller = controllerResult.getOrNull()
    var windowVisible by remember { mutableStateOf(true) }

    val quit = {
        controller?.close()
        exitApplication()
    }

    if (controller != null) {
        DisposableEffect(controller) {
            val keys = GlobalMediaKeys(
                onPlayPause = controller::toggle,
                onNext = controller::next,
                onPrevious = controller::previous,
                onStop = controller::stop
            )
            keys.start()
            onDispose { keys.stop() }
        }
        SonoraTray(controller, onShow = { windowVisible = true }, onQuit = quit)
    }

    Window(
        visible = windowVisible,
        // Dengan tray: X cuma menyembunyikan jendela, musik lanjut. Tanpa tray atau tanpa pemutar: keluar.
        onCloseRequest = { if (controller != null && isTraySupported) windowVisible = false else quit() },
        title = "Sonora Music",
        state = rememberWindowState(size = DpSize(900.dp, 640.dp))
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            controllerResult.fold(
                onSuccess = { PlayerApp(it) },
                onFailure = { MpvMissing(it) }
            )
        }
    }
}

@Composable
private fun MpvMissing(error: Throwable) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Txt("Pemutar libmpv gagal dimuat", Color(0xFFEF9A9A), 18)
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = error.message ?: error.toString(),
            style = TextStyle(color = Color.White, fontSize = 13.sp)
        )
    }
}

@Composable
private fun PlayerApp(controller: PlaybackController) {
    val scope = rememberCoroutineScope()
    val settings = controller.settings

    val state by controller.state.collectAsState()
    val queue by controller.queue.collectAsState()
    val playerMessage by controller.message.collectAsState()
    val eq by settings.equalizer.collectAsState()
    val normalize by settings.normalizeVolume.collectAsState()
    val crossfadeOn by settings.crossfadeEnabled.collectAsState()
    val crossfadeSec by settings.crossfadeSeconds.collectAsState()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    val playable = remember(results) { results.filterNot { it.isAlbumEntry() } }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searchMessage = "Mencari…"
            runCatching { MusicRepository.search(q) }
                .onSuccess {
                    results = it
                    searchMessage = if (it.isEmpty()) "Tidak ada hasil" else null
                }
                .onFailure { searchMessage = "Pencarian gagal: ${it.message}" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).background(Panel).padding(12.dp)) {
                if (query.isEmpty()) Txt("Cari lagu atau artis…", Dim)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                        if (e.key == Key.Enter && e.type == KeyEventType.KeyDown) {
                            search()
                            true
                        } else {
                            false
                        }
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.background(Accent).clickable { search() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) { Txt("Cari", Color.Black) }
        }

        (searchMessage ?: playerMessage)?.let {
            Txt(it, Dim, 13, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(playable) { index, song ->
                val isCurrent = queue.current?.url == song.url
                Row(
                    Modifier.fillMaxWidth().clickable { controller.playList(playable, index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(song.title, if (isCurrent) Accent else Color.White, 14)
                        Txt(song.artist, Dim, 12)
                    }
                    if (song.durationSeconds > 0) {
                        Txt(formatTime(song.durationSeconds * 1000), Dim, 12)
                    }
                }
            }
        }

        PlayerBar(
            queue = queue,
            state = state,
            eq = eq,
            normalize = normalize,
            crossfadeLabel = if (crossfadeOn) "Crossfade: ${crossfadeSec} dtk" else "Crossfade: mati",
            controller = controller
        )
    }
}

@Composable
private fun PlayerBar(
    queue: QueueState,
    state: PlayerState,
    eq: EqualizerState,
    normalize: Boolean,
    crossfadeLabel: String,
    controller: PlaybackController
) {
    val song = queue.current
    Column(Modifier.fillMaxWidth().background(Panel).padding(16.dp)) {
        Txt(song?.let { "${it.title} — ${it.artist}" } ?: "Belum ada lagu diputar", Color.White, 14)
        Spacer(Modifier.height(8.dp))

        val fraction =
            if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF333333))
                .pointerInput(state.durationMs) {
                    detectTapGestures { offset ->
                        if (state.durationMs > 0) {
                            controller.seekTo((offset.x / size.width * state.durationMs).toLong())
                        }
                    }
                }
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Accent))
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}", Dim, 12)
            Txt(
                if (queue.songs.isEmpty()) "" else "  ·  ${queue.index + 1}/${queue.songs.size}",
                Dim, 12
            )
            Spacer(Modifier.weight(1f))
            Txt("Vol ${state.volume}", Dim, 12)
            SmallButton("−") { controller.setVolume(state.volume - 10) }
            SmallButton("+") { controller.setVolume(state.volume + 10) }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallButton("Prev") { controller.previous() }
            SmallButton(if (state.isPlaying) "Jeda" else "Putar") { controller.toggle() }
            SmallButton("Next") { controller.next() }
            SmallButton("Stop") { controller.stop() }
            Spacer(Modifier.weight(1f))
            SmallButton(
                when (queue.repeat) {
                    RepeatMode.OFF -> "Ulang: mati"
                    RepeatMode.ALL -> "Ulang: semua"
                    RepeatMode.ONE -> "Ulang: satu"
                }
            ) { controller.toggleRepeat() }
            SmallButton(crossfadeLabel) { controller.cycleCrossfade() }
            SmallButton(if (eq.enabled) "EQ: ${eq.preset.label}" else "EQ: mati") { controller.cycleEqualizer() }
            SmallButton(if (normalize) "Normalisasi: on" else "Normalisasi: off") { controller.toggleNormalize() }
        }

        state.error?.let { Txt(it, Color(0xFFEF9A9A), 12, Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 8.dp).background(Color(0xFF333333)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Txt(label, Color.White, 13) }
}

@Composable
private fun Txt(text: String, color: Color = Color.White, size: Int = 14, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}