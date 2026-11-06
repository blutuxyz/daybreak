package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    DesktopDialog(onDismiss = { dismissedTag = ready.release.tag }, maxWidth = 480.dp) { close ->
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
                    ready.release.changelog.take(600),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
