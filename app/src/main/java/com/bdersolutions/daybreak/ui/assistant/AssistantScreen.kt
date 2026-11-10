package com.bdersolutions.daybreak.ui.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.border
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown
import com.bdersolutions.daybreak.data.assistant.AssistantModels
import com.bdersolutions.daybreak.data.assistant.AttachmentProblem
import com.bdersolutions.daybreak.data.assistant.AttachmentReader
import com.bdersolutions.daybreak.data.assistant.AssistantFailure
import com.bdersolutions.daybreak.data.assistant.ChatAttachment
import com.bdersolutions.daybreak.data.assistant.ChatMessage
import com.bdersolutions.daybreak.data.assistant.ChatRole
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.ui.components.motionAnimationsEnabled
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.ui.settings.currentAppLanguage
import com.bdersolutions.daybreak.ui.timetable.LessonFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The assistant chat page, pushed over the tabs like the other pages. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shows a lesson from a card (an exam, a lesson of a day) in the timetable. */
    onOpenLesson: ((LessonFocus) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language = if (currentAppLanguage() == AppLanguage.GERMAN) "de" else "en"
    var input by rememberSaveable { mutableStateOf("") }
    var attachmentNote by remember { mutableStateOf<Int?>(null) }
    // A fresh list per chat: messages are numbered per chat, so the same number in another chat is another message.
    val listState = remember(state.chatId) { androidx.compose.foundation.lazy.LazyListState() }
    // The messages of each chat as they were last on screen, for a chat that is leaving.
    val chatSnapshots = remember { HashMap<String, List<ChatMessage>>() }
    var showHistory by remember { mutableStateOf(false) }
    if (state.failure == AssistantFailure.LIMIT) {
        UsageLimitSheet(onDismiss = viewModel::dismissFailure)
    }
    if (showHistory) {
        HistorySheet(
            chats = state.history,
            currentId = state.chatId,
            onOpen = {
                showHistory = false
                viewModel.openChat(it)
            },
            onDelete = viewModel::deleteChat,
            onRename = viewModel::renameChat,
            onDismiss = { showHistory = false },
        )
    }

    val addUri = { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                when (val result = AttachmentReader.read(context, uri)) {
                    is AttachmentReader.Result.Ok -> {
                        attachmentNote = null
                        viewModel.addAttachment(result.attachment)
                    }
                    is AttachmentReader.Result.Problem -> attachmentNote = when (result.problem) {
                        AttachmentProblem.TOO_LARGE -> R.string.assistant_attach_too_large
                        AttachmentProblem.UNSUPPORTED -> R.string.assistant_attach_unsupported
                        AttachmentProblem.UNREADABLE -> R.string.assistant_attach_unreadable
                    }
                }
            }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { addUri(it) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { addUri(it) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) addUri(uri)
    }

    val send = { text: String ->
        if ((text.isNotBlank() || state.staged.isNotEmpty()) && !state.busy) {
            haptics.perform(HapticEvent.MESSAGE_SENT)
            viewModel.send(text, language)
            input = ""
        }
    }

    // Only questions, answers and drafts are shown; tool results stay behind the scenes.
    val shown = state.messages.filter { message ->
        when (message.role) {
            ChatRole.USER -> true
            ChatRole.ASSISTANT -> message.text.isNotBlank() || message.toolCalls.isNotEmpty() || message.reasoning.isNotBlank()
            ChatRole.TOOL -> false
        }
    }

    // The rows of the list, counted here rather than read from the list's layout info: that info is only
    // updated by the next layout pass, so right after a change it still has the old number of rows.
    val lastQuestion = shown.lastOrNull { it.role == ChatRole.USER }?.id
    val last = shown.lastOrNull()
    // An opened chat plays its latest answer in word by word; a new question or a new chat ends that (see openChat).
    val replayId = if (state.opened) shown.lastOrNull { it.role == ChatRole.ASSISTANT && it.text.isNotBlank() }?.id else null
    val itemCount = shown.size +
        (if (state.runningTools.isNotEmpty()) 1 else 0) +
        (if (state.waiting) 1 else 0) +
        (if (state.failure != null) 1 else 0)
    val growth = listOf(last?.text?.length, last?.reasoning?.length, last?.toolCalls?.size, state.streamingId, state.drafts, state.cards.size)

    // Follow the conversation while its end is in view. Once the student drags the list up to read, it
    // stays put until they come back down to the end or ask something new.
    val follow = remember(listState) { FollowState() }
    val animations = motionAnimationsEnabled()
    LaunchedEffect(listState) {
        launch {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    follow.touched = true
                    follow.enabled = false
                }
            }
        }
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && follow.touched) {
                follow.touched = false
                follow.enabled = !listState.canScrollForward
            }
        }
    }
    LaunchedEffect(listState, itemCount, lastQuestion, growth) {
        if (itemCount == 0) return@LaunchedEffect
        val firstLayout = follow.count == 0
        val newRow = itemCount > follow.count
        if (lastQuestion != follow.question) follow.enabled = true
        follow.count = itemCount
        follow.question = lastQuestion
        if (follow.enabled) listState.revealEnd(itemCount - 1, animated = newRow && !firstLayout && animations)
    }
    // The keyboard (or anything else) shrinking the list keeps the end in view too.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }.collect {
            val count = listState.layoutInfo.totalItemsCount
            if (follow.enabled && count > 0) listState.revealEnd(count - 1, animated = false)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DaybreakMark(size = 24, plain = true)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.assistant_title))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticEvent.BACK)
                            onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Both icons grow their own space as they appear, so the other one slides over instead of jumping.
                    AnimatedVisibility(visible = state.history.isNotEmpty(), enter = barIconEnter, exit = barIconExit) {
                        IconButton(
                            onClick = {
                                haptics.perform(HapticEvent.SHEET_OPEN)
                                showHistory = true
                            },
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.assistant_history))
                        }
                    }
                    AnimatedVisibility(visible = state.messages.isNotEmpty(), enter = barIconEnter, exit = barIconExit) {
                        IconButton(
                            onClick = {
                                haptics.perform(HapticEvent.TAP)
                                viewModel.clear()
                            },
                        ) {
                            Icon(Icons.Outlined.EditNote, contentDescription = stringResource(R.string.assistant_new_chat))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            // The welcome page and each chat move as a whole: switching chats, or starting a new one, makes the chat
            // that goes away slide and fade out too. A leaving chat shows the messages it had when it was on screen.
            val screenKey = when {
                !viewModel.available -> "unavailable"
                shown.isEmpty() && !state.busy -> "welcome"
                else -> "chat-${state.chatId}"
            }
            if (screenKey.startsWith("chat-")) chatSnapshots[screenKey] = shown
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = screenKey,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 90)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 12 })
                            .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(200, easing = LinearOutSlowInEasing)) { -it / 12 })
                            .using(SizeTransform(clip = false))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "chatScreen",
                ) { key ->
                if (key == "unavailable") {
                    UnavailableState()
                } else if (key == "welcome") {
                    WelcomeState(onSuggestion = send)
                } else {
                    val messages = chatSnapshots[key].orEmpty()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(messages, key = { "${state.chatId}-${it.id}" }) { message ->
                            Column(modifier = Modifier.animateItem(fadeInSpec = rowFadeIn, fadeOutSpec = null), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (message.role == ChatRole.ASSISTANT && message.reasoning.isNotBlank()) {
                                    ThinkingSection(
                                        reasoning = message.reasoning,
                                        thinking = message.id == state.streamingId && message.text.isBlank() && message.toolCalls.isEmpty(),
                                    )
                                }
                                // The quiet record of the tools, without the ones whose card shows what they did.
                                val recorded = message.toolCalls.filterNot { it.name in CARD_TOOLS && state.cards[it.id] != null }
                                if (message.role == ChatRole.ASSISTANT && recorded.isNotEmpty()) {
                                    ToolActivity(names = recorded.map { it.name }, running = false)
                                }
                                if (message.text.isNotBlank() || message.attachments.isNotEmpty()) {
                                    MessageBubble(
                                        message = message,
                                        streaming = message.id == state.streamingId,
                                        replay = message.id == replayId,
                                    )
                                }
                                message.toolCalls.forEach { call ->
                                    state.cards[call.id]?.let { card -> AssistantCardView(card, onOpenLesson) }
                                    state.drafts[call.id]?.let { draft ->
                                        DraftCard(
                                            state = draft,
                                            onSend = {
                                                haptics.perform(HapticEvent.MESSAGE_SENT)
                                                viewModel.decideDraft(true)
                                            },
                                            onCancel = {
                                                haptics.perform(HapticEvent.TAP)
                                                viewModel.decideDraft(false)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (state.runningTools.isNotEmpty()) {
                            item(key = "tools") { ToolActivity(names = state.runningTools, running = true, modifier = statusRow()) }
                        }
                        if (state.waiting) {
                            item(key = "typing") { TypingIndicator(statusRow()) }
                        }
                        // A used-up day is explained in a sheet (see UsageLimitSheet), not in the chat.
                        state.failure?.takeIf { it != AssistantFailure.LIMIT }?.let { failure ->
                            item(key = "failure") {
                                FailureCard(failure = failure, onRetry = { viewModel.retry(language) }, modifier = statusRow())
                            }
                        }
                    }
                }
                }
            }
            if (viewModel.available) {
                AnimatedVisibility(visible = attachmentNote != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Text(
                        text = attachmentNote?.let { stringResource(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                InputBar(
                    value = input,
                    onValueChange = { input = it.take(MAX_INPUT) },
                    staged = state.staged,
                    canAttach = state.staged.size < AssistantViewModel.MAX_ATTACHMENTS,
                    busy = state.busy,
                    onSend = { send(input) },
                    onStop = {
                        haptics.perform(HapticEvent.TAP)
                        viewModel.stop()
                    },
                    onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPickFile = { pickFile.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/csv")) },
                    onTakePhoto = {
                        // The picture goes to the app's own cache folder, through the file provider, and is read back on return.
                        val file = File(context.cacheDir, "camera/photo-${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraUri = uri
                        takePhoto.launch(uri)
                    },
                    onRemove = viewModel::removeAttachment,
                    models = state.models,
                    selectedModel = state.selectedModel,
                    selectedEffort = state.selectedEffort,
                    onSelectModel = viewModel::selectModel,
                    onSelectEffort = viewModel::selectEffort,
                )
            }
        }
    }
}

private const val MAX_INPUT = 2000

/** How many saved chats the history sheet shows at first, and how many more each "show more" adds. */
private const val HISTORY_PAGE = 10

/** The delay between two rows that "Show more" grows in, so they open one after another. */
private const val GROW_STAGGER_MS = 45


/** Tools that show a card; the card says what they did, so they get no "looked it up" line. */
private val CARD_TOOLS = setOf("show_timetable", "show_exams", "show_absences")

/** A top-bar icon appearing: its space opens with a spring while it fades and grows in. */
private val barIconEnter =
    expandHorizontally(spring(dampingRatio = 0.85f, stiffness = 420f), expandFrom = Alignment.Start) +
        fadeIn(tween(220, delayMillis = 60)) +
        scaleIn(spring(dampingRatio = 0.6f, stiffness = 420f), initialScale = 0.5f)

private val barIconExit =
    shrinkHorizontally(spring(dampingRatio = 1f, stiffness = 500f), shrinkTowards = Alignment.Start) +
        fadeOut(tween(120)) +
        scaleOut(tween(160), targetScale = 0.5f)

/** The gap between the rename field and the pill-shaped row around it, the same on every side. */
private val RENAME_GAP = 6.dp

/** How the history row and its buttons change between showing a chat and renaming it. */
private val renameSpring = spring<IntSize>(dampingRatio = 0.85f, stiffness = 420f, visibilityThreshold = IntSize.VisibilityThreshold)

/** How the chats below a deleted one move up: a soft spring, so they flow rather than snap. */
private val historyPlacement = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 340f, visibilityThreshold = IntOffset.VisibilityThreshold)

/**
 * Rows fade in but never fade out. A removed row that fades out is kept and drawn where it was until its
 * fade ends, and the row that replaces it (the answer after the typing dots, the error after them, the dots
 * after a retry) appears in exactly that place, so the two were drawn on top of each other. The list also
 * scrolls while that happens, and the fading row was then left behind on screen until the next relayout
 * (opening or closing the keyboard).
 */
private val rowFadeIn = tween<Float>(durationMillis = 180)

/** The typing dots, the running tools and the error: they come and go in place, so they do not slide either. */
private fun LazyItemScope.statusRow(): Modifier =
    Modifier.animateItem(fadeInSpec = rowFadeIn, placementSpec = null, fadeOutSpec = null)

/** What the chat list remembers about following the conversation; read only by effects, so not state. */
private class FollowState {
    var enabled = true
    var touched = false
    var count = 0
    var question: Long? = null
}

/**
 * Scrolls so the bottom of row [lastIndex] sits at the bottom of the list (or as far as the list goes).
 * The first scroll lays the list out again, so the sizes used for the second one are current.
 */
private suspend fun LazyListState.revealEnd(lastIndex: Int, animated: Boolean) {
    if (animated) animateScrollToItem(lastIndex) else scrollToItem(lastIndex)
    val info = layoutInfo
    val row = info.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    val overflow = row.offset + row.size - (info.viewportEndOffset - info.afterContentPadding)
    if (overflow > 0) {
        if (animated) animateScrollBy(overflow.toFloat()) else scrollBy(overflow.toFloat())
    }
}

/** The first page of an empty chat: the mark, a short hello, and the questions most students ask as rows. */
@Composable
private fun WelcomeState(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        Icons.Outlined.Today to stringResource(R.string.assistant_suggestion_tomorrow),
        Icons.Outlined.Event to stringResource(R.string.assistant_suggestion_exam),
        Icons.Outlined.MenuBook to stringResource(R.string.assistant_suggestion_study),
        Icons.Outlined.EventBusy to stringResource(R.string.assistant_suggestion_changes),
        Icons.Outlined.PersonOff to stringResource(R.string.assistant_suggestion_absences),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Reveal(index = 0) { DaybreakMark(size = 84) }
        Spacer(Modifier.height(20.dp))
        Reveal(index = 1) {
            Text(
                text = stringResource(R.string.assistant_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Reveal(index = 2) {
            Text(
                text = stringResource(R.string.assistant_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEachIndexed { index, (icon, text) ->
                Reveal(index = 3 + index) { SuggestionRow(icon = icon, text = text, onClick = { onSuggestion(text) }) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Reveal(index = 8) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.assistant_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** One suggested question: an icon in a tinted disc, the question, and an arrow. */
@Composable
private fun SuggestionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = colors.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun UnavailableState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.assistant_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fades and lifts its content in, one after the other by [index]. */
@Composable
internal fun Reveal(index: Int, content: @Composable () -> Unit) {
    val animations = motionAnimationsEnabled()
    val progress = remember { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animations) return@LaunchedEffect
        delay(60L * index)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 24.dp.toPx()
            val scale = 0.94f + 0.06f * progress.value
            scaleX = scale
            scaleY = scale
        },
    ) { content() }
}

@Composable
private fun MessageBubble(message: ChatMessage, streaming: Boolean, replay: Boolean = false, modifier: Modifier = Modifier) {
    val user = message.role == ChatRole.USER
    val colors = MaterialTheme.colorScheme
    // While the answer streams, only the part released so far is on screen (see rememberRevealedLength).
    val revealed = rememberRevealedLength(message.text.length, streaming = streaming && !user)
    // An opened chat's latest answer plays in word by word, each word fading in quickly.
    val playing = replay && !streaming && !user
    val words = rememberWordRevealedLength(message.text, replay = playing)
    val shownText = when {
        playing -> message.text.take(words)
        streaming && !user -> message.text.take(revealed)
        else -> message.text
    }
    val fresh = rememberFreshText(
        shownText.length,
        active = (streaming || playing) && !user,
        fadeNanos = if (playing) WORD_FADE_NANOS else FADE_NANOS,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
            if (message.attachments.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    message.attachments.forEach { AttachmentThumb(it) }
                }
            }
            if (message.text.isNotBlank()) {
                if (user) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
                        color = colors.primary,
                        contentColor = colors.onPrimary,
                        modifier = Modifier.widthIn(max = 320.dp),
                    ) {
                        Text(text = message.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                } else {
                    // The answer sits directly in the chat, without a card behind it.
                    AnswerContent(
                        text = shownText,
                        // Streaming and the replay of an opened chat both fade the newest text in.
                        streaming = (streaming || playing) && !user,
                        fresh = fresh,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * An assistant answer, with or without formulas. Formulas are drawn by KaTeX as soon as their delimiters are
 * there, also while the answer streams; the plain text holds the place until the formula page has drawn.
 */
@Composable
private fun AnswerContent(text: String, streaming: Boolean, fresh: FreshText?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val blocks = rememberAnswerBlocks(text)
    val hasMath = remember(blocks) { AnswerMarkdown.hasMath(blocks) }
    if (hasMath) {
        val palette = MathPalette(
            text = colors.onSurface,
            accent = colors.primary,
            muted = colors.onSurfaceVariant,
            codeBackground = colors.surfaceContainerHighest,
            divider = colors.outlineVariant,
        )
        MathText(
            blocks = blocks,
            palette = palette,
            modifier = modifier,
            fresh = if (streaming) fresh else null,
            placeholder = { AnswerText(blocks, streaming = false, modifier = Modifier.fillMaxWidth()) },
        )
    } else {
        AnswerText(blocks, streaming = streaming, modifier = modifier, fresh = if (streaming) fresh else null)
    }
}

/** A picture as a small rounded preview, a file as an icon with its name. */
@Composable
private fun AttachmentThumb(attachment: ChatAttachment, onRemove: (() -> Unit)? = null) {
    val bitmap = remember(attachment) { AttachmentReader.thumbnail(attachment)?.asImageBitmap() }
    Box {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    modifier = Modifier
                        .height(64.dp)
                        .widthIn(max = 180.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (onRemove != null) {
            Surface(
                onClick = onRemove,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(20.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.assistant_attach_remove), modifier = Modifier.padding(3.dp))
            }
        }
    }
}

/**
 * One typing dot's hop: rest, up, back down, then rest until the loop starts again. Keyframes end on the
 * animation's target value (1, the top) unless the end is given, so without the last line every dot crept
 * back up after its hop and snapped down when the loop restarted.
 */
internal fun typingDotKeyframes(dot: Int) = keyframes {
    durationMillis = TYPING_CYCLE_MS
    0f at 0
    0f at (dot * 160)
    1f at (dot * 160 + 240)
    0f at (dot * 160 + 480)
    0f at TYPING_CYCLE_MS
}

internal const val TYPING_CYCLE_MS = 1200

/**
 * The generating indicator: one thin ring in the primary colour that breathes in and out while the answer is being
 * made. No card and no mark around it; it sits where the answer will appear.
 */
@Composable
internal fun TypingIndicator(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val animations = motionAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "typingRing")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animations) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "typingRingBreathe",
    )
    Row(modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val scale = 0.7f + 0.3f * breathe
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.35f + 0.65f * breathe
                }
                .border(width = 2.dp, color = colors.primary, shape = CircleShape),
        )
    }
}

/** "Looking at your exams…" while tools run, and a quiet record of them afterwards. */
@Composable
private fun ToolActivity(names: List<String>, running: Boolean, modifier: Modifier = Modifier) {
    val labels = names.distinct().map { toolLabel(it) }
    Row(modifier = modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (running) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = labels.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun toolLabel(name: String): String = when (name) {
    "get_lessons" -> stringResource(R.string.assistant_tool_lessons)
    "get_exams" -> stringResource(R.string.assistant_tool_exams)
    "get_lesson_notes" -> stringResource(R.string.assistant_tool_notes)
    "get_homework" -> stringResource(R.string.assistant_tool_homework)
    "get_absences" -> stringResource(R.string.assistant_tool_absences)
    "list_teachers" -> stringResource(R.string.assistant_tool_teachers)
    "draft_message" -> stringResource(R.string.assistant_tool_draft)
    "show_timetable" -> stringResource(R.string.assistant_tool_timetable)
    "show_exams" -> stringResource(R.string.assistant_tool_exams_card)
    "show_absences" -> stringResource(R.string.assistant_tool_absences_card)
    else -> name
}

/**
 * A message the assistant drafted. It is sent only by the "Send" button here, after the student read it;
 * the card says plainly that it goes to a real teacher.
 */
@Composable
private fun DraftCard(state: DraftState, onSend: () -> Unit, onCancel: () -> Unit) {
    val draft = state.draft
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MailOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.assistant_draft_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.assistant_draft_to, draft.recipientName), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.assistant_draft_subject, draft.subject), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(draft.body, style = MaterialTheme.typography.bodyMedium)
            AnimatedContent(targetState = state.status, label = "draftStatus") { status ->
                when (status) {
                    DraftState.Status.PENDING -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.assistant_draft_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.assistant_draft_cancel)) }
                            Button(onClick = onSend) { Text(stringResource(R.string.assistant_draft_send)) }
                        }
                    }
                    DraftState.Status.SENDING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.assistant_draft_sending), style = MaterialTheme.typography.labelLarge)
                    }
                    DraftState.Status.SENT -> StatusLine(stringResource(R.string.assistant_draft_sent), MaterialTheme.colorScheme.primary)
                    DraftState.Status.CANCELLED -> StatusLine(stringResource(R.string.assistant_draft_cancelled), MaterialTheme.colorScheme.outline)
                    DraftState.Status.FAILED -> StatusLine(stringResource(R.string.assistant_draft_failed), MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

@Composable
private fun FailureCard(failure: AssistantFailure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (failure) {
        AssistantFailure.OFFLINE -> stringResource(R.string.assistant_error_offline)
        AssistantFailure.BUSY -> stringResource(R.string.assistant_error_busy)
        AssistantFailure.UPSTREAM -> stringResource(R.string.assistant_error_upstream)
        AssistantFailure.UNAVAILABLE -> stringResource(R.string.assistant_error_unavailable)
        AssistantFailure.LIMIT -> stringResource(R.string.assistant_error_limit)
        AssistantFailure.REJECTED -> stringResource(R.string.assistant_error_rejected)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (failure != AssistantFailure.REJECTED) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.assistant_retry)) }
            }
        }
    }
}

/**
 * The prompt: one rounded card with the text on top and, underneath, the attach button on the left and the
 * model and effort choices and the send button on the right, as in the chat apps students know. The line
 * under it says the answers can be wrong, and it is always there.
 */
@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    staged: List<ChatAttachment>,
    canAttach: Boolean,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemove: (ChatAttachment) -> Unit,
    models: AssistantModels?,
    selectedModel: String?,
    selectedEffort: String?,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val currentModel = models?.models?.firstOrNull { it.id == selectedModel } ?: models?.models?.firstOrNull()
    val currentEffort = selectedEffort ?: models?.defaultEffort ?: models?.efforts?.firstOrNull()
    // The model's description opens under the prompt when its tag is tapped; a new model closes it.
    var describing by remember { mutableStateOf(false) }
    LaunchedEffect(currentModel?.id) { describing = false }
    val description = currentModel?.description.orEmpty()
    Surface(color = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        ) {
            AnimatedVisibility(visible = staged.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp, bottom = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    staged.forEach { attachment -> AttachmentThumb(attachment, onRemove = { onRemove(attachment) }) }
                }
            }
            AnimatedVisibility(
                visible = describing && description.isNotBlank(),
                enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200, easing = LinearOutSlowInEasing)) + fadeOut(tween(140)),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = colors.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AttachMenu(
                            enabled = canAttach && !busy,
                            onPhoto = onPickImage,
                            onCamera = onTakePhoto,
                            onFile = onPickFile,
                        )
                        Spacer(Modifier.weight(1f))
                        if (models != null && models.showSwitcher && currentModel != null) {
                            PickerChip(
                                current = currentModel.label,
                                options = models.models.map { PickerOption(it.id, it.label, it.tag, it.tagAlarm) },
                                selectedId = currentModel.id,
                                onSelect = onSelectModel,
                                tag = currentModel.tag,
                                tagAlarm = currentModel.tagAlarm,
                                onTagLongClick = if (description.isNotBlank()) ({ describing = !describing }) else null,
                                onChipLongClick = if (description.isNotBlank()) ({ describing = !describing }) else null,
                                onInfoClick = if (description.isNotBlank()) ({ describing = !describing }) else null,
                            )
                        }
                        if (models != null && models.showEffort && currentEffort != null) {
                            PickerChip(
                                current = effortLabel(currentEffort),
                                options = models.efforts.map { PickerOption(it, effortLabel(it)) },
                                selectedId = currentEffort,
                                onSelect = onSelectEffort,
                                icon = Icons.Outlined.Psychology,
                            )
                        }
                        Spacer(Modifier.size(6.dp))
                        AnimatedContent(
                            targetState = busy,
                            transitionSpec = {
                                (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f)).togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.6f))
                            },
                            label = "sendStop",
                        ) { isBusy ->
                            if (isBusy) {
                                FilledIconButton(
                                    onClick = onStop,
                                    modifier = Modifier.size(40.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = colors.secondaryContainer,
                                        contentColor = colors.onSecondaryContainer,
                                    ),
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.assistant_stop))
                                }
                            } else {
                                FilledIconButton(
                                    onClick = onSend,
                                    enabled = value.isNotBlank() || staged.isNotEmpty(),
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.assistant_send))
                                }
                            }
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.assistant_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = colors.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
            )
        }
    }
}


