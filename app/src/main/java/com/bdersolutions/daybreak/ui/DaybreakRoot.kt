package com.bdersolutions.daybreak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.legal.TermsAcceptance
import com.bdersolutions.daybreak.data.notices.UpdateGate
import com.bdersolutions.daybreak.ui.notices.ServerMessageDialog
import com.bdersolutions.daybreak.ui.update.UpdateRequiredRoute
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.data.branding.AppNaming
import com.bdersolutions.daybreak.data.support.PaymentCheck
import com.bdersolutions.daybreak.data.support.SupportPromptPolicy
import com.bdersolutions.daybreak.ui.support.SupportSheet
import com.bdersolutions.daybreak.ui.support.openSupportPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.ui.absences.AbsencesScreen
import com.bdersolutions.daybreak.ui.absences.AbsencesViewModel
import com.bdersolutions.daybreak.ui.debug.DebugScreen
import com.bdersolutions.daybreak.ui.debug.DebugViewModel
import com.bdersolutions.daybreak.ui.login.LoginScreen
import com.bdersolutions.daybreak.ui.legal.TermsGateScreen
import com.bdersolutions.daybreak.ui.login.LoginViewModel
import com.bdersolutions.daybreak.data.update.ReleaseInfo
import com.bdersolutions.daybreak.ui.account.AccountScreen
import com.bdersolutions.daybreak.ui.account.AccountViewModel
import com.bdersolutions.daybreak.ui.messages.MessagesScreen
import com.bdersolutions.daybreak.ui.messages.MessagesViewModel
import com.bdersolutions.daybreak.ui.settings.SettingsPage
import com.bdersolutions.daybreak.ui.upcoming.UpcomingScreen
import com.bdersolutions.daybreak.ui.upcoming.UpcomingViewModel
import com.bdersolutions.daybreak.ui.settings.WhatsNewSheet
import com.bdersolutions.daybreak.ui.settings.SettingsPageHost
import com.bdersolutions.daybreak.ui.settings.SettingsScreen
import com.bdersolutions.daybreak.ui.settings.SettingsViewModel
import com.bdersolutions.daybreak.ui.timetable.TimetableScreen
import com.bdersolutions.daybreak.ui.components.LocalAppTitle
import com.bdersolutions.daybreak.ui.components.rememberMorphCorner
import com.bdersolutions.daybreak.ui.theme.DaybreakTheme
import com.bdersolutions.daybreak.ui.timetable.TimetableViewModel
import java.time.LocalDate
import java.util.Locale

/** What the app shows, in order: the terms first, then the update gate, then the app itself. */
private enum class AppStage { TERMS, UPDATE_REQUIRED, APP }

/**
 * Entry point of the Compose tree. Settings are observed here so that theme, accent,
 * dark mode and motion update live when the user changes them.
 */
