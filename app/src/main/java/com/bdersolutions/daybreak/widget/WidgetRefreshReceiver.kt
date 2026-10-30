package com.bdersolutions.daybreak.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater(appContext).update()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.bdersolutions.daybreak.widget.REFRESH"
    }
}
