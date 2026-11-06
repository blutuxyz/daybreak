package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.untis.MailFolder
import com.bdersolutions.daybreak.untis.UntisAttachment
import com.bdersolutions.daybreak.untis.UntisMail
import com.bdersolutions.daybreak.untis.UntisRecipient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

/** The largest attachment the desktop saves, the same limit as the Android app. */
private const val MAX_ATTACHMENT_BYTES = 100L * 1024 * 1024

/**
 * The WebUntis message box. Students can read, write to one teacher at a time (the server decides
 * who is listed), and save attachments.
 */
@Composable
fun MessagesScreen(container: DesktopContainer) {
    val language = LocalLanguage.current
    val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", localeOf(language))
    var folder by remember { mutableStateOf(MailFolder.INBOX) }
    var mails by remember { mutableStateOf<List<UntisMail>>(emptyList()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<UntisMail?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var composing by remember { mutableStateOf(false) }

    LaunchedEffect(folder, reloadKey) {
        loading = true
        error = null
        try {
            mails = container.schoolData.mails(folder).sortedByDescending { it.sentAt }
            selectedId = null
            detail = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage(language, trFor(language, "Nachrichten konnten nicht geladen werden.", "Could not load messages."))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        detail = null
        try {
            detail = container.schoolData.mail(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage(language, trFor(language, "Nachricht konnte nicht geöffnet werden.", "Could not open the message."))
        }
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.fillMaxHeight().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MailFolder.entries.forEach { option ->
                    val label = folderLabel(option)
                    if (option == folder) {
                        Button(onClick = {}) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { folder = option }) { Text(label) }
                    }
                }
                OutlinedButton(onClick = { reloadKey++ }) { Text(tr("Aktualisieren", "Refresh")) }
            }
            Button(onClick = { composing = true }) { Text(tr("Neue Nachricht", "New message")) }
            if (loading) CircularProgressIndicator(modifier = Modifier.height(24.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!loading && error == null && mails.isEmpty()) {
                Text(tr("Keine Nachrichten in diesem Ordner.", "No messages in this folder."))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(mails, key = { it.id }) { mail ->
                    Card(onClick = { selectedId = mail.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                mail.subject.ifBlank { tr("(ohne Betreff)", "(no subject)") },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (mail.read) FontWeight.Normal else FontWeight.SemiBold,
                            )
                            val who = mail.sender?.name ?: mail.recipients.joinToString { it.name }
                            Text(
                                listOfNotNull(who.takeIf { it.isNotBlank() }, mail.sentAt?.format(dateTime)).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxHeight().weight(1.5f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val current = detail
            if (current == null) {
                Text(if (selectedId == null) tr("Wähle eine Nachricht.", "Choose a message.") else tr("Lädt…", "Loading…"))
            } else {
                Text(current.subject, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(current.sender?.name, current.sentAt?.format(dateTime)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(current.content.ifBlank { current.preview }, style = MaterialTheme.typography.bodyLarge)
                if (current.attachments.isNotEmpty()) {
                    Text(tr("Anhänge", "Attachments"), style = MaterialTheme.typography.titleSmall)
                    current.attachments.forEach { attachment ->
                        AttachmentRow(container, attachment, onStatus = { notice = it })
                    }
                }
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    if (composing) {
        ComposeDialog(
            container = container,
            onDismiss = { composing = false },
            onSent = {
                composing = false
                folder = MailFolder.SENT
                notice = trFor(language, "Nachricht gesendet.", "Message sent.")
            },
        )
    }
}

@Composable
private fun folderLabel(folder: MailFolder): String = when (folder) {
    MailFolder.INBOX -> tr("Posteingang", "Inbox")
    MailFolder.SENT -> tr("Gesendet", "Sent")
    MailFolder.DRAFTS -> tr("Entwürfe", "Drafts")
}
/** One attachment with a save button. The file name is chosen by the user in a save dialog. */
@Composable
private fun AttachmentRow(container: DesktopContainer, attachment: UntisAttachment, onStatus: (String) -> Unit) {
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(attachment.name, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            // The dialog runs on the UI thread; the download runs on IO.
            val target = pickSaveTarget(safeFileName(attachment.name)) ?: return@OutlinedButton
            scope.launch {
                onStatus(trFor(language, "Wird gespeichert…", "Saving…"))
                try {
                    withContext(Dispatchers.IO) {
                        val link = container.schoolData.attachmentLink(attachment.id)
                        FileOutputStream(target).use { out ->
                            container.schoolData.downloadAttachment(link, out, MAX_ATTACHMENT_BYTES)
                        }
                    }
                    onStatus(trFor(language, "Gespeichert: ${target.name}", "Saved: ${target.name}"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    target.delete()
                    onStatus(e.userMessage(language, trFor(language, "Anhang konnte nicht gespeichert werden.", "Could not save the attachment.")))
                }
            }
        }) { Text(tr("Speichern…", "Save…")) }
    }
}
