package com.bdersolutions.daybreak.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.permissions.PermissionStep
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * The permissions asked once at the first launch, one after another, before anything else. Each step can be
 * skipped. [onNotificationAnswered] learns whether notifications were allowed; [canInstallUnknownApps] is checked
 * again when the person comes back from the settings page. [onDone] runs after the last step.
 */
@Composable
fun PermissionsIntroScreen(
    steps: List<PermissionStep>,
    canInstallUnknownApps: () -> Boolean,
    onNotificationAnswered: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = steps.getOrNull(index)
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val doneNow by rememberUpdatedState(onDone)
    val advance = { index++ }

    LaunchedEffect(index) {
        if (index >= steps.size) doneNow()
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onNotificationAnswered(granted)
        advance()
    }

    // Back from the settings page: if the permission is now given, the step is complete.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (current == PermissionStep.UNKNOWN_SOURCES && canInstallUnknownApps()) advance()
    }

    if (current == null) return
    val total = steps.size
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (fadeIn(tween(260)) togetherWith fadeOut(tween(180)))
            },
            label = "permissionStep",
        ) { shown ->
            val step = steps.getOrNull(shown) ?: return@AnimatedContent
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (step == PermissionStep.NOTIFICATIONS) Icons.Outlined.Notifications else Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = stringResource(R.string.perm_step_count, index + 1, total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        if (step == PermissionStep.NOTIFICATIONS) R.string.perm_notifications_title else R.string.perm_unknown_title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (step == PermissionStep.NOTIFICATIONS) R.string.perm_notifications_body else R.string.perm_unknown_body,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = {
                        haptics.perform(HapticEvent.TAP)
                        if (step == PermissionStep.NOTIFICATIONS) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    },
                    elevation = null,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        stringResource(
                            if (step == PermissionStep.NOTIFICATIONS) R.string.perm_notifications_allow else R.string.perm_unknown_allow,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        haptics.perform(HapticEvent.TAP)
                        advance()
                    },
                ) {
                    Text(stringResource(R.string.perm_skip))
                }
            }
        }
    }
}
