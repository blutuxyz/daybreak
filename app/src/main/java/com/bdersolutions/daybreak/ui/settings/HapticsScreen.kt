package com.bdersolutions.daybreak.ui.settings

import com.bdersolutions.daybreak.ui.components.OutlinedButton
import com.bdersolutions.daybreak.ui.components.IconButton
import com.bdersolutions.daybreak.ui.components.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticCapabilities
import com.bdersolutions.daybreak.data.haptics.HapticCategory
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.haptics.HapticPreset
import com.bdersolutions.daybreak.data.haptics.HapticSettings
import com.bdersolutions.daybreak.data.haptics.HapticTexture
import com.bdersolutions.daybreak.ui.components.SelectionRow
import com.bdersolutions.daybreak.ui.haptics.HapticSlider
import kotlin.math.roundToInt

/** Strength sliders move in 5 % notches from 0 to 150 %. */
private const val INTENSITY_STEPS = 29
private const val INTENSITY_NOTCH = 0.05f

/**
 * Everything about haptic feedback: the master switch, ready-made presets, overall strength,
 * texture and rhythm, and below that every group and every single event. Each slider is felt while
 * it is dragged, and every event has a play button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HapticsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptics = settings.haptics
    val capabilities = viewModel.hapticCapabilities
    var systemOn by remember { mutableStateOf(viewModel.systemTouchFeedbackOn()) }
    // The system switch can be flipped in Android's settings and the app resumed afterwards.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { systemOn = viewModel.systemTouchFeedbackOn() }
    var expandedName by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsPageScaffold(title = stringResource(R.string.settings_haptics), onBack = onBack) {
        item(key = "master") {
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Outlined.Vibration,
                    title = stringResource(R.string.haptics_enabled),
                    subtitle = stringResource(R.string.haptics_enabled_desc),
                    trailing = {
                        SpringSwitch(
                            checked = haptics.enabled,
                            onCheckedChange = { on ->
                                viewModel.editHaptics { it.copy(enabled = on) }
                                // The switch cannot play its own "on" while the master switch is still off.
                                if (on) viewModel.previewHaptic(HapticEvent.TOGGLE_ON)
                            },
                        )
                    },
                )
            }
        }

        item(key = "device") {
            val message = when {
                !capabilities.hasVibrator -> stringResource(R.string.haptics_no_vibrator)
                haptics.followSystem && !systemOn -> stringResource(R.string.haptics_system_off)
                else -> deviceMessage(capabilities)
            }
            HapticNote(message)
        }

        if (!haptics.enabled) return@SettingsPageScaffold

        item(key = "presets_label") { SectionLabel(stringResource(R.string.haptics_presets)) }
        item(key = "presets") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (HapticPreset.entries - HapticPreset.CUSTOM).forEach { preset ->
                    SelectionRow(
                        label = hapticPresetLabel(preset),
                        supporting = hapticPresetDescription(preset),
                        selected = haptics.preset == preset,
                        onClick = {
                            viewModel.applyHapticPreset(preset)
                            // Felt with the new preset right away.
                            viewModel.previewHaptic(HapticEvent.TOGGLE_ON)
                        },
                    )
                }
                if (haptics.preset == HapticPreset.CUSTOM) {
                    SelectionRow(
                        label = hapticPresetLabel(HapticPreset.CUSTOM),
                        supporting = hapticPresetDescription(HapticPreset.CUSTOM),
                        selected = true,
                        onClick = {},
                    )
                }
            }
        }

        item(key = "feel_label") { SectionLabel(stringResource(R.string.haptics_feel)) }
        item(key = "feel") {
            SettingsGroup {
                AppearanceOption(
                    icon = Icons.Outlined.Bolt,
                    title = stringResource(R.string.haptics_intensity),
                ) {
                    HintText(stringResource(R.string.haptics_intensity_desc))
                    TunedSlider(
                        description = stringResource(R.string.haptics_intensity),
                        value = haptics.intensity,
                        range = 0f..HapticSettings.MAX_INTENSITY,
                        steps = INTENSITY_STEPS,
                        valueLabel = percentLabel(haptics.intensity),
                        onValueChange = { v -> viewModel.editHaptics { it.copy(intensity = snap(v, INTENSITY_NOTCH)) } },
                        tick = { viewModel.previewHaptic(HapticEvent.TAB_SWITCH) },
                    )
                }
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.GraphicEq,
                    title = stringResource(R.string.haptics_texture),
                ) {
                    HintText(stringResource(R.string.haptics_texture_desc))
                    SegmentChoice(
                        options = HapticTexture.entries.map { it to hapticTextureLabel(it) },
                        selected = haptics.texture,
                        onSelect = { texture ->
                            viewModel.editHaptics { it.copy(texture = texture) }
                            viewModel.previewHaptic(HapticEvent.TOGGLE_ON)
                        },
                    )
                }
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.LinearScale,
                    title = stringResource(R.string.haptics_spacing),
                ) {
                    HintText(stringResource(R.string.haptics_spacing_desc))
                    TunedSlider(
                        description = stringResource(R.string.haptics_spacing),
                        value = haptics.spacing,
                        range = HapticSettings.MIN_SPACING..HapticSettings.MAX_SPACING,
                        steps = 14,
                        valueLabel = percentLabel(haptics.spacing),
                        onValueChange = { v -> viewModel.editHaptics { it.copy(spacing = snap(v, 0.1f)) } },
                        tick = { viewModel.previewHaptic(HapticEvent.REFRESH_DONE) },
                    )
                }
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.Timer,
                    title = stringResource(R.string.haptics_min_gap),
                ) {
                    HintText(stringResource(R.string.haptics_min_gap_desc))
                    TunedSlider(
                        description = stringResource(R.string.haptics_min_gap),
                        value = haptics.minGapMs.toFloat(),
                        range = 0f..HapticSettings.MAX_MIN_GAP_MS.toFloat(),
                        steps = 11,
                        valueLabel = stringResource(R.string.haptics_ms, haptics.minGapMs),
                        onValueChange = { v ->
                            viewModel.editHaptics { it.copy(minGapMs = (v / 10f).roundToInt() * 10) }
                        },
                        tick = { viewModel.previewHaptic(HapticEvent.STEP) },
                    )
                }
                AppearanceDivider()
                SettingsRow(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = stringResource(R.string.haptics_follow_system),
                    subtitle = stringResource(R.string.haptics_follow_system_desc),
                    trailing = {
                        SpringSwitch(
                            checked = haptics.followSystem,
                            onCheckedChange = { on -> viewModel.editHaptics { it.copy(followSystem = on) } },
                        )
                    },
                )
            }
        }

        item(key = "areas_label") { SectionLabel(stringResource(R.string.haptics_categories)) }
        HapticCategory.entries.forEach { category ->
            item(key = "category_${category.name}") {
                HapticCategoryCard(
                    category = category,
                    settings = haptics,
                    expanded = expandedName == category.name,
                    onToggleExpanded = {
                        expandedName = if (expandedName == category.name) null else category.name
                    },
                    viewModel = viewModel,
                )
            }
        }

        item(key = "reset") {
            OutlinedButton(
                onClick = {
                    viewModel.applyHapticPreset(HapticPreset.PREMIUM)
                    viewModel.previewHaptic(HapticEvent.TOGGLE_ON)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.haptics_reset, hapticPresetLabel(HapticPreset.PREMIUM)))
            }
        }
    }
}

/** One group of events: a header with its own switch, and when open its strength and every event. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HapticCategoryCard(
    category: HapticCategory,
    settings: HapticSettings,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val on = category !in settings.disabledCategories
    val total = HapticEvent.inCategory(category).size
    val rotation by animateFloatAsState(targetValue = if (expanded && on) 180f else 0f, label = "categoryChevron")
    SettingsGroup {
        SettingsRow(
            icon = hapticCategoryIcon(category),
            title = hapticCategoryLabel(category),
            subtitle = stringResource(
                R.string.haptics_category_summary,
                settings.activeCount(category),
                total,
                kotlin.math.round(settings.categoryGain(category) * 100f).toInt(),
            ),
            subtitleMaxLines = 1,
            onClick = onToggleExpanded,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(rotation),
                    )
                    Spacer(Modifier.width(8.dp))
                    SpringSwitch(
                        checked = on,
                        onCheckedChange = { enable ->
                            viewModel.editHaptics {
                                it.copy(
                                    disabledCategories = if (enable) {
                                        it.disabledCategories - category
                                    } else {
                                        it.disabledCategories + category
                                    },
                                )
                            }
                            if (enable) viewModel.previewHaptic(HapticEvent.inCategory(category).first())
                        },
                    )
                }
            },
        )
        AnimatedVisibility(
            visible = expanded && on,
            enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +
                fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Column {
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.haptics_category_strength),
                ) {
                    TunedSlider(
                        description = hapticCategoryLabel(category),
                        value = settings.categoryGain(category),
                        range = 0f..HapticSettings.MAX_INTENSITY,
                        steps = INTENSITY_STEPS,
                        valueLabel = percentLabel(settings.categoryGain(category)),
                        onValueChange = { v ->
                            viewModel.editHaptics {
                                it.copy(categoryIntensity = it.categoryIntensity + (category to snap(v, INTENSITY_NOTCH)))
                            }
                        },
                        tick = { viewModel.previewHaptic(HapticEvent.inCategory(category).first()) },
                    )
                }
                HapticEvent.inCategory(category).forEach { event ->
                    AppearanceDivider()
                    HapticEventRow(event = event, settings = settings, viewModel = viewModel)
                }
            }
        }
    }
}

/** A single event: its name, a play button, a switch, and while it is on its own strength slider. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HapticEventRow(
    event: HapticEvent,
    settings: HapticSettings,
    viewModel: SettingsViewModel,
) {
    val active = event !in settings.disabledEvents
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hapticEventLabel(event),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.previewHaptic(event) }) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(R.string.haptics_try_event, hapticEventLabel(event)),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            SpringSwitch(
                checked = active,
                onCheckedChange = { enable ->
                    viewModel.editHaptics {
                        it.copy(disabledEvents = if (enable) it.disabledEvents - event else it.disabledEvents + event)
                    }
                    if (enable) viewModel.previewHaptic(event)
                },
            )
            Spacer(Modifier.width(8.dp))
        }
        AnimatedVisibility(
            visible = active,
            enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +
                fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            TunedSlider(
                description = hapticEventLabel(event),
                value = settings.eventGain(event),
                range = 0f..HapticSettings.MAX_INTENSITY,
                steps = INTENSITY_STEPS,
                valueLabel = percentLabel(settings.eventGain(event)),
                onValueChange = { v ->
                    viewModel.editHaptics {
                        it.copy(eventIntensity = it.eventIntensity + (event to snap(v, INTENSITY_NOTCH)))
                    }
                },
                tick = { viewModel.previewHaptic(event) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/** A slider with its value on the right. It clicks into every notch and plays [tick] there. */
