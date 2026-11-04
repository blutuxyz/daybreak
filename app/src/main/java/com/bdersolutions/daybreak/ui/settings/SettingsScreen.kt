package com.bdersolutions.daybreak.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.AppSettings
import com.bdersolutions.daybreak.data.settings.MotionStyle
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.data.settings.SubjectColorMode
import com.bdersolutions.daybreak.data.settings.TextScale
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.ui.components.SelectionRow
import com.bdersolutions.daybreak.ui.components.motionAnimationsEnabled
import com.bdersolutions.daybreak.ui.components.rememberPressScale
import com.bdersolutions.daybreak.ui.theme.accentSwatchColors
import com.bdersolutions.daybreak.ui.theme.resolveDarkTheme
import com.bdersolutions.daybreak.ui.toUserMessage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenDebug: () -> Unit,
    onOpenLessonColors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val debugUnlocked by viewModel.debugUnlocked.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentLanguage = if (
        context.resources.configuration.locales[0].language.equals("de", ignoreCase = true)
    ) {
        AppLanguage.GERMAN
    } else {
        AppLanguage.ENGLISH
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setNotificationsEnabled(true)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_notification_permission_denied),
                )
            }
        }
    }

    val retryLabel = stringResource(R.string.action_retry)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { error ->
            val result = snackbarHostState.showSnackbar(
                message = error.toUserMessage(context),
                actionLabel = retryLabel,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.syncNow()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.syncSucceeded.collect {
            snackbarHostState.showSnackbar(context.getString(R.string.timetable_refreshed))
        }
    }

    val lastSyncText = remember(syncStatus.lastSyncAt) {
        if (syncStatus.lastSyncAt > 0L) {
            Instant.ofEpochMilli(syncStatus.lastSyncAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
        } else {
            null
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                subtitle = if (session != null) {
                    {
                        Text(
                            text = session!!.studentName.ifBlank { session!!.displayName },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    null
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AccountHeader(
                    displayName = session?.displayName.orEmpty(),
                    studentName = session?.studentName.orEmpty(),
                    schoolName = session?.schoolName.orEmpty(),
                    school = session?.school.orEmpty(),
                    username = session?.username.orEmpty(),
                )
            }

            item { SectionLabel(stringResource(R.string.settings_appearance)) }
            item {
                AppearanceGroup(
                    settings = settings,
                    currentLanguage = currentLanguage,
                    onLanguage = viewModel::setLanguage,
                    onThemeMode = { mode ->
                        // Ten taps on "System" reveal the debug log (see SettingsViewModel).
                        if (mode == ThemeMode.SYSTEM) viewModel.onSystemThemeTapped()
                        viewModel.setThemeMode(mode)
                    },
                    onDynamicColor = viewModel::setDynamicColor,
                    onAccent = viewModel::setAccent,
                    onMotionStyle = viewModel::setMotionStyle,
                    onStartView = viewModel::setStartView,
                    onSubjectColorMode = viewModel::setSubjectColorMode,
                    onTextScale = viewModel::setTextScale,
                )
            }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.settings_lesson_colors),
                        subtitle = stringResource(R.string.settings_lesson_colors_desc),
                        onClick = onOpenLessonColors,
                        trailing = {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_sync)) }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.CloudSync,
                        title = stringResource(R.string.settings_sync_enabled),
                        subtitle = stringResource(R.string.settings_sync_enabled_desc),
                        trailing = {
                            SpringSwitch(
                                checked = settings.syncEnabled,
                                onCheckedChange = viewModel::setSyncEnabled,
                            )
                        },
                    )
                    if (settings.syncEnabled) {
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.settings_sync_interval),
                            subtitle = stringResource(
                                R.string.sync_interval_minutes,
                                settings.syncIntervalMinutes,
                            ),
                            onClick = { showIntervalDialog = true },
                            trailing = {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Sync,
                        title = stringResource(R.string.settings_manual_sync),
                        subtitle = lastSyncText?.let {
                            stringResource(R.string.settings_last_sync) + " · " + it
                        } ?: stringResource(R.string.settings_last_sync_never),
                        onClick = viewModel::syncNow,
                        trailing = {
                            if (syncStatus.isLoading) {
                                LoadingIndicator(modifier = Modifier.size(22.dp))
                            } else {
                                Icon(
                                    Icons.Outlined.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.settings_notifications_enabled),
                        subtitle = stringResource(R.string.settings_notifications_desc),
                        trailing = {
                            SpringSwitch(
                                checked = settings.notificationsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    } else {
                                        viewModel.setNotificationsEnabled(enabled)
                                    }
                                },
                            )
                        },
                    )
                }
            }

            // Hidden until unlocked from the theme option; see SettingsViewModel.
            if (debugUnlocked) item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.settings_debug_title),
                        subtitle = stringResource(R.string.settings_debug_desc),
                        onClick = onOpenDebug,
                        trailing = {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_about)) }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.settings_about),
                        onClick = { aboutExpanded = !aboutExpanded },
                        trailing = {
                            val rotation by animateFloatAsState(
                                targetValue = if (aboutExpanded) 90f else 0f,
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                label = "aboutChevron",
                            )
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = rotation
                                },
                            )
                        },
                    )
                    AnimatedVisibility(
                        visible = aboutExpanded,
                        enter = expandVertically(
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ) + fadeIn(
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                        exit = shrinkVertically(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        ) + fadeOut(
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_about_text),
                            modifier = Modifier.padding(
                                start = 66.dp,
                                end = 16.dp,
                                bottom = 14.dp,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        title = stringResource(R.string.settings_logout),
                        destructive = true,
                        onClick = { showLogoutDialog = true },
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_logout_title)) },
            text = { Text(stringResource(R.string.settings_logout_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_logout_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showIntervalDialog) {
        // Choosing an interval applies it straight away, so there is no save step.
        val intervals = SettingsRepository.SYNC_INTERVALS
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.settings_interval_dialog_title)) },
            text = {
                // A list rather than segments: "120 minutes" does not fit four segments in a dialog.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    intervals.forEach { interval ->
                        SelectionRow(
                            label = stringResource(R.string.sync_interval_minutes, interval),
                            supporting = null,
                            selected = settings.syncIntervalMinutes == interval,
                            onClick = {
                                viewModel.setSyncInterval(interval)
                                showIntervalDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun AccountHeader(
    displayName: String,
    studentName: String,
    schoolName: String,
    school: String,
    username: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.take(1).uppercase().ifBlank { "D" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = studentName.ifBlank { displayName }.ifBlank {
                    stringResource(R.string.app_name)
                },
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = schoolName.ifBlank { school },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (username.isNotBlank() || school.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (username.isNotBlank()) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            text = username,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (school.isNotBlank()) {
                        Spacer(Modifier.size(10.dp))
                        Icon(
                            Icons.Outlined.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            text = school,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpringSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val animationsEnabled = motionAnimationsEnabled()
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(checked) {
        if (!animationsEnabled) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        scale.snapTo(0.9f)
        scale.animateTo(1f, animationSpec = spring)
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
        content = content,
    )
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    subtitleMaxLines: Int = 2,
    titleMaxLines: Int = 2,
    trailing: @Composable (() -> Unit)? = null,
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconContainer = if (destructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val iconColor = if (destructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    // Built on ListItem so spacing, touch targets and TalkBack behave like other list
    // items. The row gives the same spring press feedback as the timetable blocks.
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(clickModifier),
        supportingContent = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = iconContainer,
                contentColor = iconColor,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private val ThemeOptions = listOf(
    ThemeMode.SYSTEM,
    ThemeMode.LIGHT,
    ThemeMode.DARK,
)

@Composable
private fun AppearanceGroup(
    settings: AppSettings,
    currentLanguage: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onMotionStyle: (MotionStyle) -> Unit,
    onStartView: (StartView) -> Unit,
    onSubjectColorMode: (SubjectColorMode) -> Unit,
    onTextScale: (TextScale) -> Unit,
) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val showAccentPicker = !dynamicAvailable || !settings.dynamicColor
    val dark = resolveDarkTheme(settings.themeMode)

    SettingsGroup {
        AppearanceOption(
            icon = Icons.Outlined.DarkMode,
            title = stringResource(R.string.settings_theme),
        ) {
            SegmentChoice(
                options = ThemeOptions.map { mode ->
                    mode to when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    }
                },
                selected = settings.themeMode,
                onSelect = onThemeMode,
            )
        }

        if (dynamicAvailable) {
            AppearanceDivider()
            SettingsRow(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_desc),
                trailing = {
                    SpringSwitch(
                        checked = settings.dynamicColor,
                        onCheckedChange = onDynamicColor,
                    )
                },
            )
        }

        AnimatedVisibility(
            visible = showAccentPicker,
            enter = expandVertically(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ) + fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ) + fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Column {
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_accent),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        AccentColor.entries.forEach { accent ->
                            val (seed, onSeed) = accentSwatchColors(accent, dark)
                            AccentSwatch(
                                color = seed,
                                onColor = onSeed,
                                label = accentLabel(accent),
                                selected = settings.accent == accent,
                                onClick = { onAccent(accent) },
                            )
                        }
                    }
                }
            }
        }

        AppearanceDivider()
        AppearanceOption(
            icon = Icons.Outlined.Speed,
            title = stringResource(R.string.settings_motion),
        ) {
            SegmentChoice(
                options = listOf(
                    MotionStyle.EXPRESSIVE to stringResource(R.string.motion_expressive),
                    MotionStyle.CALM to stringResource(R.string.motion_calm),
                ),
                selected = settings.motionStyle,
                onSelect = onMotionStyle,
            )
        }

        AppearanceDivider()
        AppearanceOption(
            icon = Icons.Outlined.Today,
            title = stringResource(R.string.settings_start_view),
        ) {
            SegmentChoice(
                options = listOf(
                    StartView.DAY to stringResource(R.string.timetable_day),
                    StartView.WEEK to stringResource(R.string.timetable_week),
                ),
                selected = settings.startView,
                onSelect = onStartView,
            )
        }

        AppearanceDivider()
        AppearanceOption(
            icon = Icons.Outlined.ColorLens,
            title = stringResource(R.string.settings_subject_colors),
        ) {
            SegmentChoice(
                options = listOf(
                    SubjectColorMode.ACCENT_HUES to stringResource(R.string.subject_colors_accent),
                    SubjectColorMode.ROLES to stringResource(R.string.subject_colors_roles),
                ),
                selected = settings.subjectColorMode,
                onSelect = onSubjectColorMode,
            )
        }

        AppearanceDivider()
        AppearanceOption(
            icon = Icons.Outlined.TextFields,
            title = stringResource(R.string.settings_text_size),
        ) {
            SegmentChoice(
                options = TextScale.entries.map { scale ->
                    scale to when (scale) {
                        TextScale.STANDARD -> stringResource(R.string.text_scale_standard)
                        TextScale.LARGE -> stringResource(R.string.text_scale_large)
                        TextScale.EXTRA_LARGE -> stringResource(R.string.text_scale_extra_large)
                    }
                },
                selected = settings.textScale,
                onSelect = onTextScale,
            )
        }

        AppearanceDivider()
        AppearanceOption(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.settings_language),
        ) {
            SegmentChoice(
                options = listOf(
                    AppLanguage.GERMAN to stringResource(R.string.language_german),
                    AppLanguage.ENGLISH to stringResource(R.string.language_english),
                ),
                selected = currentLanguage,
                onSelect = onLanguage,
            )
        }
    }
}

@Composable
private fun accentLabel(accent: AccentColor): String = when (accent) {
    AccentColor.OCEAN -> stringResource(R.string.accent_ocean)
    AccentColor.FOREST -> stringResource(R.string.accent_forest)
    AccentColor.LAVENDER -> stringResource(R.string.accent_lavender)
    AccentColor.SUNSET -> stringResource(R.string.accent_sunset)
    AccentColor.ROSE -> stringResource(R.string.accent_rose)
}

@Composable
private fun AppearanceOption(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        content()
    }
}

@Composable
private fun AppearanceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    onColor: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.84f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "accentSwatchScale",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "accentSwatchRing",
    )
    Column(
        modifier = Modifier
            .width(64.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(width = 2.dp, color = ring, shape = CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = onColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

