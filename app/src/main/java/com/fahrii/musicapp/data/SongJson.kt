package com.fahrii.musicapp.data

import org.json.JSONObject

internal fun Song.toJson(): JSONObject = JSONObject().apply {
    put("url", url)
    put("title", title)
    put("artist", artist)
    put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
    put("durationSeconds", durationSeconds)
}

internal fun JSONObject.toSong() = Song(
    url = getString("url"),
    title = getString("title"),
    artist = getString("artist"),
    thumbnailUrl = if (isNull("thumbnailUrl")) null else getString("thumbnailUrl"),
    durationSeconds = optLong("durationSeconds", -1L)
)