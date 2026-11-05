package com.bdersolutions.daybreak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Settings
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.ui.absences.AbsencesScreen
import com.bdersolutions.daybreak.ui.absences.AbsencesViewModel
import com.bdersolutions.daybreak.ui.debug.DebugScreen
import com.bdersolutions.daybreak.ui.debug.DebugViewModel
import com.bdersolutions.daybreak.ui.login.LoginScreen
import com.bdersolutions.daybreak.ui.login.LoginViewModel
import com.bdersolutions.daybreak.data.update.ReleaseInfo
import com.bdersolutions.daybreak.ui.account.AccountScreen
import com.bdersolutions.daybreak.ui.account.AccountViewModel
import com.bdersolutions.daybreak.ui.settings.SettingsPage
import com.bdersolutions.daybreak.ui.upcoming.UpcomingScreen
import com.bdersolutions.daybreak.ui.upcoming.UpcomingViewModel
import com.bdersolutions.daybreak.ui.settings.WhatsNewSheet
import com.bdersolutions.daybreak.ui.settings.SettingsPageHost
import com.bdersolutions.daybreak.ui.settings.SettingsScreen
import com.bdersolutions.daybreak.ui.settings.SettingsViewModel
import com.bdersolutions.daybreak.ui.timetable.TimetableScreen
import com.bdersolutions.daybreak.ui.components.rememberMorphCorner
import com.bdersolutions.daybreak.ui.theme.DaybreakTheme
import com.bdersolutions.daybreak.ui.timetable.TimetableViewModel
import java.time.LocalDate

/**
 * Entry point of the Compose tree. Settings are observed here so that theme, accent,
 * dark mode and motion update live when the user changes them.
 */
@Composable
fun DaybreakApp(
    container: AppContainer,
    initialDate: LocalDate? = null,
    openUpdates: Boolean = false,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    DaybreakTheme(settings = settings) {
        DaybreakRoot(container = container, initialDate = initialDate, openUpdates = openUpdates)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DaybreakRoot(
    container: AppContainer,
    initialDate: LocalDate? = null,
    openUpdates: Boolean = false,
) {
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
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
        showDebug = false
    }

    if (showDebug) {
        val debugViewModel: DebugViewModel = viewModel(factory = DebugViewModel.Factory(container))
        DebugScreen(
            viewModel = debugViewModel,
            onBack = { showDebug = false },
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
        settingsPage = null
    }
    BackHandler(enabled = mainPage != null) {
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
                    onSelect = { selectedTab = it },
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
                        initialMode = if (initialDate == null && settings.startView == StartView.WEEK) 1 else 0,
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
                    onBack = { settingsPage = null },
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
                    onBack = { mainPage = null },
                )

                MainPage.ACCOUNT -> AccountScreen(
                    session = session,
                    viewModel = viewModel(
                        key = "account_${session.studentId}",
                        factory = AccountViewModel.Factory(container),
                    ),
                    onBack = { mainPage = null },
                )

                null -> Unit
            }
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
private enum class MainPage { UPCOMING, ACCOUNT }

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
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings,
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
