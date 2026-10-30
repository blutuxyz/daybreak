package com.bdersolutions.daybreak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.ui.debug.DebugScreen
import com.bdersolutions.daybreak.ui.debug.DebugViewModel
import com.bdersolutions.daybreak.ui.login.LoginScreen
import com.bdersolutions.daybreak.ui.login.LoginViewModel
import com.bdersolutions.daybreak.ui.settings.SettingsScreen
import com.bdersolutions.daybreak.ui.settings.SettingsViewModel
import com.bdersolutions.daybreak.ui.timetable.TimetableScreen
import com.bdersolutions.daybreak.ui.timetable.TimetableViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DaybreakRoot(container: AppContainer) {
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
            LoggedInContent(container = container, session = currentSession)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoggedInContent(
    container: AppContainer,
    session: DaybreakSession,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showDebug by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            container.settingsRepository.setNotificationsEnabled(false)
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

    val motion = MaterialTheme.motionScheme
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
                0 -> TimetableScreen(viewModel = timetableViewModel)
                else -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenDebug = { showDebug = true },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DaybreakBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    FlexibleBottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    ) {
        BottomBarItem(
            selected = selectedTab == 0,
            icon = Icons.Outlined.CalendarMonth,
            selectedIcon = Icons.Filled.CalendarMonth,
            label = androidx.compose.ui.res.stringResource(R.string.nav_timetable),
            onClick = { onSelect(0) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        BottomBarItem(
            selected = selectedTab == 1,
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings,
            label = androidx.compose.ui.res.stringResource(R.string.nav_settings),
            onClick = { onSelect(1) },
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
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "bottomBarContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "bottomBarContent",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.72f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "bottomBarLabelAlpha",
    )
    val labelShift by animateFloatAsState(
        targetValue = if (selected) 0f else -2f,
        animationSpec = motion.fastSpatialSpec(),
        label = "bottomBarLabelShift",
    )
    val iconFade = motion.fastEffectsSpec<Float>()
    val iconScale = motion.defaultSpatialSpec<Float>()
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
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
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    alpha = labelAlpha
                    translationY = labelShift
                },
            )
        }
    }
}
