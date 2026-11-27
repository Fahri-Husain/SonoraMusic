package com.fahrii.musicapp.desktop

import java.io.File

/** Folder data aplikasi desktop: %APPDATA%\SonoraMusic (Windows). */
internal object AppDirs {
    val data: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + File.separator + ".config")
        File(base, "SonoraMusic").also { it.mkdirs() }
    }
}