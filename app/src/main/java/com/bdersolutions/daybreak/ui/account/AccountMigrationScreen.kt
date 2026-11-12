@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bdersolutions.daybreak.ui.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics

/**
 * Shown once, after the update, to people who used Daybreak before 2.0: make a Daybreak account (or sign in
 * with a restore code). The old install id and the old Ko-fi payment are carried over by
 * [DaybreakAccountViewModel.create]. After an account was made, the restore code is shown big, with a
 * copy button, before the person goes on. "Not now" puts the page off for a few days.
 */
@Composable
fun AccountMigrationScreen(
    viewModel: DaybreakAccountViewModel,
    onDone: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val supporter = remember { viewModel.legacySupporter }

    // Signed in by restore code: nothing to show, go on. After create, the code page comes first.
    LaunchedEffect(snapshot.signedIn, state.created) {
        if (snapshot.signedIn && state.created == null) {
            haptics.perform(HapticEvent.LOGIN_SUCCESS)
            onDone()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            AnimatedContent(
                targetState = state.created,
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 80)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 300f)) { it / 12 })
                        .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 12 })
                },
                label = "migrationPhase",
                modifier = Modifier.fillMaxSize(),
            ) { created ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (created == null) {
                        HeaderIcon(supporter)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.v2_migrate_title),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.v2_migrate_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (supporter) {
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = stringResource(R.string.v2_migrate_supporter),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        CreateOrRestorePanel(
                            busy = state.busy,
                            error = state.error,
                            onCreate = viewModel::create,
                            onRestore = viewModel::restore,
                        )
                        TextButton(onClick = onLater, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.v2_migrate_later))
                        }
                        Text(
                            text = stringResource(R.string.v2_migrate_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        HeaderIcon(false)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.v2_created_title),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.v2_created_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        RestoreCodeCard(created.restoreCode)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.v2_created_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (created.state.grandfathered) {
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = stringResource(R.string.v2_created_thanks),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = {
                                haptics.perform(HapticEvent.TAP)
                                viewModel.acknowledgeCreated()
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Text(stringResource(R.string.v2_created_continue))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(supporter: Boolean) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (supporter) Icons.Outlined.Favorite else Icons.Outlined.ManageAccounts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(38.dp),
        )
    }
}
