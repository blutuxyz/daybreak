package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.repository.TimetableTarget
import com.bdersolutions.daybreak.data.settings.FavoriteTarget
import com.bdersolutions.daybreak.data.settings.favoriteKey
import com.bdersolutions.daybreak.ui.components.CollapseOnRemove
import com.bdersolutions.daybreak.ui.components.ReorderState
import com.bdersolutions.daybreak.ui.components.rememberMorphCorner
import com.bdersolutions.daybreak.ui.components.rememberPressScale
import com.bdersolutions.daybreak.ui.components.rememberReorderState
import com.bdersolutions.daybreak.ui.components.reorderHandle
import com.bdersolutions.daybreak.untis.ElementType
import com.bdersolutions.daybreak.untis.UntisDirectory
import com.bdersolutions.daybreak.untis.UntisElement
import kotlinx.coroutines.launch

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

private sealed interface PickerPage {
    data object Pick : PickerPage
    data object Manage : PickerPage
}

/**
 * Bottom sheet to choose whose timetable to view, and to keep favourites. The list and the
 * favourites management are two pages of one sheet, so going between them slides inside the
 * sheet. Classes, teachers and rooms are shown one kind at a time; the search field searches
 * all of them. A star pins an element as a favourite.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TargetPickerSheet(
    directory: UntisDirectory?,
    selected: TimetableTarget,
    favorites: List<FavoriteTarget>,
    onSelect: (TimetableTarget) -> Unit,
    onToggleFavorite: (UntisElement) -> Unit,
    onRemoveFavorite: (key: String) -> Unit,
    onReorderFavorites: (keys: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    var page by remember { mutableStateOf<PickerPage>(PickerPage.Pick) }
    // Kept here, so they survive a visit to the favourites page.
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    val typeLabels = mapOf(
        ElementType.CLASS to stringResource(R.string.target_type_class),
        ElementType.TEACHER to stringResource(R.string.target_type_teacher),
        ElementType.ROOM to stringResource(R.string.target_type_room),
    )

    // The timetable switches right away; the sheet slides down while it loads.
    val choose: (TimetableTarget) -> Unit = { target ->
        onSelect(target)
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState == PickerPage.Manage
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
            label = "targetPickerPage",
        ) { current ->
            when (current) {
                PickerPage.Pick -> PickContent(
                    directory = directory,
                    selected = selected,
                    favorites = favorites,
                    typeLabels = typeLabels,
                    query = query,
                    onQueryChange = { query = it },
                    category = category,
                    onCategoryChange = { category = it },
                    onChoose = choose,
                    onToggleFavorite = onToggleFavorite,
                    onManage = { page = PickerPage.Manage },
                )

                PickerPage.Manage -> ManageFavorites(
                    favorites = favorites,
                    typeLabels = typeLabels,
                    onBack = { page = PickerPage.Pick },
                    onRemove = onRemoveFavorite,
                    onReorder = onReorderFavorites,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PickContent(
    directory: UntisDirectory?,
    selected: TimetableTarget,
    favorites: List<FavoriteTarget>,
    typeLabels: Map<ElementType, String>,
    query: String,
    onQueryChange: (String) -> Unit,
    category: Int,
    onCategoryChange: (Int) -> Unit,
    onChoose: (TimetableTarget) -> Unit,
    onToggleFavorite: (UntisElement) -> Unit,
    onManage: () -> Unit,
) {
    val ownLabel = stringResource(R.string.target_own)
    val noResults = stringResource(R.string.target_no_results)
    val categoryTitles = listOf(
        stringResource(R.string.target_section_classes),
        stringResource(R.string.target_section_teachers),
        stringResource(R.string.target_section_rooms),
    )
    val favoriteKeys = remember(favorites) { favorites.map { it.key }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        // A fixed minimum height, so the row does not grow when the manage button appears.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.target_picker_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(visible = favorites.isNotEmpty()) {
                IconButton(onClick = onManage) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.favorites_manage),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            // A pill, like the buttons and rows around it.
            shape = CircleShape,
            placeholder = {
                Text(
                    text = stringResource(R.string.target_picker_search),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.target_clear_search),
                        )
                    }
                }
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
        val results = remember(directory, search) {
            if (search.isBlank()) emptyList() else lists.flatten().filter { it.matches(search) }
        }
        val shown = if (search.isBlank()) lists[category] else results

        LazyColumn(
            // The list grows and shrinks when a favourite is added or the search changes. Animating
            // its size makes the sheet follow smoothly instead of jumping.
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
                .heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (search.isBlank()) {
                item(key = "own") {
                    TargetRow(
                        type = null,
                        title = ownLabel,
                        supporting = null,
                        selected = selected == TimetableTarget.Own,
                        favorite = null,
                        onClick = { onChoose(TimetableTarget.Own) },
                        onToggleFavorite = null,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (favorites.isNotEmpty()) {
                    item(key = "favorites-header") {
                        SectionHeader(
                            text = stringResource(R.string.favorites_title),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(favorites, key = { "fav-${it.key}" }) { favorite ->
                        val element = favorite.toElement()
                        if (element != null) {
                            TargetRow(
                                type = element.type,
                                title = element.displayName,
                                supporting = supportingText(element, typeLabels, showType = true),
                                selected = isSelected(selected, element),
                                favorite = true,
                                onClick = { onChoose(TimetableTarget.Element(element)) },
                                onToggleFavorite = { onToggleFavorite(element) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
                // The tabs stay at the top while a long list scrolls underneath.
                stickyHeader(key = "tabs") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(vertical = 6.dp),
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            categoryTitles.forEachIndexed { index, title ->
                                SegmentedButton(
                                    selected = category == index,
                                    onClick = { onCategoryChange(index) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = categoryTitles.size,
                                    ),
                                    label = {
                                        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        text = noResults,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(16.dp),
                    )
                }
            } else {
                items(shown, key = { "el-${it.favoriteKey}" }) { element ->
                    TargetRow(
                        type = element.type,
                        title = element.displayName,
                        supporting = supportingText(element, typeLabels, showType = search.isNotBlank()),
                        selected = isSelected(selected, element),
                        favorite = element.favoriteKey in favoriteKeys,
                        onClick = { onChoose(TimetableTarget.Element(element)) },
                        onToggleFavorite = { onToggleFavorite(element) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

/**
 * One class, teacher or room, or "my timetable" when [type] is null. The selected row is tinted
 * and shows a check, its corners morph to a pill and tighten while pressed. A star pins it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TargetRow(
    type: ElementType?,
    title: String,
    supporting: String?,
    selected: Boolean,
    favorite: Boolean?,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val corner by rememberMorphCorner(
        interactionSource = interaction,
        selected = selected,
        selectedRadius = 32.dp,
        unselectedRadius = 32.dp,
        pressedRadius = 8.dp,
    )
    val pressScale by rememberPressScale(interaction, pressedScale = 0.97f)
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        animationSpec = motion.fastEffectsSpec(),
        label = "targetRowContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        animationSpec = motion.fastEffectsSpec(),
        label = "targetRowContent",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
    ) {
        Row(
            modifier = Modifier.padding(
                start = 12.dp,
                end = if (onToggleFavorite != null) 2.dp else 16.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TypeBadge(type)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (onToggleFavorite != null && favorite != null) {
                FavoriteStar(favorite = favorite, onClick = onToggleFavorite)
            }
        }
    }
}

/** A star that pops when it turns on or off and fades between its colours. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoriteStar(favorite: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val tint by animateColorAsState(
        targetValue = if (favorite) scheme.tertiary else scheme.onSurfaceVariant,
        animationSpec = motion.fastEffectsSpec(),
        label = "starTint",
    )
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    val spring = motion.defaultSpatialSpec<Float>()
    LaunchedEffect(favorite) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        pop.snapTo(0.55f)
        pop.animateTo(1f, spring)
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(
                if (favorite) R.string.favorites_remove else R.string.favorites_add,
            ),
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        )
    }
}

/** A round badge with the kind's icon in the kind's colour; "my timetable" has no kind. */
@Composable
private fun TypeBadge(type: ElementType?) {
    val scheme = MaterialTheme.colorScheme
    val (icon, accent) = when (type) {
        null -> Icons.Outlined.Person to scheme.primary
        ElementType.CLASS -> Icons.Outlined.Groups to scheme.primary
        ElementType.TEACHER -> Icons.Outlined.School to scheme.tertiary
        ElementType.ROOM -> Icons.Outlined.MeetingRoom to scheme.secondary
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(accent.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The favourites as cards. They can be reordered by dragging the handle; the new order is saved
 * when the finger lifts. Removing one collapses its card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManageFavorites(
    favorites: List<FavoriteTarget>,
    typeLabels: Map<ElementType, String>,
    onBack: () -> Unit,
    onRemove: (key: String) -> Unit,
    onReorder: (keys: List<String>) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        items = favorites,
        listState = listState,
        keyOf = { it.key },
        onCommit = { ordered -> onReorder(ordered.map { it.key }) },
    )
    val removing = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.favorites_manage),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = favorites.isEmpty(),
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec<Float>())
                    .togetherWith(fadeOut(motion.fastEffectsSpec<Float>()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec<IntSize>() })
            },
            label = "favoritesEmpty",
        ) { empty ->
            if (empty) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp),
                ) {
                    items(reorder.order, key = { it.key }) { favorite ->
                        val dragging = reorder.isDragging(favorite.key)
                        FavoriteManageRow(
                            favorite = favorite,
                            typeLabels = typeLabels,
                            reorder = reorder,
                            dragging = dragging,
                            canReorder = reorder.order.size > 1,
                            removing = favorite.key in removing,
                            onAskRemove = { removing += favorite.key },
                            onRemoved = {
                                onRemove(favorite.key)
                                removing -= favorite.key
                            },
                            modifier = if (dragging) Modifier.zIndex(1f) else Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoriteManageRow(
    favorite: FavoriteTarget,
    typeLabels: Map<ElementType, String>,
    reorder: ReorderState<FavoriteTarget>,
    dragging: Boolean,
    canReorder: Boolean,
    removing: Boolean,
    onAskRemove: () -> Unit,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val element = favorite.toElement()
    val elevation by animateDpAsState(
        targetValue = if (dragging) 10.dp else 0.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "favoriteRowElevation",
    )
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1.03f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "favoriteRowLift",
    )

    CollapseOnRemove(removing = removing, onRemoved = onRemoved, modifier = modifier) {
        // The gap between cards belongs to the card, so it collapses with it.
        Box(modifier = Modifier.padding(bottom = 8.dp)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = elevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = reorder.offsetOf(favorite.key)
                        scaleX = lift
                        scaleY = lift
                    },
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = if (canReorder) 4.dp else 14.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                        end = 6.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (canReorder) 8.dp else 12.dp),
                ) {
                    if (canReorder) {
                        Box(
                            modifier = Modifier
                                .reorderHandle(reorder, favorite.key)
                                .size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.DragHandle,
                                contentDescription = stringResource(R.string.favorites_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TypeBadge(element?.type)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = favorite.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (element != null) {
                            Text(
                                text = supportingText(element, typeLabels, showType = true).orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = onAskRemove) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.favorites_remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun isSelected(selected: TimetableTarget, element: UntisElement): Boolean =
    selected is TimetableTarget.Element &&
        selected.element.type == element.type &&
        selected.element.id == element.id

/** "KOGL · Teacher": the short code when the title is the long name, and the kind when asked. */
private fun supportingText(
    element: UntisElement,
    typeLabels: Map<ElementType, String>,
    showType: Boolean,
): String? = listOfNotNull(
    element.name.takeIf { element.longName.isNotBlank() && it != element.displayName },
    typeLabels[element.type].takeIf { showType },
).joinToString(" · ").ifBlank { null }

private fun UntisElement.matches(search: String): Boolean =
    search.isBlank() ||
        displayName.contains(search, ignoreCase = true) ||
        name.contains(search, ignoreCase = true)
