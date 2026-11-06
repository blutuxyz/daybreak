package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.settings.FavoriteTarget
import com.bdersolutions.daybreak.data.settings.favoriteKey
import com.bdersolutions.daybreak.data.settings.reorderedByKeys
import com.bdersolutions.daybreak.data.settings.withToggled
import com.bdersolutions.daybreak.untis.ElementType
import com.bdersolutions.daybreak.untis.UntisDirectory
import com.bdersolutions.daybreak.untis.UntisElement
import kotlinx.coroutines.CancellationException

/** The two pages of the picker: choosing, and managing the favourites. */
private enum class PickerPage { PICK, MANAGE }

/**
 * Picks the timetable to show: my own, or a class, teacher or room. Favourites come first, a star
 * pins or unpins an element, and the Manage page reorders and removes them, as on Android. The
 * dialog closes with its exit animation, then calls [onDismiss].
 */
@Composable
fun ElementChooser(
    container: DesktopContainer,
    /** The element the timetable shows now; null for my own timetable. */
    current: UntisElement?,
    onPick: (UntisElement?) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalLanguage.current
    val settings by container.settings.settings.collectAsState()
    val favorites = settings.favoriteTargets
    var directory by remember { mutableStateOf<UntisDirectory?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(PickerPage.PICK) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            directory = container.schoolData.loadDirectory()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: trFor(language, "Die Liste konnte nicht geladen werden.", "Could not load the list.")
        }
    }

    DesktopDialog(onDismiss = onDismiss, maxWidth = 560.dp) { close ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState == PickerPage.MANAGE
                (
                    slideInHorizontally(DesktopMotion.spatial<IntOffset>()) { width -> if (forward) width / 8 else -width / 8 } +
                        fadeIn(DesktopMotion.effects<Float>())
                    ).togetherWith(
                    slideOutHorizontally(DesktopMotion.fastSpatial<IntOffset>()) { width -> if (forward) -width / 8 else width / 8 } +
                        fadeOut(DesktopMotion.fastEffects<Float>()),
                ).using(SizeTransform(clip = false))
            },
            label = "targetPickerPage",
        ) { shown ->
            when (shown) {
                PickerPage.PICK -> PickContent(
                    directory = directory,
                    error = error,
                    current = current,
                    favorites = favorites,
                    query = query,
                    onQuery = { query = it },
                    category = category,
                    onCategory = { category = it },
                    onChoose = { element ->
                        onPick(element)
                        close()
                    },
                    onToggleFavorite = { element ->
                        container.settings.updateFavoriteTargets { it.withToggled(element) }
                    },
                    onManage = { page = PickerPage.MANAGE },
                    onClose = close,
                )

                PickerPage.MANAGE -> ManageFavorites(
                    favorites = favorites,
                    onBack = { page = PickerPage.PICK },
                    onRemove = { key -> container.settings.updateFavoriteTargets { list -> list.filterNot { it.key == key } } },
                    onReorder = { keys -> container.settings.updateFavoriteTargets { it.reorderedByKeys(keys) } },
                )
            }
        }
    }
}

