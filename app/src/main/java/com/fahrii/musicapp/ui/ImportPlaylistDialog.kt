package com.fahrii.musicapp.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Dialog "Import playlist": tempel link playlist YouTube Music / Spotify, lalu impor.
 * Selama impor berjalan kolom link dikunci dan kemajuan ditampilkan; [onDismiss] membatalkan impornya.
 */
@Composable
fun ImportPlaylistDialog(
    state: PlaylistImportState,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    val running = state is PlaylistImportState.Running

    fun submit() {
        if (link.isNotBlank() && !running) onImport(link.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    enabled = !running,
                    singleLine = true,
                    placeholder = { Text("Tempel link playlist") },
                    trailingIcon = {
                        TextButton(onClick = { pasteFromClipboard(context)?.let { link = it } }, enabled = !running) {
                            Text("Tempel")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                when (state) {
                    is PlaylistImportState.Running -> {
                        if (state.total > 0) {
                            Text(
                                text = "Mencocokkan lagu ${state.done}/${state.total}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.done.toFloat() / state.total },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(10.dp))
                                Text("Mengambil playlist…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    is PlaylistImportState.Failed -> Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> Text(
                        text = "Mendukung YouTube Music dan Spotify. Playlist harus publik.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = link.isNotBlank() && !running) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

private fun pasteFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboard?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
