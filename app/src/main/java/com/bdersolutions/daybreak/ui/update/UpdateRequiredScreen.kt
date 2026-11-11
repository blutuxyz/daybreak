package com.bdersolutions.daybreak.ui.update

import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.components.Surface
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.update.UpdateManager
import com.bdersolutions.daybreak.data.update.UpdateState
import com.bdersolutions.daybreak.ui.settings.UpdateStatusCard
import kotlinx.coroutines.launch

/**
 * Shown instead of the whole app when the installed version is older than the server's minimum. It
 * runs the normal update flow (check, download, install) and has no way past it except closing the
 * app. [requiredVersionName] is the version the server asks for.
 */
@Composable
fun UpdateRequiredRoute(
    manager: UpdateManager,
    requiredVersionName: String,
) {
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canInstall by remember { mutableStateOf(manager.installer.canInstall()) }

    // Back from Android's "install unknown apps" screen: the permission may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = manager.installer.canInstall() }

    LaunchedEffect(Unit) { manager.check() }

    UpdateRequiredScreen(
        state = state,
        requiredVersionName = requiredVersionName,
        currentVersion = manager.currentVersionName,
        lastCheckedAt = remember(state) { manager.lastCheckAt },
        canInstall = canInstall,
        onCheck = { scope.launch { manager.check() } },
        onDownload = manager::downloadInBackground,
        onCancel = manager::cancelDownload,
        onInstall = manager::install,
        onAllowInstall = { context.startActivity(manager.installer.permissionSettingsIntent()) },
        onClose = { context.findActivity()?.finishAffinity() },
    )
}

@Composable
fun UpdateRequiredScreen(
    state: UpdateState,
    requiredVersionName: String,
    currentVersion: String,
    lastCheckedAt: Long,
    canInstall: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstall: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // There is no "later" here: Back closes the app, like the terms screen.
    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
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
                text = stringResource(R.string.update_required_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.update_required_text, requiredVersionName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
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
            // The server asks for a version that is not published yet: say so, rather than "up to date".
            if (state is UpdateState.UpToDate) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.update_required_unavailable, requiredVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.updates_current_version, currentVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_required_close))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
