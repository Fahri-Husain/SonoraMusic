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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Nama album tiap lagu, untuk bagian "Your Top Albums" di Replay. Dicari sekali per lagu
 * (lewat deskripsi video YouTube Music), lalu disimpan di song_albums.json supaya tidak
 * perlu cari ulang.
 */
class AlbumRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "song_albums.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    // Maksimal 2 permintaan sekaligus, supaya tidak menembak YouTube beruntun
    private val limiter = Semaphore(2)

    private val _albums = MutableStateFlow(load())
    /** Kunci: URL lagu. Nilai: nama album. */
    val albums: StateFlow<Map<String, String>> = _albums.asStateFlow()

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>() // hanya selama aplikasi hidup, nanti dicoba lagi

    /** Minta album untuk [song]; kalau sudah ada atau sedang dicari, tidak melakukan apa-apa. */
    fun request(song: Song) {
        val key = song.url
        if (key.isEmpty() || _albums.value.containsKey(key) || key in failed) return
        if (!inFlight.add(key)) return

        scope.launch {
            try {
                val album = limiter.withPermit { MusicRepository.findAlbumName(key) }
                if (album != null) {
                    _albums.update { it + (key to album) }
                    persist()
                } else {
                    failed.add(key)
                }
            } catch (e: Exception) {
                failed.add(key)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    /**
     * Catat album untuk banyak lagu sekaligus saat albumnya sudah pasti diketahui (mis. dibuka dari
     * halaman album), tanpa perlu mencari lewat deskripsi video. Yang sudah punya album tidak ditimpa.
     */
    fun record(songUrls: Collection<String>, album: String) {
        val name = album.trim()
        if (name.isEmpty()) return
        val toAdd = songUrls.filter { it.isNotEmpty() && !_albums.value.containsKey(it) }
        if (toAdd.isEmpty()) return

        _albums.update { current -> current + toAdd.associateWith { name } }
        scope.launch { persist() }
    }

    private suspend fun persist() {
        saveMutex.withLock {
            runCatching {
                val o = JSONObject()
                _albums.value.forEach { (k, v) -> o.put(k, v) }
                file.writeText(o.toString())
            }
        }
    }

    private fun load(): Map<String, String> = try {
        val o = JSONObject(file.readText())
        buildMap { o.keys().forEach { k -> put(k, o.getString(k)) } }
    } catch (e: Exception) {
        emptyMap()
    }

    companion object {
        @Volatile
        private var instance: AlbumRepository? = null

        fun getInstance(context: Context): AlbumRepository =
            instance ?: synchronized(this) {
                instance ?: AlbumRepository(context.applicationContext).also { instance = it }
            }
    }
}