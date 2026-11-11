package com.bdersolutions.daybreak.ui.settings

import com.bdersolutions.daybreak.ui.components.TextButton
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R

/** One component that ships inside the app, with the licence it comes under. */
internal data class OpenSourceComponent(
    val name: String,
    val copyright: String,
    val license: License,
    val url: String,
)

/** The licences of the components. [text] is the full text in res/raw, or null for terms that live online. */
internal enum class License(val title: String, @RawRes val text: Int?, val url: String) {
    APACHE_2("Apache License 2.0", R.raw.license_apache_2_0, "https://www.apache.org/licenses/LICENSE-2.0"),
    MIT_KATEX("MIT License", R.raw.license_katex_mit, "https://opensource.org/license/mit"),
    GOOGLE_SDK("Android Software Development Kit License", null, "https://developer.android.com/studio/terms"),
}

/**
 * Everything the APK ships that is not Daybreak's own code (`app/build.gradle.kts`, `:shared`, `:untis`,
 * `assets/katex`). Keep it in step when a library is added: Apache 2.0 asks for its licence to be passed
 * on, MIT for the copyright notice.
 */
internal val OpenSourceComponents = listOf(
    OpenSourceComponent("Jetpack Compose (UI, Foundation, Material 3, Material Icons)", "© The Android Open Source Project", License.APACHE_2, "https://developer.android.com/jetpack/compose"),
    OpenSourceComponent("AndroidX AppCompat, Core, Activity, Lifecycle", "© The Android Open Source Project", License.APACHE_2, "https://developer.android.com/jetpack/androidx"),
    OpenSourceComponent("AndroidX WorkManager", "© The Android Open Source Project", License.APACHE_2, "https://developer.android.com/jetpack/androidx/releases/work"),
    OpenSourceComponent("AndroidX Glance (widget)", "© The Android Open Source Project", License.APACHE_2, "https://developer.android.com/jetpack/androidx/releases/glance"),
    OpenSourceComponent("Kotlin standard library", "© JetBrains s.r.o. and Kotlin Programming Language contributors", License.APACHE_2, "https://kotlinlang.org"),
    OpenSourceComponent("kotlinx.coroutines", "© JetBrains s.r.o. and contributors", License.APACHE_2, "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceComponent("OkHttp and Okio", "© Square, Inc.", License.APACHE_2, "https://square.github.io/okhttp/"),
    OpenSourceComponent("Gson", "© Google Inc.", License.APACHE_2, "https://github.com/google/gson"),
    OpenSourceComponent("KaTeX (formulas in the assistant, with its fonts)", "© 2013-2020 Khan Academy and other contributors", License.MIT_KATEX, "https://katex.org"),
    OpenSourceComponent("Google Play services code scanner", "© Google LLC", License.GOOGLE_SDK, "https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner"),
)

/** More → Open-source licences: the components, and the full licence text on a tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicensesScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<License?>(null) }
    val uriHandler = LocalUriHandler.current
    SettingsPageScaffold(title = stringResource(R.string.settings_licenses), onBack = onBack) {
        item {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item {
            SettingsGroup {
                OpenSourceComponents.forEachIndexed { index, component ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Code,
                        title = component.name,
                        subtitle = "${component.copyright}\n${component.license.title}",
                        onClick = {
                            if (component.license.text != null) open = component.license else uriHandler.openUri(component.license.url)
                        },
                    )
                }
            }
        }
    }

    open?.let { license ->
        val context = LocalContext.current
        val text = remember(license) {
            // The texts are wrapped for 80 columns; join each paragraph's lines so it flows on a phone.
            license.text?.let { id -> context.resources.openRawResource(id).bufferedReader().use { it.readText() } }.orEmpty()
                .trim()
                .split(Regex("\\n\\s*\\n"))
                .joinToString("\n\n") { paragraph -> paragraph.lines().joinToString(" ") { it.trim() } }
        }
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(license.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { uriHandler.openUri(license.url) }) { Text(license.url) }
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
