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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Foto profil artis (bukan thumbnail lagu). Dicari sekali lewat YouTube Music,
 * lalu disimpan di artist_images.json supaya tidak perlu cari ulang.
 */
class ArtistImageRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "artist_images.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private val _images = MutableStateFlow(load())
    /** Kunci: [keyOf] nama artis. Nilai: URL foto profil. */
    val images: StateFlow<Map<String, String>> = _images.asStateFlow()

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>() // hanya selama aplikasi hidup, nanti dicoba lagi

    /** Minta foto artis [name]; kalau sudah ada atau sedang dicari, tidak melakukan apa-apa. */
    fun request(name: String) {
        val key = keyOf(name)
        if (key.isEmpty() || _images.value.containsKey(key) || key in failed) return
        if (!inFlight.add(key)) return

        scope.launch {
            try {
                val url = MusicRepository.findArtistImage(name.trim())
                if (url != null) {
                    _images.update { it + (key to url) }
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

    private suspend fun persist() {
        saveMutex.withLock {
            runCatching {
                val o = JSONObject()
                _images.value.forEach { (k, v) -> o.put(k, v) }
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
        fun keyOf(name: String): String = name.trim().lowercase()

        @Volatile
        private var instance: ArtistImageRepository? = null

        fun getInstance(context: Context): ArtistImageRepository =
            instance ?: synchronized(this) {
                instance ?: ArtistImageRepository(context.applicationContext).also { instance = it }
            }
    }
}
