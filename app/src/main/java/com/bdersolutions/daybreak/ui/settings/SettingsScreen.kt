package com.bdersolutions.daybreak.ui.settings

import com.bdersolutions.daybreak.ui.components.SegmentedButton
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.haptics.HapticSlider
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SystemUpdate
import com.bdersolutions.daybreak.data.update.UpdateState
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.draw.alpha
import com.bdersolutions.daybreak.ui.components.LocalAppTitle
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.account.Entitlement
import com.bdersolutions.daybreak.ui.account.LocalPlans
import com.bdersolutions.daybreak.ui.account.LockIcon
import com.bdersolutions.daybreak.ui.account.TrialBanner
import com.bdersolutions.daybreak.ui.account.allowed
import com.bdersolutions.daybreak.ui.account.label
import com.bdersolutions.daybreak.ui.account.lockedAlpha
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.bdersolutions.daybreak.ui.theme.accentSwatchColors
import com.bdersolutions.daybreak.ui.theme.resolveDarkTheme
import com.bdersolutions.daybreak.ui.toUserMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenDebug: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenDaybreakAccount: () -> Unit,
    onOpenMessages: () -> Unit,
    showTrialBanner: Boolean = false,
    trialBusy: Boolean = false,
    trialError: AccountError? = null,
    onAcceptTrial: () -> Unit = {},
    onOpenAssistant: (() -> Unit)? = null,
    onOpenTutorial: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val debugUnlocked by viewModel.debugUnlocked.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val currentLanguage = currentAppLanguage()
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updatesSummary = when (val update = updateState) {
        is UpdateState.Available -> stringResource(R.string.settings_updates_available, update.release.versionName)
        is UpdateState.Ready -> stringResource(R.string.settings_updates_available, update.release.versionName)
        else -> stringResource(R.string.settings_updates_current, viewModel.currentVersionName)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmingLogout by remember { mutableStateOf(false) }
    val plans = LocalPlans.current
    val access by viewModel.access.collectAsStateWithLifecycle()
    val appTitle = LocalAppTitle.current
    val listState = rememberLazyListState()

    // Back closes the confirmation first, and the card is scrolled into view when it opens.
    BackHandler(enabled = confirmingLogout) { confirmingLogout = false }
    LaunchedEffect(confirmingLogout) {
        if (confirmingLogout) {
            // The card grows over a few frames, so follow it to the end of the list while it does.
            repeat(5) {
                delay(100)
                listState.animateScrollBy(4000f)
            }
        }
    }

    val on = stringResource(R.string.settings_value_on)
    val off = stringResource(R.string.settings_value_off)
    val appearanceSummary = listOf(
        themeLabel(settings.themeMode),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.dynamicColor) {
            stringResource(R.string.settings_dynamic_color)
        } else {
            accentLabel(settings.accent)
        },
    ).joinToString(" · ")
    val hapticsSummary = if (settings.haptics.enabled) {
        hapticPresetLabel(settings.haptics.preset) + " · " +
            stringResource(R.string.haptics_percent, kotlin.math.round(settings.haptics.intensity * 100f).toInt())
    } else {
        off
    }
    val timetableSummary = stringResource(R.string.settings_start_view) + ": " +
        startViewLabel(settings.startView)
    val syncSummary = if (settings.syncEnabled) {
        stringResource(R.string.settings_sync_every, settings.syncIntervalMinutes)
    } else {
        off
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
            state = listState,
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
            // The thank-you offer for old Ko-fi supporters stays pinned here for two weeks after the popup was dismissed.
            item(key = "trialBanner") {
                AnimatedVisibility(
                    visible = showTrialBanner,
                    enter = fadeIn() + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut() + shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                ) {
                    TrialBanner(busy = trialBusy, error = trialError, onAccept = onAcceptTrial, modifier = Modifier.padding(bottom = 4.dp))
                }
            }

            // The plan extras: unlocked by Plus (and kept for old supporters), shown locked before.
            item {
                PlanExtras(
                    appTitle = appTitle,
                    onOpenAppName = { onOpenPage(SettingsPage.APP_NAME) },
                    onOpenAppIcon = { onOpenPage(SettingsPage.APP_ICON) },
                    onOpenCalendarExport = { onOpenPage(SettingsPage.CALENDAR_EXPORT) },
                    onLocked = { plans.show(it) },
                )
            }

            item { GroupLabel(stringResource(R.string.more_group_profile)) }
            item {
                SettingsGroup {
                    NavigationRow(
                        icon = Icons.Outlined.WorkspacePremium,
                        title = stringResource(R.string.v2_account_row),
                        subtitle = stringResource(R.string.v2_account_row_desc, access.plan.label()),
                        onClick = onOpenDaybreakAccount,
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.AccountCircle,
                        title = stringResource(R.string.settings_account),
                        subtitle = stringResource(R.string.settings_account_desc),
                        onClick = onOpenAccount,
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.MailOutline,
                        title = stringResource(R.string.settings_messages),
                        subtitle = stringResource(R.string.settings_messages_desc),
                        onClick = onOpenMessages,
                    )
                    if (onOpenAssistant != null) {
                        SettingsDivider()
                        NavigationRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = stringResource(R.string.assistant_title),
                            subtitle = stringResource(R.string.assistant_row_desc),
                            onClick = onOpenAssistant,
                        )
                    }
                }
            }

            // Every setting lives on its own screen; this list only leads there.
            item { GroupLabel(stringResource(R.string.more_group_settings)) }
            item {
                SettingsGroup {
                    NavigationRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.settings_appearance),
                        subtitle = appearanceSummary,
                        onClick = { onOpenPage(SettingsPage.APPEARANCE) },
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.CalendarMonth,
                        title = stringResource(R.string.settings_timetable),
                        subtitle = timetableSummary,
                        onClick = { onOpenPage(SettingsPage.TIMETABLE) },
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.Brush,
                        title = stringResource(R.string.settings_lesson_colors),
                        subtitle = stringResource(R.string.settings_lesson_colors_desc),
                        onClick = { onOpenPage(SettingsPage.LESSON_COLORS) },
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = languageLabel(currentLanguage),
                        onClick = { onOpenPage(SettingsPage.LANGUAGE) },
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.CloudSync,
                        title = stringResource(R.string.settings_sync),
                        subtitle = syncSummary,
                        onClick = { onOpenPage(SettingsPage.SYNC) },
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.settings_notifications),
                        subtitle = if (settings.notificationsEnabled) on else off,
                        onClick = { onOpenPage(SettingsPage.NOTIFICATIONS) },
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.Widgets,
                        title = stringResource(R.string.settings_widget),
                        subtitle = stringResource(
                            R.string.settings_widget_summary,
                            widgetThemeLabel(settings.widget.theme),
                            settings.widget.backgroundOpacity,
                        ),
                        onClick = { onOpenPage(SettingsPage.WIDGET) },
                    )
                    SettingsDivider()
                    LockableNavigationRow(
                        entitlement = Entitlement.HAPTICS_STUDIO,
                        icon = Icons.Outlined.Vibration,
                        title = stringResource(R.string.settings_haptics),
                        subtitle = hapticsSummary,
                        onClick = { onOpenPage(SettingsPage.HAPTICS) },
                        onLocked = { plans.show(it) },
                    )
                }
            }

            item { GroupLabel(stringResource(R.string.more_group_other)) }
            item {
                SettingsGroup {
                    // Hidden until unlocked from the theme option; see SettingsViewModel.
                    if (debugUnlocked) {
                        NavigationRow(
                            icon = Icons.Outlined.BugReport,
                            title = stringResource(R.string.settings_debug_title),
                            subtitle = stringResource(R.string.settings_debug_desc),
                            onClick = onOpenDebug,
                        )
                        SettingsDivider()
                    }
                    if (viewModel.updatesConfigured && viewModel.updatesEnabled) {
                        NavigationRow(
                            icon = Icons.Outlined.SystemUpdate,
                            title = stringResource(R.string.settings_updates),
                            subtitle = updatesSummary,
                            onClick = { onOpenPage(SettingsPage.UPDATES) },
                        )
                        SettingsDivider()
                    }
                    NavigationRow(
                        icon = Icons.Outlined.School,
                        title = stringResource(R.string.settings_tutorial),
                        subtitle = stringResource(R.string.settings_tutorial_desc),
                        onClick = onOpenTutorial,
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = stringResource(R.string.settings_legal),
                        onClick = { onOpenPage(SettingsPage.LEGAL) },
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.settings_about),
                        onClick = { onOpenPage(SettingsPage.ABOUT) },
                    )
                    SettingsDivider()
                    NavigationRow(
                        icon = Icons.Outlined.Code,
                        title = stringResource(R.string.settings_licenses),
                        onClick = { onOpenPage(SettingsPage.LICENSES) },
                    )
                }
            }

            item {
                LogoutCard(
                    confirming = confirmingLogout,
                    onAsk = { confirmingLogout = true },
                    onCancel = { confirmingLogout = false },
                    onConfirm = {
                        confirmingLogout = false
                        viewModel.logout()
                    },
                )
            }
        }
    }

}

