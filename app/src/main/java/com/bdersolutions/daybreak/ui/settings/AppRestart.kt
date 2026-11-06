package com.bdersolutions.daybreak.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.bdersolutions.daybreak.DaybreakApplication
import com.bdersolutions.daybreak.RestartActivity
import com.bdersolutions.daybreak.data.branding.AppNaming
import com.bdersolutions.daybreak.R
import kotlinx.coroutines.delay

/** How long the "applying changes" popup is shown before the app opens again. */
private const val RESTART_DELAY_MS = 2900L

/**
 * A popup that cannot be dismissed. It shows the expressive loading indicator and, after
 * [RESTART_DELAY_MS], starts the app again so a changed launcher name or icon is picked up. Used by
 * the supporter pages that change the launcher entry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RestartDialog() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(RESTART_DELAY_MS)
        restartApp(context)
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { LoadingIndicator(modifier = Modifier.size(56.dp)) },
        text = {
            Text(
                text = stringResource(R.string.app_name_restart),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {},
    )
}

/**
 * Restarts the whole app. The new launcher name and icon are not switched here but by
 * [RestartActivity], which runs in its own process after this one has exited: a restart from inside
 * the process that is being restarted fails on phones that close an app whose launcher entry changes
 * (seen on Samsung and Xiaomi release builds). This process ends right after handing over.
 */
private fun restartApp(context: Context) {
    val container = (context.applicationContext as DaybreakApplication).container
    val chosen = container.settingsRepository.current()
    val paid = container.supportStore.paid.value
    val intent = RestartActivity.intent(
        context,
        AppNaming.effectivePreset(paid, chosen.appNamePreset),
        AppNaming.effectiveIconStyle(paid, chosen.appIconStyle),
    )
    context.startActivity(intent)
    var host: Context = context
    while (host is ContextWrapper && host !is Activity) host = host.baseContext
    (host as? Activity)?.finishAndRemoveTask()
    // Let the system take over the launch, then end this process so the app starts clean.
    Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, EXIT_AFTER_HANDOVER_MS)
}

private const val EXIT_AFTER_HANDOVER_MS = 250L