@Composable
private fun effortLabel(effort: String): String = when (effort) {
    "minimal" -> stringResource(R.string.assistant_effort_minimal)
    "low" -> stringResource(R.string.assistant_effort_low)
    "medium" -> stringResource(R.string.assistant_effort_medium)
    "high" -> stringResource(R.string.assistant_effort_high)
    "xhigh" -> stringResource(R.string.assistant_effort_xhigh)
    "max" -> stringResource(R.string.assistant_effort_max)
    else -> effort
}

/**
 * The saved chats, newest first. Tap opens one; the bin deletes it; the pencil turns that row itself into a
 * name field (no dialog over the sheet), with the old name selected so typing replaces it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    chats: List<com.bdersolutions.daybreak.data.assistant.ChatSummary>,
    currentId: Long?,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHaptics.current
    var renamingId by remember { mutableStateOf<Long?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    // Always fully open: a sheet that sizes itself to its content moved its top edge up in steps each time
    // "Show more" made the list taller. Opened at full height, it stays put and the list scrolls inside it.
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    // Choosing a chat slides the sheet away first, then opens the chat; removing the sheet at once cut the animation.
    val pick: (Long) -> Unit = { id ->
        sheetScope.launch {
            sheetState.hide()
            onOpen(id)
        }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
      // A fixed height (the list scrolls inside it), so growing the list never moves the sheet's edge.
      Column(modifier = Modifier.fillMaxHeight(0.92f)) {
        Text(
            text = stringResource(R.string.assistant_history),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (chats.isEmpty()) {
            Text(
                stringResource(R.string.assistant_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        var shown by rememberSaveable { mutableStateOf(HISTORY_PAGE) }
        // The rows that "Show more" just added (id -> place in that batch). They grow in one after another; the
        // rows already on the list never animate. The transition states live here, not in the rows, so a row
        // that scrolls out of view and back does not grow again.
        val growing = remember { HashMap<Long, Int>() }
        val entered = remember { HashMap<Long, MutableTransitionState<Boolean>>() }
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(chats.take(shown), key = { it.id }) { chat ->
                val state = entered.getOrPut(chat.id) { MutableTransitionState(growing[chat.id] == null) }
                state.targetState = true
                val delay = (growing[chat.id] ?: 0) * GROW_STAGGER_MS
                AnimatedVisibility(
                    visibleState = state,
                    enter = expandVertically(
                        animationSpec = tween(380, delayMillis = delay, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(tween(240, delayMillis = delay + 60, easing = FastOutSlowInEasing)),
                    exit = shrinkVertically(tween(220, easing = LinearOutSlowInEasing)) + fadeOut(tween(160)),
                    // No placement spring: the rows below must follow the growing row frame by frame, or they lag and overlap it.
                    // Rows below a deleted one glide up with a spring. While a row is still growing in, its placement is
                    // not animated: the rows below must follow its height frame by frame or they overlap it.
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = if (state.isIdle) historyPlacement else null,
                    ),
                ) {
                    // The bin turns the row into a confirmation in place: the row gives way to the question, and the
                    // answer deletes the chat. Nothing is deleted from a single tap.
                    AnimatedContent(
                        targetState = deletingId == chat.id,
                        transitionSpec = {
                            (fadeIn(tween(200, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 420f), initialScale = 0.96f))
                                .togetherWith(fadeOut(tween(90)))
                                .using(SizeTransform(clip = false) { _, _ -> renameSpring })
                        },
                        label = "historyDelete",
                    ) { confirming ->
                        if (confirming) {
                            ConfirmDeleteRow(
                                title = chat.title,
                                onCancel = {
                                    haptics.perform(HapticEvent.TAP)
                                    deletingId = null
                                },
                                onConfirm = {
                                    haptics.perform(HapticEvent.MESSAGE_SENT)
                                    deletingId = null
                                    onDelete(chat.id)
                                },
                            )
                        } else {
                            HistoryRow(
                                chat = chat,
                                current = chat.id == currentId,
                                editing = renamingId == chat.id,
                                onOpen = { pick(chat.id) },
                                onStartRename = { renamingId = chat.id },
                                onDelete = {
                                    haptics.perform(HapticEvent.TAP)
                                    deletingId = chat.id
                                },
                                onSave = { name ->
                                    haptics.perform(HapticEvent.TAP)
                                    onRename(chat.id, name)
                                    renamingId = null
                                },
                                onCancel = { renamingId = null },
                            )
                        }
                    }
                }
            }
            item(key = "showMore") {
                val remaining = chats.size - shown
                // The button is always in the list, so it can shrink away when the last chats are shown.
                AnimatedVisibility(
                    visible = remaining > 0,
                    enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(260, delayMillis = 120)),
                    exit = shrinkVertically(tween(260, easing = LinearOutSlowInEasing)) + fadeOut(tween(120)),
                ) {
                    TextButton(
                        onClick = {
                            haptics.perform(HapticEvent.TAP)
                            chats.drop(shown).take(HISTORY_PAGE).forEachIndexed { index, chat -> growing[chat.id] = index }
                            shown += HISTORY_PAGE
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        AnimatedContent(
                            targetState = minOf(HISTORY_PAGE, remaining),
                            transitionSpec = {
                                (fadeIn(tween(200)) + slideInVertically { it / 2 })
                                    .togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 2 })
                            },
                            label = "showMoreCount",
                        ) { count ->
                            Text(stringResource(R.string.assistant_history_more, count))
                        }
                    }
                }
            }
        }
      }
    }
}

/**
 * One saved chat. Renaming happens in place: the name and date give way to a pill-shaped field (the row
 * eases to its new height while the field fades and grows in), and the pencil and bin turn into cancel and
 * save with a small pop. Cancel or save turns it back the same way.
 */
