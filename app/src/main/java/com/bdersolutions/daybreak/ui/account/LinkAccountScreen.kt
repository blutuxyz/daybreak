@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bdersolutions.daybreak.ui.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.components.motionAnimationsEnabled
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.ui.legal.LegalDocument
import com.bdersolutions.daybreak.ui.theme.PressFeedback

/**
 * The required page between the WebUntis login and the app: every phone needs a Daybreak account, and the
 * WebUntis login that is signed in is connected to it. There is no "later": the page is done when both are
 * done. Creating and linking are one run, so the page can say "creating" and then "linking" before it shows
 * the restore code. The shapes are the expressive Material 3 ones: a sun that morphs and turns while a call
 * runs.
 */
@Composable
fun LinkAccountScreen(
    session: DaybreakSession,
    viewModel: DaybreakAccountViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val supporter = remember { viewModel.legacySupporter }
    val stage = state.linkStage
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(stage) {
        if (stage == LinkStage.LINKED) haptics.perform(HapticEvent.LOGIN_SUCCESS)
    }
    LaunchedEffect(state.error) {
        if (state.error != null) haptics.perform(HapticEvent.ERROR)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    val spatial = motion.defaultSpatialSpec<IntOffset>()
                    (
                        fadeIn(motion.slowEffectsSpec()) +
                            slideInVertically(spatial) { it / 10 } +
                            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)
                        )
                        .togetherWith(
                            fadeOut(motion.fastEffectsSpec()) +
                                slideOutVertically(spatial) { -it / 10 },
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "linkStage",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                when (current) {
                    null -> IntroPhase(
                        session = session,
                        signedIn = snapshot.signedIn,
                        busy = state.busy,
                        error = state.error,
                        supporter = supporter,
                        onCreate = { viewModel.createAndLink(session) },
                        onRestore = { code -> viewModel.restoreAndLink(session, code) },
                    )

                    LinkStage.CREATING, LinkStage.LINKING -> WorkingPhase(
                        stage = current,
                        schoolName = session.schoolName,
                        slots = snapshot.state?.webuntis.orEmpty(),
                        error = state.error,
                        busy = state.busy,
                        onRetry = { viewModel.createAndLink(session) },
                        onUnbind = { slot -> viewModel.unbind(slot, session) },
                    )

                    LinkStage.LINKED -> DonePhase(
                        code = state.created?.restoreCode ?: snapshot.credentials?.restoreCode,
                        grandfathered = state.created?.state?.grandfathered == true,
                        onContinue = {
                            haptics.perform(HapticEvent.TAP)
                            viewModel.confirmCodeSaved()
                            viewModel.markLinked(session)
                            viewModel.acknowledgeCreated()
                            onDone()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroPhase(
    session: DaybreakSession,
    signedIn: Boolean,
    busy: Boolean,
    error: com.bdersolutions.daybreak.data.account.AccountError?,
    supporter: Boolean,
    onCreate: () -> Unit,
    onRestore: (String) -> Unit,
) {
    val haptics = LocalHaptics.current
    var showTerms by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (signedIn) Icons.Outlined.Link else Icons.Outlined.ManageAccounts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(if (signedIn) R.string.v2_link_title_existing else R.string.v2_link_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.v2_link_body, session.schoolName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        // Fine print, not a card: it belongs to the text above, and tapping it opens the legal text. The
        // press tint is the error red, because this is a condition, not a hint.
        val termsPress = remember { MutableInteractionSource() }
        Text(
            text = stringResource(R.string.v2_link_terms),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(
                    interactionSource = termsPress,
                    indication = PressFeedback(MaterialTheme.colorScheme.error),
                    onClick = {
                        haptics.perform(HapticEvent.SELECT)
                        showTerms = true
                    },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (supporter) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
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
        if (signedIn) {
            Button(
                onClick = {
                    haptics.perform(HapticEvent.TAP)
                    onCreate()
                },
                enabled = !busy,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                BusyText(busy, stringResource(R.string.v2_link_link))
            }
            ErrorLine(error, Modifier.padding(horizontal = 8.dp))
        } else {
            CreateOrRestorePanel(
                busy = busy,
                error = error,
                onCreate = {
                    haptics.perform(HapticEvent.TAP)
                    onCreate()
                },
                onRestore = onRestore,
                createLabel = stringResource(R.string.v2_link_create),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.v2_migrate_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    if (showTerms) {
        AlertDialog(
            onDismissRequest = { showTerms = false },
            title = { Text(stringResource(R.string.legal_screen_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { LegalDocument() }
            },
            confirmButton = {
                TextButton(onClick = { showTerms = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun WorkingPhase(
    stage: LinkStage,
    schoolName: String,
    slots: List<com.bdersolutions.daybreak.data.account.WebUntisSlot>,
    error: com.bdersolutions.daybreak.data.account.AccountError?,
    busy: Boolean,
    onRetry: () -> Unit,
    onUnbind: (String) -> Unit,
) {
    val haptics = LocalHaptics.current
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LinkOrb(stage = stage)
        Spacer(Modifier.height(28.dp))
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (
                    fadeIn(motion.defaultEffectsSpec()) +
                        slideInVertically(motion.defaultSpatialSpec<IntOffset>()) { it / 4 } +
                        scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f)
                    )
                    .togetherWith(
                        fadeOut(motion.fastEffectsSpec()) +
                            slideOutVertically(motion.fastSpatialSpec<IntOffset>()) { -it / 4 } +
                            scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f),
                    )
            },
            label = "linkStageText",
        ) { current ->
            Text(
                text = stringResource(
                    if (current == LinkStage.CREATING) R.string.v2_link_creating else R.string.v2_link_linking,
                ),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "linkStageHint",
        ) { current ->
            Text(
                text = if (current == LinkStage.CREATING) {
                    stringResource(R.string.v2_link_creating_hint)
                } else {
                    stringResource(R.string.v2_link_linking_hint, schoolName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (error != null) {
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
            // All three places taken: the person removes one here, so the required page cannot become a dead end.
            if (error == com.bdersolutions.daybreak.data.account.AccountError.LIMIT && slots.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        slots.forEach { slot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = slot.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { onUnbind(slot.id) }, enabled = !busy) {
                                    Icon(
                                        Icons.Outlined.LinkOff,
                                        contentDescription = stringResource(R.string.v2_account_unbind),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    haptics.perform(HapticEvent.TAP)
                    onRetry()
                },
                enabled = !busy,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                BusyText(busy, stringResource(R.string.v2_link_retry))
            }
        }
    }
}

@Composable
private fun DonePhase(
    code: String?,
    grandfathered: Boolean,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SuccessMark()
        Spacer(Modifier.height(22.dp))
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
        if (code != null) {
            RestoreCodeCard(code)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.v2_created_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = stringResource(R.string.v2_account_code_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (grandfathered) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
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
        // The code is the only way back into the account, so Continue waits until the user confirms they kept it.
        var saved by rememberSaveable { mutableStateOf(false) }
        if (code != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { saved = !saved },
            ) {
                Checkbox(checked = saved, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.v2_created_saved),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = onContinue,
            enabled = code == null || saved,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.v2_created_continue))
        }
    }
}

/**
 * The running state as one shape that morphs between two Material shapes while it slowly turns and breathes,
 * with the expressive loading indicator in its middle. Motion off means a still sun.
 */
@Composable
private fun LinkOrb(stage: LinkStage) {
    val animationsEnabled = motionAnimationsEnabled()
    val morph = remember { Morph(start = MaterialShapes.Sunny, end = MaterialShapes.VerySunny) }
    val progress = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    val breathe by rememberInfiniteTransition(label = "linkBreathe").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "linkBreatheValue",
    )
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            progress.snapTo(0f)
            turn.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            progress.animateTo(1f, tween(1_500, easing = FastOutSlowInEasing))
            progress.animateTo(0f, tween(1_500, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(animationsEnabled, stage) {
        if (!animationsEnabled) {
            turn.snapTo(0f)
            return@LaunchedEffect
        }
        val direction = if (stage == LinkStage.CREATING) 1f else -1f
        while (true) turn.animateTo(turn.value + direction * 360f, tween(6_000, easing = LinearEasing))
    }
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val glowColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Box(
        modifier = Modifier
            .size(152.dp)
            .graphicsLayer {
                rotationZ = turn.value
                val scale = if (animationsEnabled) breathe else 1f
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.20f), glowColor.copy(alpha = 0f)),
                    center = size.center,
                    radius = size.minDimension * 0.5f,
                ),
                radius = size.minDimension * 0.5f,
                center = size.center,
            )
            path.rewind()
            morph.toPath(progress = progress.value, path = path)
            matrix.reset()
            val scale = size.minDimension * 0.66f
            matrix.scale(x = scale, y = scale)
            path.transform(matrix)
            path.translate(size.center - path.getBounds().center)
            drawPath(path = path, color = containerColor)
            drawCircle(color = dotColor, radius = size.minDimension * 0.11f, center = size.center)
        }
        LoadingIndicator(
            modifier = Modifier.size(30.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** A check inside the expressive sun shape, growing in when the page is done. */
@Composable
private fun SuccessMark() {
    val animationsEnabled = motionAnimationsEnabled()
    val motion = MaterialTheme.motionScheme
    val scale = remember { Animatable(if (animationsEnabled) 0.6f else 1f) }
    val alpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            scale.snapTo(1f)
            alpha.snapTo(1f)
            return@LaunchedEffect
        }
        alpha.animateTo(1f, tween(180))
        scale.animateTo(1f, motion.defaultSpatialSpec())
    }
    Box(
        modifier = Modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(42.dp),
        )
    }
}
