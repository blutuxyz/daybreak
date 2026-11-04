package com.bdersolutions.daybreak.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.settings.LessonColorSettings
import com.bdersolutions.daybreak.data.settings.SubjectColorMode

/**
 * The colours of one lesson. [accent] is the stripe or badge colour and [container] is a faint
 * tint of it, used when the whole card is coloured.
 */
@Immutable
data class SubjectTone(
    val container: Color,
    val accent: Color,
    val onContainer: Color,
)

/** Which palette automatic lesson colours come from. Provided by DaybreakTheme. */
val LocalSubjectColorMode = staticCompositionLocalOf { SubjectColorMode.ACCENT_HUES }

/** The user's lesson colour choices. Provided by DaybreakTheme. */
val LocalLessonColors = staticCompositionLocalOf { LessonColorSettings() }

/** Colour for one subject: the user's choice if there is one, otherwise the automatic colour. */
@Composable
fun rememberSubjectTone(subject: String): SubjectTone {
    val scheme = MaterialTheme.colorScheme
    val mode = LocalSubjectColorMode.current
    val custom = LocalLessonColors.current.subjectColors[subject]
    val dark = scheme.surface.luminance() < 0.5f
    return remember(subject, scheme, mode, dark, custom) {
        if (custom != null) {
            toneFor(Color(custom.toInt()), scheme)
        } else {
            automaticTone(subject, scheme, dark, mode)
        }
    }
}

/** The automatic colour a subject gets when the user has not chosen one. */
fun automaticSubjectColor(
    subject: String,
    scheme: ColorScheme,
    dark: Boolean,
    mode: SubjectColorMode,
): Color = automaticTone(subject, scheme, dark, mode).accent

private fun automaticTone(
    subject: String,
    scheme: ColorScheme,
    dark: Boolean,
    mode: SubjectColorMode,
): SubjectTone = when (mode) {
    SubjectColorMode.ROLES -> roleTone(subject, scheme)
    SubjectColorMode.ACCENT_HUES -> accentHueTone(subject, scheme, dark)
}

private fun toneFor(accent: Color, scheme: ColorScheme): SubjectTone = SubjectTone(
    container = lerp(scheme.surfaceContainerLow, accent, 0.14f),
    accent = accent,
    onContainer = scheme.onSurface,
)

private fun roleTone(subject: String, scheme: ColorScheme): SubjectTone =
    when (stableSubjectHash(subject) % 3) {
        0 -> SubjectTone(
            container = scheme.primaryContainer,
            accent = scheme.primary,
            onContainer = scheme.onPrimaryContainer,
        )

        1 -> SubjectTone(
            container = scheme.secondaryContainer,
            accent = scheme.secondary,
            onContainer = scheme.onSecondaryContainer,
        )

        else -> SubjectTone(
            container = scheme.tertiaryContainer,
            accent = scheme.tertiary,
            onContainer = scheme.onTertiaryContainer,
        )
    }

private fun accentHueTone(subject: String, scheme: ColorScheme, dark: Boolean): SubjectTone {
    val primary = scheme.primary
    val primaryHue = srgbToOkLch(
        primary.red.toDouble(),
        primary.green.toDouble(),
        primary.blue.toDouble(),
    ).hueDegrees
    val rgb = oklchToSrgb(
        OkLch(
            lightness = if (dark) 0.8 else 0.56,
            chroma = if (dark) 0.12 else 0.14,
            hueDegrees = subjectHueDegrees(subject, primaryHue),
        ),
    )
    return toneFor(Color(rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat()), scheme)
}

/** Text colour that stays readable on [background]. */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.35f) Color(0xFF1B1B1F) else Color.White

/** Warm accent used for room changes, independent of the wallpaper. */
@Composable
fun warningAccent(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFFFFB35C) else Color(0xFFC45A00)
}

/** Substitution colour: the user's choice, or the warm default. */
@Composable
fun substitutionAccent(): Color =
    LocalLessonColors.current.substitutionColor?.let { Color(it.toInt()) } ?: warningAccent()

/** Cancelled colour: the user's choice, or the theme's error colour. */
@Composable
fun cancelledAccent(): Color =
    LocalLessonColors.current.cancelledColor?.let { Color(it.toInt()) }
        ?: MaterialTheme.colorScheme.error

/**
 * Yellow used for exams (Prüfungen). The default is a dark gold on light backgrounds so the
 * stripe and dot still reach 3:1 against the card. Exams always carry the word "Prüfung", so
 * the colour is never the only signal.
 */
@Composable
fun examAccent(): Color {
    LocalLessonColors.current.examColor?.let { return Color(it.toInt()) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFFFFD54F) else Color(0xFFB38600)
}

@Composable
fun examContainer(): Color {
    LocalLessonColors.current.examColor?.let {
        return lerp(MaterialTheme.colorScheme.surfaceContainerLow, Color(it.toInt()), 0.3f)
    }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFF5C4A00) else Color(0xFFFFE38A)
}

@Composable
fun examContent(): Color {
    if (LocalLessonColors.current.examColor != null) return MaterialTheme.colorScheme.onSurface
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFFFFE38A) else Color(0xFF3D2E00)
}

/** Slightly stronger orange container for badges. */
@Composable
fun warningContainer(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFF5A3200) else Color(0xFFFFE0C2)
}

@Composable
fun AccentBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
        )
    }
}
