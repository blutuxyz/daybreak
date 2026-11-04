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
import com.bdersolutions.daybreak.data.settings.SubjectColorMode

/**
 * A stable tonal triad per subject, taken from the active colour scheme. Each subject
 * maps to one of the primary, secondary or tertiary role families, so the lesson
 * colours always follow Material You or the chosen accent and keep their contrast.
 */
@Immutable
data class SubjectTone(
    val container: Color,
    val accent: Color,
    val onContainer: Color,
)

/** Which palette lesson colours come from. Provided by [com.bdersolutions.daybreak.ui.theme.DaybreakTheme]. */
val LocalSubjectColorMode = staticCompositionLocalOf { SubjectColorMode.ACCENT_HUES }

/**
 * Colour for one subject.
 *
 * - [SubjectColorMode.ACCENT_HUES]: a stripe colour on one of eight hues around the
 *   primary hue, with a faint tint of it as the container.
 * - [SubjectColorMode.ROLES]: the older primary, secondary and tertiary role families.
 */
@Composable
fun rememberSubjectTone(subject: String): SubjectTone {
    val scheme = MaterialTheme.colorScheme
    val mode = LocalSubjectColorMode.current
    val dark = scheme.surface.luminance() < 0.5f
    return remember(subject, scheme, mode, dark) {
        when (mode) {
            SubjectColorMode.ROLES -> roleTone(subject, scheme)
            SubjectColorMode.ACCENT_HUES -> accentHueTone(subject, scheme, dark)
        }
    }
}

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
    val stripe = Color(rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat())
    return SubjectTone(
        container = lerp(scheme.surfaceContainerLow, stripe, 0.14f),
        accent = stripe,
        onContainer = scheme.onSurface,
    )
}

/** Warm accent used for substitutions and room changes, independent of the wallpaper. */
@Composable
fun warningAccent(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFFFFB35C) else Color(0xFFC45A00)
}

/**
 * Yellow used for exams (Prüfungen). The accent is a dark gold on light backgrounds so the
 * stripe and dot still reach 3:1 against the card. Exams always carry the word "Prüfung",
 * so the colour is never the only signal.
 */
@Composable
fun examAccent(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFFFFD54F) else Color(0xFFB38600)
}

@Composable
fun examContainer(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFF5C4A00) else Color(0xFFFFE38A)
}

@Composable
fun examContent(): Color {
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
