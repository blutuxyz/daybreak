package com.bdersolutions.daybreak.ui.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown
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
    var showHistory by remember { mutableStateOf(false) }
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
    val itemCount = shown.size +
        (if (state.runningTools.isNotEmpty()) 1 else 0) +
        (if (state.waiting) 1 else 0) +
        (if (state.failure != null) 1 else 0)
    val lastQuestion = shown.lastOrNull { it.role == ChatRole.USER }?.id
    val last = shown.lastOrNull()
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
                    Column {
                        Text(stringResource(R.string.assistant_title))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ModelPicker(state = state, onSelect = viewModel::selectModel)
                            EffortPicker(state = state, onSelect = viewModel::selectEffort)
                        }
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
            Box(modifier = Modifier.weight(1f)) {
                if (!viewModel.available) {
                    UnavailableState()
                } else if (shown.isEmpty() && !state.busy) {
                    WelcomeState(onSuggestion = send)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(shown, key = { "${state.chatId}-${it.id}" }) { message ->
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
                                    MessageBubble(message = message, streaming = message.id == state.streamingId)
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
                        state.failure?.let { failure ->
                            item(key = "failure") {
                                FailureCard(failure = failure, onRetry = { viewModel.retry(language) }, modifier = statusRow())
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
                    onRemove = viewModel::removeAttachment,
                )
            }
        }
    }
}

private const val MAX_INPUT = 2000

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

/** The model picker under the title, when the server shows it; otherwise the plain subtitle. */
@Composable
private fun ModelPicker(state: AssistantUiState, onSelect: (String) -> Unit) {
    val models = state.models
    val subtitleStyle = MaterialTheme.typography.labelMedium
    if (models == null || !models.showSwitcher) {
        Text(stringResource(R.string.assistant_subtitle), style = subtitleStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var open by remember { mutableStateOf(false) }
    val current = models.models.firstOrNull { it.id == state.selectedModel } ?: models.models.firstOrNull()
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                Text(current?.label ?: "", style = subtitleStyle, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Outlined.ExpandMore, contentDescription = stringResource(R.string.assistant_model), modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            models.models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.label) },
                    onClick = {
                        open = false
                        onSelect(model.id)
                    },
                    trailingIcon = if (model.id == current?.id) {
                        { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeState(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        stringResource(R.string.assistant_suggestion_tomorrow),
        stringResource(R.string.assistant_suggestion_exam),
        stringResource(R.string.assistant_suggestion_study),
        stringResource(R.string.assistant_suggestion_changes),
        stringResource(R.string.assistant_suggestion_absences),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Reveal(index = 0) { SparkleOrb(size = 88) }
        Spacer(Modifier.height(20.dp))
        Reveal(index = 1) {
            Text(
                text = stringResource(R.string.assistant_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
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
            )
        }
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                Reveal(index = 3 + index) {
                    Surface(
                        onClick = { onSuggestion(suggestion) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Reveal(index = 9) {
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

/** A slowly turning gradient disc with a sparkle: the assistant's face. */
@Composable
internal fun SparkleOrb(size: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val animations = motionAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animations) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "orbAngle",
    )
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animations) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "orbBreathe",
    )
    val brush = remember(colors) {
        Brush.sweepGradient(listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary))
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = breathe
                scaleY = breathe
            }
            .clip(CircleShape)
            .drawBehind { rotate(angle) { drawCircle(brush, radius = this.size.maxDimension) } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size((size * 0.45f).dp))
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, streaming: Boolean, modifier: Modifier = Modifier) {
    val user = message.role == ChatRole.USER
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!user) {
            SparkleOrb(size = 28, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.size(8.dp))
        }
        Column(horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
            if (message.attachments.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    message.attachments.forEach { AttachmentThumb(it) }
                }
            }
            if (message.text.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (user) 20.dp else 6.dp,
                        bottomEnd = if (user) 6.dp else 20.dp,
                    ),
                    color = if (user) colors.primary else colors.surfaceContainerHigh,
                    contentColor = if (user) colors.onPrimary else colors.onSurface,
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    if (user) {
                        Text(text = message.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    } else {
                        val blocks = rememberAnswerBlocks(message.text)
                        val bubblePadding = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        if (!streaming && remember(blocks) { AnswerMarkdown.hasMath(blocks) }) {
                            // Formulas: drawn by KaTeX once the answer is complete; the plain answer holds the place until then.
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
                                modifier = bubblePadding,
                                placeholder = { AnswerText(blocks, streaming = false) },
                            )
                        } else {
                            AnswerText(blocks, streaming = streaming, modifier = bubblePadding)
                        }
                    }
                }
            }
        }
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

@Composable
internal fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SparkleOrb(size = 28)
        Spacer(Modifier.size(8.dp))
        Surface(shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { dot ->
                    val lift by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(typingDotKeyframes(dot)),
                        label = "dot$dot",
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer {
                                translationY = -lift * 5.dp.toPx()
                                alpha = 0.45f + 0.55f * lift
                            }
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    )
                }
            }
        }
    }
}

