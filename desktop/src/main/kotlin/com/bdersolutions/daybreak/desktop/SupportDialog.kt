package com.bdersolutions.daybreak.desktop

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdersolutions.daybreak.data.support.PaymentCheck
import com.bdersolutions.daybreak.data.support.SupportLinks
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

private val KofiBlue = Color(0xFF29ABE0)

/** Opens the Ko-fi page in the default browser. Does nothing when the computer has none. */
fun openSupportPage() {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(SupportLinks.KOFI_URL))
        }
    }
}

/** The Ko-fi button of the More page. It disappears once a payment was confirmed. */
@Composable
fun KofiButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = KofiBlue, contentColor = Color.White),
    ) {
        Text(str("settings_support_button"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Which page of the support dialog is showing. */
private enum class SupportStage { ASK, HIDE, THANKS }

/**
 * A rare, friendly request to support Daybreak on Ko-fi. Every feature a timetable needs stays free.
 *
 * As on Android, "Hide this popup" is paid: it leads to a second page with this computer's unlock
 * code, sends the person to Ko-fi to pay with the code in the message, and hides the popup for good
 * once [onCheckPayment] says the server has seen the payment. Without a server ([unlockCode] is null)
 * the free "Don't ask again" stays.
 */
@Composable
fun SupportDialog(
    unlockCode: String?,
    onSupport: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    onCheckPayment: suspend () -> PaymentCheck,
    onPaid: () -> Unit,
    onClosed: () -> Unit,
) {
    var stage by remember { mutableStateOf(SupportStage.ASK) }
    DesktopDialog(onDismiss = onClosed, maxWidth = 540.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (stage) {
                SupportStage.ASK -> {
                    DialogHeader(Icons.Outlined.Favorite, str("support_title"))
                    Text(str("support_body"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(str("support_privacy"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            onSupport()
                            close()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(),
                        shape = CircleShape,
                    ) {
                        Text(str("support_action"))
                    }
                    if (unlockCode != null) {
                        OutlinedButton(
                            onClick = { stage = SupportStage.HIDE },
                            modifier = Modifier.fillMaxWidth().height(48.dp).handCursor(),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(str("support_hide"))
                        }
                        TextButton(
                            onClick = {
                                onLater()
                                close()
                            },
                            modifier = Modifier.fillMaxWidth().handCursor(),
                        ) { Text(str("support_later")) }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    onLater()
                                    close()
                                },
                                modifier = Modifier.weight(1f).handCursor(),
                            ) { Text(str("support_later")) }
                            TextButton(
                                onClick = {
                                    onNever()
                                    close()
                                },
                                modifier = Modifier.weight(1f).handCursor(),
                            ) { Text(str("support_never")) }
                        }
                    }
                }

                SupportStage.HIDE -> {
                    DialogHeader(Icons.Outlined.VisibilityOff, str("support_hide_title"))
                    Text(str("support_hide_body"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    UnlockPanel(
                        code = checkNotNull(unlockCode),
                        onCheckPayment = onCheckPayment,
                        onPaid = {
                            onPaid()
                            stage = SupportStage.THANKS
                        },
                    ) {
                        Button(
                            onClick = { openSupportPage() },
                            modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(),
                            shape = CircleShape,
                        ) { Text(str("support_hide_open")) }
                    }
                    TextButton(onClick = { stage = SupportStage.ASK }, modifier = Modifier.fillMaxWidth().handCursor()) {
                        Text(str("support_hide_back"))
                    }
                }

                SupportStage.THANKS -> ThanksPage(onDone = {
                    onLater()
                    close()
                })
            }
        }
    }
}

/**
 * The dialog behind "Hide this button" and the locked supporter extras on the More page. It repeats the
 * Ko-fi button, shows [note] (what paying does), and holds the same unlock code and payment check as the
 * popup. The dialog thanks the person and closes once the server confirmed the payment.
 */
@Composable
fun HideSupportButtonDialog(
    unlockCode: String,
    note: String,
    onDismiss: () -> Unit,
    onCheckPayment: suspend () -> PaymentCheck,
) {
    var thanked by remember { mutableStateOf(false) }
    DesktopDialog(onDismiss = onDismiss, maxWidth = 540.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (thanked) {
                ThanksPage(onDone = close)
            } else {
                KofiButton(onClick = { openSupportPage() })
                Text(
                    note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                UnlockPanel(code = unlockCode, onCheckPayment = onCheckPayment, onPaid = { thanked = true }) {}
            }
        }
    }
}

@Composable
private fun ThanksPage(onDone: () -> Unit) {
    DialogHeader(Icons.Outlined.Favorite, str("support_thanks_title"))
    Text(str("support_thanks_body"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(),
        shape = CircleShape,
    ) { Text(str("support_thanks_done")) }
}

@Composable
private fun DialogHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * The paying part: this computer's unlock code with a Copy button, the steps, a slot for the button
 * that opens Ko-fi ([openButton]), and "Check payment". [onPaid] runs once the server confirms it.
 */
@Composable
private fun UnlockPanel(
    code: String,
    onCheckPayment: suspend () -> PaymentCheck,
    onPaid: () -> Unit,
    openButton: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PaymentCheck?>(null) }
    var copied by remember { mutableStateOf(false) }

    Text(str("support_hide_code_label"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                code,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    copyToClipboard(code)
                    copied = true
                },
                modifier = Modifier.handCursor(),
            ) {
                Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(str(if (copied) "support_hide_copied" else "support_hide_copy"))
            }
        }
    }
    Text(str("support_hide_steps"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    openButton()
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
        modifier = Modifier.fillMaxWidth().height(48.dp).handCursor(),
        shape = CircleShape,
    ) {
        if (checking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(str(if (checking) "support_hide_checking" else "support_hide_check"))
    }
    result?.let { check ->
        Text(
            str(if (check == PaymentCheck.NOT_FOUND) "support_hide_not_found" else "support_hide_offline"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Text(str("support_hide_privacy"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
