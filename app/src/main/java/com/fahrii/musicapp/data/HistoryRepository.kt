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
import org.json.JSONObject
import java.io.File

data class ArtistStat(
    val name: String,
    val plays: Int,
    val imageUrl: String?
)

class HistoryRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "history.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private val _artists = MutableStateFlow(load())
    /** Artis yang pernah diputar, diurutkan dari yang paling sering. */
    val artists: StateFlow<List<ArtistStat>> = _artists.asStateFlow()

    fun record(song: Song) {
        val name = song.artist.trim()
        if (name.isEmpty() || name == "Unknown artist") return

        _artists.update { list ->
            val existing = list.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val updated = if (existing == null) {
                ArtistStat(name, 1, song.thumbnailUrl)
            } else {
                existing.copy(
                    plays = existing.plays + 1,
                    imageUrl = song.thumbnailUrl ?: existing.imageUrl
                )
            }
            (list.filterNot { it === existing } + updated).sortedByDescending { it.plays }
        }
        persist()
    }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                runCatching {
                    val arr = JSONArray()
                    _artists.value.forEach { a ->
                        arr.put(JSONObject().apply {
                            put("name", a.name)
                            put("plays", a.plays)
                            put("imageUrl", a.imageUrl ?: JSONObject.NULL)
                        })
                    }
                    file.writeText(arr.toString())
                }
            }
        }
    }

    private fun load(): List<ArtistStat> = try {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ArtistStat(
                name = o.getString("name"),
                plays = o.optInt("plays", 1),
                imageUrl = if (o.isNull("imageUrl")) null else o.getString("imageUrl")
            )
        }.sortedByDescending { it.plays }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        @Volatile
        private var instance: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}