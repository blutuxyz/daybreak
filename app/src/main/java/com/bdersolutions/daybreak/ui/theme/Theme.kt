package com.bdersolutions.daybreak.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DaybreakPrimaryDark,
    onPrimary = DaybreakOnPrimaryDark,
    primaryContainer = DaybreakPrimaryContainerDark,
    onPrimaryContainer = DaybreakOnPrimaryContainerDark,
    secondary = DaybreakSecondaryDark,
    secondaryContainer = DaybreakSecondaryContainerDark,
    tertiary = DaybreakTertiaryDark,
    tertiaryContainer = DaybreakTertiaryContainerDark,
    background = DaybreakBackgroundDark,
    surface = DaybreakSurfaceDark,
    onBackground = DaybreakOnSurfaceDark,
    onSurface = DaybreakOnSurfaceDark,
    surfaceVariant = DaybreakSurfaceVariantDark,
    onSurfaceVariant = DaybreakOnSurfaceVariantDark,
    outline = DaybreakOutlineDark,
    error = DaybreakErrorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = DaybreakPrimaryLight,
    onPrimary = DaybreakOnPrimaryLight,
    primaryContainer = DaybreakPrimaryContainerLight,
    onPrimaryContainer = DaybreakOnPrimaryContainerLight,
    secondary = DaybreakSecondaryLight,
    secondaryContainer = DaybreakSecondaryContainerLight,
    tertiary = DaybreakTertiaryLight,
    tertiaryContainer = DaybreakTertiaryContainerLight,
    background = DaybreakBackgroundLight,
    surface = DaybreakSurfaceLight,
    onBackground = DaybreakOnSurfaceLight,
    onSurface = DaybreakOnSurfaceLight,
    surfaceVariant = DaybreakSurfaceVariantLight,
    onSurfaceVariant = DaybreakOnSurfaceVariantLight,
    outline = DaybreakOutlineLight,
    error = DaybreakErrorLight,
    scrim = Color.Black,
)

val DaybreakShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DaybreakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DaybreakShapes,
        content = content,
    )
}
