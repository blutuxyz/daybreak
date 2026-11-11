package com.bdersolutions.daybreak.ui.settings

import com.bdersolutions.daybreak.ui.components.FilledTonalButton
import com.bdersolutions.daybreak.ui.components.IconButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.settings.LessonColorSettings
import com.bdersolutions.daybreak.ui.components.cancelledAccent
import com.bdersolutions.daybreak.ui.components.automaticSubjectColor
import com.bdersolutions.daybreak.ui.components.examAccent
import com.bdersolutions.daybreak.ui.components.lessonSwatchArgb
import com.bdersolutions.daybreak.ui.components.onColorFor
import com.bdersolutions.daybreak.ui.components.substitutionAccent

/** Which colour the sheet is editing: a status, or one subject. */
private sealed interface ColorTarget {
    data object Cancelled : ColorTarget
    data object Exam : ColorTarget
    data object Substitution : ColorTarget
    data class Subject(val name: String) : ColorTarget
}

/**
 * Lets the user colour every lesson state and every subject. Choices apply straight away.
 * Anything not chosen keeps its automatic colour.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LessonColorsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val colors = settings.lessonColors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val editingTarget = editing?.let { key -> targetFromKey(key) }

    // Automatic colours, shown as the default in each row.
    val cancelledDefault = cancelledAccent()
    val examDefault = examAccent()
    val substitutionDefault = substitutionAccent()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = scheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.lesson_colors_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroup {
                    FillCardRow(
                        checked = colors.fillWholeCard,
                        onCheckedChange = { checked ->
                            viewModel.updateLessonColors { it.copy(fillWholeCard = checked) }
                        },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.lesson_colors_statuses)) }
            item {
                SettingsGroup {
                    ColorRow(
                        title = stringResource(R.string.lesson_cancelled),
                        color = colors.cancelledColor?.let { Color(it.toInt()) } ?: cancelledDefault,
                        custom = colors.cancelledColor != null,
                        onClick = { editing = keyOf(ColorTarget.Cancelled) },
                    )
                    SettingsDivider()
                    ColorRow(
                        title = stringResource(R.string.exam_badge),
                        color = colors.examColor?.let { Color(it.toInt()) } ?: examDefault,
                        custom = colors.examColor != null,
                        onClick = { editing = keyOf(ColorTarget.Exam) },
                    )
                    SettingsDivider()
                    ColorRow(
                        title = stringResource(R.string.lesson_substitution),
                        color = colors.substitutionColor?.let { Color(it.toInt()) } ?: substitutionDefault,
                        custom = colors.substitutionColor != null,
                        onClick = { editing = keyOf(ColorTarget.Substitution) },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.lesson_colors_subjects)) }
            if (subjects.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.lesson_colors_no_subjects),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            } else {
                item {
                    SettingsGroup {
                        subjects.forEachIndexed { index, subject ->
                            if (index > 0) SettingsDivider()
                            val custom = colors.subjectColors[subject]
                            ColorRow(
                                title = subject,
                                color = custom?.let { Color(it.toInt()) }
                                    ?: automaticSubjectColor(subject, scheme, dark, settings.subjectColorMode),
                                custom = custom != null,
                                onClick = { editing = keyOf(ColorTarget.Subject(subject)) },
                            )
                        }
                    }
                }
            }
        }
    }

    editingTarget?.let { target ->
        val currentArgb: Long?
        val automatic: Color
        val title: String
        when (target) {
            ColorTarget.Cancelled -> {
                currentArgb = colors.cancelledColor
                automatic = cancelledDefault
                title = stringResource(R.string.lesson_cancelled)
            }

            ColorTarget.Exam -> {
                currentArgb = colors.examColor
                automatic = examDefault
                title = stringResource(R.string.exam_badge)
            }

            ColorTarget.Substitution -> {
                currentArgb = colors.substitutionColor
                automatic = substitutionDefault
                title = stringResource(R.string.lesson_substitution)
            }

            is ColorTarget.Subject -> {
                currentArgb = colors.subjectColors[target.name]
                automatic = automaticSubjectColor(target.name, scheme, dark, settings.subjectColorMode)
                title = target.name
            }
        }
        ColorSheet(
            title = title,
            currentArgb = currentArgb,
            automatic = automatic,
            onPick = { argb ->
                viewModel.updateLessonColors { applyChoice(it, target, argb) }
                editing = null
            },
            onReset = {
                viewModel.updateLessonColors { applyChoice(it, target, null) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun keyOf(target: ColorTarget): String = when (target) {
    ColorTarget.Cancelled -> "cancelled"
    ColorTarget.Exam -> "exam"
    ColorTarget.Substitution -> "substitution"
    is ColorTarget.Subject -> "subject:${target.name}"
}

private fun targetFromKey(key: String): ColorTarget = when {
    key == "cancelled" -> ColorTarget.Cancelled
    key == "exam" -> ColorTarget.Exam
    key == "substitution" -> ColorTarget.Substitution
    key.startsWith("subject:") -> ColorTarget.Subject(key.removePrefix("subject:"))
    else -> ColorTarget.Cancelled
}

/** Returns the colours with one target set to [argb], or back to automatic when null. */
private fun applyChoice(
    colors: LessonColorSettings,
    target: ColorTarget,
    argb: Long?,
): LessonColorSettings = when (target) {
    ColorTarget.Cancelled -> colors.copy(cancelledColor = argb)
    ColorTarget.Exam -> colors.copy(examColor = argb)
    ColorTarget.Substitution -> colors.copy(substitutionColor = argb)
    is ColorTarget.Subject -> colors.copy(
        subjectColors = if (argb == null) {
            colors.subjectColors - target.name
        } else {
            colors.subjectColors + (target.name to argb)
        },
    )
}

@Composable
private fun FillCardRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.lesson_colors_fill_card),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.lesson_colors_fill_card_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** One colour choice: a dot showing the colour, the name, and whether it is automatic. */
@Composable
private fun ColorRow(
    title: String,
    color: Color,
    custom: Boolean,
    onClick: () -> Unit,
) {
    val dotColor by animateColorAsState(color, label = "colorRowDot")
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        },
        supportingContent = if (!custom) {
            {
                Text(
                    text = stringResource(R.string.lesson_colors_automatic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(dotColor, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape),
            )
        },
        trailingContent = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** Bottom sheet with a palette of swatches, plus a way back to the automatic colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSheet(
    title: String,
    currentArgb: Long?,
    automatic: Color,
    onPick: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val swatches = remember { lessonSwatchArgb() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(16.dp))
            swatches.chunked(6).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { argb ->
                        Swatch(
                            color = Color(argb.toInt()),
                            selected = argb == currentArgb,
                            onClick = { onPick(argb) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            FilledTonalButton(
                onClick = onReset,
                enabled = currentArgb != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(automatic, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.lesson_colors_reset))
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ring by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        label = "swatchRing",
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(3.dp, ring, CircleShape)
            .padding(4.dp)
            .background(color, CircleShape)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = onColorFor(color),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
