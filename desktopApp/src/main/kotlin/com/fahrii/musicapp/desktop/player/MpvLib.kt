package com.fahrii.musicapp.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File

/** Bagian kecil dari API C libmpv yang kita pakai. */
internal interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_free(data: Pointer)
    fun mpv_error_string(error: Int): String
}

internal object MpvLoader {

    private val DLL_NAMES = listOf("libmpv-2.dll", "mpv-2.dll")

    /** Lokasi yang dicek, berurutan. Pesan error menampilkan semuanya supaya gampang dilacak. */
    fun searchPaths(): List<File> = buildList {
        val resDir = System.getProperty("compose.application.resources.dir")
        if (resDir != null) DLL_NAMES.forEach { add(File(resDir, it)) }
        DLL_NAMES.forEach { add(File("desktopApp/resources/windows-x64", it)) }
        DLL_NAMES.forEach { add(File("resources/windows-x64", it)) }
    }

    fun load(): MpvLib {
        val file = searchPaths().firstOrNull { it.isFile }
            ?: throw IllegalStateException(
                "libmpv-2.dll tidak ditemukan. Taruh di desktopApp/resources/windows-x64/. " +
                        "Lokasi yang dicek:\n" + searchPaths().joinToString("\n") { " - " + it.absolutePath }
            )
        return Native.load(
            file.absolutePath,
            MpvLib::class.java,
            mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")
        )
    }
}