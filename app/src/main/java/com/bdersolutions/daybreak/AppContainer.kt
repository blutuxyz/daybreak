package com.bdersolutions.daybreak

import android.content.Context
import com.bdersolutions.daybreak.data.attachments.AttachmentDownloader
import com.bdersolutions.daybreak.data.cache.AbsenceLessonStore
import com.bdersolutions.daybreak.data.cache.TimetableCacheStore
import com.bdersolutions.daybreak.data.debug.RawLogStore
import com.bdersolutions.daybreak.data.haptics.Haptics
import com.bdersolutions.daybreak.data.haptics.VibratorHapticOutput
import com.bdersolutions.daybreak.data.legal.TermsStore
import com.bdersolutions.daybreak.data.notifications.LessonNotificationManager
import com.bdersolutions.daybreak.data.repository.AbsenceLessonHistory
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.security.SecretVault
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.sync.SyncScheduler
import com.bdersolutions.daybreak.data.update.UpdateApi
import com.bdersolutions.daybreak.data.update.UpdateManager
import com.bdersolutions.daybreak.data.update.UpdateNotificationManager
import com.bdersolutions.daybreak.data.update.UpdateScheduler
import com.bdersolutions.daybreak.data.update.UpdateStore
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.widget.WidgetUpdater

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(appContext)

    // One haptics engine for the whole app. It reads the settings on every call, so edits apply at once.
    val haptics = Haptics(
        settings = { settingsRepository.current().haptics },
        output = VibratorHapticOutput(appContext),
        systemAllows = { VibratorHapticOutput.systemTouchFeedbackOn(appContext) },
    )
    val termsStore = TermsStore(appContext)
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
    val attachmentDownloader = AttachmentDownloader(appContext, schoolData)
    val absenceLessonHistory = AbsenceLessonHistory(
        store = AbsenceLessonStore(appContext),
        timetable = timetableRepository,
        sessionStore = sessionStore,
        api = untisApi,
    )
    val lessonNotificationManager = LessonNotificationManager(appContext, settingsRepository)
    val syncScheduler = SyncScheduler(appContext)
    val widgetUpdater = WidgetUpdater(appContext)

    // Updates come from the update server (update-server/). Without a configured address the
    // feature is off and the Updates screen is hidden.
    val updateManager = UpdateManager(
        context = appContext,
        api = BuildConfig.UPDATE_API_URL.takeIf { it.isNotBlank() }?.let { UpdateApi(it) },
        store = UpdateStore(appContext),
    )
    val updateNotificationManager = UpdateNotificationManager(appContext, settingsRepository)
    val updateScheduler = UpdateScheduler(appContext)

    fun start() {
        lessonNotificationManager.ensureChannel()
        if (sessionStore.isLoggedIn()) {
            syncScheduler.schedule(settingsRepository.current())
        }
        if (updateManager.isConfigured) {
            updateNotificationManager.ensureChannel()
            updateScheduler.schedule(settingsRepository.current().autoCheckUpdates)
        }
    }
}
