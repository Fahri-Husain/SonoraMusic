package com.fahrii.musicapp.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.fahrii.musicapp.R

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun TextStyle.inter(
    weight: FontWeight? = null,
    size: TextUnit = fontSize
): TextStyle = copy(
    fontFamily = InterFamily,
    fontWeight = weight ?: fontWeight,
    fontSize = size,
    letterSpacing = 0.sp
)

private val base = Typography()

// Ukuran disesuaikan mendekati iOS (body 17, subheadline 15, caption 13)
val AppTypography = Typography(
    displayLarge = base.displayLarge.inter(),
    displayMedium = base.displayMedium.inter(),
    displaySmall = base.displaySmall.inter(),
    headlineLarge = base.headlineLarge.inter(FontWeight.Bold),
    headlineMedium = base.headlineMedium.inter(FontWeight.Bold),
    headlineSmall = base.headlineSmall.inter(FontWeight.Bold),
    titleLarge = base.titleLarge.inter(FontWeight.SemiBold),
    titleMedium = base.titleMedium.inter(FontWeight.SemiBold, 17.sp),
    titleSmall = base.titleSmall.inter(FontWeight.Medium, 15.sp),
    bodyLarge = base.bodyLarge.inter(FontWeight.Normal, 17.sp),
    bodyMedium = base.bodyMedium.inter(FontWeight.Normal, 15.sp),
    bodySmall = base.bodySmall.inter(FontWeight.Normal, 13.sp),
    labelLarge = base.labelLarge.inter(FontWeight.Medium, 15.sp),
    labelMedium = base.labelMedium.inter(FontWeight.Medium, 13.sp),
    labelSmall = base.labelSmall.inter(FontWeight.Medium, 11.sp)
)