/**
 * The plan extras on the More page, drawn to stand out from the plain settings: a tinted card with a star
 * heading. Where the plan includes an extra, its row opens its page and carries a "New" badge. Where it does
 * not, the row is dimmed with a lock and opens the plans sheet, so nothing in it works.
 */
@Composable
private fun PlanExtras(
    appTitle: String,
    onOpenAppName: () -> Unit,
    onOpenAppIcon: () -> Unit,
    onOpenCalendarExport: () -> Unit,
    onLocked: (Entitlement) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val anyUnlocked = Entitlement.APP_NAME.allowed() || Entitlement.APP_ICON.allowed() || Entitlement.CALENDAR_EXPORT.allowed()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Star,
                contentDescription = null,
                tint = scheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(R.string.v2_extras_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.tertiary,
            )
        }
        val borderAlpha by animateFloatAsState(if (anyUnlocked) 0.7f else 0.35f, label = "extrasBorder")
        Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.tertiaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, scheme.tertiary.copy(alpha = borderAlpha)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                ExtraRow(
                    entitlement = Entitlement.APP_NAME,
                    icon = Icons.Outlined.DriveFileRenameOutline,
                    title = stringResource(R.string.settings_app_name),
                    unlockedSubtitle = appTitle,
                    onOpen = onOpenAppName,
                    onLocked = onLocked,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    color = scheme.tertiary.copy(alpha = 0.25f),
                )
                ExtraRow(
                    entitlement = Entitlement.APP_ICON,
                    icon = Icons.Outlined.Apps,
                    title = stringResource(R.string.settings_app_icon),
                    unlockedSubtitle = stringResource(R.string.settings_app_icon_desc),
                    onOpen = onOpenAppIcon,
                    onLocked = onLocked,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    color = scheme.tertiary.copy(alpha = 0.25f),
                )
                ExtraRow(
                    entitlement = Entitlement.CALENDAR_EXPORT,
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.settings_calendar_export),
                    unlockedSubtitle = stringResource(R.string.settings_calendar_export_desc),
                    onOpen = onOpenCalendarExport,
                    onLocked = onLocked,
                )
            }
        }
    }
}

