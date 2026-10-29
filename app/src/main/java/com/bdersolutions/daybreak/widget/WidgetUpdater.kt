package com.bdersolutions.daybreak.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

class WidgetUpdater(private val context: Context) {
    suspend fun update() {
        runCatching { DaybreakWidget().updateAll(context) }
    }
}