@Composable
fun DaybreakApp(
    container: AppContainer,
    initialDate: LocalDate? = null,
    openUpdates: Boolean = false,
    startMode: Int? = null,
    openSearch: Boolean = false,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val acceptedVersion by container.termsStore.acceptedVersion.collectAsStateWithLifecycle()
    val termsAccepted = TermsAcceptance.covers(acceptedVersion)
    val supportPaid by container.supportStore.paid.collectAsStateWithLifecycle()
    val appTitle = AppNaming.resolve(supportPaid, settings.appNamePreset, settings.customAppName)
    // The minimum version comes from the last answer of the update server, so it also holds offline.
    val serverStatus by container.serverNotices.status.collectAsStateWithLifecycle()
    val updateRequired = container.updateManager.isConfigured && UpdateGate.isRequired(
        installedVersionCode = container.updateManager.currentVersionCode,
        requiredVersionCode = serverStatus?.minVersionCode,
    )
    val stage = when {
        !termsAccepted -> AppStage.TERMS
        updateRequired -> AppStage.UPDATE_REQUIRED
        else -> AppStage.APP
    }

    // Like every other network call, the server is asked only after the terms are accepted, and again
    // each time the app comes back to the front, so a new minimum version reaches a phone that is open.
    val scope = rememberCoroutineScope()
    LaunchedEffect(termsAccepted) {
        if (termsAccepted) container.serverNotices.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (termsAccepted) scope.launch { container.serverNotices.refresh() }
    }

    CompositionLocalProvider(
        LocalHaptics provides container.haptics,
        LocalAppTitle provides appTitle,
    ) {
        DaybreakTheme(settings = settings) {
            // Nothing of the app runs behind the gates: no login screen, no sync prompts, no update check.
            Crossfade(targetState = stage, label = "appStage") { current ->
                when (current) {
                    AppStage.TERMS -> TermsGateScreen(
                        isUpdate = TermsAcceptance.isUpdate(acceptedVersion),
                        onAccept = { container.termsStore.accept() },
                        onLanguageChange = { language ->
                            container.settingsRepository.setLanguage(language)
                            container.lessonNotificationManager.ensureChannel()
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(Locale.forLanguageTag(language.tag).toLanguageTag()),
                            )
                        },
                    )

                    AppStage.UPDATE_REQUIRED -> UpdateRequiredRoute(
                        manager = container.updateManager,
                        requiredVersionName = serverStatus?.minVersionName ?: "",
                    )

                    AppStage.APP -> DaybreakRoot(
                        container = container,
                        initialDate = initialDate,
                        openUpdates = openUpdates,
                        startMode = startMode,
                        openSearch = openSearch,
                    )
                }
            }
            if (stage == AppStage.APP) ServerMessageDialog(notices = container.serverNotices)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DaybreakRoot(
    container: AppContainer,
    initialDate: LocalDate? = null,
    openUpdates: Boolean = false,
    startMode: Int? = null,
    openSearch: Boolean = false,
) {
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
    // A swell when signing in succeeds. Opening the app while already signed in stays silent.
    val haptics = LocalHaptics.current
    var wasLoggedOut by remember { mutableStateOf(session == null) }
    LaunchedEffect(session == null) {
        if (session != null && wasLoggedOut) haptics.perform(HapticEvent.LOGIN_SUCCESS)
        wasLoggedOut = session == null
    }
    AnimatedContent(
        targetState = session,
        contentKey = { it?.studentId ?: 0L },
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (
                fadeIn(motion.slowEffectsSpec<Float>()) +
                    scaleIn(
                        animationSpec = motion.defaultSpatialSpec(),
                        initialScale = 0.94f,
                    )
                )
                .togetherWith(
                    fadeOut(motion.fastEffectsSpec<Float>()) +
                        scaleOut(
                            animationSpec = motion.fastSpatialSpec(),
                            targetScale = 1.02f,
                        ),
                )
                .using(SizeTransform(clip = false))
        },
        label = "session",
    ) { currentSession ->
        if (currentSession == null) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.Factory(container),
            )
            LoginScreen(viewModel = loginViewModel)
        } else {
            LoggedInContent(
                container = container,
                session = currentSession,
                initialDate = initialDate,
                openUpdates = openUpdates,
                startMode = startMode,
                openSearch = openSearch,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoggedInContent(
    container: AppContainer,
    session: DaybreakSession,
    initialDate: LocalDate?,
    openUpdates: Boolean,
    startMode: Int?,
    openSearch: Boolean,
) {
    // Tapping the "update available" notification lands on the Updates screen.
    var selectedTab by rememberSaveable { mutableIntStateOf(if (openUpdates) 2 else 0) }
    var showDebug by rememberSaveable { mutableStateOf(false) }
    // The settings screen that is open over the list, if any. Debug has its own flag above.
    var settingsPage by rememberSaveable {
        mutableStateOf<SettingsPage?>(if (openUpdates) SettingsPage.UPDATES else null)
    }
    // Kept after closing, so the screen can still be drawn while it slides out.
    var shownPage by remember { mutableStateOf(settingsPage) }
    if (settingsPage != null) shownPage = settingsPage
    // Pages that open from the timetable and the settings list, with the same slide.
    var mainPage by rememberSaveable { mutableStateOf<MainPage?>(null) }
    var shownMainPage by remember { mutableStateOf(mainPage) }
    if (mainPage != null) shownMainPage = mainPage
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            container.settingsRepository.setNotificationsEnabled(false)
        }
    }

    // After an update, show what changed; and look for a new version when it is time to.
    var whatsNew by remember { mutableStateOf<ReleaseInfo?>(null) }
    LaunchedEffect(Unit) {
        val updates = container.updateManager
        if (!updates.isConfigured) return@LaunchedEffect
        whatsNew = updates.whatsNew()
        if (settings.autoCheckUpdates && updates.isCheckDue()) updates.check()
    }

    // A rare, friendly request to support Daybreak on Ko-fi (see SupportPromptPolicy). It waits a few
    // seconds so it never covers the timetable on opening, and never appears with the update notes.
    var showSupport by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session.studentId) {
        val today = LocalDate.now().toEpochDay()
        val state = container.supportStore.recordOpen(today)
        if (SupportPromptPolicy.shouldShow(state, today)) {
            delay(5_000)
            if (whatsNew == null) {
                container.supportStore.markShown(today)
                showSupport = true
            }
        }
    }

    LaunchedEffect(session.studentId) {
        if (Build.VERSION.SDK_INT >= 33 &&
            container.settingsRepository.current().notificationsEnabled &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = showDebug) {
        haptics.perform(HapticEvent.BACK)
        showDebug = false
    }

    if (showDebug) {
        val debugViewModel: DebugViewModel = viewModel(factory = DebugViewModel.Factory(container))
        DebugScreen(
            viewModel = debugViewModel,
            onBack = {
                haptics.perform(HapticEvent.BACK)
                showDebug = false
            },
        )
        return
    }

    val timetableViewModel: TimetableViewModel = viewModel(
        key = "timetable_${session.studentId}",
        factory = TimetableViewModel.Factory(container),
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings_${session.studentId}",
        factory = SettingsViewModel.Factory(container),
    )

    BackHandler(enabled = settingsPage != null) {
        haptics.perform(HapticEvent.BACK)
        settingsPage = null
    }
    BackHandler(enabled = mainPage != null) {
        haptics.perform(HapticEvent.BACK)
        mainPage = null
    }

    val motion = MaterialTheme.motionScheme
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                DaybreakBottomBar(
                    selectedTab = selectedTab,
                    onSelect = {
                        if (it != selectedTab) haptics.perform(HapticEvent.TAB_SWITCH)
                        selectedTab = it
                    },
                )
            },
        ) { innerPadding ->
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    val slide = motion.defaultSpatialSpec<IntOffset>()
                    val scale = motion.fastSpatialSpec<Float>()
                    val fade = motion.fastEffectsSpec<Float>()
                    (
                        slideInHorizontally(slide) { width -> direction * width / 8 } +
                            fadeIn(fade) +
                            scaleIn(scale, initialScale = 0.98f)
                        )
                        .togetherWith(
                            slideOutHorizontally(slide) { width -> -direction * width / 8 } +
                                fadeOut(fade),
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "tabContent",
            ) { tab ->
                when (tab) {
                    0 -> TimetableScreen(
                        viewModel = timetableViewModel,
                        initialDate = initialDate,
                        initialMode = startMode
                            ?: if (initialDate == null && settings.startView == StartView.WEEK) 1 else 0,
                        openSearchOnStart = openSearch,
                        onOpenUpcoming = { mainPage = MainPage.UPCOMING },
                    )

                    1 -> {
                        val absencesViewModel: AbsencesViewModel = viewModel(
                            key = "absences_${session.studentId}",
                            factory = AbsencesViewModel.Factory(container),
                        )
                        AbsencesScreen(viewModel = absencesViewModel)
                    }

                    else -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onOpenDebug = { showDebug = true },
                        onOpenPage = { settingsPage = it },
                        onOpenAccount = { mainPage = MainPage.ACCOUNT },
                        onOpenMessages = { mainPage = MainPage.MESSAGES },
                    )
                }
            }
        }

        // Settings sub-screens slide in from the right, stock style, with no scaling.
        AnimatedVisibility(
            visible = settingsPage != null,
            modifier = Modifier.fillMaxSize(),
            enter = subScreenEnter,
            exit = subScreenExit,
        ) {
            shownPage?.let { page ->
                SettingsPageHost(
                    page = page,
                    viewModel = settingsViewModel,
                    onBack = {
                        haptics.perform(HapticEvent.BACK)
                        settingsPage = null
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = mainPage != null,
            modifier = Modifier.fillMaxSize(),
            enter = subScreenEnter,
            exit = subScreenExit,
        ) {
            when (shownMainPage) {
                MainPage.UPCOMING -> UpcomingScreen(
                    viewModel = viewModel(
                        key = "upcoming_${session.studentId}",
                        factory = UpcomingViewModel.Factory(container),
                    ),
                    onBack = {
                        haptics.perform(HapticEvent.BACK)
                        mainPage = null
                    },
                )

                MainPage.ACCOUNT -> AccountScreen(
                    session = session,
                    viewModel = viewModel(
                        key = "account_${session.studentId}",
                        factory = AccountViewModel.Factory(container),
                    ),
                    onBack = {
                        haptics.perform(HapticEvent.BACK)
                        mainPage = null
                    },
                )

                MainPage.MESSAGES -> MessagesScreen(
                    viewModel = viewModel(
                        key = "messages_${session.studentId}",
                        factory = MessagesViewModel.Factory(container),
                    ),
                    onBack = {
                        haptics.perform(HapticEvent.BACK)
                        mainPage = null
                    },
                )

                null -> Unit
            }
        }

        if (showSupport) {
            val supportApi = container.supportApi
            SupportSheet(
                // Hiding the popup is paid where there is a server to confirm it (see SupportSheet).
                unlockCode = remember { supportApi?.let { container.supportStore.unlockCode() } },
                onSupport = {
                    // Going to Ko-fi does not hide the popup: only a confirmed payment does.
                    showSupport = false
                    openSupportPage(context)
                },
                onLater = { showSupport = false },
                onNever = {
                    container.supportStore.optOut()
                    showSupport = false
                },
                onCheckPayment = {
                    supportApi?.check(container.supportStore.unlockCode()) ?: PaymentCheck.UNAVAILABLE
                },
                onPaid = { container.supportStore.markPaid() },
            )
        }

        whatsNew?.let { release ->
            WhatsNewSheet(
                release = release,
                onDismiss = {
                    container.updateManager.markWhatsNewSeen()
                    whatsNew = null
                },
            )
        }
    }
}

/** Pages that are not settings but open over the tabs the same way. */
private enum class MainPage { UPCOMING, ACCOUNT, MESSAGES }

/** Stock slide used by the settings sub-screens. Plain tweens, so there is no spring overshoot. */
private val subScreenEnter = slideInHorizontally(
    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
) { width -> width }

private val subScreenExit = slideOutHorizontally(
    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
) { width -> width }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DaybreakBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    FlexibleBottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BottomBarItem(
            selected = selectedTab == 0,
            icon = Icons.Outlined.CalendarMonth,
            selectedIcon = Icons.Filled.CalendarMonth,
            label = stringResource(R.string.nav_timetable),
            onClick = { onSelect(0) },
            modifier = Modifier.weight(1f),
        )
        BottomBarItem(
            selected = selectedTab == 1,
            icon = Icons.Outlined.EventBusy,
            selectedIcon = Icons.Filled.EventBusy,
            label = stringResource(R.string.nav_absences),
            onClick = { onSelect(1) },
            modifier = Modifier.weight(1f),
        )
        BottomBarItem(
            selected = selectedTab == 2,
            icon = Icons.Outlined.MoreHoriz,
            selectedIcon = Icons.Filled.MoreHoriz,
            label = stringResource(R.string.nav_settings),
            onClick = { onSelect(2) },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.BottomBarItem(
    selected: Boolean,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "bottomBarContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "bottomBarContent",
    )
    val iconFade = motion.fastEffectsSpec<Float>()
    val iconScale = motion.defaultSpatialSpec<Float>()
    val interactionSource = remember { MutableInteractionSource() }
    val corner by rememberMorphCorner(
        interactionSource = interactionSource,
        selected = selected,
        selectedRadius = 32.dp,
        unselectedRadius = 22.dp,
        pressedRadius = 14.dp,
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (
                        fadeIn(iconFade) +
                            scaleIn(iconScale, initialScale = 0.7f)
                        )
                        .togetherWith(
                            fadeOut(iconFade) +
                                scaleOut(iconScale, targetScale = 0.7f),
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "bottomBarIcon",
            ) { isSelected ->
                Icon(
                    imageVector = if (isSelected) selectedIcon else icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
