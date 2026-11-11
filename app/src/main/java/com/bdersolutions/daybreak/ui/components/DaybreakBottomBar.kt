package com.bdersolutions.daybreak.ui.components

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

/** The bottom bar of the app, also used by the introduction, so the two look the same. */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DaybreakBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    // A floating pill, like the Material 3 Expressive toolbar: the selected tab opens into a filled
    // pill with its label, the others are round icon buttons.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarItem(
                selected = selectedTab == 0,
                icon = Icons.Outlined.CalendarMonth,
                selectedIcon = Icons.Filled.CalendarMonth,
                label = stringResource(R.string.nav_timetable),
                onClick = { onSelect(0) },
            )
            BottomBarItem(
                selected = selectedTab == 1,
                icon = Icons.Outlined.EventBusy,
                selectedIcon = Icons.Filled.EventBusy,
                label = stringResource(R.string.nav_absences),
                onClick = { onSelect(1) },
            )
            BottomBarItem(
                selected = selectedTab == 2,
                icon = Icons.Outlined.MoreHoriz,
                selectedIcon = Icons.Filled.MoreHoriz,
                label = stringResource(R.string.nav_settings),
                onClick = { onSelect(2) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomBarItem(
    selected: Boolean,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    onClick: () -> Unit,
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
    val iconFade = motion.fastEffectsSpec<Float>()
    val iconScale = motion.defaultSpatialSpec<Float>()
    val labelSize = motion.defaultSpatialSpec<IntSize>()
    Surface(
        onClick = onClick,
        // A 50 % corner is a full pill at any width; the surface's size animates when the label opens.
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        modifier = Modifier
            .height(56.dp)
            .animateContentSize(labelSize),
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .padding(horizontal = 16.dp),
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
                    contentDescription = if (isSelected) null else label,
                    modifier = Modifier.size(24.dp),
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(iconFade) + expandHorizontally(labelSize),
                exit = fadeOut(iconFade) + shrinkHorizontally(labelSize),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
