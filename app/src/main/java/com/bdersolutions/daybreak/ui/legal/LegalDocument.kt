package com.bdersolutions.daybreak.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.legal.LegalTerms
import com.bdersolutions.daybreak.data.legal.LegalText

/** The text on screen and its version, provided once by the app so every screen shows the same one. */
val LocalLegalText = compositionLocalOf { LegalText(LegalTerms.VERSION, null) }

/**
 * The privacy policy and terms of use, as one column of titled sections. Shared by the screen that
 * asks for acceptance and by the read-only copy under Settings, so both always show the same text.
 * The text is the server's copy when one is in use, otherwise the copy the app ships with.
 */
@Composable
fun LegalDocument(modifier: Modifier = Modifier) {
    val legal = LocalLegalText.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.legal_version_line, legal.version),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val remote = legal.sections
        if (remote != null) {
            remote.forEach { section -> LegalSectionBlock(section.title, section.body) }
        } else {
            LegalTerms.sections.forEach { section ->
                LegalSectionBlock(stringResource(section.title), stringResource(section.body))
            }
        }
    }
}

@Composable
private fun LegalSectionBlock(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
