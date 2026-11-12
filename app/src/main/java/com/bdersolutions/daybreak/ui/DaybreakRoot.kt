package com.bdersolutions.daybreak.ui

import com.bdersolutions.daybreak.ui.components.DaybreakBottomBar
import com.bdersolutions.daybreak.ui.components.MainTab
import com.bdersolutions.daybreak.ui.assistant.DawnIntroOverlay
import com.bdersolutions.daybreak.ui.components.Surface
import androidx.compose.runtime.saveable.Saver
import com.bdersolutions.daybreak.ui.components.PushHost
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.bdersolutions.daybreak.data.notices.RemoteFeatures
import com.bdersolutions.daybreak.data.notices.RemoteSwitches
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
import com.bdersolutions.daybreak.ui.timetable.LessonFocus
import com.bdersolutions.daybreak.ui.timetable.TimetableScreen
import com.bdersolutions.daybreak.ui.components.LocalAppTitle
import com.bdersolutions.daybreak.ui.theme.DaybreakTheme
import com.bdersolutions.daybreak.ui.timetable.TimetableViewModel
import com.bdersolutions.daybreak.data.tutorial.TutorialProgress
import com.bdersolutions.daybreak.ui.tutorial.TutorialScreen
import com.bdersolutions.daybreak.ui.assistant.AssistantScreen
import com.bdersolutions.daybreak.ui.assistant.AssistantViewModel
import java.time.LocalDate
import java.util.Locale

/**
 * What the app shows, in order: the terms first, then the update gate, then the introduction (once, for
 * everyone, also for users who were already signed in), then the app itself.
 */
private enum class AppStage { TERMS, UPDATE_REQUIRED, TUTORIAL, APP }

/**
 * Entry point of the Compose tree. Settings are observed here so that theme, accent,
 * dark mode and motion update live when the user changes them.
 */
