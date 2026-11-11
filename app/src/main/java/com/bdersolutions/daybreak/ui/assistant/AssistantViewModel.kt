package com.bdersolutions.daybreak.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.assistant.AppAssistantData
import com.bdersolutions.daybreak.data.assistant.AssistantApi
import com.bdersolutions.daybreak.data.assistant.AssistantContext
import com.bdersolutions.daybreak.data.assistant.AssistantData
import com.bdersolutions.daybreak.data.assistant.AssistantCard
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.data.assistant.AssistantEvent
import com.bdersolutions.daybreak.data.assistant.AssistantFailure
import com.bdersolutions.daybreak.data.assistant.AssistantIdentity
import com.bdersolutions.daybreak.data.assistant.AssistantModels
import com.bdersolutions.daybreak.data.assistant.AssistantToolRunner
import com.bdersolutions.daybreak.data.assistant.ChatAttachment
import com.bdersolutions.daybreak.data.assistant.ChatArchive
import com.bdersolutions.daybreak.data.assistant.ChatSaving
import com.bdersolutions.daybreak.data.assistant.ChatStore
import com.bdersolutions.daybreak.data.assistant.ChatSummary
import com.bdersolutions.daybreak.data.assistant.SavedChat
import com.bdersolutions.daybreak.data.assistant.SavedDraft
import com.bdersolutions.daybreak.data.assistant.TitleSource
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.bdersolutions.daybreak.data.assistant.ChatMessage
import com.bdersolutions.daybreak.data.assistant.ChatRole
import com.bdersolutions.daybreak.data.assistant.MessageDraft
import com.bdersolutions.daybreak.data.assistant.ToolCall
import com.bdersolutions.daybreak.data.assistant.ToolOutcome
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.haptics.Haptics
import com.bdersolutions.daybreak.data.support.SupportStore
import com.bdersolutions.daybreak.untis.UntisHomework
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A drafted message and what became of it. */
data class DraftState(
    val draft: MessageDraft,
    val status: Status,
) {
    enum class Status { PENDING, SENDING, SENT, CANCELLED, FAILED }
}

/** The tool that shows a website. Its arguments are read while the model writes them. */
internal const val WEBSITE_TOOL = "show_website"

/**
 * A website whose code is still being written: the arguments so far, for the card's "Generating code" and the
 * code view. Gone when the answer's tools run, and the finished website is a card then.
 */
data class LiveWebsite(val messageId: Long, val callId: String, val arguments: String)

/** What the chat shows. */
data class AssistantUiState(
    /** Every message, tool results included; the screen shows only questions and answers. */
    val messages: List<ChatMessage> = emptyList(),
    /** The id of the answer that is still streaming, or null. */
    val streamingId: Long? = null,
    /** True between sending and the first piece of an answer: the typing dots. */
    val waiting: Boolean = false,
    /** The tools running right now, by name, for the "looking it up" line. */
    val runningTools: List<String> = emptyList(),
    val failure: AssistantFailure? = null,
    /** Drafted messages by tool call id. */
    val drafts: Map<String, DraftState> = emptyMap(),
    /** Cards the assistant showed (a day, exams, absences, websites), by tool call id. */
    val cards: Map<String, AssistantCard> = emptyMap(),
    /** The website being written right now, or null. */
    val live: LiveWebsite? = null,
    /** Pictures and files waiting in the input bar. */
    val staged: List<ChatAttachment> = emptyList(),
    val models: AssistantModels? = null,
    val selectedModel: String? = null,
    val selectedEffort: String? = null,
    /** The saved chat this is, or null for a new chat that has no question yet. */
    val chatId: Long? = null,
    /** True right after a saved chat was opened: its latest answer is played in word by word. */
    val opened: Boolean = false,
    val history: List<ChatSummary> = emptyList(),
    /** The chat's name when it is not simply the first question (an AI title or the student's own). */
    val chatTitle: String? = null,
    val titleSource: TitleSource = TitleSource.AUTO,
) {
    val busy: Boolean get() = waiting || streamingId != null || runningTools.isNotEmpty() || pendingDraft != null
    val pendingDraft: DraftState? get() = drafts.values.firstOrNull { it.status == DraftState.Status.PENDING || it.status == DraftState.Status.SENDING }
}

