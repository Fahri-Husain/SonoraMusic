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

private val Bg = Color(0xFF121212)
private val Panel = Color(0xFF1E1E1E)
private val Accent = Color(0xFF80CBC4)
private val Dim = Color(0xFF9E9E9E)

fun main() = application {
    val playerResult = remember { runCatching { MpvAudioPlayer() } }

    Window(
        onCloseRequest = {
            playerResult.getOrNull()?.release()
            exitApplication()
        },
        title = "Sonora Music",
        state = rememberWindowState(size = DpSize(900.dp, 640.dp))
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            playerResult.fold(
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
private fun PlayerApp(player: AudioPlayer) {
    val scope = rememberCoroutineScope()
    val state by player.state.collectAsState()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Song?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            message = "Mencari…"
            runCatching { MusicRepository.search(q) }
                .onSuccess {
                    results = it
                    message = if (it.isEmpty()) "Tidak ada hasil" else null
                }
                .onFailure { message = "Pencarian gagal: ${it.message}" }
        }
    }

    fun play(song: Song) {
        scope.launch {
            nowPlaying = song
            message = "Mengambil stream…"
            runCatching {
                withContext(Dispatchers.IO) {
                    MusicRepository.resolvePlayableUrlBlocking(song.url, AudioQuality.HIGH)
                }
            }
                .onSuccess { url ->
                    message = null
                    player.play(url)
                }
                .onFailure { message = "Gagal mengambil stream: ${it.message}" }
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

        message?.let { Txt(it, Dim, 13, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(results) { song ->
                Row(
                    Modifier.fillMaxWidth().clickable { play(song) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(song.title, Color.White, 14)
                        Txt(song.artist, Dim, 12)
                    }
                    if (song.durationSeconds > 0) {
                        Txt(formatTime(song.durationSeconds * 1000), Dim, 12)
                    }
                }
            }
        }

        PlayerBar(nowPlaying, state, player)
    }
}

@Composable
private fun PlayerBar(song: Song?, state: PlayerState, player: AudioPlayer) {
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
                            player.seekTo((offset.x / size.width * state.durationMs).toLong())
                        }
                    }
                }
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Accent))
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}", Dim, 12)
            Spacer(Modifier.weight(1f))
            Txt("Vol ${state.volume}", Dim, 12)
            SmallButton("−") { player.setVolume(state.volume - 10) }
            SmallButton("+") { player.setVolume(state.volume + 10) }
            SmallButton(if (state.isPlaying) "Jeda" else "Putar") { player.toggle() }
            SmallButton("Stop") { player.stop() }
        }
        state.error?.let { Txt(it, Color(0xFFEF9A9A), 12, Modifier.padding(top = 6.dp)) }
        if (state.ended) Txt("Selesai", Dim, 12, Modifier.padding(top = 6.dp))
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