@Composable
fun DaybreakApp(
    container: AppContainer,
    initialDate: LocalDate? = null,
    openPage: SettingsPage? = null,
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
    // The developer's switches (see RemoteSwitches): each one can turn a part of the app off remotely.
    val remote = serverStatus?.switches ?: RemoteSwitches()
    val updateRequired = container.updateManager.isConfigured && UpdateGate.isRequired(
        installedVersionCode = container.updateManager.currentVersionCode,
        requiredVersionCode = serverStatus?.minVersionCode,
        switches = remote,
    )
    val tutorialVersion by container.tutorialStore.completedVersion.collectAsStateWithLifecycle()
    val stage = when {
        !termsAccepted -> AppStage.TERMS
        updateRequired -> AppStage.UPDATE_REQUIRED
        !TutorialProgress.isDone(tutorialVersion) -> AppStage.TUTORIAL
        else -> AppStage.APP
    }

    // Like every other network call, the server is asked only after the terms are accepted, and again
    // each time the app comes back to the front, so a new minimum version reaches a phone that is open.
    val scope = rememberCoroutineScope()
    // Asked again whenever the phone comes online, so a start without a connection heals itself.
    val online by container.networkMonitor.online.collectAsStateWithLifecycle()
    LaunchedEffect(termsAccepted, online) {
        if (termsAccepted && online) container.serverNotices.refresh()
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

                    AppStage.TUTORIAL -> TutorialScreen(
                        settingsRepository = container.settingsRepository,
                        onFinish = {
                            container.tutorialStore.complete()
                            // New users meet the new name in the introduction, so the notice is not needed for them.
                            container.dawnIntroStore.markShown()
                        },
                    )

                    AppStage.APP -> DaybreakRoot(
                        container = container,
                        initialDate = initialDate,
                        openPage = openPage,
                        startMode = startMode,
                        openSearch = openSearch,
                    )
                }
            }
            if (stage == AppStage.APP && remote.messages) ServerMessageDialog(notices = container.serverNotices)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DaybreakRoot(
    container: AppContainer,
    initialDate: LocalDate? = null,
    openPage: SettingsPage? = null,
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
                openPage = openPage,
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
    openPage: SettingsPage?,
    startMode: Int?,
    openSearch: Boolean,
) {
    // Tapping the "update available" notification lands on the Updates screen.
    var selectedTab by rememberSaveable { mutableIntStateOf(if (openPage != null) MainTab.MORE else MainTab.TIMETABLE) }
    // The full-screen page pushed over the tabs (a settings page, Upcoming, Account, Messages or
    // the debug log), if any. All of them slide in alike through one PushHost.
    var destination by rememberSaveable(stateSaver = DestinationSaver) {
        mutableStateOf<Destination?>(openPage?.let { Destination.Settings(it) })
    }
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
        if (!updates.isConfigured || !container.serverNotices.switches.updates) return@LaunchedEffect
        whatsNew = updates.whatsNew()
        if (settings.autoCheckUpdates && updates.isCheckDue(settings.updateCheckHours)) updates.check()
    }

    // A rare, friendly request to support Daybreak on Ko-fi (see SupportPromptPolicy). It waits a few
    // seconds so it never covers the timetable on opening, and never appears with the update notes.
    var showSupport by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session.studentId) {
        val today = LocalDate.now().toEpochDay()
        val state = container.supportStore.recordOpen(today)
        val popupAllowed = container.serverNotices.featureOn(RemoteFeatures.SUPPORT_POPUP)
        if (popupAllowed && SupportPromptPolicy.shouldShow(state, today)) {
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

    // The assistant shows when the server offers it and the developer's switch ("assistant") is on.
    var assistantOffered by remember { mutableStateOf(false) }
    val online by container.networkMonitor.online.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Asked when the app starts, whenever the phone comes online, and when the app comes back to the front.
    LaunchedEffect(online) { assistantOffered = container.assistantApi?.models() != null }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch { assistantOffered = container.assistantApi?.models() != null }
    }
    val assistantShown = assistantOffered && container.serverNotices.featureOn(RemoteFeatures.ASSISTANT)
    // The one-time notice "Daylight AI is now Dawn" for people who already used the app.
    var showDawnIntro by remember { mutableStateOf(!container.dawnIntroStore.shown()) }
    val openAssistant: (() -> Unit)? = if (assistantShown) {
        { destination = Destination.Main(MainPage.ASSISTANT) }
    } else {
        null
    }

    val timetableViewModel: TimetableViewModel = viewModel(
        key = "timetable_${session.studentId}",
        factory = TimetableViewModel.Factory(container),
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings_${session.studentId}",
        factory = SettingsViewModel.Factory(container),
    )

    val motion = MaterialTheme.motionScheme
    val closePage = {
        haptics.perform(HapticEvent.BACK)
        destination = null
    }
    // A lesson to show in the timetable, asked for from a card in the assistant: the page slides away,
    // the Timetable tab comes up at that day and opens the lesson.
    var lessonFocus by remember { mutableStateOf<LessonFocus?>(null) }
    val openLesson = { focus: LessonFocus ->
        haptics.perform(HapticEvent.TAP)
        lessonFocus = focus
        if (selectedTab != MainTab.TIMETABLE) selectedTab = MainTab.TIMETABLE
        destination = null
    }
    Box(modifier = Modifier.fillMaxSize()) {
      PushHost(
        page = destination,
        onBack = closePage,
        base = {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // The content fades out just above the bar instead of ending in a straight edge.
                val fadeColor = MaterialTheme.colorScheme.surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val fade = 32.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(fadeColor.copy(alpha = 0f), fadeColor),
                                    startY = -fade,
                                    endY = 0f,
                                ),
                                topLeft = Offset(0f, -fade),
                                size = Size(size.width, fade),
                            )
                        }
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    DaybreakBottomBar(
                        selectedTab = selectedTab,
                        onSelect = {
                            if (it != selectedTab) haptics.perform(HapticEvent.TAB_SWITCH)
                            selectedTab = it
                        },
                    )
                }
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
                    MainTab.TIMETABLE -> TimetableScreen(
                        viewModel = timetableViewModel,
                        initialDate = initialDate,
                        initialMode = startMode
                            ?: if (initialDate == null && settings.startView == StartView.WEEK) 1 else 0,
                        openSearchOnStart = openSearch,
                        onOpenUpcoming = { destination = Destination.Main(MainPage.UPCOMING) },
                        onOpenAssistant = openAssistant,
                        focus = lessonFocus,
                        onFocusHandled = { lessonFocus = null },
                    )

                    MainTab.ABSENCES -> {
                        val absencesViewModel: AbsencesViewModel = viewModel(
                            key = "absences_${session.studentId}",
                            factory = AbsencesViewModel.Factory(container),
                        )
                        AbsencesScreen(viewModel = absencesViewModel)
                    }

                    else -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onOpenDebug = { destination = Destination.Debug },
                        onOpenPage = { destination = Destination.Settings(it) },
                        onOpenAccount = { destination = Destination.Main(MainPage.ACCOUNT) },
                        onOpenMessages = { destination = Destination.Main(MainPage.MESSAGES) },
                        onOpenAssistant = openAssistant,
                        onOpenTutorial = { destination = Destination.Tutorial },
                    )
                }
            }
        }

        },
      ) { page ->
        when (page) {
            is Destination.Settings -> SettingsPageHost(
                page = page.page,
                viewModel = settingsViewModel,
                onBack = closePage,
            )

            Destination.Tutorial -> TutorialScreen(
                settingsRepository = container.settingsRepository,
                onFinish = closePage,
                onClose = closePage,
            )

            Destination.Debug -> DebugScreen(
                viewModel = viewModel(factory = DebugViewModel.Factory(container)),
                onBack = closePage,
            )

            is Destination.Main -> when (page.page) {
                MainPage.UPCOMING -> UpcomingScreen(
                    viewModel = viewModel(
                        key = "upcoming_${session.studentId}",
                        factory = UpcomingViewModel.Factory(container),
                    ),
                    onBack = closePage,
                )

                MainPage.ACCOUNT -> AccountScreen(
                    session = session,
                    viewModel = viewModel(
                        key = "account_${session.studentId}",
                        factory = AccountViewModel.Factory(container),
                    ),
                    onBack = closePage,
                )

                MainPage.ASSISTANT -> AssistantScreen(
                    viewModel = viewModel(
                        key = "assistant_${session.studentId}",
                        factory = AssistantViewModel.Factory(container),
                    ),
                    onBack = closePage,
                    onOpenLesson = openLesson,
                )

                MainPage.MESSAGES -> MessagesScreen(
                    viewModel = viewModel(
                        key = "messages_${session.studentId}",
                        factory = MessagesViewModel.Factory(container),
                    ),
                    onBack = closePage,
                )
            }
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
        if (showDawnIntro && assistantShown) {
            DawnIntroOverlay(
                onTry = {
                    container.dawnIntroStore.markShown()
                    showDawnIntro = false
                    openAssistant?.invoke()
                },
                onLater = {
                    container.dawnIntroStore.markShown()
                    showDawnIntro = false
                },
            )
        }
    }
}

/** Pages that are not settings but open over the tabs the same way. */
private enum class MainPage { UPCOMING, ACCOUNT, MESSAGES, ASSISTANT }

/** A full-screen page pushed over the tabs. */
private sealed interface Destination {
    data class Settings(val page: SettingsPage) : Destination
    data class Main(val page: MainPage) : Destination
    data object Debug : Destination
    data object Tutorial : Destination
}

/** Keeps the open page across configuration changes and process death, as a short text. */
private val DestinationSaver = Saver<Destination?, String>(
    save = { destination ->
        when (destination) {
            is Destination.Settings -> "S:${destination.page.name}"
            is Destination.Main -> "M:${destination.page.name}"
            Destination.Debug -> "D"
            Destination.Tutorial -> "T"
            null -> ""
        }
    },
    restore = { saved ->
        when {
            saved == "D" -> Destination.Debug
            saved == "T" -> Destination.Tutorial
            saved.startsWith("S:") -> SettingsPage.entries.firstOrNull { it.name == saved.drop(2) }?.let { Destination.Settings(it) }
            saved.startsWith("M:") -> MainPage.entries.firstOrNull { it.name == saved.drop(2) }?.let { Destination.Main(it) }
            else -> null
        }
    },
)
