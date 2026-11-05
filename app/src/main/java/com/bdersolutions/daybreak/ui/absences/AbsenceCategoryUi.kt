package com.bdersolutions.daybreak.ui.absences

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.ui.components.CollapseOnRemove
import com.bdersolutions.daybreak.ui.components.ReorderState
import com.bdersolutions.daybreak.ui.components.rememberMorphCorner
import com.bdersolutions.daybreak.ui.components.rememberPressScale
import com.bdersolutions.daybreak.ui.components.rememberReorderState
import com.bdersolutions.daybreak.ui.components.rememberStaggeredReveal
import com.bdersolutions.daybreak.ui.components.rememberSubjectTone
import com.bdersolutions.daybreak.ui.components.reorderHandle
import com.bdersolutions.daybreak.untis.UntisAbsence
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Which category sheet is open on the Absences screen. */
internal sealed interface CategorySheetRequest {
    data object New : CategorySheetRequest
    data object Manage : CategorySheetRequest
    data class Edit(val id: String) : CategorySheetRequest
}

/**
 * The cards at the top of the Absences screen: "Total" first, then one card per category, with
 * page dots below. The page state is owned by the caller, because the list under the cards shows
 * the absences of the selected category.
 */
@Composable
internal fun AbsenceSummaryPager(
    absences: List<UntisAbsence>,
    categories: CategoriesUiState,
    pagerState: PagerState,
) {
    val pageCount = 1 + categories.categories.size
    val scope = rememberCoroutineScope()

    // A new category becomes the visible page, so it is clear where it went.
    var knownCount by remember { mutableIntStateOf(pageCount) }
    LaunchedEffect(pageCount) {
        if (pageCount > knownCount) pagerState.animateScrollToPage(pageCount - 1)
        knownCount = pageCount
    }

    // All cards share the height of the tallest one, so the list below does not jump while
    // swiping between cards of different content.
    val density = LocalDensity.current
    var cardHeightPx by remember(density.density, density.fontScale) { mutableIntStateOf(0) }
    val cardModifier = Modifier
        .heightIn(min = with(density) { cardHeightPx.toDp() })
        .onSizeChanged { size -> if (size.height > cardHeightPx) cardHeightPx = size.height }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
            ),
            key = { page -> if (page == 0) "total" else categories.categories.getOrNull(page - 1)?.id ?: page },
        ) { page ->
            if (page == 0) {
                AbsenceSummary(absences = absences, modifier = cardModifier)
            } else {
                val category = categories.categories.getOrNull(page - 1)
                if (category != null) {
                    CategoryCard(
                        category = category,
                        usage = categories.usage[category.id],
                        loading = categories.loading,
                        missingSubject = category.id in categories.missingSubject,
                        partial = categories.failedWeeks > 0,
                        modifier = cardModifier,
                    )
                }
            }
        }

        if (pageCount > 1) {
            Spacer(Modifier.height(12.dp))
            PageDots(
                count = pageCount,
                current = pagerState.currentPage,
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            )
        }
    }
}

/** The pages of the category sheet. Switching between them animates inside one sheet. */
internal sealed interface SheetPage {
    data object Manage : SheetPage
    data class Form(val id: String?) : SheetPage
}

