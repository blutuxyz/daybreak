package com.bdersolutions.daybreak.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

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

@Composable
fun rememberSubjectTone(subject: String): SubjectTone {
    val scheme = MaterialTheme.colorScheme
    return remember(subject, scheme) {
        when (stableHash(subject) % 3) {
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
    }
}

private fun stableHash(value: String): Int {
    var hash = 7
    for (char in value.lowercase(Locale.ROOT)) {
        hash = hash * 31 + char.code
    }
    return hash and 0x7fffffff
}

/** Warm accent used for substitutions and room changes, independent of the wallpaper. */
@Composable
fun warningAccent(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) Color(0xFFFFB35C) else Color(0xFFC45A00)
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
