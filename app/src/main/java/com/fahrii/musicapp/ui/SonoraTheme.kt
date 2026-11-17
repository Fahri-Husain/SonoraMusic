package com.fahrii.musicapp.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Sonora {
    val Background = Color(0xFF111113)
    val Surface = Color(0xFF1B1B1E)
    val SurfaceHigh = Color(0xFF28282C)
    val Border = Color(0xFF3A3A40)
    val TextSecondary = Color(0xFF8E8E93)
    val Accent = Color(0xFFCDB8FF)   // ungu lavender, senada garis diagonal & kartu genre di Beranda
}

val SonoraColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Sonora.Background,
    onBackground = Color.White,
    surface = Sonora.Surface,
    onSurface = Color.White,
    surfaceVariant = Sonora.SurfaceHigh,
    onSurfaceVariant = Sonora.TextSecondary,
    surfaceTint = Color.Transparent,
    outline = Sonora.Border
)

/** Tinggi bar mengambang di bawah, supaya daftar bisa di-scroll sampai tidak tertutup. */
val LocalBottomInset = compositionLocalOf<Dp> { 0.dp }