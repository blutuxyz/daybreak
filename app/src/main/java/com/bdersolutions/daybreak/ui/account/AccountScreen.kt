package com.bdersolutions.daybreak.ui.account

import com.bdersolutions.daybreak.ui.components.FilledTonalButton
import androidx.compose.runtime.remember
import com.bdersolutions.daybreak.ui.components.schoolPlainText
import com.bdersolutions.daybreak.ui.components.schoolRichText
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.ui.settings.SettingsPageScaffold
import com.bdersolutions.daybreak.ui.toUserMessage
import com.bdersolutions.daybreak.ui.upcoming.EmptyText
import com.bdersolutions.daybreak.ui.upcoming.ErrorBlock
import com.bdersolutions.daybreak.ui.upcoming.ItemCard

/** Who is signed in, and the school's notices of today. */
@Composable
fun AccountScreen(
    session: DaybreakSession,
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsPageScaffold(
        title = stringResource(R.string.account_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item(key = "profile") {
            ItemCard {
                Text(
                    text = session.studentName.ifBlank { session.displayName },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Fact(stringResource(R.string.account_school), session.schoolName.ifBlank { session.school })
                    Fact(stringResource(R.string.account_username), session.username)
                }
            }
        }

        item(key = "class-services") {
            ClassServicesCard(url = classServicesUrl(session))
        }

        item(key = "messages-title") {
            Text(
                text = stringResource(R.string.account_messages),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
            )
        }
        val error = state.error
        when {
            state.loading -> item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> item(key = "error") {
                ErrorBlock(message = error.toUserMessage(context), onRetry = viewModel::load)
            }

            state.messages.isEmpty() -> item(key = "empty") { EmptyText(R.string.account_messages_empty) }

            else -> items(state.messages, key = { "msg-${it.id}" }) { message ->
                ItemCard {
                    if (message.subject.isNotBlank()) {
                        Text(
                            // Notices are sometimes written in HTML; the subject stays one plain line.
                            text = remember(message.subject) { schoolPlainText(message.subject) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (message.text.isNotBlank()) {
                        if (message.subject.isNotBlank()) Spacer(Modifier.height(6.dp))
                        val linkColor = MaterialTheme.colorScheme.primary
                        Text(
                            text = remember(message.text, linkColor) { schoolRichText(message.text, linkColor) },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The class services (Klassendienste) page of the school's WebUntis. The list is only served to a
 * web sign-in, not to the app's key, so the app opens the page in the browser. Null when the
 * server is not an https address.
 */
private fun classServicesUrl(session: DaybreakSession): String? {
    val server = session.server.trim().trimEnd('/')
    val absolute = if (server.contains("://")) server else "https://$server"
    return "$absolute/student-class-services".takeIf { it.startsWith("https://") }
}

@Composable
private fun ClassServicesCard(url: String?) {
    val context = LocalContext.current
    ItemCard {
        Text(
            text = stringResource(R.string.account_class_services),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.account_class_services_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.account_class_services_no_browser, Toast.LENGTH_LONG).show()
                }
            },
            enabled = url != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.account_class_services_open))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
