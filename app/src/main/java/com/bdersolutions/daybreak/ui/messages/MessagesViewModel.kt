package com.bdersolutions.daybreak.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.untis.MailFolder
import com.bdersolutions.daybreak.untis.UntisMail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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

data class MessagesUiState(
    val folder: MailFolder = MailFolder.INBOX,
    val lists: Map<MailFolder, MailListState> = emptyMap(),
    val open: OpenMail? = null,
) {
    val current: MailListState get() = lists[folder] ?: MailListState()
}

class MessagesViewModel(
    private val schoolData: SchoolDataRepository,
) : ViewModel() {
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
            return MessagesViewModel(container.schoolData) as T
        }
    }
}
