package com.bdersolutions.daybreak.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.bdersolutions.daybreak.ui.settings.SettingsPageScaffold
import com.bdersolutions.daybreak.ui.toUserMessage
import com.bdersolutions.daybreak.ui.upcoming.EmptyText
import com.bdersolutions.daybreak.ui.upcoming.ErrorBlock
import com.bdersolutions.daybreak.untis.MailFolder
import com.bdersolutions.daybreak.untis.UntisMail
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The message box of WebUntis: inbox, sent and drafts. A tap opens the whole message in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val folders = MailFolder.entries

    SettingsPageScaffold(
        title = stringResource(R.string.messages_title),
        onBack = onBack,
        modifier = modifier,
    ) {
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

    state.open?.let { open ->
        ModalBottomSheet(
            onDismissRequest = viewModel::close,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            MailSheetContent(open = open, folder = state.folder, locale = locale)
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
                    text = mail.preview,
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
private fun MailSheetContent(open: OpenMail, folder: MailFolder, locale: Locale) {
    val context = LocalContext.current
    val mail = open.full ?: open.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
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
                    Text(
                        text = mail.content.ifBlank { stringResource(R.string.messages_no_text) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (mail.hasAttachments) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.messages_attachments),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
