package com.fahrii.musicapp.data

import android.content.Context

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