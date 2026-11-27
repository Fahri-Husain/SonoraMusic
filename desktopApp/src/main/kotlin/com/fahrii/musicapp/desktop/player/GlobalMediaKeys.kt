package com.fahrii.musicapp.desktop.player

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/**
 * Tombol media global Windows (Play/Pause, Next, Prev, Stop) lewat RegisterHotKey.
 * Berjalan di thread sendiri karena WM_HOTKEY hanya sampai ke thread yang mendaftarkannya.
 * Di sistem selain Windows, [start] tidak melakukan apa-apa.
 */
internal class GlobalMediaKeys(
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onStop: () -> Unit
) {
    @Volatile private var threadId = 0

    fun start() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        Thread({ loop() }, "SonoraMediaKeys").apply { isDaemon = true }.start()
    }

    fun stop() {
        val id = threadId
        if (id != 0) {
            User32.INSTANCE.PostThreadMessage(id, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        }
    }

    private fun loop() {
        val user32 = User32.INSTANCE
        threadId = Kernel32.INSTANCE.GetCurrentThreadId()

        val actions = mapOf(
            ID_PLAY_PAUSE to onPlayPause,
            ID_NEXT to onNext,
            ID_PREVIOUS to onPrevious,
            ID_STOP to onStop
        )
        val keys = mapOf(
            ID_PLAY_PAUSE to VK_MEDIA_PLAY_PAUSE,
            ID_NEXT to VK_MEDIA_NEXT_TRACK,
            ID_PREVIOUS to VK_MEDIA_PREV_TRACK,
            ID_STOP to VK_MEDIA_STOP
        )
        keys.forEach { (id, vk) ->
            if (!user32.RegisterHotKey(null, id, MOD_NOREPEAT, vk)) {
                println("Media key (id=$id) gagal didaftarkan, mungkin sudah dipakai aplikasi lain.")
            }
        }

        val msg = WinUser.MSG()
        while (user32.GetMessage(msg, null, 0, 0) > 0) {
            if (msg.message == WM_HOTKEY) {
                actions[msg.wParam.toInt()]?.invoke()
            }
        }

        keys.keys.forEach { user32.UnregisterHotKey(null, it) }
        threadId = 0
    }

    private companion object {
        const val WM_HOTKEY = 0x0312
        const val WM_QUIT = 0x0012
        const val MOD_NOREPEAT = 0x4000

        const val VK_MEDIA_NEXT_TRACK = 0xB0
        const val VK_MEDIA_PREV_TRACK = 0xB1
        const val VK_MEDIA_STOP = 0xB2
        const val VK_MEDIA_PLAY_PAUSE = 0xB3

        const val ID_PLAY_PAUSE = 1
        const val ID_NEXT = 2
        const val ID_PREVIOUS = 3
        const val ID_STOP = 4
    }
}