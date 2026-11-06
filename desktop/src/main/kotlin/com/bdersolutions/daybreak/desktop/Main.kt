package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bdersolutions.daybreak.data.support.SupportPromptPolicy
import java.time.LocalDate
import com.bdersolutions.daybreak.untis.UntisChild
import com.bdersolutions.daybreak.untis.UntisLoginResult
import com.bdersolutions.daybreak.untis.UntisSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

fun main() {
    val container = DesktopContainer()
    DemoData.seedIfRequested(container)
    application {
        val settings by container.settings.settings.collectAsState()
        val paid by container.supportStore.paid.collectAsState()
        Window(
            onCloseRequest = ::exitApplication,
            // The supporter extra "app name" also shows in the title bar.
            title = appTitle(paid, settings.customAppName),
            // The taskbar and title bar icon. Without it the window shows the Java cup.
            icon = BitmapPainter(useResource("daybreak-icon.png", ::loadImageBitmap)),
            state = rememberWindowState(width = 1100.dp, height = 760.dp),
        ) {
            DesktopRoot(container, onQuit = ::exitApplication)
        }
    }
}

/** The three tabs of the navigation rail, in the Android bottom bar's order. */
enum class MainTab { TIMETABLE, ABSENCES, MORE }

/** Pages opened from a top bar or from the More list. Each one has a back arrow. */
enum class SubRoute {
    SEARCH, UPCOMING, NOTICES, MESSAGES,
    APPEARANCE, TIMETABLE_SETTINGS, LESSON_COLORS, LANGUAGE, NOTIFICATIONS, SYNC_UPDATES,
    LEGAL, ABOUT, DEBUG,
    APP_NAME, CALENDAR_EXPORT,
}

@Composable
fun MainTab.label(): String = when (this) {
    MainTab.TIMETABLE -> tr("Stundenplan", "Timetable")
    MainTab.ABSENCES -> tr("Fehlstunden", "Absences")
    MainTab.MORE -> tr("Mehr", "More")
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.TIMETABLE -> Icons.Outlined.CalendarMonth
    MainTab.ABSENCES -> Icons.Outlined.EventBusy
    MainTab.MORE -> Icons.Outlined.MoreHoriz
}

@Composable
fun DesktopRoot(container: DesktopContainer, onQuit: () -> Unit) {
    val settings by container.settings.settings.collectAsState()
    DaybreakDesktopTheme(settings.themeMode, settings.accent) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalLanguage provides settings.language,
            LocalLessonColorSettings provides settings.lessonColors,
            LocalDensity provides Density(density.density, density.fontScale * settings.textScale.multiplier),
        ) {
            // Paints the theme background behind every page, so text colours always sit on the theme's surface.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                App(container, onQuit)
            }
        }
    }
}