/** The chat as it would be saved, stamped [updatedAt]; null for a chat without a question yet. */
internal fun AssistantUiState.toSavedChat(updatedAt: Long): SavedChat? {
    val id = chatId ?: return null
    if (messages.none { it.role == ChatRole.USER }) return null
    return SavedChat(
        id = id,
        title = chatTitle ?: ChatArchive.titleFor(messages),
        titleSource = titleSource,
        updatedAt = updatedAt,
        messages = messages,
        drafts = drafts.mapValues { (_, state) -> SavedDraft(state.draft, state.status.name) },
        cards = cards,
    )
}

/**
 * The turn is over: the typing dots and the streaming cursor go away, whatever the last event was.
 * A provider may finish a round with "done" but without any text, and the newest answer may already
 * have been shown, so neither the dots nor a stale streaming id may be left behind.
 */
internal fun AssistantUiState.turnFinished(): AssistantUiState = copy(waiting = false, streamingId = null, live = null)

/**
 * The chat up to (not including) the message at [index]: what a copy of the question or answer is resent from.
 * Cards and drafts that belong to dropped tool calls go with it.
 */
internal fun AssistantUiState.truncatedAt(index: Int): AssistantUiState {
    val kept = messages.take(index)
    val callIds = kept.flatMap { message -> message.toolCalls.map { it.id } }.toSet()
    return copy(
        messages = kept,
        cards = cards.filterKeys { it in callIds },
        drafts = drafts.filterKeys { it in callIds },
        live = null,
        streamingId = null,
        waiting = false,
        runningTools = emptyList(),
        failure = null,
    )
}

/**
 * Asking again after a failure. The half answer of the failed round is dropped: it is never sent again
 * (the request leaves trailing answers out), and keeping it would show the old half answer above the new one.
 * Tool results end with a tool message, so an answer whose tools ran is kept.
 */
internal fun AssistantUiState.retrying(): AssistantUiState = copy(
    messages = messages.dropLastWhile { it.role == ChatRole.ASSISTANT },
    waiting = true,
    streamingId = null,
    failure = null,
)

/**
 * The assistant chat. The conversation lives only in this ViewModel: it is never written to disk and is
 * gone when the page is closed for good.
 *
 * A question can take several rounds: the model may ask for tools (lessons, exams, notes, teachers), the
 * app runs them on the phone and sends the results back, until the model answers. A drafted message is
 * shown as a card and waits for the student; only "Send" sends it, and nothing else in the app can.
 */
