package com.fahrii.musicapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File

/** Riwayat kata kunci pencarian: yang terbaru di paling atas, tanpa duplikat, disimpan di file. */
class SearchHistoryRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "search_history.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    /** Catat satu pencarian; kalau sudah ada, dipindah ke paling atas. */
    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _items.update { list ->
            (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_ITEMS)
        }
        persist()
    }

    fun remove(query: String) {
        _items.update { list -> list.filterNot { it.equals(query, ignoreCase = true) } }
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                runCatching {
                    val arr = JSONArray()
                    _items.value.forEach { arr.put(it) }
                    file.writeText(arr.toString())
                }
            }
        }
    }

    private fun load(): List<String> = try {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i -> arr.getString(i) }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val MAX_ITEMS = 20

        @Volatile
        private var instance: SearchHistoryRepository? = null

        fun getInstance(context: Context): SearchHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: SearchHistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}
