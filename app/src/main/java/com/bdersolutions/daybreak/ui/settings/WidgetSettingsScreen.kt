package com.bdersolutions.daybreak.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.settings.WidgetDetail
import com.bdersolutions.daybreak.data.settings.WidgetSettings
import com.bdersolutions.daybreak.data.settings.WidgetTheme
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.widget.DaybreakWidgetReceiver

/** How the home screen widget looks and what it shows. Every change redraws the widget at once. */
@Composable
fun WidgetSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val widget = settings.widget
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val provider = remember(context) { ComponentName(context, DaybreakWidgetReceiver::class.java) }
    val canPin = remember(manager) { manager.isRequestPinAppWidgetSupported }
    // Counted again whenever the screen comes back, e.g. after placing a widget from the launcher.
    var placed by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            placed = runCatching { manager.getAppWidgetIds(provider).size }.getOrDefault(0)
        }
    }

    SettingsPageScaffold(title = stringResource(R.string.settings_widget), onBack = onBack) {
        item(key = "home") {
            SettingsGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (placed > 0) {
                            pluralStringResource(R.plurals.widget_settings_placed, placed, placed)
                        } else {
                            stringResource(R.string.widget_settings_not_placed)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (canPin) {
                        Button(
                            onClick = {
                                haptics.perform(HapticEvent.TAP)
                                manager.requestPinAppWidget(provider, null, null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(
                                Icons.Outlined.AddToHomeScreen,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.widget_settings_add))
                        }
                    }
                }
            }
        }

        item(key = "look_label") { SectionLabel(stringResource(R.string.widget_settings_look)) }
        item(key = "look") {
            SettingsGroup {
                AppearanceOption(
                    icon = Icons.Outlined.DarkMode,
                    title = stringResource(R.string.widget_settings_theme),
                ) {
                    SegmentChoice(
                        options = WidgetTheme.entries.map { it to widgetThemeLabel(it) },
                        selected = widget.theme,
                        onSelect = { theme -> viewModel.updateWidget { it.copy(theme = theme) } },
                    )
                }
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.Opacity,
                    title = stringResource(R.string.widget_settings_background),
                ) {
                    SegmentChoice(
                        options = WidgetSettings.BACKGROUND_OPACITIES.map { it to "$it %" },
                        selected = widget.backgroundOpacity,
                        onSelect = { value -> viewModel.updateWidget { it.copy(backgroundOpacity = value) } },
                    )
                    Text(
                        text = stringResource(R.string.widget_settings_background_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item(key = "content_label") { SectionLabel(stringResource(R.string.widget_settings_content)) }
        item(key = "content") {
            SettingsGroup {
                WidgetSwitchRow(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.widget_settings_headline),
                    subtitle = stringResource(R.string.widget_settings_headline_desc),
                    checked = widget.showHeadline,
                    onCheckedChange = { value -> viewModel.updateWidget { it.copy(showHeadline = value) } },
                )
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.ViewAgenda,
                    title = stringResource(R.string.widget_settings_detail),
                ) {
                    SegmentChoice(
                        options = WidgetDetail.entries.map { it to widgetDetailLabel(it) },
                        selected = widget.detail,
                        onSelect = { detail -> viewModel.updateWidget { it.copy(detail = detail) } },
                    )
                }
                AppearanceDivider()
                WidgetSwitchRow(
                    icon = Icons.Outlined.EventBusy,
                    title = stringResource(R.string.widget_settings_cancelled),
                    subtitle = stringResource(R.string.widget_settings_cancelled_desc),
                    checked = widget.showCancelled,
                    onCheckedChange = { value -> viewModel.updateWidget { it.copy(showCancelled = value) } },
                )
                AppearanceDivider()
                WidgetSwitchRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.widget_settings_next_day),
                    subtitle = stringResource(R.string.widget_settings_next_day_desc),
                    checked = widget.nextDayAfterSchool,
                    onCheckedChange = { value -> viewModel.updateWidget { it.copy(nextDayAfterSchool = value) } },
                )
                AppearanceDivider()
                WidgetSwitchRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(R.string.widget_settings_sync_time),
                    subtitle = stringResource(R.string.widget_settings_sync_time_desc),
                    checked = widget.showSyncTime,
                    onCheckedChange = { value -> viewModel.updateWidget { it.copy(showSyncTime = value) } },
                )
            }
        }
    }
}

@Composable
private fun WidgetSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = { SpringSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
internal fun widgetThemeLabel(theme: WidgetTheme): String = when (theme) {
    WidgetTheme.APP -> stringResource(R.string.widget_theme_app)
    WidgetTheme.SYSTEM -> stringResource(R.string.theme_system)
    WidgetTheme.LIGHT -> stringResource(R.string.theme_light)
    WidgetTheme.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun widgetDetailLabel(detail: WidgetDetail): String = when (detail) {
    WidgetDetail.ROOM -> stringResource(R.string.widget_detail_room)
    WidgetDetail.TEACHER -> stringResource(R.string.widget_detail_teacher)
    WidgetDetail.ROOM_AND_TEACHER -> stringResource(R.string.widget_detail_both)
}
