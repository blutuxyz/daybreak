package com.bdersolutions.daybreak.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.update.ReleaseInfo
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.data.update.UpdateFailure
import com.bdersolutions.daybreak.data.update.UpdateManager
import com.bdersolutions.daybreak.data.update.UpdateState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The update flow as a screen: check, download with progress, install, and the changelog. */
@Composable
fun UpdatesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    val releases by viewModel.updateReleases.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(viewModel.canInstallUpdates()) }

    // Back from Android's "install unknown apps" screen: the permission may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = viewModel.canInstallUpdates() }

    // A swell when a download has just finished; opening the screen on an old download stays silent.
    val haptics = LocalHaptics.current
    val updateReady = state is UpdateState.Ready
    var wasReady by remember { mutableStateOf(updateReady) }
    LaunchedEffect(updateReady) {
        if (updateReady && !wasReady) haptics.perform(HapticEvent.UPDATE_READY)
        wasReady = updateReady
    }

    LaunchedEffect(Unit) {
        viewModel.refreshUpdateReleases()
        if (state is UpdateState.Idle || state is UpdateState.UpToDate) viewModel.checkForUpdates()
    }

    SettingsPageScaffold(title = stringResource(R.string.settings_updates), onBack = onBack) {
        item(key = "status") {
            UpdateStatusCard(
                state = state,
                currentVersion = viewModel.currentVersionName,
                lastCheckedAt = remember(state) { viewModel.lastUpdateCheckAt },
                canInstall = canInstall,
                onCheck = viewModel::checkForUpdates,
                onDownload = viewModel::downloadUpdate,
                onCancel = viewModel::cancelUpdateDownload,
                onInstall = viewModel::installUpdate,
                onAllowInstall = { context.startActivity(viewModel.installPermissionIntent()) },
            )
        }
        item(key = "auto") {
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Outlined.Autorenew,
                    title = stringResource(R.string.updates_auto_check),
                    subtitle = stringResource(R.string.updates_auto_check_desc),
                    trailing = {
                        SpringSwitch(
                            checked = settings.autoCheckUpdates,
                            onCheckedChange = viewModel::setAutoCheckUpdates,
                        )
                    },
                )
            }
        }
        item(key = "changelog-label") {
            SectionLabel(stringResource(R.string.updates_changelog))
        }
        if (releases.isEmpty()) {
            item(key = "changelog-empty") {
                Text(
                    text = stringResource(R.string.updates_changelog_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        items(releases, key = { it.tag }) { release ->
            ReleaseCard(
                release = release,
                installed = release.versionCode == viewModel.currentVersionCode,
                newer = release.versionCode > viewModel.currentVersionCode,
                startExpanded = release == releases.first(),
            )
        }
    }
}

/** The state of an update, reduced to what decides the card's look. Progress changes do not count. */
private enum class StatusKind { CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, INSTALLING, FAILED }

private fun UpdateState.kind(): StatusKind = when (this) {
    UpdateState.Idle, is UpdateState.UpToDate -> StatusKind.CURRENT
    UpdateState.Checking -> StatusKind.CHECKING
    is UpdateState.Available -> StatusKind.AVAILABLE
    is UpdateState.Downloading -> StatusKind.DOWNLOADING
    is UpdateState.Ready -> StatusKind.READY
    is UpdateState.Installing -> StatusKind.INSTALLING
    is UpdateState.Failed -> StatusKind.FAILED
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateStatusCard(
    state: UpdateState,
    currentVersion: String,
    lastCheckedAt: Long,
    canInstall: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val attention = state.kind() in setOf(StatusKind.AVAILABLE, StatusKind.READY, StatusKind.DOWNLOADING)
    val failed = state.kind() == StatusKind.FAILED
    val container by animateColorAsState(
        targetValue = when {
            failed -> scheme.errorContainer
            attention -> scheme.primaryContainer
            else -> scheme.surfaceContainerLow
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "updateCardContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            failed -> scheme.onErrorContainer
            attention -> scheme.onPrimaryContainer
            else -> scheme.onSurface
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "updateCardContent",
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it.kind() },
            transitionSpec = {
                (
                    fadeIn(motion.defaultEffectsSpec<Float>()) +
                        scaleIn(motion.defaultSpatialSpec<Float>(), initialScale = 0.96f)
                    )
                    .togetherWith(fadeOut(motion.fastEffectsSpec<Float>()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec<IntSize>() })
            },
            label = "updateCardContent",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusHeader(
                    state = current,
                    currentVersion = currentVersion,
                    lastCheckedAt = lastCheckedAt,
                    tint = content,
                )
                StatusActions(
                    state = current,
                    canInstall = canInstall,
                    onCheck = onCheck,
                    onDownload = onDownload,
                    onCancel = onCancel,
                    onInstall = onInstall,
                    onAllowInstall = onAllowInstall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusHeader(
    state: UpdateState,
    currentVersion: String,
    lastCheckedAt: Long,
    tint: androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current
    val icon: ImageVector = when (state.kind()) {
        StatusKind.CHECKING -> Icons.Outlined.Autorenew
        StatusKind.CURRENT -> Icons.Outlined.CheckCircle
        StatusKind.AVAILABLE -> Icons.Outlined.SystemUpdate
        StatusKind.DOWNLOADING -> Icons.Outlined.FileDownload
        StatusKind.READY -> Icons.Outlined.SystemUpdate
        StatusKind.INSTALLING -> Icons.Outlined.SystemUpdate
        StatusKind.FAILED -> Icons.Outlined.ErrorOutline
    }
    val title = when (state) {
        UpdateState.Checking -> stringResource(R.string.updates_checking)
        UpdateState.Idle, is UpdateState.UpToDate -> stringResource(R.string.updates_up_to_date)
        is UpdateState.Available -> stringResource(R.string.updates_available_title, state.release.versionName)
        is UpdateState.Downloading -> stringResource(R.string.updates_downloading)
        is UpdateState.Ready -> stringResource(R.string.updates_ready)
        is UpdateState.Installing -> stringResource(R.string.updates_installing)
        is UpdateState.Failed -> stringResource(
            when (state.failure) {
                UpdateFailure.CHECK -> R.string.updates_failed_check
                UpdateFailure.DOWNLOAD -> R.string.updates_failed_download
                UpdateFailure.VERIFY -> R.string.updates_failed_verify
                UpdateFailure.INSTALL -> R.string.updates_failed_install
            },
        )
    }
    val subtitle = when (state) {
        UpdateState.Checking -> null
        UpdateState.Idle, is UpdateState.UpToDate -> {
            val installed = stringResource(R.string.updates_current_version, currentVersion)
            val checked = if (lastCheckedAt > 0L) {
                stringResource(R.string.updates_last_checked, formatDateTime(lastCheckedAt))
            } else {
                stringResource(R.string.updates_never_checked)
            }
            "$installed\n$checked"
        }

        is UpdateState.Available -> listOfNotNull(
            state.release.apk?.size?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
            formatDate(state.release.publishedAt),
        ).joinToString(" · ").ifBlank { null }

        is UpdateState.Downloading -> listOfNotNull(
            Formatter.formatShortFileSize(context, state.downloaded) + " / " +
                Formatter.formatShortFileSize(context, state.total),
        ).joinToString().takeIf { state.total > 0 }

        is UpdateState.Ready -> stringResource(R.string.updates_ready_detail, state.release.versionName)
        is UpdateState.Installing -> stringResource(R.string.updates_installing_detail)
        is UpdateState.Failed -> stringResource(
            when (state.failure) {
                UpdateFailure.CHECK -> R.string.updates_failed_check_detail
                UpdateFailure.DOWNLOAD -> R.string.updates_failed_download_detail
                UpdateFailure.VERIFY -> R.string.updates_failed_verify_detail
                UpdateFailure.INSTALL ->
                    if (state.detail == UpdateManager.VERIFIER_REFUSED) {
                        R.string.updates_failed_install_protect
                    } else {
                        R.string.updates_failed_install_detail
                    }
            },
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (state.kind() == StatusKind.CHECKING || state.kind() == StatusKind.INSTALLING) {
                LoadingIndicator(modifier = Modifier.size(28.dp), color = tint)
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun StatusActions(
    state: UpdateState,
    canInstall: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstall: () -> Unit,
) {
    when (state) {
        UpdateState.Checking, is UpdateState.Installing -> Unit

        UpdateState.Idle, is UpdateState.UpToDate -> FilledTonalButton(
            onClick = onCheck,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(R.string.updates_check_now))
        }

        is UpdateState.Available -> Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(R.string.updates_download))
        }

        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val progress = state.fraction
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
            } else {
                val animated by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "downloadProgress",
                )
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_cancel))
            }
        }

        is UpdateState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canInstall) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.updates_install))
                }
            } else {
                Text(
                    text = stringResource(R.string.updates_allow_install_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onAllowInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.updates_allow_install))
                }
            }
        }

        is UpdateState.Failed -> {
            val retry = when {
                state.failure == UpdateFailure.CHECK || state.release == null -> onCheck
                state.failure == UpdateFailure.INSTALL -> onCheck
                else -> onDownload
            }
            Button(
                onClick = retry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

/** One version and its notes. Tapping the header opens or closes the notes. */
@Composable
private fun ReleaseCard(
    release: ReleaseInfo,
    installed: Boolean,
    newer: Boolean,
    startExpanded: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    var expanded by rememberSaveable(release.tag) { mutableStateOf(startExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "releaseChevron",
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = release.versionName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (installed) VersionTag(stringResource(R.string.updates_changelog_installed))
                        if (newer) VersionTag(stringResource(R.string.updates_changelog_new), highlighted = true)
                    }
                    formatDate(release.publishedAt)?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                    if (release.changelog.isBlank()) {
                        Text(
                            text = stringResource(R.string.updates_changelog_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ChangelogText(markdown = release.changelog)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionTag(text: String, highlighted: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun formatDate(iso: String?): String? = iso?.let {
    runCatching {
        Instant.parse(it).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrNull()
}

private fun formatDateTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