/** One row of [PlanExtras]: open when the plan has it, dimmed with a lock (and the plans sheet) when not. */
@Composable
private fun ExtraRow(
    entitlement: Entitlement,
    icon: ImageVector,
    title: String,
    unlockedSubtitle: String,
    onOpen: () -> Unit,
    onLocked: (Entitlement) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val allowed = entitlement.allowed()
    Box(modifier = Modifier.lockedAlpha(!allowed)) {
        SettingsRow(
            icon = icon,
            title = title,
            subtitle = if (allowed) unlockedSubtitle else stringResource(R.string.v2_locked_plan, entitlement.minPlan.label()),
            subtitleMaxLines = 1,
            onClick = if (allowed) onOpen else ({ onLocked(entitlement) }),
            trailing = {
                AnimatedContent(targetState = allowed, label = "extraTrailing") { open ->
                    if (open) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NewBadge()
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LockIcon()
                    }
                }
            },
        )
    }
}

/** A small "New" pill that marks a freshly unlocked extra. */
@Composable
private fun NewBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        modifier = Modifier.padding(end = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.badge_new),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** The small heading above a group of rows on the More page. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

/**
 * The logout row. Tapping it turns the card itself into the confirmation: it flows into a muted
 * red, says what is removed, and offers two equal buttons. No dialog opens over the list.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogoutCard(
    confirming: Boolean,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (confirming) {
            lerp(scheme.surfaceContainerHigh, scheme.errorContainer, 0.55f)
        } else {
            scheme.surfaceContainerLow
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "logoutContainer",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier.fillMaxWidth(),
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
            label = "logoutContent",
        ) { isConfirming ->
            if (isConfirming) {
                LogoutConfirmation(onCancel = onCancel, onConfirm = onConfirm)
            } else {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = stringResource(R.string.settings_logout),
                    destructive = true,
                    onClick = onAsk,
                )
            }
        }
    }
}

@Composable
private fun LogoutConfirmation(
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
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(scheme.error.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_logout_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onErrorContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_logout_message),
                    style = MaterialTheme.typography.bodyMedium,
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
                Text(stringResource(R.string.settings_logout_confirm))
            }
        }
    }
}

/** A [NavigationRow] for a feature of a plan: dimmed with a lock, and the plans sheet instead of the page, where the plan lacks it. */
@Composable
internal fun LockableNavigationRow(
    entitlement: Entitlement,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onLocked: (Entitlement) -> Unit,
) {
    val allowed = entitlement.allowed()
    Box(modifier = Modifier.lockedAlpha(!allowed)) {
        SettingsRow(
            icon = icon,
            title = title,
            subtitle = if (allowed) subtitle else stringResource(R.string.v2_locked_plan, entitlement.minPlan.label()),
            subtitleMaxLines = 1,
            onClick = if (allowed) onClick else ({ onLocked(entitlement) }),
            trailing = {
                AnimatedContent(targetState = allowed, label = "lockableTrailing") { open ->
                    if (open) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LockIcon()
                    }
                }
            },
        )
    }
}

