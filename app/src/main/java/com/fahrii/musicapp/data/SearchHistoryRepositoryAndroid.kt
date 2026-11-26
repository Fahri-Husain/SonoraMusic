package com.fahrii.musicapp.data

import android.content.Context

@Volatile
private var searchHistoryInstance: SearchHistoryRepository? = null

/** Pintu masuk Android: satu instance untuk seluruh aplikasi, disimpan di filesDir. */
fun SearchHistoryRepository.Companion.getInstance(context: Context): SearchHistoryRepository =
    searchHistoryInstance ?: synchronized(SearchHistoryRepository) {
        searchHistoryInstance ?: SearchHistoryRepository(context.applicationContext.filesDir)
            .also { searchHistoryInstance = it }
    }