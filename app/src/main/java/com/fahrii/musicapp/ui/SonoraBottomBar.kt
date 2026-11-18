package com.fahrii.musicapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.fahrii.musicapp.R


enum class BottomTab { HOME, COLLECTION, SEARCH }

private val BarItemWidth = 96.dp
private val CircleSize = 64.dp
private val SideMargin = 12.dp
private val ItemGap = 12.dp
private val RowTop = 6.dp
private val RowBottom = 10.dp

/** Penanda tiap bagian bar di dalam Layout (MiniPlayer bisa tidak ada kalau belum ada lagu). */
private enum class BarSlot { Mini, Pill, Circle }

/**
 * Bar mengambang di bawah: mini player, pill Beranda/Koleksi, dan bulatan cari.
 *
 * - [compact] = false (Beranda, Koleksi, dll.): mini player di atas, di bawahnya pill + bulatan cari.
 * - [compact] = true (halaman Cari): bulatan cari di kiri, mini player di kanannya, pill hilang.
 *
 * Perpindahan keduanya dianimasikan: bulatan cari bergeser ke kiri, mini player turun dan
 * bergeser ke kanan (lebarnya menyusut dari sisi kiri), pill memudar.
 *
 * [onSelect] dipanggil untuk Beranda/Koleksi; bulatan cari memanggil [onSearchClick].
 */
@Composable
fun SonoraBottomBars(
    selected: BottomTab,
    compact: Boolean,
    playerState: PlayerUiState,
    backdrop: GlassBackdrop,
    onSelect: (BottomTab) -> Unit,
    onSearchClick: () -> Unit,
    onToggle: () -> Unit,
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 0f = tampilan penuh, 1f = ringkas. Dibaca di fase layout/draw saja, tanpa recompose per frame
    val progress = animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "barCompact"
    )

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        content = {
            MiniPlayer(
                state = playerState,
                backdrop = backdrop,
                onToggle = onToggle,
                onClick = onPlayerClick,
                modifier = Modifier.layoutId(BarSlot.Mini)
            )
            Box(
                modifier = Modifier
                    .layoutId(BarSlot.Pill)
                    .graphicsLayer { alpha = (1f - progress.value * 1.5f).coerceIn(0f, 1f) }
            ) {
                NavPill(selected = selected, backdrop = backdrop, onSelect = onSelect)
            }
            Box(modifier = Modifier.layoutId(BarSlot.Circle)) {
                SearchCircle(
                    searchSelected = selected == BottomTab.SEARCH,
                    backdrop = backdrop,
                    onClick = onSearchClick
                )
            }
        }
    ) { measurables, constraints ->
        val t = progress.value.coerceIn(0f, 1f)
        val maxW = constraints.maxWidth
        val margin = SideMargin.roundToPx()
        val circleSize = CircleSize.roundToPx()
        val gap = ItemGap.roundToPx()
        val rowTop = RowTop.roundToPx()
        val rowBottom = RowBottom.roundToPx()

        val loose = Constraints(maxWidth = maxW)
        val pill = measurables.first { it.layoutId == BarSlot.Pill }.measure(loose)
        val circle = measurables.first { it.layoutId == BarSlot.Circle }.measure(loose)

        // Mini player: penuh lebar saat tampilan penuh; saat ringkas sisi kirinya
        // bergeser ke kanan bulatan cari (padding 12dp miliknya jadi jarak ke bulatan)
        val miniX = lerp(0, margin + circleSize, t)
        val mini = measurables
            .firstOrNull { it.layoutId == BarSlot.Mini }
            ?.measure(Constraints.fixedWidth(maxW - miniX))
        val miniH = mini?.height ?: 0

        // Tinggi: tampilan penuh = mini player + baris pill; ringkas = satu baris
        val compactRowH = maxOf(circleSize, miniH)
        val fullH = miniH + rowTop + circleSize + rowBottom
        val compactH = rowTop + compactRowH + rowBottom
        val totalH = lerp(fullH, compactH, t)

        // Posisi baris pill + bulatan: di tengah saat penuh, bulatan ke tepi kiri saat ringkas
        val fullStartX = (maxW - (pill.width + gap + circle.width)) / 2
        val circleX = lerp(fullStartX + pill.width + gap, margin, t)
        val rowY = lerp(miniH + rowTop, rowTop + (compactRowH - circleSize) / 2, t)
        val miniY = lerp(0, rowTop + (compactRowH - miniH) / 2, t)

        layout(maxW, totalH) {
            mini?.place(miniX, miniY)
            // Pill sudah transparan saat ringkas; tidak ditempatkan supaya tidak menangkap sentuhan
            if (t < 1f) pill.place(fullStartX, rowY)
            circle.place(circleX, rowY)
        }
    }
}

