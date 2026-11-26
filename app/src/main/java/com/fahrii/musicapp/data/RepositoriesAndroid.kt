package com.fahrii.musicapp.data

import android.content.Context
import android.content.SharedPreferences

@Volatile
private var historyInstance: HistoryRepository? = null

@Volatile
private var replayInstance: ReplayRepository? = null

fun HistoryRepository.Companion.getInstance(context: Context): HistoryRepository =
    historyInstance ?: synchronized(HistoryRepository) {
        historyInstance ?: HistoryRepository(context.applicationContext.filesDir)
            .also { historyInstance = it }
    }

fun ReplayRepository.Companion.getInstance(context: Context): ReplayRepository =
    replayInstance ?: synchronized(ReplayRepository) {
        replayInstance ?: ReplayRepository(context.applicationContext.filesDir)
            .also { replayInstance = it }
    }

@Volatile
private var libraryInstance: LibraryRepository? = null

fun LibraryRepository.Companion.getInstance(context: Context): LibraryRepository =
    libraryInstance ?: synchronized(LibraryRepository) {
        libraryInstance ?: LibraryRepository(context.applicationContext.filesDir)
            .also { libraryInstance = it }
    }

/** Membungkus SharedPreferences "settings" yang sama dengan versi lama, jadi pengaturan lama tetap terbaca. */
private class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}

@Volatile
private var settingsInstance: SettingsRepository? = null

fun SettingsRepository.Companion.getInstance(context: Context): SettingsRepository =
    settingsInstance ?: synchronized(SettingsRepository) {
        settingsInstance ?: SettingsRepository(
            SharedPrefsStore(
                context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            )
        ).also { settingsInstance = it }
    }