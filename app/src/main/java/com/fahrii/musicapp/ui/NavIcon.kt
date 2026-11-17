package com.fahrii.musicapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun NavIcon(
    on: Int,
    off: Int,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(if (selected) on else off),
        contentDescription = null,
        // PNG-nya putih, jadi warnanya diatur di sini
        colorFilter = ColorFilter.tint(if (selected) Color.White else Sonora.TextSecondary),
        modifier = modifier.size(26.dp)
    )
}