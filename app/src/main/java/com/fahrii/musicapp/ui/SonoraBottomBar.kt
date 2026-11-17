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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahrii.musicapp.R


enum class BottomTab { HOME, COLLECTION, SEARCH }

private val BarItemWidth = 96.dp

@Composable
fun SonoraBottomBar(
    selected: BottomTab,
    backdrop: GlassBackdrop,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(64.dp)
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

        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .glass(backdrop, CircleShape)
                .then(
                    if (searchSelected) {
                        Modifier.background(Color.White.copy(alpha = 0.12f), CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable { onSelect(BottomTab.SEARCH) },
            contentAlignment = Alignment.Center
        ) {
            NavIcon(
                on = R.drawable.ic_search,
                off = R.drawable.ic_search,
                selected = searchSelected
            )
        }
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

    // Latar pill digambar terpisah di SonoraBottomBar supaya bisa bergeser antar item
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