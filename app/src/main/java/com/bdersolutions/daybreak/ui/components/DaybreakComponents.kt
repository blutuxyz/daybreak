package com.bdersolutions.daybreak.ui.components

import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * A stable tonal pair per subject. The hue is derived from the current Material
 * You primary hue plus a fixed per-subject rotation, so a subject keeps the same
 * family of colors across screens while still following the user's wallpaper.
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
    val isDark = scheme.surface.luminance() < 0.5f
    return remember(subject, scheme.primary, isDark) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(scheme.primary.toArgb(), hsv)
        val seed = (stableHash(subject) % 12) * 30f
        val hue = (hsv[0] + seed) % 360f
        val container = if (isDark) {
            Color.hsv(hue, 0.42f, 0.32f)
        } else {
            Color.hsv(hue, 0.26f, 0.96f)
        }
        val accent = if (isDark) {
            Color.hsv(hue, 0.52f, 0.92f)
        } else {
            Color.hsv(hue, 0.7f, 0.52f)
        }
        SubjectTone(
            container = container,
            accent = accent,
            onContainer = scheme.onSurface,
        )
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
