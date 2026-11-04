package com.bdersolutions.daybreak

import android.content.Context
import com.bdersolutions.daybreak.data.cache.TimetableCacheStore
import com.bdersolutions.daybreak.data.debug.RawLogStore
import com.bdersolutions.daybreak.data.notifications.LessonNotificationManager
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.security.SecretVault
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.sync.SyncScheduler
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.widget.WidgetUpdater

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(appContext)
    val secretVault = SecretVault(appContext)
    val sessionStore = SessionStore(appContext, secretVault)
    val rawLogStore = RawLogStore(appContext)
    val untisApi = UntisApi(rawLogStore)
    val timetableCacheStore = TimetableCacheStore(appContext)
    val timetableRepository = TimetableRepository(
        cacheStore = timetableCacheStore,
        sessionStore = sessionStore,
        api = untisApi,
    )
    val schoolData = SchoolDataRepository(untisApi, sessionStore)
    val lessonNotificationManager = LessonNotificationManager(appContext, settingsRepository)
    val syncScheduler = SyncScheduler(appContext)
    val widgetUpdater = WidgetUpdater(appContext)

    fun start() {
        lessonNotificationManager.ensureChannel()
        if (sessionStore.isLoggedIn()) {
            syncScheduler.schedule(settingsRepository.current())
        }
    }
}
