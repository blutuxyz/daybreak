@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.bdersolutions.daybreak.ui.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.OutlinedButton
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import kotlinx.coroutines.delay

/** The restore code in large type with a copy button. The button turns into a check mark for a moment. */
@Composable
fun RestoreCodeCard(code: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        onClick = {
            haptics.perform(HapticEvent.SELECT)
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText("Daybreak", code))
            copied = true
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            AnimatedContent(
                targetState = copied,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "copyIcon",
            ) { done ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (done) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.v2_account_copy),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                    if (done) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.v2_account_copied),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Make a new account, or sign in with a restore code. The code field opens under the second button with an
 * eased resize, so nothing around it jumps.
 */
@Composable
fun CreateOrRestorePanel(
    busy: Boolean,
    error: AccountError?,
    onCreate: () -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier = Modifier,
    createLabel: String = stringResource(R.string.v2_account_create),
) {
    var restoring by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    val haptics = LocalHaptics.current
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(elevation = null,
            onClick = onCreate,
            enabled = !busy,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            BusyText(busy && !restoring, createLabel)
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = restoring,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "restoreToggle",
        ) { open ->
            if (!open) {
                TextButton(
                    onClick = {
                        haptics.perform(HapticEvent.SELECT)
                        restoring = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.v2_account_have_code))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(24) },
                        label = { Text(stringResource(R.string.v2_account_code_label)) },
                        placeholder = { Text("DA-XXXX-XXXX-XXXX") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank() && !busy) onRestore(code) }),
                        isError = error == AccountError.UNKNOWN_CODE,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { onRestore(code) },
                        enabled = !busy && code.isNotBlank(),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        BusyText(busy && restoring, stringResource(R.string.v2_account_sign_in))
                    }
                }
            }
        }
        ErrorLine(error)
    }
}

@Composable
fun BusyText(busy: Boolean, text: String) {
    val alpha by animateFloatAsState(if (busy) 1f else 0f, label = "busyAlpha")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { this.alpha = alpha },
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(text)
    }
}

/** An error text under a form. It grows in and out instead of popping. */
@Composable
fun ErrorLine(error: AccountError?, modifier: Modifier = Modifier) {
    // Keep the last text while the line shrinks away.
    var last by remember { mutableStateOf(error) }
    if (error != null) last = error
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn() + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut() + shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        modifier = modifier,
    ) {
        Text(
            text = last?.text().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
