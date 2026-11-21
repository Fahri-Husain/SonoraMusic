package com.fahrii.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.fahrii.musicapp.data.Song
import kotlinx.coroutines.launch

private val QueueSheetColor = Color(0xFF242424)

/** Satu baris antrean dengan id tetap, supaya baris tetap "sama" walau urutannya berpindah saat digeser. */
private data class QueueEntry(val id: Long, val song: Song)

/**
 * Daftar antrean pemutar. Ketuk lagu untuk memutarnya, ketuk X untuk menghapus, dan tahan lalu
 * geser ikon garis di kanan untuk memindahkan urutan. Lagu yang sedang diputar ditandai
 * (dengan bar ritme di cover saat sedang bermain) dan tidak bisa dihapus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onPlayItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    )

    // Salinan lokal antrean: dipindah langsung saat digeser (tanpa menunggu pemutar),
    // lalu diselaraskan lagi dengan pemutar setiap antreannya berubah.
    val idCounter = remember { longArrayOf(0L) }
    fun build(list: List<Song>) = list.map { QueueEntry(idCounter[0]++, it) }
    val entries = remember { mutableStateListOf<QueueEntry>().apply { addAll(build(queue)) } }
    var currentId by remember { mutableStateOf(entries.getOrNull(currentIndex)?.id) }

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(queue, currentIndex) {
        if (draggingId != null) return@LaunchedEffect
        if (entries.map { it.song.url } != queue.map { it.url }) {
            entries.clear()
            entries.addAll(build(queue))
        }
        currentId = entries.getOrNull(currentIndex)?.id
    }

    fun onDragDelta(dy: Float) {
        val id = draggingId ?: return
        dragOffset += dy
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        // Tata letak belum menyusul pindahan terakhir: tunggu frame berikutnya supaya tidak tertukar dua kali
        if (entries.getOrNull(item.index)?.id != id) return

        val center = item.offset + item.size / 2f + dragOffset
        val target = info.visibleItemsInfo.firstOrNull {
            it.index != item.index && center >= it.offset && center <= it.offset + it.size
        }
        if (target != null) {
            val from = item.index
            val to = target.index
            entries.add(to, entries.removeAt(from))
            onMoveItem(from, to)
            // Baris sudah pindah tempat di tata letak, jadi geseran jari dikurangi sejauh itu
            dragOffset -= (target.offset - item.offset)
            return
        }

        // Gulir otomatis saat baris yang diseret mendekati tepi atas/bawah daftar
        val top = item.offset + dragOffset
        val bottom = top + item.size
        val threshold = item.size * 0.6f
        val step = when {
            top < info.viewportStartOffset + threshold -> -16f
            bottom > info.viewportEndOffset - threshold -> 16f
            else -> 0f
        }
        if (step != 0f) {
            scope.launch { dragOffset += listState.scrollBy(step) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = QueueSheetColor,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Antrean",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "${entries.size} lagu",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(bottom = 16.dp)
            ) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { _, entry ->
                    val isDragging = entry.id == draggingId
                    QueueRow(
                        song = entry.song,
                        isCurrent = entry.id == currentId,
                        isPlaying = isPlaying,
                        isDragging = isDragging,
                        onClick = {
                            val i = entries.indexOfFirst { it.id == entry.id }
                            if (i >= 0) onPlayItem(i)
                        },
                        onRemove = {
                            val i = entries.indexOfFirst { it.id == entry.id }
                            if (i >= 0) {
                                entries.removeAt(i)
                                onRemoveItem(i)
                            }
                        },
                        onDragStart = {
                            draggingId = entry.id
                            dragOffset = 0f
                        },
                        onDrag = { dy -> onDragDelta(dy) },
                        onDragEnd = {
                            draggingId = null
                            dragOffset = 0f
                        },
                        modifier = if (isDragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = dragOffset }
                        } else {
                            Modifier.animateItem()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    // Kalau baris yang sedang diseret hilang dari komposisi, lepaskan status seret
    if (isDragging) {
        DisposableEffect(Unit) { onDispose { currentOnDragEnd() } }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                when {
                    isDragging -> Color(0xFF353535)
                    isCurrent -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sama seperti daftar lagu lain: lagu yang sedang bermain cover-nya diganti bar ritme
        if (isCurrent && isPlaying) {
            PlayingThumb(isPlaying = isPlaying, size = 48.dp)
        } else {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Sonora.Surface)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artistLabel(song),
                color = Sonora.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Slot X selalu ada (kosong untuk lagu yang sedang diputar) supaya handle sejajar di semua baris
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .then(if (isCurrent) Modifier else Modifier.clickable(onClick = onRemove)),
            contentAlignment = Alignment.Center
        ) {
            if (!isCurrent) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "Hapus dari antrean",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // Handle geser: hanya area ini yang memulai drag, jadi ketukan di baris tetap memutar lagu
        Box(
            modifier = Modifier
                .size(40.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { currentOnDragStart() },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentOnDrag(dragAmount.y)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Geser untuk memindahkan",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}