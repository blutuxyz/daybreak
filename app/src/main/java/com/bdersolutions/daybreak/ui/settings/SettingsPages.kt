package com.bdersolutions.daybreak.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.MotionStyle
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.data.settings.SubjectColorMode
import com.bdersolutions.daybreak.data.settings.TextScale
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.ui.components.SelectionRow
import com.bdersolutions.daybreak.ui.theme.accentSwatchColors
import com.bdersolutions.daybreak.ui.theme.resolveDarkTheme
import com.bdersolutions.daybreak.ui.toUserMessage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The screens that open from the settings list. Each one slides in over it. */
enum class SettingsPage {
    APPEARANCE,
    TIMETABLE,
    LESSON_COLORS,
    LANGUAGE,
    SYNC,
    NOTIFICATIONS,
    UPDATES,
    ABOUT,
    LEGAL,
}

/** Shows the screen of [page]. */
@Composable
fun SettingsPageHost(
    page: SettingsPage,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    when (page) {
        SettingsPage.APPEARANCE -> AppearanceScreen(viewModel, onBack)
        SettingsPage.TIMETABLE -> TimetableSettingsScreen(viewModel, onBack)
        SettingsPage.LESSON_COLORS -> LessonColorsScreen(viewModel = viewModel, onBack = onBack)
        SettingsPage.LANGUAGE -> LanguageScreen(viewModel, onBack)
        SettingsPage.SYNC -> SyncScreen(viewModel, onBack)
        SettingsPage.NOTIFICATIONS -> NotificationsScreen(viewModel, onBack)
        SettingsPage.UPDATES -> UpdatesScreen(viewModel, onBack)
        SettingsPage.ABOUT -> AboutScreen(onBack = onBack)
        SettingsPage.LEGAL -> LegalScreen(onBack = onBack)
    }
}

/** The frame every settings screen shares: a collapsing title bar with a back arrow, and a list. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title) },
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
            content = content,
        )
    }
}

/** The language the app is showing right now, which is not always the stored choice. */
@Composable
internal fun currentAppLanguage(): AppLanguage {
    val context = LocalContext.current
    return if (context.resources.configuration.locales[0].language.equals("de", ignoreCase = true)) {
        AppLanguage.GERMAN
    } else {
        AppLanguage.ENGLISH
    }
}

@Composable
internal fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
internal fun startViewLabel(view: StartView): String = when (view) {
    StartView.DAY -> stringResource(R.string.timetable_day)
    StartView.WEEK -> stringResource(R.string.timetable_week)
}

@Composable
internal fun subjectColorModeLabel(mode: SubjectColorMode): String = when (mode) {
    SubjectColorMode.ACCENT_HUES -> stringResource(R.string.subject_colors_accent)
    SubjectColorMode.ROLES -> stringResource(R.string.subject_colors_roles)
}

@Composable
internal fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.GERMAN -> stringResource(R.string.language_german)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
}

/** Theme, colours, text size and motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val showAccentPicker = !dynamicAvailable || !settings.dynamicColor
    val dark = resolveDarkTheme(settings.themeMode)

    SettingsPageScaffold(title = stringResource(R.string.settings_appearance), onBack = onBack) {
        item {
            SettingsGroup {
                AppearanceOption(
                    icon = Icons.Outlined.DarkMode,
                    title = stringResource(R.string.settings_theme),
                ) {
                    SegmentChoice(
                        options = ThemeOptions.map { mode -> mode to themeLabel(mode) },
                        selected = settings.themeMode,
                        onSelect = { mode ->
                            // Ten taps on "System" reveal the debug log (see SettingsViewModel).
                            if (mode == ThemeMode.SYSTEM) viewModel.onSystemThemeTapped()
                            viewModel.setThemeMode(mode)
                        },
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
                                onCheckedChange = viewModel::setDynamicColor,
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
                                        onClick = { viewModel.setAccent(accent) },
                                    )
                                }
                            }
                        }
                    }
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
                        onSelect = viewModel::setTextScale,
                    )
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
                        onSelect = viewModel::setMotionStyle,
                    )
                }
            }
        }
    }
}

/** What the timetable opens with and how its subjects are coloured. */
@Composable
fun TimetableSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsPageScaffold(title = stringResource(R.string.settings_timetable), onBack = onBack) {
        item {
            SettingsGroup {
                AppearanceOption(
                    icon = Icons.Outlined.Today,
                    title = stringResource(R.string.settings_start_view),
                ) {
                    SegmentChoice(
                        options = StartView.entries.map { it to startViewLabel(it) },
                        selected = settings.startView,
                        onSelect = viewModel::setStartView,
                    )
                }
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.ColorLens,
                    title = stringResource(R.string.settings_subject_colors),
                ) {
                    SegmentChoice(
                        options = SubjectColorMode.entries.map { it to subjectColorModeLabel(it) },
                        selected = settings.subjectColorMode,
                        onSelect = viewModel::setSubjectColorMode,
                    )
                }
            }
        }
    }
}

/** The app language. Choosing one applies it straight away. */
@Composable
fun LanguageScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val current = currentAppLanguage()

    SettingsPageScaffold(title = stringResource(R.string.settings_language), onBack = onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLanguage.entries.forEach { language ->
                    SelectionRow(
                        label = languageLabel(language),
                        supporting = null,
                        selected = current == language,
                        onClick = { viewModel.setLanguage(language) },
                    )
                }
            }
        }
    }
}

/** Automatic sync, how often it runs, and a button to sync now. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
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

    SettingsPageScaffold(
        title = stringResource(R.string.settings_sync),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
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
                AnimatedVisibility(
                    visible = settings.syncEnabled,
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
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.settings_sync_interval),
                        ) {
                            // A list rather than segments: "120 minutes" does not fit four segments.
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SettingsRepository.SYNC_INTERVALS.forEach { interval ->
                                    SelectionRow(
                                        label = stringResource(R.string.sync_interval_minutes, interval),
                                        supporting = null,
                                        selected = settings.syncIntervalMinutes == interval,
                                        onClick = { viewModel.setSyncInterval(interval) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup {
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
    }
}

/** Change notifications. Turning them on asks for the notification permission when needed. */
@Composable
fun NotificationsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
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

    SettingsPageScaffold(
        title = stringResource(R.string.settings_notifications),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
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
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setNotificationsEnabled(enabled)
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}
