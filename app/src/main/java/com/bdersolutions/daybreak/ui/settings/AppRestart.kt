package com.bdersolutions.daybreak.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import com.bdersolutions.daybreak.MainActivity
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
 * Starts the app's screen again from scratch: opens MainActivity in a fresh task and closes the old
 * one, so the title and the new launcher entry are read anew. The process is not ended: the new
 * activity runs in it too, so killing it would close the app again a moment after it opened.
 */
private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    var host: Context = context
    while (host is ContextWrapper && host !is Activity) host = host.baseContext
    (host as? Activity)?.finishAffinity()
}