@Composable
private fun HistoryRow(
    chat: com.bdersolutions.daybreak.data.assistant.ChatSummary,
    current: Boolean,
    editing: Boolean,
    onOpen: () -> Unit,
    onStartRename: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        when {
            editing -> colors.surfaceContainerHigh
            current -> colors.secondaryContainer
            else -> Color.Transparent
        },
        tween(220),
        label = "historyRow",
    )
    // The field's text lives here, so it survives the row changing shape.
    var field by remember(editing) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(chat.title, androidx.compose.ui.text.TextRange(0, chat.title.length)))
    }
    val canSave = field.text.isNotBlank()
    // A pill around a pill: with the same 6 dp gap on every side of the field (the save button's 40 dp
    // circle sits 4 dp inside its 48 dp touch area, hence 2 dp at the end), the outer corners run
    // parallel to the field's. The pill shape follows the row's height while it animates.
    val start by animateDpAsState(if (editing) RENAME_GAP else 16.dp, spring(dampingRatio = 0.85f, stiffness = 420f), label = "rowStart")
    val end by animateDpAsState(if (editing) RENAME_GAP - 4.dp else 4.dp, spring(dampingRatio = 0.85f, stiffness = 420f), label = "rowEnd")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = !editing, onClick = onOpen)
            .padding(start = start, end = end, top = RENAME_GAP, bottom = RENAME_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = editing,
            transitionSpec = {
                (fadeIn(tween(200, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 420f), initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(90)))
                    .using(SizeTransform(clip = false) { _, _ -> renameSpring })
            },
            contentAlignment = Alignment.CenterStart,
            label = "historyName",
            modifier = Modifier.weight(1f),
        ) { isEditing ->
            if (isEditing) {
                RenameField(value = field, onValueChange = { field = it }, onDone = { if (canSave) onSave(field.text) })
            } else {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(chat.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        android.text.format.DateUtils.getRelativeTimeSpanString(chat.updatedAt).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedContent(
            targetState = editing,
            transitionSpec = {
                (fadeIn(tween(160, delayMillis = 40)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.4f))
                    .togetherWith(fadeOut(tween(90)) + scaleOut(tween(120), targetScale = 0.4f))
                    .using(SizeTransform(clip = false) { _, _ -> renameSpring })
            },
            label = "historyButtons",
        ) { isEditing ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.assistant_rename_cancel))
                    }
                    FilledIconButton(onClick = { onSave(field.text) }, enabled = canSave) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.assistant_rename_save))
                    }
                } else {
                    IconButton(onClick = onStartRename) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.assistant_rename))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.assistant_delete_chat))
                    }
                }
            }
        }
    }
}