/** Shows the login until a session exists, then the navigation rail with the pages. */
@Composable
fun App(container: DesktopContainer, onQuit: () -> Unit) {
    // The privacy policy and terms come first, as on Android: nothing else runs until they are accepted.
    var accepted by remember { mutableStateOf(container.termsStore.isAccepted()) }
    if (!accepted) {
        TermsGate(
            isUpdate = container.termsStore.isUpdate(),
            onAccept = {
                container.termsStore.accept()
                accepted = true
            },
            onDecline = onQuit,
            onLanguageChange = { container.settings.setLanguage(it) },
        )
        return
    }
    var session by remember { mutableStateOf(container.sessionStore.timetableSession()) }
    val current = session
    if (current == null) {
        LoginScreen(container = container, onLoggedIn = { result: UntisLoginResult, child: UntisChild ->
            val saved = container.sessionStore.saveLogin(result, child)
            if (saved) session = container.sessionStore.timetableSession()
            saved
        })
    } else {
        SignedInScreen(
            container = container,
            session = current,
            onLogout = {
                container.sessionStore.clear()
                session = null
            },
        )
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SignedInScreen(container: DesktopContainer, session: UntisSession, onLogout: () -> Unit) {
    // Back and forward like a browser: Escape, Alt+Left/Right and the mouse side buttons use it.
    val nav = remember { NavHistory() }
    val place = nav.current
    var supportRequest by remember { mutableStateOf<SupportRequest?>(null) }
    val settings by container.settings.settings.collectAsState()
    val focus = remember { FocusRequester() }

    // The support popup: rare, after several days of use. Counted once per start, as on Android.
    LaunchedEffect(Unit) {
        val today = LocalDate.now().toEpochDay()
        val state = container.supportStore.recordOpen(today)
        if (SupportPromptPolicy.shouldShow(state, today)) {
            container.supportStore.markShown(today)
            supportRequest = SupportRequest
        }
    }

    SupportHost(container, supportRequest) { supportRequest = null }
    val updateState by container.updater.state.collectAsState()
    UpdateReadyHost(container, updateState)

    // Background sync: refresh this week and the next on the chosen interval, while the app is open.
    LaunchedEffect(settings.syncEnabled, settings.syncIntervalMinutes) {
        if (!settings.syncEnabled) return@LaunchedEffect
        while (isActive) {
            delay(settings.syncIntervalMinutes * 60_000L)
            try {
                syncTimetableOnce(container, container.settings.current())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The next interval tries again.
            }
        }
    }

    // Reminders: checked once a minute, so the morning summary and the evening reminder come on time.
    LaunchedEffect(settings.notificationsEnabled) {
        while (isActive) {
            if (settings.notificationsEnabled) {
                try {
                    runReminders(container, container.settings.current())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Try again in a minute.
                }
            }
            delay(60_000)
        }
    }

    // Update check: looks for a newer build on the chosen interval, when automatic checks are on.
    LaunchedEffect(settings.autoCheckUpdates, settings.updateCheckHours) {
        if (!settings.autoCheckUpdates || !container.updater.build.updatesEnabled) return@LaunchedEffect
        while (isActive) {
            // Automatic checks also download a newer build, so it is ready when the person is asked.
            container.updater.check(autoDownload = true)
            delay(settings.updateCheckHours * 3_600_000L)
        }
    }

    // The root takes keyboard focus so Escape and Alt+arrows work on every page, whatever is inside.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    event.key == Key.Escape -> nav.back()
                    event.isAltPressed && event.key == Key.DirectionLeft -> nav.back()
                    event.isAltPressed && event.key == Key.DirectionRight -> nav.forward()
                    event.key == Key.Back -> nav.back()
                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                // The side buttons of a mouse, as in a browser.
                when (event.button) {
                    PointerButton.Back -> if (nav.back()) event.changes.forEach { it.consume() }
                    PointerButton.Forward -> if (nav.forward()) event.changes.forEach { it.consume() }
                    else -> Unit
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideRail(
                tab = place.tab,
                onTab = { nav.open(Place(it)) },
                inPage = place.sub != null,
                canGoBack = nav.canGoBack,
                canGoForward = nav.canGoForward,
                onBack = { nav.back() },
                onForward = { nav.forward() },
            )
            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                // Pages open over the tabs with a slide, and slide back when closed, as on Android. A
                // change of tab fades through, moving a little in the direction of the new tab.
                AnimatedContent(
                    targetState = place,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { placeTransition(initialState, targetState) },
                    label = "place",
                ) { target ->
                    val route = target.sub
                    if (route != null) {
                        SubPage(container, route, onBack = { nav.back() })
                    } else {
                        when (target.tab) {
                            MainTab.TIMETABLE -> TimetableScreen(
                                container = container,
                                onOpenSearch = { nav.open(Place(MainTab.TIMETABLE, SubRoute.SEARCH)) },
                                onOpenUpcoming = { nav.open(Place(MainTab.TIMETABLE, SubRoute.UPCOMING)) },
                            )
                            MainTab.ABSENCES -> AbsencesScreen(container)
                            MainTab.MORE -> MoreScreen(
                                container = container,
                                session = session,
                                onOpen = { nav.open(Place(MainTab.MORE, it)) },
                                onLogout = onLogout,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How one place gives way to the next: pages slide sideways, tabs fade and drift. */
private fun AnimatedContentTransitionScope<Place>.placeTransition(from: Place, to: Place): ContentTransform {
    val opening = to.sub != null && (from.sub == null || to != from)
    val closing = to.sub == null && from.sub != null
    return when {
        opening || closing -> (
            slideInHorizontally(DesktopMotion.spatial<IntOffset>()) { width -> if (opening) width / 8 else -width / 8 } +
                fadeIn(DesktopMotion.effects<Float>())
            ).togetherWith(
            slideOutHorizontally(DesktopMotion.spatial<IntOffset>()) { width -> if (opening) -width / 8 else width / 8 } +
                fadeOut(DesktopMotion.fastEffects<Float>()),
        ).using(SizeTransform(clip = false))

        else -> {
            val direction = if (to.tab.ordinal >= from.tab.ordinal) 1 else -1
            (
                slideInVertically(DesktopMotion.spatial<IntOffset>()) { height -> direction * height / 24 } +
                    fadeIn(DesktopMotion.effects<Float>())
                ).togetherWith(
                slideOutVertically(DesktopMotion.fastSpatial<IntOffset>()) { height -> -direction * height / 24 } +
                    fadeOut(DesktopMotion.fastEffects<Float>()),
            ).using(SizeTransform(clip = false))
        }
    }
}

/**
 * The navigation rail: a rounded panel with a margin around it, the app mark on top, the three tabs,
 * and back and forward at the bottom, like a browser's.
 */
@Composable
private fun SideRail(
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    inPage: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(92.dp),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = Color.Transparent,
            windowInsets = WindowInsets(0, 0, 0, 0),
            header = {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MainTab.TIMETABLE.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            },
        ) {
            MainTab.entries.forEach { item ->
                NavigationRailItem(
                    selected = !inPage && tab == item,
                    onClick = { onTab(item) },
                    icon = { Icon(item.icon(), contentDescription = item.label()) },
                    label = { Text(item.label()) },
                    modifier = Modifier.handCursor().padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            RailArrow(forward = false, enabled = canGoBack, description = tr("Zurück (Esc)", "Back (Esc)"), onClick = onBack)
            RailArrow(forward = true, enabled = canGoForward, description = tr("Vorwärts (Alt+→)", "Forward (Alt+→)"), onClick = onForward)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RailArrow(forward: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.handCursor()) {
        Icon(
            if (forward) Icons.AutoMirrored.Outlined.ArrowForward else Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = description,
        )
    }
}

/** A page reached from the top bar or the More list: a back arrow and the page's content. */
@Composable
private fun SubPage(container: DesktopContainer, route: SubRoute, onBack: () -> Unit) {
    when (route) {
        SubRoute.SEARCH -> Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = tr("Suche", "Search"), onBack = onBack)
            SearchScreen(container)
        }
        SubRoute.UPCOMING -> Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = tr("Hausaufgaben & Prüfungen", "Homework & exams"), onBack = onBack)
            UpcomingScreen(container)
        }
        SubRoute.NOTICES -> Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = tr("Mein Konto", "My account"), onBack = onBack)
            NoticesScreen(container)
        }
        SubRoute.MESSAGES -> Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = tr("Nachrichten", "Messages"), onBack = onBack)
            MessagesScreen(container)
        }
        SubRoute.APP_NAME -> SettingsSubPage(tr("App-Name", "App name"), onBack) { AppNamePage(container) }
        SubRoute.CALENDAR_EXPORT -> SettingsSubPage(tr("Kalender-Export", "Calendar export"), onBack) { CalendarExportPage(container) }
        SubRoute.LEGAL -> SettingsSubPage(str("settings_legal"), onBack) { LegalPage() }
        SubRoute.ABOUT -> SettingsSubPage(str("settings_about"), onBack) { AboutPage(container) }
        SubRoute.DEBUG -> SettingsSubPage(str("settings_debug_title"), onBack) { DebugPage(container) }
        SubRoute.APPEARANCE -> SettingsSubPage(tr("Darstellung", "Appearance"), onBack) { AppearanceSettings(container) }
        SubRoute.TIMETABLE_SETTINGS -> SettingsSubPage(tr("Stundenplan", "Timetable"), onBack) { TimetableSettings(container) }
        SubRoute.LESSON_COLORS -> SettingsSubPage(tr("Stundenfarben", "Lesson colours"), onBack) { LessonColorsScreen(container) }
        SubRoute.LANGUAGE -> SettingsSubPage(tr("Sprache", "Language"), onBack) { LanguageSettings(container) }
        SubRoute.NOTIFICATIONS -> SettingsSubPage(tr("Benachrichtigungen", "Notifications"), onBack) {
            NotificationSettings(container)
        }
        SubRoute.SYNC_UPDATES -> SettingsSubPage(tr("Synchronisierung & Updates", "Sync & updates"), onBack) {
            SyncSettings(container)
        }
    }
}

/** A header in the app's style: a large title, an optional subtitle, icon actions on the right. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Zurück", "Back"))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

/** Settings sub-pages: a header with a back arrow and the page body. */
@Composable
private fun SettingsSubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = title, onBack = onBack)
        content()
    }
}

