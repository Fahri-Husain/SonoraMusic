package com.fahrii.musicapp.widget

import android.content.Context
import java.io.File

/** Isi widget saat ini. Null dari [WidgetStore.read] berarti belum ada lagu yang diputar. */
data class WidgetSnapshot(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val artworkUrl: String?,
    /** Warna latar (ARGB) yang diambil dari cover; null kalau cover belum/gagal dimuat. */
    val tint: Int?
)

/**
 * Penyimpanan kecil yang dibaca widget. Widget bisa digambar ulang saat app sedang tidak jalan,
 * jadi statusnya disimpan di disk, bukan diambil langsung dari pemutar.
 */
object WidgetStore {
    private const val PREFS = "sonora_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"
    private const val KEY_ARTWORK_URL = "artwork_url"
    private const val KEY_TINT = "tint"
    private const val KEY_HAS_TINT = "has_tint"

    /** Cover lagu yang sedang diputar, sudah dimuat dan disimpan sebagai PNG. */
    fun coverFile(context: Context): File = File(context.filesDir, "widget_cover.png")

    fun read(context: Context): WidgetSnapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        return WidgetSnapshot(
            title = title,
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            artworkUrl = prefs.getString(KEY_ARTWORK_URL, null),
            tint = if (prefs.getBoolean(KEY_HAS_TINT, false)) prefs.getInt(KEY_TINT, 0) else null
        )
    }

    fun write(context: Context, snapshot: WidgetSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putBoolean(KEY_PLAYING, snapshot.isPlaying)
            .putString(KEY_ARTWORK_URL, snapshot.artworkUrl)
            .putBoolean(KEY_HAS_TINT, snapshot.tint != null)
            .putInt(KEY_TINT, snapshot.tint ?: 0)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        coverFile(context).delete()
    }
}