@Composable
private fun TunedSlider(
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    tick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HapticSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            tick = tick,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(58.dp),
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A small tonal card with an info icon, for what the person should know about their phone. */
@Composable
private fun HapticNote(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Rounds a slider value to its notch so equal positions give equal numbers. */
private fun snap(value: Float, notch: Float): Float = (value / notch).roundToInt() * notch

@Composable
private fun percentLabel(value: Float): String =
    stringResource(R.string.haptics_percent, (value * 100f).roundToInt())

@Composable
private fun deviceMessage(capabilities: HapticCapabilities): String = when (capabilities.tier) {
    HapticCapabilities.Tier.ADVANCED -> stringResource(
        R.string.haptics_device_advanced,
        capabilities.primitivesSupported,
        capabilities.primitivesTotal,
    )
    HapticCapabilities.Tier.BASIC -> stringResource(R.string.haptics_device_basic)
    HapticCapabilities.Tier.MINIMAL -> stringResource(R.string.haptics_device_minimal)
    HapticCapabilities.Tier.NONE -> stringResource(R.string.haptics_no_vibrator)
}

@Composable
internal fun hapticPresetLabel(preset: HapticPreset): String = when (preset) {
    HapticPreset.PREMIUM -> stringResource(R.string.haptics_preset_premium)
    HapticPreset.SUBTLE -> stringResource(R.string.haptics_preset_subtle)
    HapticPreset.PUNCHY -> stringResource(R.string.haptics_preset_punchy)
    HapticPreset.MINIMAL -> stringResource(R.string.haptics_preset_minimal)
    HapticPreset.CUSTOM -> stringResource(R.string.haptics_preset_custom)
}

@Composable
private fun hapticPresetDescription(preset: HapticPreset): String = when (preset) {
    HapticPreset.PREMIUM -> stringResource(R.string.haptics_preset_premium_desc)
    HapticPreset.SUBTLE -> stringResource(R.string.haptics_preset_subtle_desc)
    HapticPreset.PUNCHY -> stringResource(R.string.haptics_preset_punchy_desc)
    HapticPreset.MINIMAL -> stringResource(R.string.haptics_preset_minimal_desc)
    HapticPreset.CUSTOM -> stringResource(R.string.haptics_preset_custom_desc)
}

@Composable
private fun hapticTextureLabel(texture: HapticTexture): String = when (texture) {
    HapticTexture.SOFT -> stringResource(R.string.haptics_texture_soft)
    HapticTexture.CRISP -> stringResource(R.string.haptics_texture_crisp)
    HapticTexture.PUNCHY -> stringResource(R.string.haptics_texture_punchy)
}

private fun hapticCategoryIcon(category: HapticCategory): ImageVector = when (category) {
    HapticCategory.NAVIGATION -> Icons.Outlined.Explore
    HapticCategory.TOUCH -> Icons.Outlined.TouchApp
    HapticCategory.CONTROLS -> Icons.Outlined.Tune
    HapticCategory.STATUS -> Icons.Outlined.Sync
    HapticCategory.MOMENTS -> Icons.Outlined.AutoAwesome
}

@Composable
private fun hapticCategoryLabel(category: HapticCategory): String = when (category) {
    HapticCategory.NAVIGATION -> stringResource(R.string.haptics_cat_navigation)
    HapticCategory.TOUCH -> stringResource(R.string.haptics_cat_touch)
    HapticCategory.CONTROLS -> stringResource(R.string.haptics_cat_controls)
    HapticCategory.STATUS -> stringResource(R.string.haptics_cat_status)
    HapticCategory.MOMENTS -> stringResource(R.string.haptics_cat_moments)
}

@Composable
private fun hapticEventLabel(event: HapticEvent): String = when (event) {
    HapticEvent.TAB_SWITCH -> stringResource(R.string.haptics_event_tab_switch)
    HapticEvent.PAGE_SNAP -> stringResource(R.string.haptics_event_page_snap)
    HapticEvent.BACK -> stringResource(R.string.haptics_event_back)
    HapticEvent.TAP -> stringResource(R.string.haptics_event_tap)
    HapticEvent.SHEET_OPEN -> stringResource(R.string.haptics_event_sheet_open)
    HapticEvent.REORDER_PICKUP -> stringResource(R.string.haptics_event_reorder_pickup)
    HapticEvent.REORDER_SWAP -> stringResource(R.string.haptics_event_reorder_swap)
    HapticEvent.TOGGLE_ON -> stringResource(R.string.haptics_event_toggle_on)
    HapticEvent.TOGGLE_OFF -> stringResource(R.string.haptics_event_toggle_off)
    HapticEvent.SELECT -> stringResource(R.string.haptics_event_select)
    HapticEvent.STEP -> stringResource(R.string.haptics_event_step)
    HapticEvent.REFRESH_PULL -> stringResource(R.string.haptics_event_refresh_pull)
    HapticEvent.REFRESH_DONE -> stringResource(R.string.haptics_event_refresh_done)
    HapticEvent.ERROR -> stringResource(R.string.haptics_event_error)
    HapticEvent.LOGIN_SUCCESS -> stringResource(R.string.haptics_event_login_success)
    HapticEvent.ANSWER_TOKEN -> stringResource(R.string.haptics_event_answer_token)
    HapticEvent.MESSAGE_SENT -> stringResource(R.string.haptics_event_message_sent)
    HapticEvent.UPDATE_READY -> stringResource(R.string.haptics_event_update_ready)
    HapticEvent.DEBUG_TAP -> stringResource(R.string.haptics_event_debug_tap)
    HapticEvent.DEBUG_UNLOCK -> stringResource(R.string.haptics_event_debug_unlock)
}
