package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.untis.UntisRecipient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The new-message form: a teacher, a subject and a text. Sending asks first, in a second dialog,
 * and needs the server to confirm exactly one recipient, as the Android app does.
 */
@Composable
internal fun ComposeDialog(container: DesktopContainer, onDismiss: () -> Unit, onSent: () -> Unit) {
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    var recipients by remember { mutableStateOf<List<UntisRecipient>>(emptyList()) }
    var loadingRecipients by remember { mutableStateOf(true) }
    var recipientsError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<UntisRecipient?>(null) }
    var subject by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            recipients = container.schoolData.recipients().sortedWith(compareBy({ it.group.ordinal }, { it.name }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recipientsError = e.message ?: trFor(language, "Empfänger konnten nicht geladen werden.", "Could not load recipients.")
        } finally {
            loadingRecipients = false
        }
    }

    val shown = recipients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    val canSend = chosen != null && subject.isNotBlank() && content.isNotBlank() && !sending

    DesktopDialog(onDismiss = { if (!sending) onDismiss() }, maxWidth = 620.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                tr("Neue Nachricht", "New message"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            if (loadingRecipients) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            recipientsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(tr("Lehrer suchen", "Search teachers")) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(modifier = Modifier.height(150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(shown, key = { it.userId }) { recipient ->
                    ChoiceRow(
                        label = listOfNotNull(recipient.name, recipient.tags.joinToString().takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        selected = chosen?.userId == recipient.userId,
                        onClick = { chosen = recipient },
                    )
                }
            }
            Text(
                chosen?.let { tr("An: ${it.name}", "To: ${it.name}") } ?: tr("Kein Empfänger gewählt.", "No recipient chosen."),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                singleLine = true,
                label = { Text(tr("Betreff", "Subject")) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                minLines = 6,
                label = { Text(tr("Nachricht", "Message")) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !sending, onClick = close, modifier = Modifier.handCursor()) {
                    Text(tr("Abbrechen", "Cancel"))
                }
                Spacer(Modifier.width(8.dp))
                Button(enabled = canSend, onClick = { confirming = true }, modifier = Modifier.handCursor()) {
                    Text(tr("Senden", "Send"))
                }
            }
        }
    }

    val pending = chosen
    if (confirming && pending != null) {
        DesktopDialog(onDismiss = { confirming = false }, maxWidth = 440.dp) { closeConfirm ->
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    tr("Nachricht senden?", "Send the message?"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(tr("Die Nachricht geht an ${pending.name}.", "The message goes to ${pending.name}."))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = closeConfirm, modifier = Modifier.handCursor()) {
                        Text(tr("Abbrechen", "Cancel"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        modifier = Modifier.handCursor(),
                        onClick = {
                            closeConfirm()
                            sending = true
                            error = null
                            scope.launch {
                                try {
                                    val count = container.schoolData.sendMail(subject, content, pending.userId)
                                    if (count == 1) {
                                        onSent()
                                    } else {
                                        // Exactly one recipient must be confirmed by the server, as in the Android app.
                                        error = trFor(
                                            language,
                                            "Der Server meldet $count Empfänger statt eines. Nichts wurde gesendet.",
                                            "The server reports $count recipients instead of one. Nothing was sent.",
                                        )
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = e.message ?: trFor(language, "Senden fehlgeschlagen.", "Sending failed.")
                                } finally {
                                    sending = false
                                }
                            }
                        },
                    ) {
                        Text(tr("Senden", "Send"))
                    }
                }
            }
        }
    }
}
