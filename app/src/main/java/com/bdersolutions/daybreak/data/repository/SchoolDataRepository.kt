package com.bdersolutions.daybreak.data.repository

import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.untis.UntisAuthException
import com.bdersolutions.daybreak.untis.UntisClassService
import com.bdersolutions.daybreak.untis.UntisDirectory
import com.bdersolutions.daybreak.untis.UntisElement
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisMessage
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.untis.UntisSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Whose timetable the timetable screen shows. */
sealed interface TimetableTarget {
    /** The signed-in student's own timetable, which is cached and synced. */
    data object Own : TimetableTarget

    /** A class, teacher or room. These are loaded on demand and kept in memory only. */
    data class Element(val element: UntisElement) : TimetableTarget {
        val cachePrefix: String
            get() = "${element.type.name}:${element.id}:"

        fun cacheKey(weekStart: LocalDate): String = cachePrefix + weekStart
    }
}

/**
 * Reads school data that is not part of the student's cached timetable: master data for
 * the picker, absences, and other classes', teachers' or rooms' timetables.
 */
class SchoolDataRepository(
    private val api: UntisApi,
    private val sessionStore: SessionStore,
) {
    private val _directory = MutableStateFlow<UntisDirectory?>(null)
    val directory: StateFlow<UntisDirectory?> = _directory.asStateFlow()

    /** Loads the master data once; [force] fetches it again. */
    suspend fun loadDirectory(force: Boolean = false): UntisDirectory {
        if (!force) _directory.value?.let { return it }
        val fresh = api.fetchDirectory(requireSession(), LocalDate.now())
        _directory.value = fresh
        return fresh
    }

    suspend fun absences(startDate: LocalDate, endDate: LocalDate): List<UntisAbsence> =
        api.fetchAbsences(requireSession(), startDate, endDate)

    suspend fun homework(startDate: LocalDate, endDate: LocalDate): List<UntisHomework> =
        api.fetchHomework(requireSession(), startDate, endDate)

    /** Whether the WebUntis password is stored, so web-only data loads without asking. */
    val passwordSaved: StateFlow<Boolean> get() = sessionStore.passwordSaved

    fun forgetPassword() = sessionStore.forgetPassword()

    /**
     * Class services need a web sign-in. Uses [password] when given, and stores it once it worked,
     * so it is only asked for once; otherwise uses the stored one. A password the server rejects
     * is dropped, so the next attempt asks again.
     */
    suspend fun classServices(password: String?, startDate: LocalDate, endDate: LocalDate): List<UntisClassService> {
        val supplied = password?.takeIf { it.isNotBlank() }
        val used = supplied ?: sessionStore.storedPassword() ?: throw UntisAuthException("No password.")
        try {
            val services = api.fetchClassServices(requireSession(), used, startDate, endDate)
            if (supplied != null) sessionStore.savePassword(supplied)
            return services
        } catch (error: UntisAuthException) {
            if (supplied == null) sessionStore.forgetPassword()
            throw error
        }
    }

    /**
     * The school's notices of [date]. With a stored password they come from the WebUntis start page
     * as well as from the mobile API; without one only from the mobile API. If one source fails the
     * other still shows.
     */
    suspend fun messages(date: LocalDate): List<UntisMessage> {
        val session = requireSession()
        val mobile = runCatching { api.fetchMessages(session, date) }
            .onFailure { if (it is CancellationException) throw it }
        val password = sessionStore.storedPassword()
        val web = password?.let {
            runCatching { api.fetchWebMessages(session, it, date) }
                .onFailure { error -> if (error is CancellationException) throw error }
        }
        if (web?.exceptionOrNull() is UntisAuthException) sessionStore.forgetPassword()

        val lists = listOfNotNull(mobile.getOrNull(), web?.getOrNull())
        if (lists.isEmpty()) {
            throw mobile.exceptionOrNull() ?: web?.exceptionOrNull() ?: UntisAuthException("Not signed in.")
        }
        return lists.flatten().distinctBy { it.subject to it.text }
    }

    /** Exams in a range. They carry only a subject id, so the master data names them. */
    suspend fun exams(startDate: LocalDate, endDate: LocalDate): List<UntisExam> {
        val names = loadDirectory().subjectNamesById
        return api.fetchExams(requireSession(), startDate, endDate, names)
    }

    suspend fun elementWeek(element: UntisElement, weekStart: LocalDate): List<UntisLesson> =
        api.fetchElementTimetable(
            session = requireSession(),
            element = element,
            startDate = weekStart,
            endDate = weekStart.plusDays(6),
        ).lessons

    fun clear() {
        _directory.value = null
    }

    private fun requireSession(): UntisSession =
        sessionStore.timetableSession() ?: throw UntisAuthException("Not signed in.")
}
