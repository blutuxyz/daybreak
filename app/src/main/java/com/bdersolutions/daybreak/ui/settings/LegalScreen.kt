package com.bdersolutions.daybreak.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.ui.legal.LegalDocument

/** The privacy policy and terms of use that were accepted at the start, to read again. */
@Composable
fun LegalScreen(onBack: () -> Unit) {
    SettingsPageScaffold(title = stringResource(R.string.legal_screen_title), onBack = onBack) {
        item { LegalDocument() }
    }
}
