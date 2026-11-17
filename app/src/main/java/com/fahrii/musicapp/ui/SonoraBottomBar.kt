package com.fahrii.musicapp.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahrii.musicapp.R


enum class BottomTab { HOME, COLLECTION, SEARCH }

@Composable
fun SonoraBottomBar(
    selected: BottomTab,
    backdrop: GlassBackdrop,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchSelected = selected == BottomTab.SEARCH

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .glass(backdrop, CircleShape)
                .padding(6.dp),
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

    Column(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}