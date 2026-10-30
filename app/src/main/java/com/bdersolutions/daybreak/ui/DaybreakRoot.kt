package com.bdersolutions.daybreak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun DaybreakRoot(container: AppContainer) {
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val currentSession = session
    if (currentSession == null) {
        val loginViewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory(container))
        LoginScreen(viewModel = loginViewModel)
    } else {
        LoggedInContent(container = container, session = currentSession)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
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
            label = androidx.compose.ui.res.stringResource(R.string.nav_timetable),
            onClick = { onSelect(0) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        BottomBarItem(
            selected = selectedTab == 1,
            icon = Icons.Outlined.Settings,
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
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "bottomBarContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "bottomBarContent",
    )
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}
