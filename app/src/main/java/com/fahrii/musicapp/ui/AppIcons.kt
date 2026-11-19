package com.fahrii.musicapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.ArrowCircleDown
import com.adamglin.phosphoricons.bold.ArrowCounterClockwise
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.adamglin.phosphoricons.bold.TrendUp
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
import com.adamglin.phosphoricons.bold.CaretUp
import com.adamglin.phosphoricons.bold.SortAscending
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.fill.ArrowsClockwise
import com.adamglin.phosphoricons.fill.ClockClockwise
import com.adamglin.phosphoricons.fill.Disc
import com.adamglin.phosphoricons.fill.ImageSquare
import com.adamglin.phosphoricons.fill.Pencil
import com.adamglin.phosphoricons.fill.PlayCircle
import com.adamglin.phosphoricons.fill.Playlist
import com.adamglin.phosphoricons.fill.Radio
import com.adamglin.phosphoricons.fill.ShareNetwork
import com.adamglin.phosphoricons.fill.Trash as TrashFill
import com.adamglin.phosphoricons.fill.Users

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

    val Replay: ImageVector = PhosphorIcons.Bold.ArrowCounterClockwise
    val Trending: ImageVector = PhosphorIcons.Bold.TrendUp
    val Download: ImageVector = PhosphorIcons.Bold.ArrowCircleDown
    val MoreVert: ImageVector = PhosphorIcons.Bold.DotsThreeVertical

    // Halaman playlist: tombol kecil (Bold) dan ikon menu bottom sheet (Fill)
    val Sort: ImageVector = PhosphorIcons.Bold.SortAscending
    val MoveUp: ImageVector = PhosphorIcons.Bold.CaretUp
    val MoveDown: ImageVector = PhosphorIcons.Bold.CaretDown
    val Close: ImageVector = PhosphorIcons.Bold.X

    val Edit: ImageVector = PhosphorIcons.Fill.Pencil
    val EditImage: ImageVector = PhosphorIcons.Fill.ImageSquare
    val Queue: ImageVector = PhosphorIcons.Fill.Playlist
    val Sync: ImageVector = PhosphorIcons.Fill.ArrowsClockwise
    val Refresh: ImageVector = PhosphorIcons.Fill.ClockClockwise
    val DeleteFilled: ImageVector = PhosphorIcons.Fill.TrashFill
    val Share: ImageVector = PhosphorIcons.Fill.ShareNetwork
    val Artist: ImageVector = PhosphorIcons.Fill.Users
    val Album: ImageVector = PhosphorIcons.Fill.Disc
    val Radio: ImageVector = PhosphorIcons.Fill.Radio
    val PlayNext: ImageVector = PhosphorIcons.Fill.PlayCircle
}