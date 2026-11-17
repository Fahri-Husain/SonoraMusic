package com.fahrii.musicapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.CaretDown
import com.adamglin.phosphoricons.bold.CaretLeft
import com.adamglin.phosphoricons.bold.CaretRight
import com.adamglin.phosphoricons.bold.ListPlus
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.bold.Repeat
import com.adamglin.phosphoricons.bold.RepeatOnce
import com.adamglin.phosphoricons.bold.Shuffle
import com.adamglin.phosphoricons.fill.Books
import com.adamglin.phosphoricons.fill.FastForward
import com.adamglin.phosphoricons.fill.Heart as HeartFill
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.fill.Rewind
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.regular.MusicNotes
import com.adamglin.phosphoricons.regular.Trash
import com.adamglin.phosphoricons.bold.ClockCounterClockwise
import com.adamglin.phosphoricons.fill.Gear
import com.adamglin.phosphoricons.fill.House
import com.adamglin.phosphoricons.fill.SkipBack
import com.adamglin.phosphoricons.fill.SkipForward

val HeartColor = Color(0xFFFF2D55) // pink khas iOS

object AppIcons {
    val Search: ImageVector = PhosphorIcons.Bold.MagnifyingGlass
    val Back: ImageVector = PhosphorIcons.Bold.CaretDown
    val BackArrow: ImageVector = PhosphorIcons.Bold.CaretLeft
    val ChevronRight: ImageVector = PhosphorIcons.Bold.CaretRight
    val Shuffle: ImageVector = PhosphorIcons.Bold.Shuffle
    val Repeat: ImageVector = PhosphorIcons.Bold.Repeat
    val RepeatOne: ImageVector = PhosphorIcons.Bold.RepeatOnce
    val Plus: ImageVector = PhosphorIcons.Bold.Plus
    val PlaylistAdd: ImageVector = PhosphorIcons.Bold.ListPlus

    val Play: ImageVector = PhosphorIcons.Fill.Play
    val Pause: ImageVector = PhosphorIcons.Fill.Pause
    val Previous: ImageVector = PhosphorIcons.Fill.Rewind
    val Next: ImageVector = PhosphorIcons.Fill.FastForward
    val HeartFilled: ImageVector = PhosphorIcons.Fill.HeartFill
    val Library: ImageVector = PhosphorIcons.Fill.Books

    val Heart: ImageVector = PhosphorIcons.Regular.Heart
    val Trash: ImageVector = PhosphorIcons.Regular.Trash
    val MusicNote: ImageVector = PhosphorIcons.Regular.MusicNotes

    val Home: ImageVector = PhosphorIcons.Fill.House
    val Settings: ImageVector = PhosphorIcons.Fill.Gear
    val History: ImageVector = PhosphorIcons.Bold.ClockCounterClockwise
    val SkipPrevious: ImageVector = PhosphorIcons.Fill.SkipBack
    val SkipNext: ImageVector = PhosphorIcons.Fill.SkipForward
}