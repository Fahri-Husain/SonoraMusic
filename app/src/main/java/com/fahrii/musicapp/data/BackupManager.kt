package com.fahrii.musicapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pintu masuk Android untuk cadangan: Uri dan ContentResolver diubah jadi stream, lalu
 * pekerjaan sebenarnya dikerjakan [BackupService] di modul shared.
 */
class BackupManager(context: Context) {

    private val context = context.applicationContext

    private val service = BackupService(
        library = LibraryRepository.getInstance(this.context),
        history = HistoryRepository.getInstance(this.context),
        replay = ReplayRepository.getInstance(this.context),
        appVersion = runCatching {
            this.context.packageManager.getPackageInfo(this.context.packageName, 0).versionName
        }.getOrNull(),
        encodeCover = { path -> encodeCover(path) }
    )

    /** Tulis cadangan ke [uri] (hasil CreateDocument). */
    fun exportTo(uri: Uri): ExportSummary {
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupException("Tidak bisa menulis ke lokasi yang dipilih")
        return service.export(out)
    }

    /** Baca cadangan dari [uri] (hasil OpenDocument) lalu gabungkan: yang sudah ada dilewati. */
    fun importFrom(uri: Uri): ImportReport {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("Berkas tidak bisa dibuka")
        return service.import(input)
    }

    /** Cover buatan pengguna diperkecil jadi JPEG (maks 600 px) sebelum dimasukkan ke cadangan. */
    private fun encodeCover(path: String): ByteArray? = runCatching {
        if (!File(path).exists()) return@runCatching null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > COVER_MAX_PX * 2) sample *= 2

        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > COVER_MAX_PX) {
            val ratio = COVER_MAX_PX.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }.getOrNull()

    private companion object {
        const val COVER_MAX_PX = 600
    }
}