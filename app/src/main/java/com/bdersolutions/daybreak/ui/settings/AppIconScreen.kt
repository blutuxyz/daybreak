package com.bdersolutions.daybreak.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.branding.AppIconStyle

/**
 * Supporter extra: the picture of the app icon. "Standard" is the current logo, "Classic" the original
 * one. Like the name, it is switched through the launcher aliases, so the app restarts afterwards.
 */
@Composable
fun AppIconScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var restarting by rememberSaveable { mutableStateOf(false) }
    if (restarting) RestartDialog()

    SettingsPageScaffold(title = stringResource(R.string.settings_app_icon), onBack = onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIconStyle.entries.forEach { style ->
                    IconOption(
                        style = style,
                        selected = settings.appIconStyle == style,
                        onClick = {
                            if (settings.appIconStyle != style) {
                                viewModel.setAppIconStyle(style)
                                restarting = true
                            }
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.app_icon_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun IconOption(
    style: AppIconStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (icon, title, description) = when (style) {
        AppIconStyle.STANDARD -> Triple(
            R.mipmap.ic_launcher,
            R.string.icon_style_standard,
            R.string.icon_style_standard_desc,
        )
        AppIconStyle.CLASSIC -> Triple(
            R.mipmap.ic_launcher_classic,
            R.string.icon_style_classic,
            R.string.icon_style_classic_desc,
        )
        AppIconStyle.GRID -> Triple(
            R.mipmap.ic_launcher_grid,
            R.string.icon_style_grid,
            R.string.icon_style_grid_desc,
        )
        AppIconStyle.NIGHT -> Triple(
            R.mipmap.ic_launcher_night,
            R.string.icon_style_night,
            R.string.icon_style_night_desc,
        )
        AppIconStyle.LETTER -> Triple(
            R.mipmap.ic_launcher_letter,
            R.string.icon_style_letter,
            R.string.icon_style_letter_desc,
        )
        AppIconStyle.BLOOM -> Triple(
            R.mipmap.ic_launcher_bloom,
            R.string.icon_style_bloom,
            R.string.icon_style_bloom_desc,
        )
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPreview(icon, 64.dp)
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/** Draws a launcher icon (an adaptive icon, which Compose cannot load directly) as it looks on the home screen. */
@Composable
private fun IconPreview(@DrawableRes icon: Int, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(icon, px) {
        ContextCompat.getDrawable(context, icon)?.toBitmap(px, px)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}