/** A row of the settings list that opens a screen: icon, title, the current value, and a chevron. */
@Composable
internal fun NavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        subtitleMaxLines = 1,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
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
internal fun SpringSwitch(
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
    val haptics = LocalHaptics.current
    Switch(
        checked = checked,
        onCheckedChange = { value ->
            haptics.perform(if (value) HapticEvent.TOGGLE_ON else HapticEvent.TOGGLE_OFF)
            onCheckedChange(value)
        },
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

/**
 * The rounded container of settings rows. [animateSize] is off for groups that animate their own height
 * (an [AnimatedVisibility] inside), so only one animation drives the size and nothing lags behind.
 */
@Composable
internal fun SettingsGroup(
    animateSize: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                if (animateSize) {
                    Modifier.animateContentSize(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    )
                } else {
                    Modifier
                },
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
    subtitleMaxLines: Int = 3,
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
    // items. The press feedback (tint and shrink, no ripple) comes from the theme's indication.
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHaptics.current
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = {
                haptics.perform(HapticEvent.TAP)
                onClick()
            },
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
        modifier = clickModifier,
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

/**
 * A slider for a percentage with its value on the right. It follows the finger locally and hands
 * the value over when the finger lifts, so dragging does not write the settings on every move.
 */
@Composable
internal fun PercentSlider(
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value.toFloat()
    Row(verticalAlignment = Alignment.CenterVertically) {
        HapticSlider(
            value = shown.coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onValueChange(((it / step).roundToInt() * step).coerceIn(range.first, range.last)) }
                dragging = null
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${shown.roundToInt()} %",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}

internal val ThemeOptions = listOf(
    ThemeMode.SYSTEM,
    ThemeMode.LIGHT,
    ThemeMode.DARK,
)

@Composable
internal fun accentLabel(accent: AccentColor): String = when (accent) {
    AccentColor.OCEAN -> stringResource(R.string.accent_ocean)
    AccentColor.FOREST -> stringResource(R.string.accent_forest)
    AccentColor.LAVENDER -> stringResource(R.string.accent_lavender)
    AccentColor.SUNSET -> stringResource(R.string.accent_sunset)
    AccentColor.ROSE -> stringResource(R.string.accent_rose)
}

@Composable
internal fun AppearanceOption(
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
internal fun AppearanceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val haptics = LocalHaptics.current
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = {
                    if (value != selected) haptics.perform(HapticEvent.SELECT)
                    onSelect(value)
                },
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
internal fun AccentSwatch(
    color: Color,
    onColor: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHaptics.current
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
                onClick = {
                    if (!selected) haptics.perform(HapticEvent.SELECT)
                    onClick()
                },
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

