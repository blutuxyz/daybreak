package com.bdersolutions.daybreak.ui.support

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.support.PaymentCheck
import com.bdersolutions.daybreak.data.support.SupportLinks
import kotlinx.coroutines.launch

/** Opens the Ko-fi page in the browser. Does nothing if the phone has no browser. */
fun openSupportPage(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SupportLinks.KOFI_URL))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser installed: nothing to open.
    }
}

/** Which page of the support sheet is showing. */
private enum class SupportStage { ASK, HIDE, THANKS }

/**
 * A rare, friendly request to support Daybreak on Ko-fi. It is a bottom sheet like "What is new" and
 * says that every feature stays free.
 *
 * "Hide this popup" is paid: it leads to a second page that shows this phone's [unlockCode], sends
 * the person to Ko-fi to pay with that code in the message, and hides the popup for good once
 * [onCheckPayment] says the server has seen the payment. Where there is no server to ask
 * ([unlockCode] is null, as in a build without an update server) the free "Do not ask again" stays.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SupportSheet(
    unlockCode: String?,
    onSupport: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    onCheckPayment: suspend () -> PaymentCheck,
    onPaid: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable { mutableStateOf(SupportStage.ASK) }

    // Slide the sheet away first, then act, so every way of closing it is animated.
    fun closeThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            when (stage) {
                SupportStage.ASK -> AskPage(
                    canPayToHide = unlockCode != null,
                    onSupport = { closeThen(onSupport) },
                    onHide = { stage = SupportStage.HIDE },
                    onLater = { closeThen(onLater) },
                    onNever = { closeThen(onNever) },
                )

                SupportStage.HIDE -> HidePage(
                    code = checkNotNull(unlockCode),
                    onBack = { stage = SupportStage.ASK },
                    onCheckPayment = onCheckPayment,
                    onPaid = {
                        onPaid()
                        stage = SupportStage.THANKS
                    },
                )

                SupportStage.THANKS -> ThanksPage(onDone = { closeThen(onLater) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
    }
}

@Composable
private fun AskPage(
    canPayToHide: Boolean,
    onSupport: () -> Unit,
    onHide: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
) {
    SheetHeader(Icons.Outlined.Favorite, stringResource(R.string.support_title))
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.support_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.support_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onSupport,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape,
    ) {
        Text(stringResource(R.string.support_action))
    }
    Spacer(Modifier.height(8.dp))
    if (canPayToHide) {
        OutlinedButton(
            onClick = onHide,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = CircleShape,
        ) {
            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.support_hide))
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.support_later))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onLater, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.support_later))
            }
            TextButton(onClick = onNever, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.support_never))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HidePage(
    code: String,
    onBack: () -> Unit,
    onCheckPayment: suspend () -> PaymentCheck,
    onPaid: () -> Unit,
) {
    val context = LocalContext.current
    SheetHeader(Icons.Outlined.VisibilityOff, stringResource(R.string.support_hide_title))
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.support_hide_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(16.dp))
    UnlockPanel(code = code, onCheckPayment = onCheckPayment, onPaid = onPaid) {
        Button(
            onClick = { openSupportPage(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = CircleShape,
        ) {
            Text(stringResource(R.string.support_hide_open))
        }
    }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.support_hide_back))
    }
}

/**
 * The paying part of hiding: this phone's unlock code with a Copy button, the steps, a slot for the
 * button that opens Ko-fi ([openButton]), and "Check payment". [onPaid] runs once the server
 * confirms the payment.
 */
@Composable
internal fun UnlockPanel(
    code: String,
    onCheckPayment: suspend () -> PaymentCheck,
    onPaid: () -> Unit,
    openButton: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PaymentCheck?>(null) }
    var copied by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.support_hide_code_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Daybreak", code))
                    copied = true
                },
            ) {
                Icon(
                    if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(if (copied) R.string.support_hide_copied else R.string.support_hide_copy))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.support_hide_steps),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    openButton()
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            if (checking) return@OutlinedButton
            checking = true
            result = null
            scope.launch {
                val check = onCheckPayment()
                checking = false
                if (check == PaymentCheck.PAID) onPaid() else result = check
            }
        },
        enabled = !checking,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = CircleShape,
    ) {
        if (checking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(stringResource(if (checking) R.string.support_hide_checking else R.string.support_hide_check))
    }
    result?.let { check ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (check == PaymentCheck.NOT_FOUND) R.string.support_hide_not_found else R.string.support_hide_offline,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.support_hide_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * The sheet behind "Hide this button" and the locked supporter extras on the More page. It repeats the
 * Ko-fi button, shows [note] (what paying does), and holds the same unlock code and payment check as
 * the popup.
 * [onPaid] runs when the server confirmed the payment; the sheet then thanks the person and closes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HideSupportButtonSheet(
    unlockCode: String,
    note: String,
    onDismiss: () -> Unit,
    onCheckPayment: suspend () -> PaymentCheck,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var thanked by rememberSaveable { mutableStateOf(false) }

    fun closeThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            if (thanked) {
                ThanksPage(onDone = { closeThen(onDismiss) })
            } else {
                KofiButton(onClick = { openSupportPage(context) })
                Spacer(Modifier.height(10.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                UnlockPanel(
                    code = unlockCode,
                    onCheckPayment = onCheckPayment,
                    onPaid = { thanked = true },
                ) {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThanksPage(onDone: () -> Unit) {
    SheetHeader(Icons.Outlined.Favorite, stringResource(R.string.support_thanks_title))
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.support_thanks_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape,
    ) {
        Text(stringResource(R.string.support_thanks_done))
    }
}
