package com.bdersolutions.daybreak.ui.absences

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.ui.components.SelectionRow
import com.bdersolutions.daybreak.untis.UntisAbsence
import kotlinx.coroutines.launch

/**
 * The cards at the top of the Absences screen: "Total" first, then one card per category, with
 * page dots and the buttons that add and manage categories.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AbsenceSummaryPager(
    absences: List<UntisAbsence>,
    categories: CategoriesUiState,
    onSave: (id: String?, name: String, subject: String, maxLessons: Int) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    val pageCount = 1 + categories.categories.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
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

    var showNew by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<AbsenceCategory?>(null) }

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

        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { showNew = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.absences_add_category))
            }
            if (categories.categories.isNotEmpty()) {
                TextButton(onClick = { showManage = true }) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.absences_manage))
                }
            }
        }
    }

    if (showNew) {
        CategorySheet(
            initial = null,
            subjects = categories.subjects,
            takenSubjects = categories.categories.map { it.subject.lowercase() }.toSet(),
            onSave = { name, subject, max ->
                onSave(null, name, subject, max)
                showNew = false
            },
            onDismiss = { showNew = false },
        )
    }

    val editing = categories.categories.firstOrNull { it.id == editingId }
    if (editing != null) {
        CategorySheet(
            initial = editing,
            subjects = categories.subjects,
            takenSubjects = categories.categories
                .filter { it.id != editing.id }
                .map { it.subject.lowercase() }
                .toSet(),
            onSave = { name, subject, max ->
                onSave(editing.id, name, subject, max)
                editingId = null
            },
            onDismiss = { editingId = null },
        )
    }

    if (showManage) {
        ManageSheet(
            categories = categories.categories,
            onEdit = { category ->
                showManage = false
                editingId = category.id
            },
            onDelete = { category -> deleting = category },
            onDismiss = { showManage = false },
        )
    }

    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_message, category.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(category.id)
                        deleting = null
                        if (categories.categories.size <= 1) showManage = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.category_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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

@Composable
private fun CategoryCard(
    category: AbsenceCategory,
    usage: CategoryUsage?,
    loading: Boolean,
    missingSubject: Boolean,
    partial: Boolean,
    modifier: Modifier = Modifier,
) {
    val used = usage?.total ?: 0
    val max = category.maxLessons
    val atLimit = usage != null && used >= max
    val accent = when {
        atLimit -> MaterialTheme.colorScheme.error
        usage != null && used * 5 >= max * 4 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val progress by animateFloatAsState(
        targetValue = if (usage == null) 0f else (used.toFloat() / max).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "categoryProgress",
    )
    val status = when {
        missingSubject -> stringResource(R.string.category_missing_subject)
        usage == null -> stringResource(R.string.category_loading)
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
                            text = if (usage == null) dash else used.toString(),
                            style = MaterialTheme.typography.displaySmallEmphasized,
                            color = if (atLimit) accent else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = " / $max",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                // A name that is just the subject needs no second label.
                if (!category.name.equals(category.subject, ignoreCase = true)) Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = category.subject,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
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

/** Create or edit a category: a name, one of the student's subjects, and the lesson limit. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategorySheet(
    initial: AbsenceCategory?,
    subjects: List<String>,
    takenSubjects: Set<String>,
    onSave: (name: String, subject: String, maxLessons: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var subject by rememberSaveable { mutableStateOf(initial?.subject.orEmpty()) }
    var maxText by rememberSaveable { mutableStateOf(initial?.maxLessons?.toString().orEmpty()) }

    // A category whose subject left the timetable keeps it visible in the list.
    val options = remember(subjects, initial) {
        val own = initial?.subject?.takeIf { s -> subjects.none { it.equals(s, ignoreCase = true) } }
        listOfNotNull(own) + subjects
    }
    val maxLessons = maxText.toIntOrNull()
    val canSave = subject.isNotBlank() && maxLessons != null && maxLessons > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(
                    if (initial == null) R.string.category_new_title else R.string.category_edit_title,
                ),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                label = { Text(stringResource(R.string.category_name)) },
                placeholder = { if (subject.isNotBlank()) Text(subject) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.category_subject),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            if (options.isEmpty()) {
                Text(
                    text = stringResource(R.string.lesson_colors_no_subjects),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(options, key = { it }) { option ->
                        val taken = option.lowercase() in takenSubjects
                        SelectionRow(
                            label = option,
                            supporting = if (taken) stringResource(R.string.category_subject_in_use) else null,
                            selected = subject.equals(option, ignoreCase = true),
                            onClick = { if (!taken) subject = option },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = maxText,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all { it.isDigit() }) maxText = value
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                label = { Text(stringResource(R.string.category_max)) },
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
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSave(name, subject, maxLessons ?: return@Button) },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManageSheet(
    categories: List<AbsenceCategory>,
    onEdit: (AbsenceCategory) -> Unit,
    onDelete: (AbsenceCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
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
            if (categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.category_manage_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(categories, key = { it.id }) { category ->
                        Surface(
                            onClick = { onEdit(category) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            R.string.category_row_summary,
                                            category.subject,
                                            category.maxLessons,
                                        ),
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { onDelete(category) }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.category_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}
