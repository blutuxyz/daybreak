package com.bdersolutions.daybreak

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.bdersolutions.daybreak.ui.DaybreakApp
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyStoredLanguage()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DaybreakApplication).container
        val widgetDate = intent.getStringExtra(EXTRA_WIDGET_DATE)?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()
        }
        val openUpdates = intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)
        setContent {
            DaybreakApp(container = container, initialDate = widgetDate, openUpdates = openUpdates)
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

    companion object {
        const val EXTRA_WIDGET_DATE = "com.bdersolutions.daybreak.widget.DATE"
        const val EXTRA_OPEN_UPDATES = "com.bdersolutions.daybreak.OPEN_UPDATES"
    }
}