/** Pill Beranda/Koleksi dengan penanda tab aktif yang bergeser di antara keduanya. */
@Composable
private fun NavPill(
    selected: BottomTab,
    backdrop: GlassBackdrop,
    onSelect: (BottomTab) -> Unit
) {
    val searchSelected = selected == BottomTab.SEARCH

    // Posisi pill terakhir di Beranda/Koleksi; saat tab Cari aktif pill memudar di tempatnya
    var pillTab by remember { mutableStateOf(if (searchSelected) BottomTab.HOME else selected) }
    if (!searchSelected && pillTab != selected) pillTab = selected

    val pillOffset by animateDpAsState(
        targetValue = if (pillTab == BottomTab.COLLECTION) BarItemWidth else 0.dp,
        animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "pillOffset"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (searchSelected) 0f else 1f,
        animationSpec = tween(200),
        label = "pillAlpha"
    )

    Box(
        modifier = Modifier
            .height(CircleSize)
            .glass(backdrop, CircleShape)
            .padding(6.dp)
    ) {
        // Pill penanda tab aktif: satu elemen yang bergeser di antara Beranda <-> Koleksi
        Box(
            modifier = Modifier
                .offset { IntOffset(pillOffset.roundToPx(), 0) }
                .width(BarItemWidth)
                .fillMaxHeight()
                .graphicsLayer { alpha = pillAlpha }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        )

        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem(
                icon = {
                    NavIcon(
                        on = R.drawable.ic_home_on,
                        off = R.drawable.ic_home_off,
                        selected = selected == BottomTab.HOME
                    )
                },
                label = "Beranda",
                selected = selected == BottomTab.HOME
            ) {
                onSelect(BottomTab.HOME)
            }

            BarItem(
                icon = {
                    NavIcon(
                        on = R.drawable.ic_collection_on,
                        off = R.drawable.ic_collection_off,
                        selected = selected == BottomTab.COLLECTION
                    )
                },
                label = "Koleksi",
                selected = selected == BottomTab.COLLECTION
            ) {
                onSelect(BottomTab.COLLECTION)
            }
        }
    }
}

/** Bulatan cari; latarnya menyala tipis saat halaman Cari sedang aktif. */
@Composable
private fun SearchCircle(
    searchSelected: Boolean,
    backdrop: GlassBackdrop,
    onClick: () -> Unit
) {
    val highlight by animateFloatAsState(
        targetValue = if (searchSelected) 0.12f else 0f,
        animationSpec = tween(200),
        label = "searchHighlight"
    )

    Box(
        modifier = Modifier
            .size(CircleSize)
            .glass(backdrop, CircleShape)
            .background(Color.White.copy(alpha = highlight), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        NavIcon(
            on = R.drawable.ic_search,
            off = R.drawable.ic_search,
            selected = searchSelected
        )
    }
}

@Composable
private fun BarItem(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f)

    // Latar pill digambar terpisah di NavPill supaya bisa bergeser antar item
    Column(
        modifier = Modifier
            .width(BarItemWidth)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}