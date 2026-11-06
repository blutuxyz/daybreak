package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.desktop.update.UpdateState
import kotlin.system.exitProcess

/**
 * Tells the person once that the newest build is downloaded and checked. "Install now" starts the
 * installer and closes the app; "Later" keeps the download, and the More page still offers it.
 * A release is asked about once, not again after "Later".
 */
@Composable
fun UpdateReadyHost(container: DesktopContainer, state: UpdateState) {
    val ready = state as? UpdateState.ReadyToInstall ?: return
    var dismissedTag by remember { mutableStateOf<String?>(null) }
    if (dismissedTag == ready.release.tag) return
    val version = ready.release.versionName.ifBlank { ready.release.tag }

    DesktopDialog(onDismiss = { dismissedTag = ready.release.tag }, maxWidth = 580.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                tr("Update bereit", "Update ready"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                tr(
                    "Version $version ist heruntergeladen und geprüft. Daybreak schließt sich für die Installation.",
                    "Version $version is downloaded and checked. Daybreak closes for the installation.",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ready.release.changelog.isNotBlank()) {
                Text(
                    tr("Das ist neu", "What is new"),
                    style = MaterialTheme.typography.titleMedium,
                )
                ChangelogView(ready.release.changelog, maxHeight = 340.dp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close, modifier = Modifier.handCursor()) {
                    Text(tr("Später", "Later"))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        container.updater.install(ready.file)
                        exitProcess(0)
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(tr("Jetzt installieren", "Install now"))
                }
            }
        }
    }
}

/**
 * A strip above the pages that shows an update while it is on its way: the download with a progress
 * bar, and afterwards "ready" with an install button, so the person does not have to open More to see
 * it. Nothing shows when there is no update, while checking, or when a check failed (an automatic check
 * must not nag about a network problem).
 */
@Composable
fun UpdateBanner(container: DesktopContainer, state: UpdateState) {
    val visible = state is UpdateState.Downloading || state is UpdateState.ReadyToInstall
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(DesktopMotion.effects()) + expandVertically(DesktopMotion.spatial()),
        exit = fadeOut(DesktopMotion.fastEffects()) + shrinkVertically(DesktopMotion.fastSpatial()),
    ) {
        // Keep the last text while the strip animates out.
        val shown = rememberLastBannerState(state)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val current = shown) {
                    is UpdateState.Downloading -> {
                        val version = current.release.versionName.ifBlank { current.release.tag }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                tr("Update $version wird heruntergeladen …", "Downloading update $version …"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (current.total > 0) {
                                LinearProgressIndicator(
                                    progress = { (current.done.toFloat() / current.total).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Text(
                            if (current.total > 0) {
                                "${current.done / MEGABYTE} / ${current.total / MEGABYTE} MB"
                            } else {
                                "${current.done / MEGABYTE} MB"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        val version = current.release.versionName.ifBlank { current.release.tag }
                        Text(
                            tr("Update $version ist bereit.", "Update $version is ready."),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                container.updater.install(current.file)
                                exitProcess(0)
                            },
                            modifier = Modifier.handCursor(),
                        ) { Text(tr("Jetzt installieren", "Install now")) }
                    }

                    else -> Unit
                }
            }
        }
    }
}

private const val MEGABYTE = 1024L * 1024L

/** The last download or ready state, so the strip keeps its text while it animates away. */
@Composable
private fun rememberLastBannerState(state: UpdateState): UpdateState {
    var last by remember { mutableStateOf(state) }
    if (state is UpdateState.Downloading || state is UpdateState.ReadyToInstall) last = state
    return last
}
