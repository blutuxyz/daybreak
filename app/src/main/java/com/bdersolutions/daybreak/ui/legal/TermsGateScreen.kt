package com.bdersolutions.daybreak.ui.legal

import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.Surface
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.ui.settings.currentAppLanguage

/**
 * Shown before anything else until the current privacy policy and terms are accepted. It is
 * not dismissable: the user accepts, or declines and the app closes. [isUpdate] is true when an
 * older version was accepted before, so the screen can say that the texts changed.
 */
@Composable
fun TermsGateScreen(
    isUpdate: Boolean,
    onAccept: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentLanguage = currentAppLanguage()
    val switchLanguage = if (currentLanguage == AppLanguage.GERMAN) AppLanguage.ENGLISH else AppLanguage.GERMAN

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onLanguageChange(switchLanguage) }) {
                    Icon(
                        Icons.Outlined.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringResource(
                            if (switchLanguage == AppLanguage.GERMAN) {
                                R.string.language_german
                            } else {
                                R.string.language_english
                            },
                        ),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.legal_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (isUpdate) R.string.legal_gate_updated else R.string.legal_gate_intro,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                LegalDocument(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.legal_accept_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.legal_accept))
            }
            TextButton(
                onClick = { context.findActivity()?.finishAffinity() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.legal_decline))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
