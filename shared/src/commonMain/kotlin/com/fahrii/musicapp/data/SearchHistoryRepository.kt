package com.fahrii.musicapp.data

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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Riwayat kata kunci pencarian: yang terbaru di paling atas, tanpa duplikat, disimpan di file.
 * [dir] adalah folder penyimpanan: di Android filesDir, di desktop folder AppData.
 */
class SearchHistoryRepository(dir: File) {

    private val file = File(dir, "search_history.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private val serializer = ListSerializer(String.serializer())

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
                    file.writeText(Json.encodeToString(serializer, _items.value))
                }
            }
        }
    }

    private fun load(): List<String> = try {
        Json.decodeFromString(serializer, file.readText())
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val MAX_ITEMS = 20
    }
}