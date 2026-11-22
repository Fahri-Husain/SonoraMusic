package com.fahrii.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahrii.musicapp.data.EqPreset
import com.fahrii.musicapp.data.EqualizerBands
import com.fahrii.musicapp.data.EqualizerState
import java.util.Locale
import kotlin.math.roundToInt

// Warna sama dengan SettingsScreen (di sana private), supaya halaman ini terasa satu keluarga
private val SectionLabelColor = Color(0xFF6E6E76)
private val Hairline = Color.White.copy(alpha = 0.06f)
private val ChipBackground = Color.White.copy(alpha = 0.05f)
private val ChipBorder = Color.White.copy(alpha = 0.07f)
private val Ink = Color(0xFFF2F2F4)
private val ControlOffTrack = Color(0xFF2E2E33)

private val BandTrackHeight = 176.dp
private val BandThumbRadius = 9.dp

/**
 * Halaman Equalizer: saklar utama, preset, lima pita frekuensi, dan bass boost.
 * Pengaturan langsung terdengar saat digeser (lihat EqualizerController di PlaybackService).
 */
@Composable
fun EqualizerScreen(
    state: EqualizerState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelect: (EqPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val backdrop = rememberGlassBackdrop()
    var titleBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Kontrol tetap bisa diatur saat equalizer mati (untuk menyiapkan dulu), hanya diredupkan
    val controlsAlpha = if (state.enabled) 1f else 0.5f

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .background(Sonora.Background),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBarTop + 56.dp,
                bottom = LocalBottomInset.current + 16.dp
            )
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        text = "Equalizer",
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .onGloballyPositioned { titleBottom = it.boundsInRoot().bottom }
                    )

                    // Saklar utama
                    EqCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aktifkan equalizer",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.1).sp
                                )
                                Text(
                                    text = if (state.enabled) "Aktif untuk semua lagu"
                                    else "Mati, nyalakan untuk mendengar efeknya",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = Sonora.TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            SonoraSwitch(
                                checked = state.enabled,
                                onCheckedChange = onEnabledChange,
                                label = "Equalizer"
                            )
                        }
                    }

                    // Preset
                    Column(modifier = Modifier.alpha(controlsAlpha)) {
                        SectionTitle("Preset")
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EqPreset.selectable.forEach { preset ->
                                PresetChip(
                                    label = preset.label,
                                    selected = state.preset == preset,
                                    onClick = { onPresetSelect(preset) }
                                )
                            }
                            // Muncul hanya saat pita sudah digeser sendiri; ketukannya tidak melakukan apa-apa
                            if (state.preset == EqPreset.CUSTOM) {
                                PresetChip(label = EqPreset.CUSTOM.label, selected = true, onClick = {})
                            }
                        }
                    }

                    // Lima pita frekuensi
                    Column(modifier = Modifier.alpha(controlsAlpha)) {
                        SectionTitle("Pita frekuensi")
                        EqCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 16.dp)
                            ) {
                                for (index in 0 until EqualizerBands.COUNT) {
                                    BandSlider(
                                        label = EqualizerBands.LABELS[index],
                                        valueDb = state.gainsDb.getOrElse(index) { 0f },
                                        onChange = { db -> onBandChange(index, db) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Text(
                                text = "Kalau suara terdengar pecah, turunkan pita yang dinaikkan atau kecilkan volume.",
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = Sonora.TextSecondary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                            )
                        }
                    }

                    // Bass boost
                    Column(modifier = Modifier.alpha(controlsAlpha)) {
                        SectionTitle("Bass boost")
                        EqCard {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Kekuatan",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${state.bassBoost / 10}%",
                                        fontSize = 13.sp,
                                        color = Sonora.TextSecondary
                                    )
                                }
                                Slider(
                                    value = state.bassBoost / 10f,
                                    onValueChange = { onBassBoostChange((it * 10f).roundToInt()) },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Ink,
                                        activeTrackColor = Ink,
                                        inactiveTrackColor = ControlOffTrack
                                    ),
                                    modifier = Modifier.semantics { contentDescription = "Kekuatan bass boost" }
                                )
                                Text(
                                    text = "Tidak semua perangkat mendukung bass boost.",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = Sonora.TextSecondary
                                )
                            }
                        }
                    }

                    // Atur ulang
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(ChipBackground)
                            .border(1.dp, ChipBorder, RoundedCornerShape(50))
                            .clickable(onClick = onReset)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Atur ulang ke Flat",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = Ink
                        )
                    }
                }
            }
        }

        HeroTopBars(
            title = "Equalizer",
            listState = listState,
            titleBottom = titleBottom,
            backdrop = backdrop,
            onBack = onBack
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.3.sp,
        color = SectionLabelColor,
        modifier = Modifier.padding(start = 8.dp, bottom = 9.dp)
    )
}

