@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bdersolutions.daybreak.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.account.AccountState
import com.bdersolutions.daybreak.data.account.Plan
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.FilledTonalButton
import com.bdersolutions.daybreak.ui.components.OutlinedButton
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.ui.settings.SettingsDivider
import com.bdersolutions.daybreak.ui.settings.SettingsGroup
import com.bdersolutions.daybreak.ui.settings.SettingsPageScaffold
import com.bdersolutions.daybreak.ui.settings.SettingsRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The Daybreak account page (More, My profile): plan, restore code, the WebUntis accounts bound to it. */
@Composable
fun DaybreakAccountScreen(
    session: DaybreakSession?,
    viewModel: DaybreakAccountViewModel,
    onBack: () -> Unit,
    onOpenPlans: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var binding by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(snapshot.signedIn) {
        if (snapshot.signedIn) viewModel.refresh()
    }
    LaunchedEffect(session?.studentId, snapshot.state?.webuntis) { viewModel.loadBound(session) }

    SettingsPageScaffold(title = stringResource(R.string.v2_account_title), onBack = onBack) {
        val credentials = snapshot.credentials
        val account = snapshot.state
        if (credentials == null) {
            item(key = "setup") {
                Card(Modifier.animateItem()) {
                    Text(
                        text = stringResource(R.string.v2_account_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    CreateOrRestorePanel(
                        busy = state.busy,
                        error = state.error,
                        onCreate = viewModel::create,
                        onRestore = viewModel::restore,
                    )
                }
            }
        } else {
            item(key = "plan") {
                PlanCard(account, onOpenPlans, onManageSubscription, Modifier.animateItem())
            }
            item(key = "code") {
                Card(Modifier.animateItem()) {
                    Text(
                        text = stringResource(R.string.v2_account_code_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    val code = credentials.restoreCode
                    if (code != null) {
                        RestoreCodeCard(code)
                    } else {
                        Text(
                            text = stringResource(R.string.v2_account_code_unknown),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    // Accounts made before the code was confirmed at creation (or restored on a device) are asked once.
                    var codeConfirmed by rememberSaveable(credentials.accountId) {
                        mutableStateOf(viewModel.codeConfirmed(credentials.accountId))
                    }
                    if (code != null && !codeConfirmed) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.v2_account_unsaved_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.v2_account_unsaved_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(elevation = null,
                                    onClick = {
                                        haptics.perform(HapticEvent.TAP)
                                        viewModel.confirmCodeSaved()
                                        codeConfirmed = true
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.v2_account_unsaved_confirm))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.v2_account_code_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "webuntis") {
                WebUntisCard(
                    account = account,
                    session = session,
                    boundSlotId = state.boundSlotId,
                    busy = state.busy,
                    sharedWarning = state.sharedWarning,
                    error = state.error,
                    onBind = { binding = true },
                    onUnbind = { slot -> viewModel.unbind(slot, session) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "signout") {
                AnimatedSignOut(
                    confirming = confirmingSignOut,
                    onAsk = { confirmingSignOut = true },
                    onCancel = { confirmingSignOut = false },
                    onConfirm = {
                        confirmingSignOut = false
                        haptics.perform(HapticEvent.TAP)
                        viewModel.signOut()
                    },
                )
            }
            item(key = "delete") {
                AnimatedDeleteAccount(
                    confirming = confirmingDelete,
                    error = state.error,
                    busy = state.busy,
                    onAsk = { confirmingDelete = true },
                    onCancel = { confirmingDelete = false },
                    onConfirm = {
                        haptics.perform(HapticEvent.TAP)
                        viewModel.deleteAccount()
                    },
                )
            }
        }
    }

    if (binding && session != null) {
        var label by rememberSaveable { mutableStateOf(session.schoolName.take(40)) }
        AlertDialog(
            onDismissRequest = { binding = false },
            title = { Text(stringResource(R.string.v2_account_bind_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.v2_account_bind_body), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(40) },
                        label = { Text(stringResource(R.string.v2_account_bind_label)) },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    binding = false
                    viewModel.bind(session, label)
                }) { Text(stringResource(R.string.v2_account_bind_confirm)) }
            },
            dismissButton = { TextButton(onClick = { binding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            content = content,
        )
    }
}

@Composable
private fun PlanCard(
    account: AccountState?,
    onOpenPlans: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = account?.plan ?: Plan.FREE
    val until = account?.planUntil?.let(::formatDate)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (plan == Plan.FREE) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.v2_account_plan, plan.label()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val sub = when {
                        until != null -> stringResource(R.string.v2_account_plan_until, until)
                        plan != Plan.FREE -> stringResource(R.string.v2_account_plan_active)
                        else -> stringResource(R.string.v2_account_plan_free_hint)
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (account?.grandfathered == true) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.v2_account_supporter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan != Plan.PRO) {
                    Button(elevation = null, onClick = onOpenPlans, shape = CircleShape) { Text(stringResource(R.string.v2_account_upgrade)) }
                }
                if (plan != Plan.FREE) {
                    FilledTonalButton(elevation = null, onClick = onManage, shape = CircleShape) { Text(stringResource(R.string.v2_plans_manage)) }
                }
            }
        }
    }
}

@Composable
private fun WebUntisCard(
    account: AccountState?,
    session: DaybreakSession?,
    boundSlotId: String?,
    busy: Boolean,
    sharedWarning: Boolean,
    error: com.bdersolutions.daybreak.data.account.AccountError?,
    onBind: () -> Unit,
    onUnbind: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = account?.webuntis.orEmpty()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.v2_account_webuntis_title, slots.size, MAX_SLOTS),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        SettingsGroup {
            if (slots.isEmpty()) {
                Text(
                    text = stringResource(R.string.v2_account_webuntis_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            slots.forEachIndexed { index, slot ->
                if (index > 0) SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.School,
                    title = slot.label,
                    subtitle = if (slot.id == boundSlotId) stringResource(R.string.v2_account_webuntis_this) else null,
                    trailing = {
                        IconButton(onClick = { onUnbind(slot.id) }, enabled = !busy) {
                            Icon(Icons.Outlined.LinkOff, contentDescription = stringResource(R.string.v2_account_unbind))
                        }
                    },
                )
            }
        }
        if (session != null && boundSlotId == null) {
            val full = slots.size >= MAX_SLOTS
            OutlinedButton(
                onClick = onBind,
                enabled = !busy && !full,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (full) R.string.v2_account_webuntis_full else R.string.v2_account_bind))
            }
        }
        AnimatedVisibility(
            visible = sharedWarning,
            enter = fadeIn() + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut() + shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            Text(
                text = stringResource(R.string.v2_account_shared_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        ErrorLine(error, Modifier.padding(horizontal = 8.dp))
    }
}

/** Signing out of the account: the row becomes its own confirmation, like the WebUntis logout on More. */
@Composable
private fun AnimatedSignOut(
    confirming: Boolean,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The confirmation grows the group itself: one height animation, anchored at the row above, so the
    // panel unrolls downwards instead of the group and the panel fighting over the height.
    SettingsGroup(animateSize = false) {
        Column(modifier = modifier) {
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = stringResource(R.string.v2_account_signout),
                subtitle = stringResource(R.string.v2_account_signout_desc),
                onClick = if (confirming) null else onAsk,
                destructive = true,
            )
            AnimatedVisibility(
                visible = confirming,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Top),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Top),
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Text(
                        text = stringResource(R.string.v2_account_signout_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Full-width buttons stacked: the labels are long and must not wrap.
                    Button(elevation = null, onClick = onConfirm, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.v2_account_signout), maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancel, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Deleting the account: a second confirmation row under sign-out, with the consequences spelled out. The
 * account stays if the server refuses or is offline; the error shows under the buttons.
 */
@Composable
private fun AnimatedDeleteAccount(
    confirming: Boolean,
    error: AccountError?,
    busy: Boolean,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(animateSize = false) {
        Column(modifier = modifier) {
            SettingsRow(
                icon = Icons.Outlined.DeleteOutline,
                title = stringResource(R.string.v2_account_delete),
                subtitle = stringResource(R.string.v2_account_delete_desc),
                onClick = if (confirming) null else onAsk,
                destructive = true,
            )
            // Timed, not springs: the height and the fade run on one clock, so the panel visibly opens and closes.
            AnimatedVisibility(
                visible = confirming,
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
                exit = fadeOut(tween(120, easing = LinearOutSlowInEasing)) +
                    shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Text(
                        text = stringResource(R.string.v2_account_delete_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Full-width buttons stacked: the labels are long and must not wrap.
                    Button(elevation = null,
                        onClick = onConfirm,
                        enabled = !busy,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.v2_account_delete_now), maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancel, enabled = !busy, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel), maxLines = 1)
                    }
                    // A plain text, not an ErrorLine: its own expand would run inside this panel's, two animations of one height.
                    error?.let {
                        Text(
                            text = it.text(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

internal const val MAX_SLOTS = 3

private fun formatDate(iso: String): String? = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrNull()
