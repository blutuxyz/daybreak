package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.repository.TimetableTarget
import com.bdersolutions.daybreak.ui.components.SelectionRow
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

/**
 * Bottom sheet to choose whose timetable to view. Classes, teachers and rooms are shown
 * one category at a time; typing in the search field searches all of them.
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
    var category by rememberSaveable { mutableIntStateOf(0) }
    val ownLabel = stringResource(R.string.target_own)
    val noResults = stringResource(R.string.target_no_results)
    val categoryTitles = listOf(
        stringResource(R.string.target_section_classes),
        stringResource(R.string.target_section_teachers),
        stringResource(R.string.target_section_rooms),
    )

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
            Spacer(Modifier.height(10.dp))

            if (directory == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val search = query.trim()
            val lists = listOf(directory.classes, directory.teachers, directory.rooms)
            val shown: List<UntisElement> = if (search.isBlank()) {
                lists[category]
            } else {
                lists.flatten().filter { it.matches(search) }
            }

            if (search.isBlank()) {
                SelectionRow(
                    label = ownLabel,
                    supporting = null,
                    selected = selected == TimetableTarget.Own,
                    onClick = { onSelect(TimetableTarget.Own) },
                )
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    categoryTitles.forEachIndexed { index, title ->
                        SegmentedButton(
                            selected = category == index,
                            onClick = { category = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = categoryTitles.size),
                            label = {
                                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (shown.isEmpty()) {
                Text(
                    text = noResults,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { "${it.type.name}-${it.id}" }) { element ->
                        val target = TimetableTarget.Element(element)
                        SelectionRow(
                            label = element.displayName,
                            supporting = if (search.isBlank()) null else categoryName(element, categoryTitles),
                            selected = selected == target,
                            onClick = { onSelect(target) },
                        )
                    }
                }
            }
        }
    }
}

private fun categoryName(element: UntisElement, titles: List<String>): String = when (element.type) {
    ElementType.CLASS -> titles[0]
    ElementType.TEACHER -> titles[1]
    ElementType.ROOM -> titles[2]
}

private fun UntisElement.matches(search: String): Boolean =
    search.isBlank() ||
        displayName.contains(search, ignoreCase = true) ||
        name.contains(search, ignoreCase = true)