class AssistantViewModel(
    private val api: AssistantApi?,
    private val data: AssistantData,
    private val identity: AssistantIdentity,
    private val timetable: TimetableRepository,
    private val schoolData: SchoolDataRepository,
    private val tools: AssistantToolRunner,
    private val supportStore: SupportStore,
    private val chatStore: ChatStore,
    /** Felt for each piece of an answer as it arrives; null where there is no motor (tests). */
    private val haptics: Haptics? = null,
) : ViewModel() {
    // The history the store already holds (it outlives this page), so the top bar's history icon is there
    // from the first frame instead of growing in while the page slides open.
    private val _state = MutableStateFlow(AssistantUiState(history = chatStore.chats.value))
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    val available: Boolean get() = api != null

    private var nextId = 1L

    /** The chat as last written (or as read when it was opened), to tell a real change from just opening it. */
    private var lastSaved: SavedChat? = null
    private var answerJob: Job? = null
    private var draftDecision: CompletableDeferred<Boolean>? = null

    // Homework is not in the timetable cache. It is asked for once, and a failure leaves it out.
    private val homework: Deferred<List<UntisHomework>> = viewModelScope.async {
        val today = LocalDate.now()
        runCatching { schoolData.homework(today.minusDays(1), today.plusDays(HOMEWORK_DAYS)) }.getOrDefault(emptyList())
    }

    // The school year's absences, asked for once like the homework; null when they could not be loaded.
    private val absences: Deferred<List<UntisAbsence>?> = viewModelScope.async { data.absences() }

    init {
        viewModelScope.launch { loadModels() }
        viewModelScope.launch {
            chatStore.load()
            chatStore.chats.collect { list -> _state.update { it.copy(history = list) } }
        }
        // Every chat is saved on the device as it goes, a moment after it changes.
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch {
            _state
                .map { listOf(it.chatId, it.messages, it.drafts, it.cards, it.chatTitle, it.titleSource) }
                .distinctUntilChanged()
                .debounce(SAVE_DELAY_MS)
                .collect {
                    val current = _state.value
                    val candidate = current.toSavedChat(System.currentTimeMillis()) ?: return@collect
                    // Only real changes are written; a chat that was only opened keeps its place and time.
                    val chat = ChatSaving.toSave(lastSaved, candidate) ?: return@collect
                    chatStore.save(chat)
                    lastSaved = chat
                }
        }
    }

    /**
     * Asks the server which models and options it offers now, and keeps the student's pick when it is still
     * there. Called when the assistant opens, so a change on the server shows without restarting the app.
     */
    fun refreshModels() {
        viewModelScope.launch { loadModels() }
    }

    /** An offline or failed request keeps the models shown before. */
    private suspend fun loadModels() {
        val models = api?.models() ?: return
        val saved = identity.model
        val selected = when {
            !models.showSwitcher -> null
            models.models.any { it.id == saved } -> saved
            else -> models.defaultModel
        }
        val savedEffort = identity.effort
        val effort = when {
            !models.showEffort -> null
            savedEffort in models.efforts -> savedEffort
            else -> models.defaultEffort
        }
        _state.update { it.copy(models = models, selectedModel = selected, selectedEffort = effort) }
    }

    fun selectEffort(effort: String) {
        identity.effort = effort
        _state.update { it.copy(selectedEffort = effort) }
    }

    /** Opens a saved chat to read or continue. A draft that was never answered stays unsent. */
    fun openChat(id: Long) {
        stop()
        viewModelScope.launch {
            val chat = chatStore.open(id) ?: return@launch
            nextId = (chat.messages.maxOfOrNull { it.id } ?: 0L) + 1
            val drafts = chat.drafts.mapValues { (_, saved) ->
                val status = runCatching { DraftState.Status.valueOf(saved.status) }.getOrDefault(DraftState.Status.CANCELLED)
                DraftState(
                    saved.draft,
                    if (status == DraftState.Status.PENDING || status == DraftState.Status.SENDING) DraftState.Status.CANCELLED else status,
                )
            }
            _state.update {
                AssistantUiState(
                    messages = chat.messages,
                    drafts = drafts,
                    cards = chat.cards,
                    chatId = chat.id,
                    opened = true,
                    chatTitle = chat.title.takeIf { chat.titleSource != TitleSource.AUTO },
                    titleSource = chat.titleSource,
                    models = it.models,
                    selectedModel = it.selectedModel,
                    selectedEffort = it.selectedEffort,
                    history = it.history,
                )
            }
            // What was read counts as saved, so opening the chat writes nothing.
            lastSaved = _state.value.toSavedChat(chat.updatedAt)
        }
    }

    /** The student's own name for a chat; a blank name goes back to the automatic one. */
    fun renameChat(id: Long, title: String) {
        val name = title.replace(Regex("\\s+"), " ").trim().take(60)
        if (_state.value.chatId == id) {
            _state.update {
                if (name.isEmpty()) it.copy(chatTitle = null, titleSource = TitleSource.AUTO) else it.copy(chatTitle = name, titleSource = TitleSource.MANUAL)
            }
        } else if (name.isNotEmpty()) {
            viewModelScope.launch { chatStore.rename(id, name) }
        }
    }

    /**
     * After the first answer of a new chat, asks the server for a short AI title. Nothing happens when the
     * chat already has one or the student named it; a failure keeps the first question as the name.
     */
    private fun nameChatIfNew(language: String) {
        val current = _state.value
        val api = api ?: return
        if (current.titleSource != TitleSource.AUTO) return
        val question = current.messages.firstOrNull { it.role == ChatRole.USER }?.text.orEmpty()
        val answer = current.messages.lastOrNull { it.role == ChatRole.ASSISTANT && it.text.isNotBlank() }?.text.orEmpty()
        if (question.isBlank() && answer.isBlank()) return
        val chatId = current.chatId ?: return
        viewModelScope.launch {
            val title = api.title(question, answer, language, current.selectedModel) ?: return@launch
            _state.update { if (it.chatId == chatId && it.titleSource == TitleSource.AUTO) it.copy(chatTitle = title, titleSource = TitleSource.AI) else it }
        }
    }

    fun deleteChat(id: Long) {
        if (_state.value.chatId == id) clear()
        viewModelScope.launch { chatStore.delete(id) }
    }

    fun selectModel(id: String) {
        identity.model = id
        _state.update { it.copy(selectedModel = id) }
    }

    fun addAttachment(attachment: ChatAttachment) {
        _state.update { if (it.staged.size >= MAX_ATTACHMENTS) it else it.copy(staged = it.staged + attachment) }
    }

    fun removeAttachment(attachment: ChatAttachment) {
        _state.update { it.copy(staged = it.staged - attachment) }
    }

    /** The student has read the usage sheet, or the error card was closed. */
    fun dismissFailure() {
        _state.update { it.copy(failure = null) }
    }

    fun send(text: String, language: String) {
        val current = _state.value
        val question = text.trim()
        if ((question.isEmpty() && current.staged.isEmpty()) || current.busy || api == null) return
        val message = ChatMessage(nextId++, ChatRole.USER, question, attachments = current.staged)
        _state.update {
            it.copy(
                messages = it.messages + message,
                staged = emptyList(),
                waiting = true,
                opened = false,
                failure = null,
                chatId = it.chatId ?: System.currentTimeMillis(),
            )
        }
        ask(language)
    }

    /** Asks again after a failure. */
    fun retry(language: String) {
        val state = _state.value
        if (state.busy || state.messages.none { it.role == ChatRole.USER }) return
        _state.update { it.retrying() }
        ask(language)
    }

    /**
     * Sends [text] in place of the question [id], and asks again: that question and everything after it are
     * replaced. Its pictures and files are sent again with the new text. Only when nothing is running.
     */
    fun editMessage(id: Long, text: String, language: String) {
        val state = _state.value
        if (state.busy) return
        val index = state.messages.indexOfFirst { it.id == id && it.role == ChatRole.USER }
        if (index < 0) return
        val attachments = state.messages[index].attachments
        _state.update { it.truncatedAt(index).copy(staged = attachments) }
        send(text, language)
    }

    /** Asks again for the answer [id]: that answer and everything after it are replaced by a new one. */
    fun regenerate(id: Long, language: String) {
        val state = _state.value
        if (state.busy) return
        val index = state.messages.indexOfFirst { it.id == id && it.role == ChatRole.ASSISTANT }
        if (index <= 0) return
        _state.update { it.truncatedAt(index).copy(waiting = true) }
        ask(language)
    }

    /** The student's answer to a drafted message. Only `true` sends it. */
    fun decideDraft(send: Boolean) {
        draftDecision?.complete(send)
    }

    private fun ask(language: String) {
        val api = api ?: return
        answerJob = viewModelScope.launch {
            // Homework makes answers better but must not hold the question up for long.
            val homeworkNow = withTimeoutOrNull(HOMEWORK_WAIT_MS) { homework.await() }.orEmpty()
            val absencesNow = withTimeoutOrNull(HOMEWORK_WAIT_MS) { absences.await() }
            // One summary per question, so the follow-up rounds reuse the provider's prompt cache.
            val context = AssistantContext.build(timetable.cache.value, LocalDateTime.now(), homeworkNow, absencesNow)
            val unlockCode = if (supportStore.paid.value) supportStore.unlockCode() else null
            repeat(MAX_ROUNDS) {
                val calls = streamOneTurn(api, context, language, unlockCode) ?: return@launch
                if (calls.isEmpty()) {
                    // The round is over, even when the model sent no text at all: the dots must go.
                    _state.update { it.turnFinished() }
                    nameChatIfNew(language)
                    return@launch
                }
                runTools(calls)
                _state.update { it.copy(waiting = true) }
            }
            _state.update { it.turnFinished() }
        }
    }

    /** One request: streams the text into a new answer. Returns the tool calls, or null after a failure. */
    private suspend fun streamOneTurn(api: AssistantApi, context: String, language: String, unlockCode: String?): List<ToolCall>? {
        val answerId = nextId++
        var calls: List<ToolCall> = emptyList()
        var failed = false
        // A website's code arrives in pieces: the arguments are kept per tool call (by stream index) for the live view.
        val liveArgs = HashMap<Int, StringBuilder>()
        val liveNames = HashMap<Int, String>()
        val liveIds = HashMap<Int, String>()
        var lastLive = 0L
        api.ask(_state.value.messages, context, language, _state.value.selectedModel, unlockCode, _state.value.selectedEffort).collect { event ->
            when (event) {
                is AssistantEvent.Text -> {
                    // Each piece of the answer is felt as it arrives.
                    haptics?.perform(HapticEvent.ANSWER_TOKEN)
                    _state.update { current ->
                        current.copy(messages = current.messages.upsert(answerId) { it.copy(text = it.text + event.text) }, waiting = false, streamingId = answerId)
                    }
                }

                // The model's reasoning arrives before the answer: it fills the "thinking" section.
                is AssistantEvent.Reasoning -> _state.update { current ->
                    current.copy(messages = current.messages.upsert(answerId) { it.copy(reasoning = it.reasoning + event.text) }, waiting = false, streamingId = answerId)
                }

                is AssistantEvent.ToolCalls -> {
                    calls = event.calls
                    _state.update { current ->
                        current.copy(messages = current.messages.upsert(answerId) { it.copy(toolCalls = event.calls) }, live = null)
                    }
                }

                is AssistantEvent.ToolDelta -> {
                    if (event.name.isNotBlank()) liveNames[event.index] = event.name
                    if (event.id.isNotBlank()) liveIds[event.index] = event.id
                    if (liveNames[event.index] == WEBSITE_TOOL) {
                        val arguments = liveArgs.getOrPut(event.index) { StringBuilder() }.append(event.delta)
                        // The code comes in many small pieces; the screen gets a fresh copy about eight times a second.
                        val now = System.nanoTime()
                        if (now - lastLive >= 120_000_000L) {
                            lastLive = now
                            val callId = liveIds[event.index].orEmpty()
                            val snapshot = arguments.toString()
                            _state.update { current ->
                                current.copy(
                                    messages = current.messages.upsert(answerId) { it },
                                    waiting = false,
                                    streamingId = answerId,
                                    live = LiveWebsite(answerId, callId, snapshot),
                                )
                            }
                        }
                    }
                }

                AssistantEvent.Done -> _state.update { it.turnFinished() }
                is AssistantEvent.Failure -> {
                    failed = true
                    // A half answer stays with the failure under it.
                    _state.update { it.copy(waiting = false, streamingId = null, failure = event.reason) }
                }
            }
        }
        _state.update { it.turnFinished() }
        return if (failed) null else calls
    }

    private suspend fun runTools(calls: List<ToolCall>) {
        for (call in calls) {
            _state.update { it.copy(runningTools = calls.map { c -> c.name }, waiting = false) }
            val content = when (val outcome = tools.run(call)) {
                is ToolOutcome.Result -> outcome.content
                is ToolOutcome.Confirm -> confirmAndMaybeSend(outcome.draft)
                is ToolOutcome.Card -> {
                    _state.update { it.copy(cards = it.cards + (call.id to outcome.card)) }
                    outcome.content
                }
            }
            _state.update { it.copy(messages = it.messages + ChatMessage(nextId++, ChatRole.TOOL, content, toolCallId = call.id)) }
        }
        _state.update { it.copy(runningTools = emptyList()) }
    }

    /** Shows the draft and waits. Sends only on the student's "Send"; anything else sends nothing. */
    private suspend fun confirmAndMaybeSend(draft: MessageDraft): String {
        val decision = CompletableDeferred<Boolean>()
        draftDecision = decision
        setDraft(draft, DraftState.Status.PENDING)
        _state.update { it.copy(runningTools = emptyList()) }
        val send = try {
            decision.await()
        } catch (error: CancellationException) {
            setDraft(draft, DraftState.Status.CANCELLED)
            throw error
        } finally {
            draftDecision = null
        }
        if (!send) {
            setDraft(draft, DraftState.Status.CANCELLED)
            return AssistantToolRunner.cancelledResult()
        }
        setDraft(draft, DraftState.Status.SENDING)
        return try {
            val count = schoolData.sendMail(draft.subject, draft.body, draft.recipientUserId)
            if (count == 1) {
                setDraft(draft, DraftState.Status.SENT)
                AssistantToolRunner.sentResult(draft)
            } else {
                // The server must report exactly one recipient, as on the Messages page.
                setDraft(draft, DraftState.Status.FAILED)
                AssistantToolRunner.failedResult("WebUntis reported $count recipients instead of one; check the sent folder.")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            setDraft(draft, DraftState.Status.FAILED)
            AssistantToolRunner.failedResult("WebUntis did not accept it (${error.javaClass.simpleName}).")
        }
    }

    private fun setDraft(draft: MessageDraft, status: DraftState.Status) {
        _state.update { it.copy(drafts = it.drafts + (draft.callId to DraftState(draft, status))) }
    }

    /** Stops the answer or the tools; what came so far stays, and a pending draft is not sent. */
    fun stop() {
        draftDecision?.complete(false)
        answerJob?.cancel()
        answerJob = null
        _state.update { current ->
            current.copy(
                waiting = false,
                streamingId = null,
                runningTools = emptyList(),
                drafts = current.drafts.mapValues { (_, state) ->
                    if (state.status == DraftState.Status.PENDING) state.copy(status = DraftState.Status.CANCELLED) else state
                },
            )
        }
    }

    fun clear() {
        stop()
        _state.update {
            AssistantUiState(models = it.models, selectedModel = it.selectedModel, selectedEffort = it.selectedEffort, history = it.history)
        }
    }

    private fun List<ChatMessage>.upsert(id: Long, change: (ChatMessage) -> ChatMessage): List<ChatMessage> {
        val index = indexOfFirst { it.id == id }
        return if (index < 0) this + change(ChatMessage(id, ChatRole.ASSISTANT, "")) else toMutableList().also { it[index] = change(it[index]) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AssistantViewModel::class.java))
            val data = AppAssistantData(
                timetable = container.timetableRepository,
                history = container.absenceLessonHistory,
                schoolData = container.schoolData,
                serverNotices = container.serverNotices,
            )
            return AssistantViewModel(
                api = container.assistantApi,
                data = data,
                identity = container.assistantIdentity,
                timetable = container.timetableRepository,
                schoolData = container.schoolData,
                tools = AssistantToolRunner(data),
                supportStore = container.supportStore,
                chatStore = container.chatStore,
                haptics = container.haptics,
            ) as T
        }
    }

    companion object {
        const val MAX_ATTACHMENTS = 4
        private const val MAX_ROUNDS = 6
        private const val SAVE_DELAY_MS = 700L
        private const val HOMEWORK_DAYS = 21L
        private const val HOMEWORK_WAIT_MS = 4_000L
    }
}
