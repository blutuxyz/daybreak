package com.bdersolutions.daybreak.ui.messages

import com.bdersolutions.daybreak.ui.components.SegmentedButton
import com.bdersolutions.daybreak.ui.components.FilterChip
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.components.FilledTonalButton
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.components.PushHost
import com.bdersolutions.daybreak.ui.components.schoolPlainText
import com.bdersolutions.daybreak.ui.components.schoolRichText
import androidx.compose.runtime.remember
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.attachments.AttachmentDownloader
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.ui.settings.SettingsPageScaffold
import com.bdersolutions.daybreak.ui.toUserMessage
import com.bdersolutions.daybreak.ui.upcoming.EmptyText
import com.bdersolutions.daybreak.ui.upcoming.ErrorBlock
import com.bdersolutions.daybreak.untis.MailFolder
import com.bdersolutions.daybreak.untis.RecipientGroup
import com.bdersolutions.daybreak.untis.UntisAttachment
import com.bdersolutions.daybreak.untis.UntisNetworkException
import com.bdersolutions.daybreak.untis.UntisRecipient
import com.bdersolutions.daybreak.untis.UntisMail
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The message box of WebUntis: inbox, sent and drafts. A tap pushes the whole message as a page of
 * its own, like every other full screen; writing a message stays a sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val locale = LocalConfiguration.current.locales[0]
    val folders = MailFolder.entries

    PushHost(
        page = state.open,
        onBack = {
            haptics.perform(HapticEvent.BACK)
            viewModel.close()
        },
        modifier = modifier,
        base = {
    SettingsPageScaffold(
        title = stringResource(R.string.messages_title),
        onBack = onBack,
    ) {
        if (viewModel.sendEnabled) item(key = "new") {
            FilledTonalButton(
                onClick = viewModel::openCompose,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 0.dp),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.messages_new))
            }
            Spacer(Modifier.height(8.dp))
        }

        item(key = "folders") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                folders.forEachIndexed { index, folder ->
                    SegmentedButton(
                        selected = state.folder == folder,
                        onClick = { viewModel.select(folder) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = folders.size),
                    ) {
                        Text(stringResource(folder.label), maxLines = 1)
                    }
                }
            }
        }

        val list = state.current
        val error = list.error
        when {
            list.loading -> item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> item(key = "error") {
                ErrorBlock(message = error.toUserMessage(context), onRetry = { viewModel.load() })
            }

            list.mails.isEmpty() -> item(key = "empty") { EmptyText(state.folder.emptyText) }

            else -> items(list.mails, key = { "mail-${state.folder}-${it.id}" }) { mail ->
                MailCard(
                    mail = mail,
                    folder = state.folder,
                    locale = locale,
                    onClick = { viewModel.open(mail) },
                )
            }
        }
    }

        },
    ) { open ->
        SettingsPageScaffold(
            title = stringResource(R.string.messages_detail_title),
            onBack = {
                haptics.perform(HapticEvent.BACK)
                viewModel.close()
            },
        ) {
            item(key = "mail") {
                MailPageContent(
                    open = open,
                    folder = state.folder,
                    locale = locale,
                    downloading = state.downloading,
                    onDownload = viewModel::downloadAttachment,
                )
            }
        }
    }

    state.compose?.let { form ->
        ComposeSheet(
            form = form,
            onDismiss = viewModel::closeCompose,
            onPick = viewModel::pickRecipient,
            onSubject = viewModel::setSubject,
            onContent = viewModel::setContent,
            onSend = viewModel::askToSend,
            onConfirm = viewModel::send,
            onDismissConfirm = viewModel::dismissConfirm,
            onRetryRecipients = viewModel::loadRecipients,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.sentEvents.collect { haptics.perform(HapticEvent.MESSAGE_SENT) }
    }

    val activity = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.attachmentEvents.collect { event ->
            when (event) {
                is AttachmentEvent.Ready -> {
                    val saved = event.saved
                    if (saved.inDownloads) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.messages_attachment_saved, saved.file.name),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    if (AttachmentDownloader.canOpen(activity, saved)) {
                        try {
                            activity.startActivity(AttachmentDownloader.viewIntent(activity, saved))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(activity, R.string.messages_attachment_no_app, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(activity, R.string.messages_attachment_no_app, Toast.LENGTH_LONG).show()
                    }
                }

                is AttachmentEvent.Failed -> Toast.makeText(
                    activity,
                    activity.getString(R.string.messages_attachment_failed, event.error.toUserMessage(activity)),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

}

private val MailFolder.label: Int
    get() = when (this) {
        MailFolder.INBOX -> R.string.messages_inbox
        MailFolder.SENT -> R.string.messages_sent
        MailFolder.DRAFTS -> R.string.messages_drafts
    }

private val MailFolder.emptyText: Int
    get() = when (this) {
        MailFolder.INBOX -> R.string.messages_inbox_empty
        MailFolder.SENT -> R.string.messages_sent_empty
        MailFolder.DRAFTS -> R.string.messages_drafts_empty
    }

/** Who the list shows on a card: the sender in the inbox, the recipients elsewhere. */
@Composable
private fun counterpart(mail: UntisMail, folder: MailFolder): String {
    val names = if (folder == MailFolder.INBOX) {
        listOfNotNull(mail.sender?.name)
    } else {
        mail.recipients.map { it.name }
    }
    return names.joinToString(", ").ifBlank { stringResource(R.string.messages_unknown_person) }
}

@Composable
private fun MailCard(mail: UntisMail, folder: MailFolder, locale: Locale, onClick: () -> Unit) {
    val unread = !mail.read
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = counterpart(mail, folder),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                mail.sentAt?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = shortTime(it, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = mail.subject.ifBlank { stringResource(R.string.messages_no_subject) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (mail.preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = remember(mail.preview) { schoolPlainText(mail.preview) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MailPageContent(
    open: OpenMail,
    folder: MailFolder,
    locale: Locale,
    downloading: Set<String>,
    onDownload: (UntisAttachment) -> Unit,
) {
    val context = LocalContext.current
    val mail = open.full ?: open.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .padding(top = 4.dp, bottom = 28.dp),
    ) {
        Text(
            text = mail.subject.ifBlank { stringResource(R.string.messages_no_subject) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        val label = if (folder == MailFolder.INBOX) R.string.messages_from else R.string.messages_to
        Text(
            text = "${stringResource(label)}: ${counterpart(mail, folder)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        mail.sentAt?.let {
            Text(
                text = longTime(it, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        val error = open.error
        when {
            error != null -> Text(
                text = error.toUserMessage(context),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )

            open.full == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> {
                SelectionContainer {
                    val linkColor = MaterialTheme.colorScheme.primary
                    val body = mail.content.ifBlank { stringResource(R.string.messages_no_text) }
                    Text(
                        // Web addresses in a message open in the browser when tapped.
                        text = remember(body, linkColor) { schoolRichText(body, linkColor) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (mail.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.messages_attachments),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    mail.attachments.forEach { file ->
                        AttachmentRow(file, loading = file.id in downloading, onClick = { onDownload(file) })
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.messages_attachments_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (mail.hasAttachments) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.messages_attachment_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ComposeSheet(
    form: ComposeState,
    onDismiss: () -> Unit,
    onPick: (UntisRecipient) -> Unit,
    onSubject: (String) -> Unit,
    onContent: (String) -> Unit,
    onSend: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onRetryRecipients: () -> Unit,
) {
    val context = LocalContext.current
    // The form needs the keyboard, so the sheet opens at full height; a half-open sheet only gets in the way.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!form.sending) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The keyboard covers the lower part of the sheet. Without this the message field and the
                // send button stay behind it and cannot be reached or scrolled to.
                .imePadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.messages_new),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.messages_recipient),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val recipientsError = form.recipientsError
            when {
                form.loadingRecipients -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                recipientsError != null -> {
                    Text(recipientsError.toUserMessage(context), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetryRecipients) { Text(stringResource(R.string.action_retry)) }
                }
                else -> {
                    listOf(
                        RecipientGroup.CLASS_TEACHER to R.string.messages_class_teacher,
                        RecipientGroup.TEACHER to R.string.messages_teachers,
                    ).forEach { (group, label) ->
                        val people = form.recipients.filter { it.group == group }
                        if (people.isNotEmpty()) {
                            Text(
                                text = stringResource(label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                people.forEach { person ->
                                    FilterChip(
                                        selected = form.recipient?.userId == person.userId,
                                        onClick = { onPick(person) },
                                        enabled = !form.sending,
                                        shape = CircleShape,
                                        label = { Text(person.name) },
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.messages_one_teacher_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = form.subject,
                onValueChange = onSubject,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.messages_subject)) },
                shape = CircleShape,
                singleLine = true,
                enabled = !form.sending,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = form.content,
                onValueChange = onContent,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.messages_text)) },
                // A tall field cannot be a full pill, so it gets a large corner radius instead.
                shape = RoundedCornerShape(28.dp),
                minLines = 5,
                enabled = !form.sending,
            )
            form.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error.toUserMessage(context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (error is IllegalStateException || error is UntisNetworkException) {
                    Text(
                        text = stringResource(R.string.messages_send_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSend,
                enabled = form.canSend,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(if (form.sending) R.string.messages_sending else R.string.messages_send))
            }
        }
    }
    if (form.confirming) {
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = { Text(stringResource(R.string.messages_confirm_title)) },
            text = {
                Text(stringResource(R.string.messages_confirm_text, form.recipient?.name.orEmpty()))
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.messages_send)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirm) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun AttachmentRow(file: UntisAttachment, loading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !loading && file.id.isNotBlank(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/** Today shows the time, this year the day and month, older messages the full date. */
private fun shortTime(time: LocalDateTime, locale: Locale): String {
    val today = LocalDate.now()
    val pattern = when {
        time.toLocalDate() == today -> "HH:mm"
        time.year == today.year -> "d. MMM"
        else -> "dd.MM.yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

private fun longTime(time: LocalDateTime, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy · HH:mm", locale).format(time)
