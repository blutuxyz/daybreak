package com.bdersolutions.daybreak.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.notices.RemoteFeatures
import com.bdersolutions.daybreak.data.notices.ServerNotices
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.attachments.AttachmentDownloader
import com.bdersolutions.daybreak.data.attachments.SavedAttachment
import com.bdersolutions.daybreak.untis.MailFolder
import com.bdersolutions.daybreak.untis.UntisAttachment
import com.bdersolutions.daybreak.untis.RecipientGroup
import com.bdersolutions.daybreak.untis.UntisMail
import com.bdersolutions.daybreak.untis.UntisRecipient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One folder of the message box while it is loaded, shown or failed. */
data class MailListState(
    val loading: Boolean = true,
    val mails: List<UntisMail> = emptyList(),
    val error: Throwable? = null,
)

/** The message that is open. [summary] comes from the list, so the header shows at once. */
data class OpenMail(
    val summary: UntisMail,
    val full: UntisMail? = null,
    val error: Throwable? = null,
)

/** The new-message form. [recipient] is the one teacher the message goes to. */
data class ComposeState(
    val recipients: List<UntisRecipient> = emptyList(),
    val loadingRecipients: Boolean = true,
    val recipientsError: Throwable? = null,
    val recipient: UntisRecipient? = null,
    val subject: String = "",
    val content: String = "",
    val confirming: Boolean = false,
    val sending: Boolean = false,
    val error: Throwable? = null,
) {
    val canSend: Boolean
        get() = recipient != null && subject.isNotBlank() && content.isNotBlank() && !sending
}

/** One-off results of an attachment download, for the screen to show. */
sealed interface AttachmentEvent {
    data class Ready(val saved: SavedAttachment) : AttachmentEvent
    data class Failed(val error: Throwable) : AttachmentEvent
}

data class MessagesUiState(
    val folder: MailFolder = MailFolder.INBOX,
    val lists: Map<MailFolder, MailListState> = emptyMap(),
    val open: OpenMail? = null,
    val compose: ComposeState? = null,
    /** Ids of the attachments that are being downloaded. */
    val downloading: Set<String> = emptySet(),
) {
    val current: MailListState get() = lists[folder] ?: MailListState()
}

class MessagesViewModel(
    private val schoolData: SchoolDataRepository,
    private val downloader: AttachmentDownloader,
    private val serverNotices: ServerNotices,
) : ViewModel() {
    /** False while the developer has writing messages switched off (see RemoteFeatures). */
    val sendEnabled: Boolean get() = serverNotices.featureOn(RemoteFeatures.MESSAGES_SEND)

    /** Emits once when a message has been sent and the server confirmed the single recipient. */
    private val _sentEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sentEvents: SharedFlow<Unit> = _sentEvents.asSharedFlow()

    private val _attachmentEvents = MutableSharedFlow<AttachmentEvent>(extraBufferCapacity = 4)
    val attachmentEvents: SharedFlow<AttachmentEvent> = _attachmentEvents.asSharedFlow()

    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    init {
        load(MailFolder.INBOX)
    }

    fun select(folder: MailFolder) {
        _state.update { it.copy(folder = folder) }
        if (_state.value.lists[folder] == null) load(folder)
    }

    /** Reads a folder again. */
    fun load(folder: MailFolder = _state.value.folder) {
        _state.update { it.copy(lists = it.lists + (folder to MailListState(loading = true))) }
        viewModelScope.launch {
            val result = try {
                MailListState(loading = false, mails = schoolData.mails(folder))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                MailListState(loading = false, error = error)
            }
            _state.update { it.copy(lists = it.lists + (folder to result)) }
        }
    }

    fun open(mail: UntisMail) {
        _state.update { it.copy(open = OpenMail(summary = mail)) }
        viewModelScope.launch {
            val result = try {
                OpenMail(summary = mail, full = schoolData.mail(mail.id))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                OpenMail(summary = mail, error = error)
            }
            // The user may have closed or switched messages meanwhile.
            _state.update { if (it.open?.summary?.id == mail.id) it.copy(open = result) else it }
            if (result.full != null) markRead(mail.id)
        }
    }

    fun close() {
        _state.update { it.copy(open = null) }
    }

    /** Downloads one attached file, then tells the screen to open it. */
    fun downloadAttachment(attachment: UntisAttachment) {
        if (attachment.id in _state.value.downloading) return
        _state.update { it.copy(downloading = it.downloading + attachment.id) }
        viewModelScope.launch {
            try {
                _attachmentEvents.emit(AttachmentEvent.Ready(downloader.download(attachment)))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _attachmentEvents.emit(AttachmentEvent.Failed(error))
            } finally {
                _state.update { it.copy(downloading = it.downloading - attachment.id) }
            }
        }
    }

    fun openCompose() {
        if (!sendEnabled || _state.value.compose != null) return
        _state.update { it.copy(compose = ComposeState()) }
        loadRecipients()
    }

    fun closeCompose() {
        _state.update { it.copy(compose = null) }
    }

    fun loadRecipients() {
        updateCompose { it.copy(loadingRecipients = true, recipientsError = null) }
        viewModelScope.launch {
            try {
                val people = schoolData.recipients()
                    .filter { it.group != RecipientGroup.OTHER }
                updateCompose { it.copy(loadingRecipients = false, recipients = people) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateCompose { it.copy(loadingRecipients = false, recipientsError = error) }
            }
        }
    }

    fun pickRecipient(recipient: UntisRecipient) = updateCompose { it.copy(recipient = recipient, error = null) }

    fun setSubject(value: String) = updateCompose { it.copy(subject = value, error = null) }

    fun setContent(value: String) = updateCompose { it.copy(content = value, error = null) }

    /** Asks to confirm before anything leaves the phone. */
    fun askToSend() = updateCompose { if (it.canSend) it.copy(confirming = true) else it }

    fun dismissConfirm() = updateCompose { it.copy(confirming = false) }

    fun send() {
        if (!sendEnabled) return
        val form = _state.value.compose ?: return
        val recipient = form.recipient ?: return
        if (!form.canSend) return
        updateCompose { it.copy(confirming = false, sending = true, error = null) }
        viewModelScope.launch {
            try {
                val count = schoolData.sendMail(form.subject, form.content, recipient.userId)
                if (count != 1) {
                    // The server must report exactly one recipient. Anything else is not what was meant.
                    updateCompose { it.copy(sending = false, error = IllegalStateException("Recipients: $count")) }
                    load(MailFolder.SENT)
                    return@launch
                }
                _state.update { it.copy(compose = null, folder = MailFolder.SENT) }
                _sentEvents.tryEmit(Unit)
                load(MailFolder.SENT)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateCompose { it.copy(sending = false, error = error) }
            }
        }
    }

    private fun updateCompose(change: (ComposeState) -> ComposeState) {
        _state.update { state -> state.compose?.let { state.copy(compose = change(it)) } ?: state }
    }

    /** Opening a message reads it, so the dot goes out of the list. */
    private fun markRead(id: Long) {
        _state.update { state ->
            state.copy(
                lists = state.lists.mapValues { (_, list) ->
                    list.copy(mails = list.mails.map { if (it.id == id) it.copy(read = true) else it })
                },
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MessagesViewModel::class.java))
            return MessagesViewModel(container.schoolData, container.attachmentDownloader, container.serverNotices) as T
        }
    }
}
