package com.fahrii.musicapp.data

data class Song(
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long
)