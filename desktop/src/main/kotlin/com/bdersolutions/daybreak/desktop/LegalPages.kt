package com.bdersolutions.daybreak.desktop

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.settings.AppLanguage

/** The sections of the privacy policy and terms, in the order of the Android app's LegalTerms. */
private val legalSectionKeys = listOf(
    "legal_what",
    "legal_privacy",
    "legal_stored",
    "legal_sent",
    "legal_rights",
    "legal_terms",
    "legal_liability",
    "legal_changes",
    "legal_contact",
)

/** The privacy policy and terms as one column of titled sections (the same texts as the Android app). */
@Composable
fun LegalDocument(modifier: Modifier = Modifier) {
    val language = LocalLanguage.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            AppStrings.format("legal_version_line", language, LEGAL_VERSION),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        legalSectionKeys.forEach { section ->
            Column {
                Text(
                    AppStrings.text("${section}_title", language),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    AppStrings.text("${section}_body", language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Settings → Privacy and terms: the same text, read again at any time. */
@Composable
fun LegalPage() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            LegalDocument()
        }
    }
}

/**
 * Shown before anything else until the current privacy policy and terms are accepted. It cannot be
 * dismissed: accept, or decline and the app closes. [isUpdate] says that the texts changed since the
 * last acceptance.
 */
@Composable
fun TermsGate(
    isUpdate: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val language = LocalLanguage.current
    val switchTo = if (language == AppLanguage.GERMAN) AppLanguage.ENGLISH else AppLanguage.GERMAN
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onLanguageChange(switchTo) }, modifier = Modifier.handCursor()) {
                Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(AppStrings.text(if (switchTo == AppLanguage.GERMAN) "language_german" else "language_english", language))
            }
        }
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                AppStrings.text("legal_screen_title", language),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                AppStrings.text(if (isUpdate) "legal_gate_updated" else "legal_gate_intro", language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                LegalDocument(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                AppStrings.text("legal_accept_hint", language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(), shape = MaterialTheme.shapes.extraLarge) {
                Text(AppStrings.text("legal_accept", language))
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().handCursor()) {
                Text(AppStrings.text("legal_decline", language))
            }
        }
    }
}
