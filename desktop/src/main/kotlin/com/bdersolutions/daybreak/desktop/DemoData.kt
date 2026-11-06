package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisChild
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.untis.UntisLoginResult
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Sample timetable for screenshots and layout checks. It is only used when the environment
 * variable DAYBREAK_DEMO=1 is set, together with DAYBREAK_DATA_DIR pointing at a separate folder,
 * so the real settings and the real login are never touched.
 */
object DemoData {
    /**
     * DAYBREAK_DEMO_UPDATE=1 shows the "Update ready" dialog as if a newer release was downloaded. The
     * release notes come from the file named by DAYBREAK_DEMO_CHANGELOG (Markdown, like a GitHub release).
     */
    private fun simulateUpdateIfRequested(container: DesktopContainer) {
        if (System.getenv("DAYBREAK_DEMO_UPDATE") != "1") return
        val changelog = System.getenv("DAYBREAK_DEMO_CHANGELOG")?.let { runCatching { java.io.File(it).readText() }.getOrNull() }
            ?: "## What's new\n- A pretend release for looking at the update dialog."
        val release = com.bdersolutions.daybreak.desktop.update.LatestRelease(
            tag = "v9.9.9",
            versionName = "9.9.9",
            versionCode = 90909,
            changelog = changelog,
            installer = com.bdersolutions.daybreak.desktop.update.WindowsBuild("https://demo.invalid/setup.exe", "Daybreak-9.9.9-setup.exe", null, null),
        )
        container.updater.simulateReady(release, java.io.File("demo-setup.exe"))
    }

    fun seedIfRequested(container: DesktopContainer) {
        if (System.getenv("DAYBREAK_DEMO") != "1") return
        val child = UntisChild(id = 1, firstName = "Max", lastName = "Mustermann", type = "STUDENT")
        container.sessionStore.saveLogin(
            UntisLoginResult(
                server = "https://demo.invalid",
                school = "demo",
                username = "max",
                secret = "DEMO-ONLY",
                displayName = "Max Mustermann",
                schoolName = "HTBLA Demo",
                children = listOf(child),
                usedPasswordLogin = false,
            ),
            child,
        )
        simulateUpdateIfRequested(container)
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val lessons = mutableListOf<UntisLesson>()
        var id = 1L
        val plan = listOf(
            listOf("ETE" to "Elektrotechnik", "GGP" to "Geografie", "ETE" to "Elektrotechnik", "MANL" to "Maschinenlehre"),
            listOf("E1" to "Englisch", "D" to "Deutsch", "M" to "Mathematik", "GGP" to "Geografie"),
            listOf("M" to "Mathematik", "ETE" to "Elektrotechnik", "D" to "Deutsch"),
            listOf("MANL" to "Maschinenlehre", "E1" to "Englisch", "M" to "Mathematik", "ETE" to "Elektrotechnik"),
            listOf("GGP" to "Geografie", "D" to "Deutsch", "MANL" to "Maschinenlehre", "E1" to "Englisch", "M" to "Mathematik"),
        )
        val rooms = listOf("OG-1001", "OG-1002", "EG-0112", "OG-2005", "EG-0108")
        val teachers = listOf("HUBER", "KRAB", "FANK", "MAYR", "LEHNER")
        plan.forEachIndexed { dayIndex, subjects ->
            val date = monday.plusDays(dayIndex.toLong())
            subjects.forEachIndexed { index, (short, long) ->
                val start = LocalTime.of(7, 55).plusMinutes(index * 55L + if (index > 1) 20L else 0L)
                val status = when {
                    dayIndex == 1 && index == 2 -> LessonStatus.CANCELLED
                    dayIndex == 3 && index == 1 -> LessonStatus.IRREGULAR
                    else -> LessonStatus.REGULAR
                }
                lessons += UntisLesson(
                    id = id++,
                    date = date,
                    start = start,
                    end = start.plusMinutes(50),
                    subject = short,
                    subjectLong = long,
                    teacher = teachers[(index + dayIndex) % teachers.size],
                    room = rooms[(index + dayIndex) % rooms.size],
                    className = "3AHMBA",
                    status = status,
                    substitutionText = if (status == LessonStatus.IRREGULAR) "Vertretung" else "",
                )
            }
        }
        val exam = UntisExam(
            id = 900,
            date = monday.plusDays(4),
            start = LocalTime.of(7, 55),
            end = LocalTime.of(9, 35),
            subject = "E1",
            name = "Englisch Schularbeit",
        )
        val cache = TimetableCache(
            studentId = 1,
            weeks = mapOf(monday to CachedWeek(monday, lessons, System.currentTimeMillis())),
            lastSyncAt = System.currentTimeMillis(),
            exams = listOf(exam),
        )
        runBlocking { container.timetableCacheStore.save(cache) }
    }
}
