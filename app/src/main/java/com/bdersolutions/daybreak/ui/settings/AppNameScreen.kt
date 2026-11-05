package com.bdersolutions.daybreak.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.branding.AppNamePreset
import com.bdersolutions.daybreak.data.branding.AppNaming
import com.bdersolutions.daybreak.ui.components.SelectionRow

/**
 * Supporter extra: the name of the app. Two things, kept apart because Android allows only one of
 * them to be free text: the name under the icon is picked from a list (each is a launcher alias),
 * the name inside the app, shown as the timetable title, can be typed.
 */
@Composable
fun AppNameScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var custom by rememberSaveable { mutableStateOf(settings.customAppName) }
    val focus = LocalFocusManager.current
    // Switching the phone name restarts the app so the new name and icon entry are picked up.
    var restarting by rememberSaveable { mutableStateOf(false) }
    if (restarting) RestartDialog()

    SettingsPageScaffold(title = stringResource(R.string.settings_app_name), onBack = onBack) {
        item { SectionLabel(stringResource(R.string.app_name_phone_title)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppNamePreset.entries.forEach { preset ->
                    SelectionRow(
                        label = preset.displayName,
                        supporting = null,
                        selected = settings.appNamePreset == preset,
                        onClick = {
                            if (settings.appNamePreset != preset) {
                                viewModel.setAppNamePreset(preset)
                                restarting = true
                            }
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.app_name_phone_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp),
                )
            }
        }

        item { SectionLabel(stringResource(R.string.app_name_inapp_title)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { typed ->
                        custom = typed.take(AppNaming.MAX_CUSTOM_LENGTH)
                        viewModel.setCustomAppName(custom)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        // Tidy the text (spaces, control characters) once the person is done typing.
                        .onFocusChanged { state ->
                            if (!state.isFocused) custom = AppNaming.sanitizeCustom(custom)
                        },
                    label = { Text(stringResource(R.string.app_name_custom_label)) },
                    placeholder = { Text(settings.appNamePreset.displayName) },
                    supportingText = {
                        Text("${custom.length}/${AppNaming.MAX_CUSTOM_LENGTH}")
                    },
                    singleLine = true,
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                )
                Text(
                    text = stringResource(R.string.app_name_inapp_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                )
            }
        }
    }
}
