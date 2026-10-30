package com.bdersolutions.daybreak.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/**
 * Bolder, more expressive type scale. The emphasized variants are used for the
 * hero moments (day header, login wordmark, big counts) while the baseline
 * styles stay readable for dense timetable content.
 */
val Typography: Typography = Base.copy(
    displayLarge = Base.displayLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = Base.displayMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.0).sp,
    ),
    displaySmall = Base.displaySmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = Base.headlineLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = Base.headlineMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = Base.headlineSmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
    ),
    titleLarge = Base.titleLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = Base.titleMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
    ),
    titleSmall = Base.titleSmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
    ),
    labelLarge = Base.labelLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
    ),
    labelMedium = Base.labelMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
    ),
    displaySmallEmphasized = Base.displaySmallEmphasized.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp,
    ),
    headlineLargeEmphasized = Base.headlineLargeEmphasized.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
    ),
    headlineMediumEmphasized = Base.headlineMediumEmphasized.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
    ),
    headlineSmallEmphasized = Base.headlineSmallEmphasized.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
    ),
    titleLargeEmphasized = Base.titleLargeEmphasized.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
    ),
    titleMediumEmphasized = Base.titleMediumEmphasized.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
    ),
    labelLargeEmphasized = Base.labelLargeEmphasized.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
    ),
)