/**
 * The day's usage is used up. The same kind of bottom sheet as the supporter popup: one icon, a short
 * explanation, and one button to close it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageLimitSheet(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Closing slides the sheet down first; the state only changes once it is gone, so the exit animates.
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(colors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.assistant_limit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.assistant_limit_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.assistant_limit_ok))
            }
        }
    }
}

/** The question that replaces a chat's row while it is being deleted: cancel, or delete for good. */
@Composable
private fun ConfirmDeleteRow(title: String, onCancel: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(CircleShape)
            .background(colors.errorContainer)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.assistant_delete_confirm),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onErrorContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onErrorContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.assistant_delete_cancel), tint = colors.onErrorContainer)
        }
        FilledIconButton(
            onClick = onConfirm,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.error, contentColor = colors.onError),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.assistant_delete_yes))
        }
    }
}

/** The name field while renaming: a pill, focused at once with the old name selected so typing replaces it. */
@Composable
private fun RenameField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onDone: () -> Unit,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = value,
        onValueChange = { onValueChange(if (it.text.length <= 60) it else it.copy(text = it.text.take(60))) },
        singleLine = true,
        shape = CircleShape,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        // The lowest surface, so the field stands out from the tinted row around it in light and dark.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}



/**
 * The model's reasoning above its answer. While it reasons, a pulsing "Denkt nach…" line shows the
 * newest thoughts; afterwards it collapses to "Gedanken anzeigen", which opens the whole text.
 */
@Composable
private fun ThinkingSection(reasoning: String, thinking: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "thinking")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "thinkingPulse",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !thinking) { open = !open }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Psychology,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { alpha = if (thinking) pulse else 1f },
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(
                    when {
                        thinking -> R.string.assistant_thinking
                        open -> R.string.assistant_thoughts_hide
                        else -> R.string.assistant_thoughts_show
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!thinking) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = if (open) 180f else 0f },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // While thinking: the last lines, so the reasoning can be watched without taking over the chat.
        AnimatedVisibility(visible = thinking, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                text = reasoning.takeLast(280).trimStart(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        AnimatedVisibility(visible = !thinking && open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = reasoning.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}