/**
 * One bottom sheet for everything about categories. The list of categories and the form to create
 * or edit one are pages inside it, so going from one to the other slides within the sheet instead
 * of closing it and opening another.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AbsenceCategorySheets(
    request: CategorySheetRequest?,
    onRequest: (CategorySheetRequest?) -> Unit,
    categories: CategoriesUiState,
    onSave: (id: String?, name: String, subject: String, maxLessons: Int, maxPercent: Int) -> Unit,
    onPrepareDelete: suspend (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: (ids: List<String>) -> Unit,
) {
    if (request == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val fromManage = request == CategorySheetRequest.Manage
    var page by remember(request) {
        mutableStateOf<SheetPage>(
            when (request) {
                CategorySheetRequest.Manage -> SheetPage.Manage
                CategorySheetRequest.New -> SheetPage.Form(null)
                is CategorySheetRequest.Edit -> SheetPage.Form(request.id)
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = { onRequest(null) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState is SheetPage.Form
                (
                    slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { width ->
                        if (forward) width / 4 else -width / 4
                    } + fadeIn(motion.defaultEffectsSpec<Float>())
                    ).togetherWith(
                        slideOutHorizontally(motion.fastSpatialSpec<IntOffset>()) { width ->
                            if (forward) -width / 4 else width / 4
                        } + fadeOut(motion.fastEffectsSpec<Float>()),
                    ).using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec<IntSize>() })
            },
            label = "categorySheetPage",
        ) { current ->
            when (current) {
                SheetPage.Manage -> ManageContent(
                    categories = categories.categories,
                    onAdd = { page = SheetPage.Form(null) },
                    onEdit = { category -> page = SheetPage.Form(category.id) },
                    onPrepareDelete = onPrepareDelete,
                    onDelete = onDelete,
                    onReorder = onReorder,
                )

                is SheetPage.Form -> {
                    val editing = current.id?.let { id -> categories.categories.firstOrNull { it.id == id } }
                    // The category was deleted while its form was open: go back to the list.
                    LaunchedEffect(current, editing) {
                        if (current.id != null && editing == null) {
                            if (fromManage) page = SheetPage.Manage else onRequest(null)
                        }
                    }
                    CategoryForm(
                        initial = editing,
                        subjects = categories.subjects,
                        takenSubjects = categories.categories
                            .filter { it.id != editing?.id }
                            .map { it.subject.lowercase() }
                            .toSet(),
                        showBack = fromManage,
                        onBack = { page = SheetPage.Manage },
                        onSubmit = { name, subject, max, percent ->
                            if (fromManage) {
                                onSave(current.id, name, subject, max, percent)
                                page = SheetPage.Manage
                            } else {
                                // Slide the sheet away first, then save, so closing is animated.
                                scope.launch {
                                    sheetState.hide()
                                    onSave(current.id, name, subject, max, percent)
                                    onRequest(null)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Launcher-style page dots: the current page is the accent colour and slightly larger. */
