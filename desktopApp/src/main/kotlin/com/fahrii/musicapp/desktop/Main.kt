package com.fahrii.musicapp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.fahrii.musicapp.data.EqualizerBands
import com.fahrii.musicapp.data.SearchHistoryRepository
import java.io.File

/** Folder data aplikasi: %APPDATA%\SonoraMusic di Windows. */
private fun appDataDir(): File {
    val base = System.getenv("APPDATA")?.let(::File)
        ?: File(System.getProperty("user.home"), ".local/share")
    return File(base, "SonoraMusic").also { it.mkdirs() }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Sonora Music") {
        val repo = remember { SearchHistoryRepository(appDataDir()) }
        val history by repo.items.collectAsState()
        val white = TextStyle(color = Color.White)

        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText("Sonora Music Desktop", style = white)
            BasicText("Pita EQ: ${EqualizerBands.LABELS.joinToString()}", style = white)
            BasicText(
                text = "[ klik di sini untuk menambah riwayat uji ]",
                style = TextStyle(color = Color(0xFF80CBC4)),
                modifier = Modifier.clickable { repo.add("lagu uji ${history.size + 1}") }
            )
            history.forEach { BasicText("• $it", style = white) }
        }
    }
}