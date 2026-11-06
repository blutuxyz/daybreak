package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.support.SupportLinks
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

/**
 * The Ko-fi button of the More page. It stays on the desktop: donating unlocks nothing there, so the
 * button is never hidden.
 */
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

/**
 * A rare, friendly request to support Daybreak on Ko-fi. Every feature stays free, and donating gives
 * no advantage on the desktop app (the terms say so). "Don't ask again" is the only way to stop it.
 */
@Composable
fun SupportDialog(
    onSupport: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    onClosed: () -> Unit,
) {
    DesktopDialog(onDismiss = onClosed, maxWidth = 540.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                }
                Text(str("support_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onLater()
                        close()
                    },
                    modifier = Modifier.weight(1f).handCursor(),
                    shape = CircleShape,
                ) {
                    Text(str("support_later"))
                }
                TextButton(
                    onClick = {
                        onNever()
                        close()
                    },
                    modifier = Modifier.weight(1f).handCursor(),
                ) {
                    Text(str("support_never"))
                }
            }
        }
    }
}