@Composable
private fun PageDots(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
) {
    val label = stringResource(R.string.absences_page_label, current + 1, count)
    Row(
        modifier = Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "pageDotColor",
            )
            val size by animateDpAsState(
                targetValue = if (selected) 9.dp else 7.dp,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "pageDotSize",
            )
            // The touch target is larger than the dot.
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(color, CircleShape),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryCard(
    category: AbsenceCategory,
    usage: CategoryUsage?,
    loading: Boolean,
    missingSubject: Boolean,
    partial: Boolean,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val percentMode = category.isPercent
    val used = usage?.total ?: 0
    val usedPercent = usage?.percent ?: 0.0
    // Both modes reduce to "how much of the limit is used", in lessons or in percent.
    val max = if (percentMode) category.maxPercent else category.maxLessons
    val amount = if (percentMode) usedPercent else used.toDouble()
    val atLimit = usage != null && amount >= max
    val accent = when {
        atLimit -> MaterialTheme.colorScheme.error
        usage != null && amount * 5 >= max * 4 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val progress by animateFloatAsState(
        targetValue = if (usage == null) 0f else (amount / max).toFloat().coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "categoryProgress",
    )
    val status = when {
        missingSubject -> stringResource(R.string.category_missing_subject)
        usage == null -> stringResource(R.string.category_loading)
        percentMode && amount - max >= 0.05 ->
            stringResource(R.string.category_percent_over, formatPercent(amount - max, locale))
        percentMode && amount >= max -> stringResource(R.string.category_limit_reached)
        percentMode -> stringResource(R.string.category_percent_remaining, formatPercent(max - amount, locale))
        used > max -> stringResource(R.string.category_over, used - max)
        used == max -> stringResource(R.string.category_limit_reached)
        else -> stringResource(R.string.category_remaining, max - used)
    }
    val dash = "–"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = when {
                                usage == null -> dash
                                percentMode -> formatPercent(usedPercent, locale) + " %"
                                else -> used.toString()
                            },
                            style = MaterialTheme.typography.displaySmallEmphasized,
                            color = if (atLimit) accent else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (percentMode) " / $max %" else " / $max",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
                // A name that is just the subject needs no second label.
                if (!category.name.equals(category.subject, ignoreCase = true)) {
                    Spacer(Modifier.width(12.dp))
                    SubjectBadge(subject = category.subject, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            if (loading && usage == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.25f),
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.25f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = if (atLimit) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (percentMode && usage != null && !missingSubject) {
                Text(
                    text = stringResource(R.string.category_percent_basis, used, usage.held),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.absences_excused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = usage?.excused?.toString() ?: dash,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.absences_unexcused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = usage?.unexcused?.toString() ?: dash,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (partial && usage != null && !missingSubject) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.category_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "7.5" or "10": one decimal at most, in the app's language. */
internal fun formatPercent(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(value)

/** The subject in its timetable colour, so it reads the same here as in the timetable. */
@Composable
private fun SubjectBadge(subject: String, modifier: Modifier = Modifier) {
    val tone = rememberSubjectTone(subject)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = tone.accent.copy(alpha = 0.2f),
        contentColor = tone.accent,
        modifier = modifier,
    ) {
        Text(
            text = subject,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * A subject as a tile in its timetable colour. Motion follows
 * the expressive spring scheme: the tile reveals with a short stagger, squashes while pressed, its
 * corners morph from a rounded square to a pill when selected, the colour flows to the full subject
 * colour, and a check badge pops in. The badge is an overlay, so selecting never reflows the grid.
 * A taken subject is dimmed and cannot be chosen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubjectTile(
    subject: String,
    index: Int,
    selected: Boolean,
    taken: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = rememberSubjectTone(subject)
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val corner by rememberMorphCorner(
        interactionSource = interaction,
        selected = selected,
        selectedRadius = 26.dp,
        unselectedRadius = 14.dp,
        pressedRadius = 8.dp,
    )
    val pressScale by rememberPressScale(interaction, pressedScale = 0.94f)
    val reveal by rememberStaggeredReveal(key = subject, index = index, staggerMillis = 18)

    val accent = tone.accent
    val readable = if (accent.luminance() > 0.3f) Color(0xDE000000) else Color.White
    val container by animateColorAsState(
        targetValue = if (selected) accent else lerp(scheme.surfaceContainerHigh, accent, 0.22f),
        animationSpec = motion.fastEffectsSpec(),
        label = "tileContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) readable else scheme.onSurface,
        animationSpec = motion.fastEffectsSpec(),
        label = "tileContent",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                val shown = reveal
                alpha = shown.coerceIn(0f, 1f) * (if (taken) 0.38f else 1f)
                val scale = pressScale * (0.85f + 0.15f * shown)
                scaleX = scale
                scaleY = scale
            },
    ) {
        Surface(
            onClick = onClick,
            enabled = !taken,
            shape = RoundedCornerShape(corner),
            color = container,
            contentColor = content,
            interactionSource = interaction,
            modifier = Modifier.height(52.dp).widthIn(min = 72.dp),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp),
            enter = scaleIn(animationSpec = motion.fastSpatialSpec()) +
                fadeIn(animationSpec = motion.fastEffectsSpec()),
            exit = scaleOut(animationSpec = motion.fastSpatialSpec()) +
                fadeOut(animationSpec = motion.fastEffectsSpec()),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, scheme.surfaceContainerLow, CircleShape)
                    .background(scheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Create or edit a category: a name, one of the student's subjects, and the lesson limit. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryForm(
    initial: AbsenceCategory?,
    subjects: List<String>,
    takenSubjects: Set<String>,
    showBack: Boolean,
    onBack: () -> Unit,
    onSubmit: (name: String, subject: String, maxLessons: Int, maxPercent: Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val motion = MaterialTheme.motionScheme
    var saving by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var subject by rememberSaveable { mutableStateOf(initial?.subject.orEmpty()) }
    var maxText by rememberSaveable {
        mutableStateOf(initial?.maxLessons?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var percentMode by rememberSaveable { mutableStateOf(initial?.isPercent == true) }
    var percent by rememberSaveable {
        mutableIntStateOf(initial?.maxPercent?.takeIf { it > 0 }?.coerceIn(1, 50) ?: 10)
    }

    // A category whose subject left the timetable keeps it visible in the list.
    val options = remember(subjects, initial) {
        val own = initial?.subject?.takeIf { s -> subjects.none { it.equals(s, ignoreCase = true) } }
        listOfNotNull(own) + subjects
    }
    val maxLessons = maxText.toIntOrNull()
    val canSave = subject.isNotBlank() &&
        (percentMode || (maxLessons != null && maxLessons > 0))
    val anyTaken = options.any { it.lowercase() in takenSubjects }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
            Text(
                text = stringResource(
                    if (initial == null) R.string.category_new_title else R.string.category_edit_title,
                ),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        }
        Spacer(Modifier.height(20.dp))

        SheetSectionLabel(stringResource(R.string.category_subject))
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.lesson_colors_no_subjects),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEachIndexed { index, option ->
                    SubjectTile(
                        subject = option,
                        index = index,
                        selected = subject.equals(option, ignoreCase = true),
                        taken = option.lowercase() in takenSubjects,
                        onClick = { subject = option },
                    )
                }
            }
            if (anyTaken) {
                Text(
                    text = stringResource(R.string.category_subject_in_use),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SheetSectionLabel(
            stringResource(if (percentMode) R.string.category_max_percent else R.string.category_max),
        )
        val modeLabel = stringResource(R.string.category_limit_mode)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .semantics { contentDescription = modeLabel },
        ) {
            listOf(false, true).forEachIndexed { index, isPercent ->
                SegmentedButton(
                    selected = percentMode == isPercent,
                    onClick = {
                        if (percentMode != isPercent) {
                            percentMode = isPercent
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) {
                    Text(
                        stringResource(
                            if (isPercent) R.string.category_mode_percent else R.string.category_mode_lessons,
                        ),
                    )
                }
            }
        }
        // The lesson count field and the percent slider swap places inside one animated slot.
        AnimatedContent(
            targetState = percentMode,
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec<Float>())
                    .togetherWith(fadeOut(motion.fastEffectsSpec<Float>()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec<IntSize>() })
            },
            label = "categoryLimitInput",
        ) { isPercent ->
            if (isPercent) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text(
                        text = stringResource(R.string.category_percent_value, percent),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = percent.toFloat(),
                        onValueChange = { percent = it.roundToInt().coerceIn(1, 50) },
                        valueRange = 1f..50f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.category_percent_support),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { value ->
                        if (value.length <= 3 && value.all { it.isDigit() }) maxText = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = CircleShape,
                    suffix = { Text(stringResource(R.string.category_max_suffix)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                    ),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SheetSectionLabel(
            text = stringResource(R.string.category_name),
            tag = stringResource(R.string.category_optional),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            // Shows what an empty name becomes: the chosen subject.
            placeholder = {
                Text(
                    text = subject.ifBlank { stringResource(R.string.category_name_hint) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            supportingText = { Text(stringResource(R.string.category_name_support)) },
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val max = if (percentMode) 0 else maxLessons ?: return@Button
                saving = true
                keyboard?.hide()
                onSubmit(name, subject, max, if (percentMode) percent else 0)
            },
            enabled = canSave && !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = CircleShape,
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

/** A section title above a field. [tag] adds a small pill at the end, e.g. "Optional". */
@Composable
private fun SheetSectionLabel(text: String, tag: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (tag != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * All categories as cards. They can be reordered by dragging the handle; the new order is saved
 * when the finger lifts. Deleting is confirmed inside the card, then the card collapses.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManageContent(
    categories: List<AbsenceCategory>,
    onAdd: () -> Unit,
    onEdit: (AbsenceCategory) -> Unit,
    onPrepareDelete: suspend (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: (ids: List<String>) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        items = categories,
        listState = listState,
        keyOf = { it.id },
        onCommit = { ordered -> onReorder(ordered.map { it.id }) },
    )
    var confirmingId by remember { mutableStateOf<String?>(null) }
    val removing = remember { mutableStateListOf<String>() }
    val prepareJobs = remember { mutableMapOf<String, Job>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.category_manage_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = categories.isEmpty(),
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec<Float>())
                    .togetherWith(fadeOut(motion.fastEffectsSpec<Float>()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec<IntSize>() })
            },
            label = "manageEmpty",
        ) { empty ->
            if (empty) {
                Text(
                    text = stringResource(R.string.category_manage_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                ) {
                    items(reorder.order, key = { it.id }) { category ->
                        val dragging = reorder.isDragging(category.id)
                        ManageRow(
                            category = category,
                            confirming = confirmingId == category.id,
                            reorder = reorder,
                            dragging = dragging,
                            removing = category.id in removing,
                            canReorder = reorder.order.size > 1,
                            onEdit = { onEdit(category) },
                            onAskDelete = { confirmingId = category.id },
                            onCancelDelete = { confirmingId = null },
                            onConfirmDelete = {
                                confirmingId = null
                                removing += category.id
                                // The pager leaves the card while its row collapses.
                                prepareJobs[category.id] = scope.launch { onPrepareDelete(category.id) }
                            },
                            onRemoved = {
                                scope.launch {
                                    prepareJobs.remove(category.id)?.join()
                                    onDelete(category.id)
                                    removing -= category.id
                                }
                            },
                            modifier = if (dragging) Modifier.zIndex(1f) else Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.absences_add_category))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManageRow(
    category: AbsenceCategory,
    confirming: Boolean,
    reorder: ReorderState<AbsenceCategory>,
    dragging: Boolean,
    removing: Boolean,
    canReorder: Boolean,
    onEdit: () -> Unit,
    onAskDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (confirming) {
            lerp(scheme.surfaceContainerHigh, scheme.errorContainer, 0.55f)
        } else {
            scheme.surfaceContainerHigh
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "manageRowContainer",
    )
    val elevation by animateDpAsState(
        targetValue = if (dragging) 10.dp else 0.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "manageRowElevation",
    )
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1.03f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "manageRowLift",
    )

    // The card collapses and fades first; only then is the category really deleted.
    CollapseOnRemove(removing = removing, onRemoved = onRemoved, modifier = modifier) {
        // The gap between cards belongs to the card, so it collapses with it.
        Box(modifier = Modifier.padding(bottom = 8.dp)) {
            Surface(
                onClick = if (confirming) onCancelDelete else onEdit,
                shape = MaterialTheme.shapes.large,
                color = container,
                shadowElevation = elevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = reorder.offsetOf(category.id)
                        scaleX = lift
                        scaleY = lift
                    },
            ) {
                AnimatedContent(
                    targetState = confirming,
                    transitionSpec = {
                        (
                            fadeIn(motion.defaultEffectsSpec()) +
                                scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)
                            )
                            .togetherWith(fadeOut(motion.fastEffectsSpec()))
                            .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
                    },
                    label = "manageRowContent",
                ) { isConfirming ->
                    if (isConfirming) {
                        DeleteConfirmation(
                            name = category.name,
                            onCancel = onCancelDelete,
                            onConfirm = onConfirmDelete,
                        )
                    } else {
                        CategoryRowContent(
                            category = category,
                            canReorder = canReorder,
                            dragHandle = Modifier.reorderHandle(reorder, category.id),
                            onEdit = onEdit,
                            onDelete = onAskDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRowContent(
    category: AbsenceCategory,
    canReorder: Boolean,
    dragHandle: Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val tone = rememberSubjectTone(category.subject)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (canReorder) 4.dp else 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (canReorder) 8.dp else 14.dp),
    ) {
        if (canReorder) {
            Box(
                modifier = dragHandle.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DragHandle,
                    contentDescription = stringResource(R.string.category_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(tone.accent.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category.subject.take(2).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = tone.accent,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    category.isPercent && category.name.equals(category.subject, ignoreCase = true) ->
                        stringResource(R.string.category_max_percent_summary, category.maxPercent)
                    category.isPercent ->
                        stringResource(R.string.category_row_percent_summary, category.subject, category.maxPercent)
                    category.name.equals(category.subject, ignoreCase = true) ->
                        stringResource(R.string.category_max_summary, category.maxLessons)
                    else ->
                        stringResource(R.string.category_row_summary, category.subject, category.maxLessons)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.category_edit_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.category_delete),
            )
        }
    }
}

/** The card's delete state: what is removed, that absences stay, and two equal pill buttons. */
@Composable
private fun DeleteConfirmation(
    name: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(scheme.error.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.category_delete_confirm_title, name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.category_delete_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onErrorContainer),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.error,
                    contentColor = scheme.onError,
                ),
            ) {
                Text(stringResource(R.string.category_delete))
            }
        }
    }
}