@Composable
private fun PickContent(
    directory: UntisDirectory?,
    error: String?,
    current: UntisElement?,
    favorites: List<FavoriteTarget>,
    query: String,
    onQuery: (String) -> Unit,
    category: Int,
    onCategory: (Int) -> Unit,
    onChoose: (UntisElement?) -> Unit,
    onToggleFavorite: (UntisElement) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
) {
    val favoriteKeys = favorites.map { it.key }.toSet()
    val search = query.trim()
    val lists = directory?.let { listOf(it.classes, it.teachers, it.rooms) }.orEmpty()
    val results = if (search.isBlank()) emptyList() else lists.flatten().filter { it.matches(search) }
    val sectionTitles = listOf(
        str("target_section_classes"),
        str("target_section_teachers"),
        str("target_section_rooms"),
    )

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                str("target_picker_title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            if (favorites.isNotEmpty()) {
                IconButton(onClick = onManage, modifier = Modifier.handCursor()) {
                    Icon(Icons.Outlined.Tune, contentDescription = str("favorites_manage"))
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            placeholder = { Text(str("target_picker_search"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.handCursor()) {
                        Icon(Icons.Outlined.Close, contentDescription = str("target_clear_search"))
                    }
                }
            },
        )

        if (directory == null) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                if (error == null) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                } else {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            if (search.isBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sectionTitles.forEachIndexed { index, title ->
                        FilterChip(
                            selected = category == index,
                            onClick = { onCategory(index) },
                            modifier = Modifier.handCursor(),
                            label = { Text(title) },
                        )
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().height(380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (search.isBlank()) {
                    item(key = "own") {
                        TargetRow(
                            title = str("target_own"),
                            subtitle = null,
                            selected = current == null,
                            favorite = null,
                            onClick = { onChoose(null) },
                            onToggleFavorite = null,
                        )
                    }
                    if (favorites.isNotEmpty()) {
                        item(key = "favorites-header") { SectionHeader(str("favorites_title")) }
                        items(favorites, key = { "fav-${it.key}" }) { favorite ->
                            val element = favorite.toElement()
                            if (element != null) {
                                TargetRow(
                                    title = favorite.displayName,
                                    subtitle = typeLabel(element.type),
                                    selected = current?.favoriteKey == favorite.key,
                                    favorite = true,
                                    onClick = { onChoose(element) },
                                    onToggleFavorite = { onToggleFavorite(element) },
                                )
                            }
                        }
                    }
                    item(key = "section-$category") { SectionHeader(sectionTitles[category]) }
                    items(lists.getOrElse(category) { emptyList() }.sortedBy { it.displayName }, key = { "el-${it.type}-${it.id}" }) { element ->
                        TargetRow(
                            title = element.displayName,
                            subtitle = null,
                            selected = current?.favoriteKey == element.favoriteKey,
                            favorite = element.favoriteKey in favoriteKeys,
                            onClick = { onChoose(element) },
                            onToggleFavorite = { onToggleFavorite(element) },
                        )
                    }
                } else if (results.isEmpty()) {
                    item(key = "no-results") {
                        Text(
                            str("target_no_results"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                } else {
                    items(results, key = { "hit-${it.type}-${it.id}" }) { element ->
                        TargetRow(
                            title = element.displayName,
                            subtitle = typeLabel(element.type),
                            selected = current?.favoriteKey == element.favoriteKey,
                            favorite = element.favoriteKey in favoriteKeys,
                            onClick = { onChoose(element) },
                            onToggleFavorite = { onToggleFavorite(element) },
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose, modifier = Modifier.handCursor()) { Text(tr("Schließen", "Close")) }
        }
    }
}

/** The favourites as rows: move them up or down, or remove them. The order is saved at once. */
@Composable
private fun ManageFavorites(
    favorites: List<FavoriteTarget>,
    onBack: () -> Unit,
    onRemove: (key: String) -> Unit,
    onReorder: (keys: List<String>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.handCursor()) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = str("action_back"))
            }
            Text(
                str("favorites_manage"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (favorites.isEmpty()) {
            Text(str("favorites_empty"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                favorites.forEachIndexed { index, favorite ->
                    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(favorite.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    favorite.toElement()?.type?.let { typeLabel(it) } ?: favorite.type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    val keys = favorites.map { it.key }.toMutableList()
                                    if (index > 0) {
                                        keys.add(index - 1, keys.removeAt(index))
                                        onReorder(keys)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.handCursor(),
                            ) {
                                Icon(Icons.Outlined.ArrowUpward, contentDescription = str("favorites_reorder"))
                            }
                            IconButton(
                                onClick = {
                                    val keys = favorites.map { it.key }.toMutableList()
                                    if (index < keys.lastIndex) {
                                        keys.add(index + 1, keys.removeAt(index))
                                        onReorder(keys)
                                    }
                                },
                                enabled = index < favorites.lastIndex,
                                modifier = Modifier.handCursor(),
                            ) {
                                Icon(Icons.Outlined.ArrowDownward, contentDescription = str("favorites_reorder"))
                            }
                            IconButton(onClick = { onRemove(favorite.key) }, modifier = Modifier.handCursor()) {
                                Icon(Icons.Outlined.Close, contentDescription = str("favorites_remove"))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One element row: the name with its type, a filled ring when selected, and a star to pin it. */
@Composable
private fun TargetRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    favorite: Boolean?,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).handCursor(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onToggleFavorite != null && favorite != null) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.handCursor()) {
                    Icon(
                        if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = str(if (favorite) "favorites_remove" else "favorites_add"),
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** The name of a kind of element, as the Android picker labels it. */
@Composable
private fun typeLabel(type: ElementType): String = when (type) {
    ElementType.CLASS -> str("target_type_class")
    ElementType.TEACHER -> str("target_type_teacher")
    ElementType.ROOM -> str("target_type_room")
}

/** A search hit: the name, the short name, or the long name of the element. */
private fun UntisElement.matches(search: String): Boolean =
    name.contains(search, ignoreCase = true) || longName.contains(search, ignoreCase = true)

/** One row of a pick list: the label, a tint when it is the chosen one, and the hand cursor. */
@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).handCursor(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
