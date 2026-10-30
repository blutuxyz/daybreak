package com.bdersolutions.daybreak.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    onSecondary = Color(0xFF1E2F44),
    secondaryContainer = DaybreakSecondaryContainerDark,
    onSecondaryContainer = DaybreakOnSecondaryContainerDark,
    tertiary = DaybreakTertiaryDark,
    onTertiary = Color(0xFF3B0E47),
    tertiaryContainer = DaybreakTertiaryContainerDark,
    onTertiaryContainer = DaybreakOnTertiaryContainerDark,
    background = DaybreakBackgroundDark,
    onBackground = DaybreakOnSurfaceDark,
    surface = DaybreakSurfaceDark,
    onSurface = DaybreakOnSurfaceDark,
    surfaceVariant = DaybreakSurfaceVariantDark,
    onSurfaceVariant = DaybreakOnSurfaceVariantDark,
    outline = DaybreakOutlineDark,
    error = DaybreakErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = DaybreakPrimaryLight,
    onPrimary = DaybreakOnPrimaryLight,
    primaryContainer = DaybreakPrimaryContainerLight,
    onPrimaryContainer = DaybreakOnPrimaryContainerLight,
    secondary = DaybreakSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = DaybreakSecondaryContainerLight,
    onSecondaryContainer = DaybreakOnSecondaryContainerLight,
    tertiary = DaybreakTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = DaybreakTertiaryContainerLight,
    onTertiaryContainer = DaybreakOnTertiaryContainerLight,
    background = DaybreakBackgroundLight,
    onBackground = DaybreakOnSurfaceLight,
    surface = DaybreakSurfaceLight,
    onSurface = DaybreakOnSurfaceLight,
    surfaceVariant = DaybreakSurfaceVariantLight,
    onSurfaceVariant = DaybreakOnSurfaceVariantLight,
    outline = DaybreakOutlineLight,
    error = DaybreakErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

val DaybreakShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/**
 * Dynamic Material You colors are kept, but the tonal containers are pushed a
 * little toward their accent color so the expressive surfaces read as bold
 * instead of washed out.
 */
private fun ColorScheme.boostedContainers(dark: Boolean): ColorScheme {
    val amount = if (dark) 0.12f else 0.14f
    return copy(
        primaryContainer = lerp(primaryContainer, primary, amount),
        secondaryContainer = lerp(secondaryContainer, secondary, amount * 0.8f),
        tertiaryContainer = lerp(tertiaryContainer, tertiary, amount),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    }.boostedContainers(darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = DaybreakShapes,
        typography = Typography,
        content = content,
    )
}
