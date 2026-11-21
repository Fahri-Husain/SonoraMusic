package com.fahrii.musicapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dialog "Pembaruan baru tersedia": versi, tanggal rilis, catatan perubahan, tombol Batalkan dan Unduh. */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel) {
    val release by viewModel.update.collectAsState()
    val info = release ?: return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text("Pembaruan baru tersedia") },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text("Versi ${info.tag} tersedia", fontWeight = FontWeight.Bold)
                formatReleaseDate(info.publishedAt)?.let {
                    Text("Rilis: $it", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text("Log diubah:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = changelogText(info.body),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Buka halaman rilis di GitHub; kalau gagal dibuka, tidak apa-apa
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                viewModel.dismiss()
            }) { Text("Unduh") }
        },
        dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Batalkan") } }
    )
}

private fun formatReleaseDate(iso: String?): String? {
    if (iso == null) return null
    return runCatching {
        val time = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss", Locale.forLanguageTag("id-ID")).format(time)
    }.getOrNull()
}

/** Ubah catatan rilis (Markdown) jadi teks biasa: judul tebal, butir jadi "•", tanda Markdown dibuang. */
private fun changelogText(markdown: String): AnnotatedString {
    val lines = markdown.replace("\r", "").lines().map { it.trim() }
    if (lines.all { it.isEmpty() }) return AnnotatedString("Tidak ada catatan perubahan.")

    fun clean(text: String): String = text
        .replace(Regex("""\[([^\]]+)]\(([^)]+)\)"""), "$1")
        .replace("**", "").replace("__", "").replace("`", "")

    return buildAnnotatedString {
        var previousBlank = true
        lines.forEach { line ->
            when {
                line.isEmpty() -> {
                    if (!previousBlank) append("\n")
                    previousBlank = true
                }
                line.startsWith("#") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(clean(line.trimStart('#').trim())) }
                    append("\n")
                    previousBlank = false
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    append("• ${clean(line.drop(2).trim())}\n")
                    previousBlank = false
                }
                else -> {
                    append("${clean(line)}\n")
                    previousBlank = false
                }
            }
        }
    }
}
