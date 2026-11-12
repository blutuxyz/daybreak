@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bdersolutions.daybreak.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.update.ReleaseInfo
import com.bdersolutions.daybreak.data.update.UpdateManager
import com.bdersolutions.daybreak.data.update.UpdateState
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.ui.settings.UpdateStatusCard
import kotlinx.coroutines.launch

/** The release a state is about, while there is one to offer (not while checking or when up to date). */
private fun UpdateState.offeredRelease(): ReleaseInfo? = when (this) {
    is UpdateState.Available -> release
    is UpdateState.Downloading -> release
    is UpdateState.Ready -> release
    is UpdateState.Installing -> release
    is UpdateState.Failed -> release
    else -> null
}

/**
 * The full-screen "A new version is available. Download it?" page. It comes up on every launch and after every
 * update check that finds a newer version (the host asks for a check at launch), and covers the app like the
 * "update required" page does, but with "Later": that closes it until the next launch or the next check.
 * A version below the server's minimum is not handled here; that page has no way past it.
 *
 * [enabled] is false while something earlier in the app's order is showing (the terms, the tutorial, the
 * required update) or when the update feature is switched off.
 */
@Composable
fun UpdateAvailableHost(
    manager: UpdateManager,
    enabled: Boolean,
    checkAtLaunch: Boolean,
    onVisibleChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by manager.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var canInstall by remember { mutableStateOf(manager.installer.canInstall()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = manager.installer.canInstall() }

    // Asked once per launch, as soon as the app may use the network (the terms are accepted).
    LaunchedEffect(enabled, checkAtLaunch) {
        if (enabled && checkAtLaunch && manager.isConfigured) manager.check()
    }

    // The page stays up while a check runs behind it (a download or a re-check does not make it flicker).
    var held by remember { mutableStateOf<ReleaseInfo?>(null) }
    state.offeredRelease()?.let { held = it }
    if (state is UpdateState.UpToDate) held = null
    // "Later" remembers the check it was pressed for; the next check (a new time stamp) shows the page again.
    var dismissedAt by rememberSaveable { mutableLongStateOf(-1L) }
    val stamp = manager.lastCheckAt
    val visible = enabled && manager.isConfigured && held != null && stamp != dismissedAt

    LaunchedEffect(visible) { onVisibleChange(visible) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 10 } + scaleIn(initialScale = 0.97f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 10 } + scaleOut(targetScale = 0.98f),
        modifier = modifier,
    ) {
        val release = held
        if (release != null) {
            BackHandler { dismissedAt = stamp }
            UpdateAvailableScreen(
                state = state,
                versionName = release.versionName,
                currentVersion = manager.currentVersionName,
                lastCheckedAt = stamp,
                canInstall = canInstall,
                onDownload = {
                    haptics.perform(HapticEvent.TAP)
                    manager.downloadInBackground()
                },
                onLater = {
                    haptics.perform(HapticEvent.TAP)
                    dismissedAt = stamp
                },
                onCancel = manager::cancelDownload,
                onInstall = manager::install,
                onAllowInstall = { context.startActivity(manager.installer.permissionSettingsIntent()) },
                onCheck = { scope.launch { manager.check() } },
            )
        }
    }
}

@Composable
fun UpdateAvailableScreen(
    state: UpdateState,
    versionName: String,
    currentVersion: String,
    lastCheckedAt: Long,
    canInstall: Boolean,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstall: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.v2_update_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.v2_update_question, versionName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                targetState = state is UpdateState.Available || state is UpdateState.Checking || state is UpdateState.UpToDate,
                transitionSpec = {
                    fadeIn(tween(260)) togetherWith fadeOut(tween(140))
                },
                label = "updateAvailablePhase",
            ) { choosing ->
                if (choosing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onDownload,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) { Text(stringResource(R.string.v2_update_download)) }
                    }
                } else {
                    // Downloading, ready, installing or failed: the usual card with its progress and buttons.
                    UpdateStatusCard(
                        state = state,
                        currentVersion = currentVersion,
                        lastCheckedAt = lastCheckedAt,
                        canInstall = canInstall,
                        onCheck = onCheck,
                        onDownload = onDownload,
                        onCancel = onCancel,
                        onInstall = onInstall,
                        onAllowInstall = onAllowInstall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.v2_update_later))
            }
            Text(
                text = stringResource(R.string.updates_current_version, currentVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
