package com.bdersolutions.daybreak.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

class WidgetUpdater(private val context: Context) {
    /**
     * Redraws every placed widget. The refresh stamp in each widget's Glance state makes a session
     * that is still alive recompose too, so it picks up the current time and data.
     */
    suspend fun update() {
        runCatching {
            val widget = DaybreakWidget()
            GlanceAppWidgetManager(context).getGlanceIds(DaybreakWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs[DaybreakWidget.REFRESH_KEY] = System.currentTimeMillis()
                }
                widget.update(context, id)
            }
        }
        WidgetUpdateScheduler.scheduleNext(context)
    }
}
