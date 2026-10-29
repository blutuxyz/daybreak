package com.bdersolutions.daybreak

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.bdersolutions.daybreak.ui.DaybreakRoot
import com.bdersolutions.daybreak.ui.theme.DaybreakTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyStoredLanguage()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DaybreakApplication).container
        setContent {
            DaybreakTheme {
                DaybreakRoot(container)
            }
        }
    }

    private fun applyStoredLanguage() {
        val application = application as? DaybreakApplication ?: return
        val language = application.container.settingsRepository.current().language
        val desired = language.tag
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != desired) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(desired))
        }
    }
}
