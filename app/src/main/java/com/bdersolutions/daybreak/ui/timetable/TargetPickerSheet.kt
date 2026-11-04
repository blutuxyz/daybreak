package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.repository.TimetableTarget
import com.bdersolutions.daybreak.untis.ElementType
import com.bdersolutions.daybreak.untis.UntisDirectory
import com.bdersolutions.daybreak.untis.UntisElement

/** Short header label for whichever timetable is showing. */
@Composable
fun targetLabel(target: TimetableTarget): String = when (target) {
    TimetableTarget.Own -> stringResource(R.string.target_own)
    is TimetableTarget.Element -> when (target.element.type) {
        ElementType.CLASS -> stringResource(R.string.target_class, target.element.displayName)
        ElementType.TEACHER -> stringResource(R.string.target_teacher, target.element.displayName)
        ElementType.ROOM -> stringResource(R.string.room_value, target.element.displayName)
    }
}

/** Chip above the timetable that shows whose timetable is displayed and opens the picker. */
@Composable
fun TargetChip(
    target: TimetableTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = targetLabel(target),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = modifier,
    )
}

/**
 * Bottom sheet to choose whose timetable to view: your own, or a class, teacher or room.
 * Results are filtered by the search field.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TargetPickerSheet(
    directory: UntisDirectory?,
    selected: TimetableTarget,
    onSelect: (TimetableTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val classesTitle = stringResource(R.string.target_section_classes)
    val teachersTitle = stringResource(R.string.target_section_teachers)
    val roomsTitle = stringResource(R.string.target_section_rooms)
    val ownLabel = stringResource(R.string.target_own)
    val noResults = stringResource(R.string.target_no_results)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.target_picker_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.target_picker_search)) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
            )
            Spacer(Modifier.height(8.dp))

            if (directory == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val search = query.trim()
                val matchingCount = listOf(directory.classes, directory.teachers, directory.rooms)
                    .fold(0) { total, list -> total + list.count { it.matches(search) } }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                ) {
                    if (search.isBlank()) {
                        item(key = "own") {
                            TargetRow(
                                label = ownLabel,
                                selected = selected == TimetableTarget.Own,
                                onClick = { onSelect(TimetableTarget.Own) },
                            )
                        }
                    }
                    elementSection(classesTitle, directory.classes, search, selected, onSelect)
                    elementSection(teachersTitle, directory.teachers, search, selected, onSelect)
                    elementSection(roomsTitle, directory.rooms, search, selected, onSelect)
                    if (matchingCount == 0 && search.isNotBlank()) {
                        item(key = "none") {
                            Text(
                                text = noResults,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.elementSection(
    title: String,
    elements: List<UntisElement>,
    search: String,
    selected: TimetableTarget,
    onSelect: (TimetableTarget) -> Unit,
) {
    val matches = elements.filter { it.matches(search) }
    if (matches.isEmpty()) return
    item(key = "header-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
        )
    }
    items(matches, key = { "${it.type.name}-${it.id}" }) { element ->
        val target = TimetableTarget.Element(element)
        TargetRow(
            label = element.displayName,
            selected = selected == target,
            onClick = { onSelect(target) },
        )
    }
}

private fun UntisElement.matches(search: String): Boolean =
    search.isBlank() ||
        displayName.contains(search, ignoreCase = true) ||
        name.contains(search, ignoreCase = true)

@Composable
private fun TargetRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