/** "Looking at your exams…" while tools run, and a quiet record of them afterwards. */
@Composable
private fun ToolActivity(names: List<String>, running: Boolean, modifier: Modifier = Modifier) {
    val labels = names.distinct().map { toolLabel(it) }
    Row(modifier = modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    onRemove: (ChatAttachment) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        ) {
            AnimatedVisibility(visible = staged.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(
                    modifier = Modifier
                        .padding(start = 48.dp, bottom = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    staged.forEach { attachment -> AttachmentThumb(attachment, onRemove = { onRemove(attachment) }) }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.padding(bottom = 4.dp)) {
                    IconButton(onClick = { menu = true }, enabled = canAttach && !busy) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.assistant_attach))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.assistant_attach_photo)) },
                            leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
                            onClick = {
                                menu = false
                                onPickImage()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.assistant_attach_file)) },
                            leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                            onClick = {
                                menu = false
                                onPickFile()
                            },
                        )
                    }
                }
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                    maxLines = 5,
                    shape = RoundedCornerShape(26.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.size(8.dp))
                AnimatedContent(
                    targetState = busy,
                    transitionSpec = {
                        (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f)).togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.6f))
                    },
                    label = "sendStop",
                    modifier = Modifier.padding(bottom = 4.dp),
                ) { isBusy ->
                    if (isBusy) {
                        FilledIconButton(
                            onClick = onStop,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.assistant_stop))
                        }
                    } else {
                        FilledIconButton(onClick = onSend, enabled = value.isNotBlank() || staged.isNotEmpty(), modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.assistant_send))
                        }
                    }
                }
            }
        }
    }
}


/** The reasoning-effort picker next to the model, when the server shows it. */
@Composable
private fun EffortPicker(state: AssistantUiState, onSelect: (String) -> Unit) {
    val models = state.models ?: return
    if (!models.showEffort) return
    var open by remember { mutableStateOf(false) }
    val current = state.selectedEffort ?: models.defaultEffort ?: models.efforts.firstOrNull() ?: return
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
            Icon(Icons.Outlined.Psychology, contentDescription = stringResource(R.string.assistant_effort), modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text(effortLabel(current), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            models.efforts.forEach { effort ->
                DropdownMenuItem(
                    text = { Text(effortLabel(effort)) },
                    onClick = {
                        open = false
                        onSelect(effort)
                    },
                    trailingIcon = if (effort == current) {
                        { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }
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
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
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
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(chats, key = { it.id }) { chat ->
                HistoryRow(
                    chat = chat,
                    current = chat.id == currentId,
                    editing = renamingId == chat.id,
                    onOpen = { onOpen(chat.id) },
                    onStartRename = { renamingId = chat.id },
                    onDelete = { onDelete(chat.id) },
                    onSave = { name ->
                        haptics.perform(HapticEvent.TAP)
                        onRename(chat.id, name)
                        renamingId = null
                    },
                    onCancel = { renamingId = null },
                    modifier = Modifier.animateItem(),
                )
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
    Column(modifier = Modifier.padding(start = 36.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !thinking) { open = !open }
                .padding(vertical = 4.dp, horizontal = 2.dp),
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
                modifier = Modifier.padding(start = 22.dp, top = 2.dp),
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