@Composable
private fun EqCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Sonora.Surface)
            .border(1.dp, Hairline, shape),
        content = content
    )
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Ink else ChipBackground)
            .border(1.dp, if (selected) Ink else ChipBorder, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Sonora.Background else Ink
        )
    }
}

/**
 * Satu pita: slider vertikal dari -12 dB (bawah) sampai +12 dB (atas), 0 dB di tengah.
 * Nilai dibulatkan ke kelipatan 0,5 dB.
 */
@Composable
private fun BandSlider(
    label: String,
    valueDb: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val heightPx = with(density) { BandTrackHeight.toPx() }
    val thumbPx = with(density) { BandThumbRadius.toPx() }
    val onChangeState = rememberUpdatedState(onChange)
    val range = EqualizerBands.MAX_DB - EqualizerBands.MIN_DB

    // Posisi sentuh (px dari atas) -> dB, dengan ruang selebar jari-jari knob di ujung atas dan bawah
    fun yToDb(y: Float): Float {
        val usable = (heightPx - 2f * thumbPx).coerceAtLeast(1f)
        val fraction = 1f - ((y - thumbPx) / usable).coerceIn(0f, 1f)
        val raw = EqualizerBands.MIN_DB + fraction * range
        return (raw * 2f).roundToInt() / 2f
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatDb(valueDb),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (valueDb == 0f) Sonora.TextSecondary else Ink
        )
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .width(44.dp)
                .height(BandTrackHeight)
                .pointerInput(heightPx, thumbPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> onChangeState.value(yToDb(offset.y)) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            onChangeState.value(yToDb(change.position.y))
                        }
                    )
                }
                .pointerInput(heightPx, thumbPx) {
                    detectTapGestures(onTap = { offset -> onChangeState.value(yToDb(offset.y)) })
                }
                .semantics {
                    contentDescription = "Pita $label"
                    stateDescription = formatDb(valueDb)
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        valueDb,
                        EqualizerBands.MIN_DB..EqualizerBands.MAX_DB
                    )
                    setProgress { target ->
                        onChangeState.value(
                            ((target * 2f).roundToInt() / 2f)
                                .coerceIn(EqualizerBands.MIN_DB, EqualizerBands.MAX_DB)
                        )
                        true
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val trackW = 4.dp.toPx()
                val usable = size.height - 2f * thumbPx
                val fraction = (valueDb - EqualizerBands.MIN_DB) / range
                val thumbY = thumbPx + (1f - fraction) * usable
                val zeroY = thumbPx + 0.5f * usable // rentang simetris, jadi 0 dB selalu di tengah

                // Jalur
                drawRoundRect(
                    color = ControlOffTrack,
                    topLeft = Offset(cx - trackW / 2f, thumbPx),
                    size = Size(trackW, usable),
                    cornerRadius = CornerRadius(trackW / 2f)
                )
                // Tanda 0 dB
                drawRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(cx - 9.dp.toPx(), zeroY - 0.5.dp.toPx()),
                    size = Size(18.dp.toPx(), 1.dp.toPx())
                )
                // Bagian terisi: dari 0 dB ke knob
                val top = minOf(zeroY, thumbY)
                val bottom = maxOf(zeroY, thumbY)
                if (bottom - top > 0.5f) {
                    drawRoundRect(
                        color = Ink,
                        topLeft = Offset(cx - trackW / 2f, top),
                        size = Size(trackW, bottom - top),
                        cornerRadius = CornerRadius(trackW / 2f)
                    )
                }
                // Knob
                drawCircle(color = Ink, radius = thumbPx, center = Offset(cx, thumbY))
                drawCircle(color = Sonora.Background, radius = 3.dp.toPx(), center = Offset(cx, thumbY))
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = Sonora.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** "+3 dB", "-2,5 dB", "0 dB". */
private fun formatDb(db: Float): String {
    val text = if (db % 1f == 0f) db.toInt().toString()
    else String.format(Locale.getDefault(), "%.1f", db)
    return if (db > 0f) "+$text dB" else "$text dB"
}
