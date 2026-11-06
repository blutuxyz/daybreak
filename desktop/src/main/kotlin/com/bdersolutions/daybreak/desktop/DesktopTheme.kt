package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.ThemeMode

/**
 * The Android app's colours. The dark and light schemes are the fallback palette in
 * app/ui/theme/Color.kt. Each accent takes the primary the app uses for it in Theme.kt.
 * The app's Material You colours follow the phone wallpaper; the desktop has none, so it uses
 * the fallback palette.
 */
private object Palette {
    const val DARK_BACKGROUND = 0xFF111318L
    const val DARK_SURFACE_LOW = 0xFF1A1C20L
    const val DARK_SURFACE = 0xFF1D1F24L
    const val DARK_SURFACE_HIGH = 0xFF282A2FL
    const val DARK_ON_SURFACE = 0xFFE2E2E9L
    const val DARK_ON_SURFACE_VARIANT = 0xFFC1C6D0L
    const val DARK_OUTLINE = 0xFF8B919BL
    const val DARK_OUTLINE_VARIANT = 0xFF414750L
    const val DARK_ERROR = 0xFFFFB4ABL
    const val DARK_ON_PRIMARY = 0xFF002F61L
    const val DARK_PRIMARY_CONTAINER = 0xFF174780L
    const val DARK_ON_PRIMARY_CONTAINER = 0xFFD5E3FFL

    const val LIGHT_BACKGROUND = 0xFFF7F8FCL
    const val LIGHT_SURFACE_LOW = 0xFFF1F3F8L
    const val LIGHT_SURFACE_HIGH = 0xFFE6E9EFL
    const val LIGHT_ON_SURFACE = 0xFF191C20L
    const val LIGHT_ON_SURFACE_VARIANT = 0xFF414750L
    const val LIGHT_OUTLINE = 0xFF717781L
    const val LIGHT_OUTLINE_VARIANT = 0xFFC1C6D0L
    const val LIGHT_ERROR = 0xFFBA1A1AL
    const val LIGHT_ON_PRIMARY = 0xFFFFFFFFL
    const val LIGHT_PRIMARY_CONTAINER = 0xFFBFD8FFL
    const val LIGHT_ON_PRIMARY_CONTAINER = 0xFF001B3BL
}

/** The primary of each accent palette in app/ui/theme/Theme.kt, dark and light. */
private fun accentPrimary(accent: AccentColor, dark: Boolean): Long = when (accent) {
    AccentColor.OCEAN -> if (dark) 0xFFA8C8FFL else 0xFF235A9CL
    AccentColor.FOREST -> if (dark) 0xFF97D6A5L else 0xFF2E6B3EL
    AccentColor.LAVENDER -> if (dark) 0xFFD0BCFFL else 0xFF6750A4L
    AccentColor.SUNSET -> if (dark) 0xFFFFB68BL else 0xFFA04B00L
    AccentColor.ROSE -> if (dark) 0xFFFFB1C8L else 0xFFA13A5CL
}

/** Status colours of lessons, as the app uses them in DaybreakComponents.kt. */
data class StatusColors(
    val exam: Color,
    val warning: Color,
    val cancelled: Color,
    val holiday: Color,
    val dark: Boolean,
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Color(0xFFFFEB3B), Color(0xFFFFB35C), Color(0xFFFFB4AB), Color(0xFF9FA8DA), true)
}

/** Corner radii of the app's cards, blocks and pills. */
val CardShape = RoundedCornerShape(30.dp)
val BlockShape = RoundedCornerShape(22.dp)
val PillShape = RoundedCornerShape(50)

/** Material 3 type scale with the weights the app uses (app/ui/theme/Type.kt). */
private fun daybreakTypography(base: Typography): Typography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.0).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
)

private fun darkScheme(primary: Color): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color(Palette.DARK_ON_PRIMARY),
    primaryContainer = Color(Palette.DARK_PRIMARY_CONTAINER),
    onPrimaryContainer = Color(Palette.DARK_ON_PRIMARY_CONTAINER),
    background = Color(Palette.DARK_BACKGROUND),
    surface = Color(Palette.DARK_BACKGROUND),
    onSurface = Color(Palette.DARK_ON_SURFACE),
    onSurfaceVariant = Color(Palette.DARK_ON_SURFACE_VARIANT),
    surfaceVariant = Color(Palette.DARK_OUTLINE_VARIANT),
    outline = Color(Palette.DARK_OUTLINE),
    outlineVariant = Color(Palette.DARK_OUTLINE_VARIANT),
    error = Color(Palette.DARK_ERROR),
    surfaceContainerLow = Color(Palette.DARK_SURFACE_LOW),
    surfaceContainer = Color(Palette.DARK_SURFACE),
    surfaceContainerHigh = Color(Palette.DARK_SURFACE_HIGH),
)

private fun lightScheme(primary: Color): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color(Palette.LIGHT_ON_PRIMARY),
    primaryContainer = Color(Palette.LIGHT_PRIMARY_CONTAINER),
    onPrimaryContainer = Color(Palette.LIGHT_ON_PRIMARY_CONTAINER),
    background = Color(Palette.LIGHT_BACKGROUND),
    surface = Color(Palette.LIGHT_BACKGROUND),
    onSurface = Color(Palette.LIGHT_ON_SURFACE),
    onSurfaceVariant = Color(Palette.LIGHT_ON_SURFACE_VARIANT),
    surfaceVariant = Color(Palette.LIGHT_OUTLINE_VARIANT),
    outline = Color(Palette.LIGHT_OUTLINE),
    outlineVariant = Color(Palette.LIGHT_OUTLINE_VARIANT),
    error = Color(Palette.LIGHT_ERROR),
    surfaceContainerLow = Color(Palette.LIGHT_SURFACE_LOW),
    surfaceContainer = Color(Palette.LIGHT_SURFACE_LOW),
    surfaceContainerHigh = Color(Palette.LIGHT_SURFACE_HIGH),
)

/** The app's colours, type scale and shapes for the chosen theme and accent. */
@Composable
fun DaybreakDesktopTheme(themeMode: ThemeMode, accent: AccentColor, content: @Composable () -> Unit) {
    // The desktop has no wallpaper colours, so System counts as dark, like the app's default.
    val dark = themeMode != ThemeMode.LIGHT
    val scheme = if (dark) {
        darkScheme(Color(accentPrimary(accent, true)))
    } else {
        lightScheme(Color(accentPrimary(accent, false)))
    }
    val status = if (dark) {
        StatusColors(Color(0xFFFFEB3B), Color(0xFFFFB35C), Color(Palette.DARK_ERROR), Color(0xFF9FA8DA), true)
    } else {
        StatusColors(Color(0xFFFFAB00), Color(0xFFC45A00), Color(Palette.LIGHT_ERROR), Color(0xFF5A5FA8), false)
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = daybreakTypography(Typography()),
        // The same corner radii as DaybreakShapes in app/ui/theme/Theme.kt.
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(30.dp),
            extraLarge = RoundedCornerShape(40.dp),
        ),
    ) {
        CompositionLocalProvider(LocalStatusColors provides status) {
            content()
        }
    }
}