/** The support dialog was opened by the rare popup. */
private object SupportRequest

/** Shows the support popup when the policy asks for it. */
@Composable
private fun SupportHost(container: DesktopContainer, request: SupportRequest?, onClosed: () -> Unit) {
    if (request == null) return
    val api = container.supportApi
    SupportDialog(
        // Without an update server there is nothing to pay-check against, so the free opt-out stays.
        unlockCode = if (api != null) container.supportStore.unlockCode() else null,
        onSupport = { openSupportPage() },
        onLater = {},
        onNever = { container.supportStore.optOut() },
        onCheckPayment = { checkSupportPayment(container) },
        onPaid = {},
        onClosed = onClosed,
    )
}

/** Asks the server whether this computer's code was paid for; a confirmed payment is remembered. */
suspend fun checkSupportPayment(container: DesktopContainer): com.bdersolutions.daybreak.data.support.PaymentCheck {
    val api = container.supportApi ?: return com.bdersolutions.daybreak.data.support.PaymentCheck.UNAVAILABLE
    val result = api.check(container.supportStore.unlockCode())
    if (result == com.bdersolutions.daybreak.data.support.PaymentCheck.PAID) container.supportStore.markPaid()
    return result
}

/** Centres a page at a readable width on a wide window. */
@Composable
fun ReadableWidth(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = 960.dp).fillMaxWidth()) {
            content()
        }
    }
}
