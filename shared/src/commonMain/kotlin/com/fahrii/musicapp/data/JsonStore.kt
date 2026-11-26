package com.fahrii.musicapp.data

import kotlinx.serialization.json.Json

/** Pengaturan JSON untuk file penyimpanan: kunci yang tak dikenal diabaikan, semua kunci ditulis. */
internal val StoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}