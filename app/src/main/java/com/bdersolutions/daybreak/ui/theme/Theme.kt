package com.bdersolutions.daybreak.ui.theme

import android.app.Activity
import android.content.Context
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.AppSettings
import com.bdersolutions.daybreak.data.settings.MotionStyle
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.ui.components.LocalAbsenceUnit
import com.bdersolutions.daybreak.ui.components.LocalLessonColors
import com.bdersolutions.daybreak.ui.components.LocalSubjectColorMode

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

/**
 * Tonal roles for one accent palette. Every on/container pair is chosen to meet
 * WCAG AA contrast (checked for all five palettes in both light and dark).
 */
private class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

private val ForestLight = AccentTones(
    primary = Color(0xFF2E6B3E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3F2BD), onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF506352), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D3), onSecondaryContainer = Color(0xFF0E1F12),
    tertiary = Color(0xFF386668), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBEBEC), onTertiaryContainer = Color(0xFF002020),
)
private val ForestDark = AccentTones(
    primary = Color(0xFF97D6A5), onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF1B5229), onPrimaryContainer = Color(0xFFB3F2BD),
    secondary = Color(0xFFB8CCB6), onSecondary = Color(0xFF233426),
    secondaryContainer = Color(0xFF3A4B3A), onSecondaryContainer = Color(0xFFD3E8D3),
    tertiary = Color(0xFFA0CFD0), onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF1F4E4F), onTertiaryContainer = Color(0xFFBBEBEC),
)

private val LavenderLight = AccentTones(
    primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4), onTertiaryContainer = Color(0xFF31111D),
)
private val LavenderDark = AccentTones(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD8E4),
)

private val SunsetLight = AccentTones(
    primary = Color(0xFFA04B00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC8), onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF76594A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADCC9), onSecondaryContainer = Color(0xFF2B1709),
    tertiary = Color(0xFF5F6130), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E7A8), onTertiaryContainer = Color(0xFF1C1E00),
)
private val SunsetDark = AccentTones(
    primary = Color(0xFFFFB68B), onPrimary = Color(0xFF522100),
    primaryContainer = Color(0xFF733400), onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE3BFA9), onSecondary = Color(0xFF412C1C),
    secondaryContainer = Color(0xFF5D4131), onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFFC8CB8D), onTertiary = Color(0xFF303400),
    tertiaryContainer = Color(0xFF474A1A), onTertiaryContainer = Color(0xFFE5E7A8),
)

private val RoseLight = AccentTones(
    primary = Color(0xFFA13A5C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF3F0019),
    secondary = Color(0xFF75565F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3DDE3), onSecondaryContainer = Color(0xFF2C151B),
    tertiary = Color(0xFF7E5A2E), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB4), onTertiaryContainer = Color(0xFF2B1700),
)
private val RoseDark = AccentTones(
    primary = Color(0xFFFFB1C8), onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7F2A4A), onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFE3BDC6), onSecondary = Color(0xFF422A31),
    secondaryContainer = Color(0xFF5B4049), onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = Color(0xFFF0BD8A), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF663F12), onTertiaryContainer = Color(0xFFFFDDB4),
)

/** Builds a scheme for a fixed accent, sharing Daybreak's neutral surfaces and error roles. */
private fun AccentTones.toScheme(dark: Boolean): ColorScheme {
    val base = if (dark) DarkColorScheme else LightColorScheme
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
    )
}

private fun fixedScheme(accent: AccentColor, dark: Boolean): ColorScheme = when (accent) {
    AccentColor.OCEAN -> if (dark) DarkColorScheme else LightColorScheme
    AccentColor.FOREST -> (if (dark) ForestDark else ForestLight).toScheme(dark)
    AccentColor.LAVENDER -> (if (dark) LavenderDark else LavenderLight).toScheme(dark)
    AccentColor.SUNSET -> (if (dark) SunsetDark else SunsetLight).toScheme(dark)
    AccentColor.ROSE -> (if (dark) RoseDark else RoseLight).toScheme(dark)
}

/** Primary and on-primary colours of an accent, used for the picker swatches. */
fun accentSwatchColors(accent: AccentColor, dark: Boolean): Pair<Color, Color> {
    val scheme = fixedScheme(accent, dark)
    return scheme.primary to scheme.onPrimary
}

/**
 * The app's colour scheme for [settings] in light or [dark] mode: Material You when it is on and the
 * phone supports it, else the chosen accent palette. The widget uses it too, so both match.
 */
fun daybreakColorScheme(context: Context, settings: AppSettings, dark: Boolean): ColorScheme = when {
    settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    else -> fixedScheme(settings.accent, dark)
}.boostedContainers(dark).let { if (dark && settings.amoledBlack) it.pitchBlack() else it }

/**
 * AMOLED black: the page itself is pure black, so OLED pixels switch off; cards and sheets stay a few steps
 * above it, faintly tinted with the accent, so they still read as layers.
 */
private fun ColorScheme.pitchBlack(): ColorScheme {
    fun level(amount: Float) = lerp(Color.Black, lerp(Color(0xFF1C1C1E), primary, 0.10f), amount)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = level(0.35f),
        surfaceContainer = level(0.55f),
        surfaceContainerHigh = level(0.75f),
        surfaceContainerHighest = level(1f),
        surfaceBright = level(1.2f),
        surfaceVariant = level(1f),
    )
}

/** Resolves the user's theme choice against the system setting. */
@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DaybreakTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(settings.themeMode)
    val context = LocalContext.current
    val colorScheme = daybreakColorScheme(context, settings, darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // The in-app text size multiplies the system font scale, so people who already
    // enlarged text on the phone are not shrunk back down.
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * settings.textScale.multiplier,
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalSubjectColorMode provides settings.subjectColorMode,
        LocalAbsenceUnit provides settings.absenceUnit,
        LocalLessonColors provides settings.lessonColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = when (settings.motionStyle) {
                MotionStyle.EXPRESSIVE -> MotionScheme.expressive()
                MotionStyle.CALM -> MotionScheme.standard()
            },
            shapes = DaybreakShapes,
            typography = Typography,
            content = content,
        )
    }
}
