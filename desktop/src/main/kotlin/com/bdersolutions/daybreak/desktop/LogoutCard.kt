package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The logout row, as on Android. Tapping it turns the card itself into the confirmation: it flows
 * into a muted red, says what is removed, and offers two buttons. No dialog opens over the list.
 */
@Composable
fun LogoutCard(onConfirm: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (confirming) lerp(scheme.surfaceContainerHigh, scheme.errorContainer, 0.55f) else scheme.surfaceContainerLow,
        animationSpec = DesktopMotion.effects(),
        label = "logoutContainer",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = confirming,
            transitionSpec = {
                (fadeIn(DesktopMotion.effects<Float>()) + scaleIn(DesktopMotion.spatial<Float>(), initialScale = 0.96f))
                    .togetherWith(fadeOut(DesktopMotion.fastEffects<Float>()))
            },
            label = "logoutContent",
        ) { isConfirming ->
            if (isConfirming) {
                LogoutConfirmation(
                    onCancel = { confirming = false },
                    onConfirm = onConfirm,
                )
            } else {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = tr("Abmelden", "Log out"),
                    subtitle = null,
                    onClick = { confirming = true },
                )
            }
        }
    }
}

@Composable
private fun LogoutConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(44.dp).background(scheme.error.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = scheme.error, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr("Wirklich abmelden?", "Log out for real?"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onErrorContainer,
                )
                Text(
                    tr(
                        "Zugangsschlüssel, Cache und Debug-Protokoll werden von diesem Gerät entfernt.",
                        "The access key, cache and debug log are removed from this computer.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(44.dp).handCursor(),
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onErrorContainer),
            ) {
                Text(tr("Abbrechen", "Cancel"))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).height(44.dp).handCursor(),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            ) {
                Text(tr("Abmelden", "Log out"))
            }
        }
    }
}
