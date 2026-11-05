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
        // Long-pressing the app icon offers Today, Week and Search (res/xml/shortcuts.xml).
        val shortcut = intent.getStringExtra(EXTRA_SHORTCUT)
        setContent {
            DaybreakApp(
                container = container,
                initialDate = widgetDate,
                openUpdates = openUpdates,
                startMode = when (shortcut) {
                    SHORTCUT_TODAY -> 0
                    SHORTCUT_WEEK -> 1
                    else -> null
                },
                openSearch = shortcut == SHORTCUT_SEARCH,
            )
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
        const val EXTRA_SHORTCUT = "com.bdersolutions.daybreak.SHORTCUT"
        const val SHORTCUT_TODAY = "today"
        const val SHORTCUT_WEEK = "week"
        const val SHORTCUT_SEARCH = "search"
    }
}
