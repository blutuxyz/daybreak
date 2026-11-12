@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.bdersolutions.daybreak.ui.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import kotlinx.coroutines.launch

/**
 * The thank-you for people who paid through Ko-fi before 2.0: three months of Pro. It comes up once, on the
 * first launch after they moved to a Daybreak account ([com.bdersolutions.daybreak.data.account.TrialOfferPolicy]).
 * Any way of closing it counts as "later": the offer then stays pinned at the top of More ([TrialBanner]).
 */
@Composable
fun TrialOfferSheet(
    busy: Boolean,
    error: AccountError?,
    started: Boolean,
    onAccept: () -> Unit,
    onLater: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current

    fun closeThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }
    LaunchedEffect(started) {
        if (started) haptics.perform(HapticEvent.UPDATE_READY)
    }

    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = started,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(140)) },
            label = "trialSheet",
        ) { done ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Text(
                        text = stringResource(if (done) R.string.v2_trial_started_title else R.string.v2_trial_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(if (done) R.string.v2_trial_started_body else R.string.v2_trial_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!done) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.v2_trial_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ErrorLine(error)
                Spacer(Modifier.height(20.dp))
                if (done) {
                    Button(elevation = null,
                        onClick = { closeThen(onLater) },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { Text(stringResource(R.string.v2_trial_done)) }
                } else {
                    Button(elevation = null,
                        onClick = onAccept,
                        enabled = !busy,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { BusyText(busy, stringResource(R.string.v2_trial_accept)) }
                    TextButton(onClick = { closeThen(onLater) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.v2_trial_later))
                    }
                }
            }
        }
    }
}

/** The reminder pinned at the top of More for two weeks after the popup was dismissed. */
@Composable
fun TrialBanner(
    busy: Boolean,
    error: AccountError?,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Favorite, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.v2_trial_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.v2_trial_banner_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(elevation = null,
                onClick = onAccept,
                enabled = !busy,
                shape = CircleShape,
            ) { BusyText(busy, stringResource(R.string.v2_trial_accept)) }
            ErrorLine(error)
        }
    }
}
