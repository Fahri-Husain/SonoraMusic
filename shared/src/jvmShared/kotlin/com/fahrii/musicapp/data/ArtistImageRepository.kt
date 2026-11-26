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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach

/**
 * Foto profil artis (bukan thumbnail lagu). Dicari sekali lewat YouTube Music,
 * lalu disimpan di artist_images.json supaya tidak perlu cari ulang.
 */
class ArtistImageRepository(dir: File) {

    private val file = File(dir, "artist_images.json")
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
                file.writeText(StoreJson.encodeToString(artistImageMapSerializer, _images.value))
            }
        }
    }

    private fun load(): Map<String, String> = try {
        StoreJson.decodeFromString(artistImageMapSerializer, file.readText())
    } catch (e: Exception) {
        emptyMap()
    }

    companion object {
        fun keyOf(name: String): String = name.trim().lowercase()
    }
}

private val artistImageMapSerializer = MapSerializer(String.serializer(), String.serializer())

