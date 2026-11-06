package com.bdersolutions.daybreak.ui.notices

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.notices.MessageFilter
import com.bdersolutions.daybreak.data.notices.NoticeLevel
import com.bdersolutions.daybreak.data.notices.ServerNotices
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.ui.settings.currentAppLanguage

/**
 * Shows the developer's messages (for example "WebUntis is down") one at a time. Each one is shown
 * once: "OK" remembers it, and a message the developer ends or hides is not shown at all.
 */
@Composable
fun ServerMessageDialog(notices: ServerNotices) {
    val status by notices.status.collectAsStateWithLifecycle()
    val dismissed by notices.dismissed.collectAsStateWithLifecycle()
    val pending = remember(status, dismissed) {
        MessageFilter.pending(status?.messages.orEmpty(), dismissed, System.currentTimeMillis())
    }
    val message = pending.firstOrNull() ?: return
    val german = currentAppLanguage() == AppLanguage.GERMAN

    AlertDialog(
        onDismissRequest = { notices.dismiss(message.id) },
        title = { Text(stringResource(message.level.titleRes())) },
        text = { Text(message.textFor(german)) },
        confirmButton = {
            TextButton(onClick = { notices.dismiss(message.id) }) {
                Text(stringResource(R.string.notice_ok))
            }
        },
    )
}

private fun NoticeLevel.titleRes(): Int = when (this) {
    NoticeLevel.INFO -> R.string.notice_title_info
    NoticeLevel.WARNING -> R.string.notice_title_warning
    NoticeLevel.CRITICAL -> R.string.notice_title_critical
}
