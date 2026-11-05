package com.bdersolutions.daybreak.untis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CancellationException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Public entry point of the :untis module. The rest of the app never deals with
 * JSON-RPC payloads, URLs, cookies or TOTP directly.
 */
class UntisApi(
    rawSink: RawResponseSink = RawResponseSink { _, _, _ -> },
) {
    private val rpc = JsonRpcClient(rawSink)

    /**
     * Logs in with the secret from an Untis Mobile QR code (or a manually entered key).
     */
    suspend fun loginWithSecret(
        server: String,
        school: String,
        username: String,
        secret: String,
    ): UntisLoginResult {
        val safeServer = normalizeInputs(server, school, username)
        val safeSecret = secret.trim()
        if (safeSecret.isBlank()) throw UntisAuthException("The WebUntis key is empty.")
        validateSecret(safeSecret)
        return loginResolved(safeServer, school.trim(), username.trim(), safeSecret, usedPasswordLogin = false)
    }

    /**
     * Password fallback used when a user has no QR code. WebUntis derives the mobile
     * app secret from the password; the password itself is never returned or stored.
     */
    suspend fun loginWithPassword(
        server: String,
        school: String,
        username: String,
        password: String,
        secondFactor: String = "",
    ): UntisLoginResult {
        val safeServer = normalizeInputs(server, school, username)
        if (password.isBlank()) throw UntisAuthException("The password is empty.")

        val params = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("userName", username.trim())
                addProperty("password", password)
                if (secondFactor.isNotBlank()) addProperty("token", secondFactor.trim())
            })
        }
        val result = rpc.call(
            server = safeServer,
            school = school.trim(),
            method = "getAppSharedSecret",
            params = params,
            // The response contains the long-lived mobile key. It must never reach the debug log.
            recordRaw = false,
        )
        val secret = extractSecret(result)
        validateSecret(secret)
        return loginResolved(safeServer, school.trim(), username.trim(), secret, usedPasswordLogin = true)
    }

    suspend fun fetchTimetable(
        session: UntisSession,
        startDate: LocalDate,
        endDate: LocalDate,
    ): UntisTimetable = requestTimetable(
        session = session,
        id = session.studentId,
        type = session.studentType.ifBlank { "STUDENT" }.uppercase(),
        startDate = startDate,
        endDate = endDate,
    )

    /** Timetable of a class, teacher or room, as the same WebUntis account may see it. */
    suspend fun fetchElementTimetable(
        session: UntisSession,
        element: UntisElement,
        startDate: LocalDate,
        endDate: LocalDate,
    ): UntisTimetable = requestTimetable(
        session = session,
        id = element.id,
        type = element.type.name,
        startDate = startDate,
        endDate = endDate,
    )

    /** Classes, teachers, rooms and absence reasons, read from the master data of one week. */
    suspend fun fetchDirectory(session: UntisSession, date: LocalDate): UntisDirectory {
        val week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return fetchTimetable(session, week, week.plusDays(6)).directory
    }

    /**
     * The student's absences in a date range. Both excused and unexcused absences are
     * requested explicitly, since the server does not return them by default.
     */
    suspend fun fetchAbsences(
        session: UntisSession,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<UntisAbsence> {
        require(!endDate.isBefore(startDate)) { "The end date is before the start date." }
        val safeServer = normalizeInputs(session.server, session.school, session.username)
        if (session.secret.isBlank()) throw UntisAuthException("The WebUntis key is missing.")
        val type = session.studentType.ifBlank { "STUDENT" }.uppercase()

        val params = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("id", session.studentId)
                addProperty("type", type)
                addProperty("startDate", utisDate(startDate))
                addProperty("endDate", utisDate(endDate))
                addProperty("includeExcused", true)
                addProperty("includeUnExcused", true)
                add("auth", auth(session.username, session.secret))
            })
        }
        val result = rpc.call(
            server = safeServer,
            school = session.school,
            method = "getStudentAbsences2017",
            params = params,
        )
        return SchoolDataParser.absences(result)
    }

    /**
     * The student's exams (Prüfungen) in a date range. Unverified against every server:
     * the raw response is written to the debug log, so the shape can be checked there.
     */
    suspend fun fetchExams(
        session: UntisSession,
        startDate: LocalDate,
        endDate: LocalDate,
        subjectNames: Map<Long, String> = emptyMap(),
    ): List<UntisExam> {
        require(!endDate.isBefore(startDate)) { "The end date is before the start date." }
        val safeServer = normalizeInputs(session.server, session.school, session.username)
        if (session.secret.isBlank()) throw UntisAuthException("The WebUntis key is missing.")
        val type = session.studentType.ifBlank { "STUDENT" }.uppercase()

        val params = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("id", session.studentId)
                addProperty("type", type)
                addProperty("startDate", utisDate(startDate))
                addProperty("endDate", utisDate(endDate))
                add("auth", auth(session.username, session.secret))
            })
        }
        val result = rpc.call(
            server = safeServer,
            school = session.school,
            method = "getExams2017",
            params = params,
        )
        return ExamParser.parse(result, subjectNames)
    }

    /** The student's homework (Hausaufgaben) that is due in a date range. */
    suspend fun fetchHomework(
        session: UntisSession,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<UntisHomework> {
        require(!endDate.isBefore(startDate)) { "The end date is before the start date." }
        val safeServer = normalizeInputs(session.server, session.school, session.username)
        if (session.secret.isBlank()) throw UntisAuthException("The WebUntis key is missing.")
        val params = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("id", session.studentId)
                addProperty("type", session.studentType.ifBlank { "STUDENT" }.uppercase())
                addProperty("startDate", utisDate(startDate))
                addProperty("endDate", utisDate(endDate))
                add("auth", auth(session.username, session.secret))
            })
        }
        val result = try {
            rpc.call(safeServer, session.school, "getHomeWork2017", params)
        } catch (_: UntisNoDataException) {
            return emptyList()
        }
        return HomeworkParser.parse(result)
    }

    /** The messages of one folder of the message box (Nachrichten), newest first. */
    suspend fun fetchMailList(session: UntisSession, folder: MailFolder): List<UntisMail> {
        val path = when (folder) {
            MailFolder.INBOX -> "messages"
            MailFolder.SENT -> "messages/sent"
            MailFolder.DRAFTS -> "messages/drafts"
        }
        val body = restGet(session, path, "mail-${folder.name.lowercase()}")
        return MailParser.parseList(folder, body)
            .sortedByDescending { it.sentAt ?: LocalDateTime.MIN }
    }

    /** One message with its full text. */
    suspend fun fetchMail(session: UntisSession, id: Long): UntisMail {
        val body = restGet(session, "messages/$id", "mail-message")
        return MailParser.parseMessage(body) ?: throw UntisParseException("The message could not be read.")
    }

    /**
     * GET on the message REST API. It does not take the mobile key itself but a short-lived token
     * that `getAuthToken` hands out for it, so no password is involved.
     */
    private suspend fun restGet(
        session: UntisSession,
        path: String,
        label: String,
        recordRaw: Boolean = true,
    ): JsonElement {
        val safeServer = normalizeInputs(session.server, session.school, session.username)
        val token = restToken(session, safeServer)
        return rpc.getRest(
            server = safeServer,
            path = "WebUntis/api/rest/view/v1/$path",
            query = emptyMap(),
            token = token,
            label = label,
            recordRaw = recordRaw,
        )
    }

    /**
     * The signed address and headers for one attached file. [attachmentId] is the id of an entry of
     * `storageAttachments`. The answer holds secrets, so it is not written to the debug log.
     */
    suspend fun fetchAttachmentLink(session: UntisSession, attachmentId: String): UntisDownloadLink {
        if (attachmentId.isBlank() || attachmentId.any { it == '/' || it == '\\' || it == '?' || it == '#' }) {
            throw UntisParseException("The attachment id is not valid.")
        }
        val body = restGet(session, "messages/$attachmentId/attachmentstorageurl", "mail-attachment-link", recordRaw = false)
        return AttachmentLinkParser.parse(body) ?: throw UntisParseException("The attachment has no download address.")
    }

    /** Downloads the file behind [link] into [out]. Returns its size in bytes. */
    suspend fun downloadAttachment(link: UntisDownloadLink, out: java.io.OutputStream, maxBytes: Long): Long =
        rpc.downloadFile(link.url, link.headers, out, maxBytes)

    /** The people the student may write to: class teachers, teachers and others. */
    suspend fun fetchRecipients(session: UntisSession): List<UntisRecipient> =
        RecipientParser.parse(restGet(session, "messages/recipients/static/persons", "mail-recipients"))

    /**
     * Sends a message to exactly one teacher, as the WebUntis web app does for a student: a
     * multipart POST to `messages/users` whose JSON part names the recipient by user id
     * (`recipientUserIds`). The plain `messages` endpoint wants a `recipientOption` instead. Returns the
     * number of recipients the server reports. Students may only write to a single teacher.
     */
    suspend fun sendMail(session: UntisSession, subject: String, content: String, recipientUserId: Long): Int {
        require(recipientUserId > 0) { "The recipient is missing." }
        if (subject.isBlank()) throw UntisParseException("The subject is empty.")
        if (content.isBlank()) throw UntisParseException("The message is empty.")
        val safeServer = normalizeInputs(session.server, session.school, session.username)
        val token = restToken(session, safeServer)
        val request = JsonObject().apply {
            addProperty("subject", subject.trim())
            addProperty("content", content.trim())
            addProperty("requestConfirmation", false)
            add("recipientUserIds", JsonArray().apply { add(recipientUserId) })
            add("oneDriveAttachments", JsonArray())
            addProperty("forbidReply", false)
        }
        val answer = rpc.postJsonPart(
            server = safeServer,
            path = "WebUntis/api/rest/view/v2/messages/users",
            token = token,
            // Only the server's answer is logged, never the text of the message.
            label = "mail-send",
            partName = "request",
            json = request,
        )
        return answer.asJsonObjectOrNull()?.intOrNull("numberOfRecipients") ?: 0
    }

    /** A short-lived token for the message REST API; `getAuthToken` hands it out for the mobile key. */
    private suspend fun restToken(session: UntisSession, safeServer: String): String {
        if (session.secret.isBlank()) throw UntisAuthException("The WebUntis key is missing.")
        val tokenResult = rpc.call(
            server = safeServer,
            school = session.school,
            method = "getAuthToken",
            params = JsonArray().apply {
                add(JsonObject().apply { add("auth", auth(session.username, session.secret)) })
            },
            // The token must never reach the debug log.
            recordRaw = false,
        )
        return tokenResult.asJsonObjectOrNull()?.stringOrNull("token", "accessToken")
            ?: tokenResult.takeIf { it.isJsonPrimitive }?.asString
            ?: throw UntisParseException("WebUntis returned no token.")
    }

    /** The school's notices (Mitteilungen) that are shown on [date]. */
    suspend fun fetchMessages(session: UntisSession, date: LocalDate): List<UntisMessage> {
        val safeServer = normalizeInputs(session.server, session.school, session.username)
        if (session.secret.isBlank()) throw UntisAuthException("The WebUntis key is missing.")
        val params = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("date", utisDate(date))
                add("auth", auth(session.username, session.secret))
            })
        }
        val result = try {
            rpc.call(safeServer, session.school, "getMessagesOfDay2017", params)
        } catch (_: UntisNoDataException) {
            return emptyList()
        }
        return MessageParser.parse(result)
    }

    private suspend fun requestTimetable(
        session: UntisSession,
        id: Long,
        type: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): UntisTimetable {
        require(!endDate.isBefore(startDate)) { "The end date is before the start date." }
        val safeServer = normalizeInputs(session.server, session.school, session.username)
        if (session.secret.isBlank()) throw UntisAuthException("The WebUntis key is missing.")

        val params = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("id", id)
                addProperty("type", type)
                addProperty("startDate", utisDate(startDate))
                addProperty("endDate", utisDate(endDate))
                addProperty("masterDataTimestamp", 0L)
                addProperty("timetableTimestamp", 0L)
                add("timetableTimestamps", JsonArray())
                add("auth", auth(session.username, session.secret))
            })
        }

        val result = rpc.call(
            server = safeServer,
            school = session.school,
            method = "getTimetable2017",
            params = params,
        )
        return TimetableParser.parse(result)
    }

    /**
     * Debug only. Calls the endpoints Daybreak does not use yet, once each: the
     * student's own timetable (to find class, teacher and room ids), absences, and
     * the class, teacher and room timetables. Each call is isolated, so one failure
     * does not stop the rest. Raw responses are recorded by the debug log sink.
     */
    suspend fun runDiagnostics(
        session: UntisSession,
        timetableStart: LocalDate,
        timetableEnd: LocalDate,
        absenceStart: LocalDate,
        absenceEnd: LocalDate,
    ): List<ProbeResult> {
        val safeServer = normalizeInputs(session.server, session.school, session.username)
        val type = session.studentType.ifBlank { "STUDENT" }.uppercase()
        val results = mutableListOf<ProbeResult>()

        suspend fun call(
            label: String,
            method: String,
            params: JsonObject,
            describe: (JsonElement) -> String,
        ): JsonElement? = try {
            val result = rpc.call(
                server = safeServer,
                school = session.school,
                method = method,
                params = JsonArray().apply { add(params) },
            )
            results += ProbeResult(label, method, ok = true, message = describe(result))
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            results += ProbeResult(
                label = label,
                method = method,
                ok = false,
                message = error.message ?: error::class.java.simpleName,
            )
            null
        }

        fun timetableParams(id: Long, objectType: String, start: LocalDate, end: LocalDate) =
            JsonObject().apply {
                addProperty("id", id)
                addProperty("type", objectType)
                addProperty("startDate", utisDate(start))
                addProperty("endDate", utisDate(end))
                addProperty("masterDataTimestamp", 0L)
                addProperty("timetableTimestamp", 0L)
                add("timetableTimestamps", JsonArray())
                add("auth", auth(session.username, session.secret))
            }

        fun timetableSummary(element: JsonElement): String {
            val lessons = runCatching { TimetableParser.parse(element).lessons.size }.getOrDefault(-1)
            return "OK, $lessons lessons parsed"
        }

        fun masterArray(element: JsonElement?, vararg keys: String): JsonArray? =
            element.asJsonObjectOrNull()?.objectOrNull("masterData")?.arrayOrNull(*keys)

        fun shapeOf(element: JsonElement): String = when {
            element.isJsonObject -> "OK, keys: " + element.asJsonObject.keySet().joinToString(", ")
            element.isJsonArray -> "OK, list with ${element.asJsonArray.size()} entries"
            else -> "OK, value: " + element.toString().take(80)
        }

        val studentResult = call(
            label = "Student timetable",
            method = "getTimetable2017",
            params = timetableParams(session.studentId, type, timetableStart, timetableEnd),
        ) { result ->
            val classes = masterArray(result, "klassen", "classes")?.size() ?: 0
            val teachers = masterArray(result, "teachers")?.size() ?: 0
            val rooms = masterArray(result, "rooms")?.size() ?: 0
            "${timetableSummary(result)}, masterData: $classes classes, $teachers teachers, $rooms rooms"
        }

        call(
            label = "Absences",
            method = "getStudentAbsences2017",
            params = JsonObject().apply {
                addProperty("id", session.studentId)
                addProperty("type", type)
                addProperty("startDate", utisDate(absenceStart))
                addProperty("endDate", utisDate(absenceEnd))
                addProperty("includeExcused", true)
                addProperty("includeUnExcused", true)
                add("auth", auth(session.username, session.secret))
            },
            describe = ::shapeOf,
        )

        // Endpoints for the Upcoming and Account pages. Their shapes are read from the debug log.
        val today = LocalDate.now()
        fun rangeParams() = JsonObject().apply {
            addProperty("id", session.studentId)
            addProperty("type", type)
            addProperty("startDate", utisDate(today.minusDays(7)))
            addProperty("endDate", utisDate(today.plusDays(28)))
            add("auth", auth(session.username, session.secret))
        }
        call("Homework", "getHomeWork2017", rangeParams(), ::shapeOf)
        call(
            label = "Messages of the day",
            method = "getMessagesOfDay2017",
            params = JsonObject().apply {
                addProperty("date", utisDate(today))
                add("auth", auth(session.username, session.secret))
            },
            describe = ::shapeOf,
        )
        val classId = masterArray(studentResult, "klassen", "classes")
            ?.firstNotNullOfOrNull { it.asJsonObjectOrNull()?.longOrNull("id") }
        val teacherId = masterArray(studentResult, "teachers")
            ?.firstNotNullOfOrNull { it.asJsonObjectOrNull()?.longOrNull("id") }

        listOf(
            Triple("Class timetable", "CLASS", classId),
            Triple("Teacher timetable", "TEACHER", teacherId),
        ).forEach { (label, objectType, id) ->
            if (id == null) {
                results += ProbeResult(
                    label = label,
                    method = "getTimetable2017",
                    ok = false,
                    message = "no $objectType id found in masterData of the student timetable",
                )
            } else {
                call(
                    label = label,
                    method = "getTimetable2017",
                    params = timetableParams(id, objectType, timetableStart, timetableEnd),
                    describe = ::timetableSummary,
                )
            }
        }

        // A room can be empty in a given week, so try a few rooms until one has lessons.
        val roomIds = masterArray(studentResult, "rooms")
            ?.mapNotNull { it.asJsonObjectOrNull()?.longOrNull("id") }
            .orEmpty()
        if (roomIds.isEmpty()) {
            results += ProbeResult(
                label = "Room timetable",
                method = "getTimetable2017",
                ok = false,
                message = "no ROOM id found in masterData of the student timetable",
            )
        }
        val maxRoomProbes = 5
        for ((index, roomId) in roomIds.take(maxRoomProbes).withIndex()) {
            val result = call(
                label = "Room timetable #${index + 1}",
                method = "getTimetable2017",
                params = timetableParams(roomId, "ROOM", timetableStart, timetableEnd),
                describe = ::timetableSummary,
            ) ?: continue
            val lessons = runCatching { TimetableParser.parse(result).lessons.size }.getOrDefault(0)
            if (lessons > 0) break
        }

        return results
    }

    private suspend fun loginResolved(
        server: String,
        school: String,
        username: String,
        secret: String,
        usedPasswordLogin: Boolean,
    ): UntisLoginResult {
        val userDataResult = rpc.call(
            server = server,
            school = school,
            method = "getUserData2017",
            params = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("deviceOs", "AND")
                    addProperty("deviceOsVersion", "14")
                    add("auth", auth(username, secret))
                })
            },
        )
        val userData = UserDataParser.parse(userDataResult)
        return UntisLoginResult(
            server = server,
            school = school,
            username = username,
            secret = secret,
            displayName = userData.displayName,
            schoolName = userData.schoolName.ifBlank { school },
            children = userData.students,
            usedPasswordLogin = usedPasswordLogin,
        )
    }

    private fun auth(username: String, secret: String): JsonObject {
        val now = System.currentTimeMillis()
        val otp = try {
            Totp.generate(secret, now)
        } catch (error: IllegalArgumentException) {
            throw UntisAuthException("The WebUntis key is not valid base32.", cause = error)
        }
        return JsonObject().apply {
            addProperty("user", username)
            addProperty("otp", otp)
            addProperty("clientTime", now)
        }
    }

    private fun validateSecret(secret: String) {
        try {
            Totp.generate(secret, System.currentTimeMillis())
        } catch (error: IllegalArgumentException) {
            throw UntisAuthException("The WebUntis key is not valid base32.", cause = error)
        }
    }

    private fun extractSecret(result: JsonElement): String {
        result.asPlainStringOrNull()?.let { return it }
        val obj = result.asJsonObjectOrNull()
            ?: throw UntisAuthException("WebUntis did not return a mobile key.")
        return obj.stringOrNull("key", "secret", "appSharedSecret", "sharedSecret", "result").orEmpty()
            .ifBlank { throw UntisAuthException("WebUntis did not return a mobile key.") }
    }

    private fun normalizeInputs(server: String, school: String, username: String): String {
        if (school.isBlank()) throw UntisAuthException("The school name is empty.")
        if (username.isBlank()) throw UntisAuthException("The user name is empty.")
        return try {
            UntisEndpoints.normalizeServer(server)
        } catch (error: IllegalArgumentException) {
            throw UntisAuthException(error.message ?: "The WebUntis server is invalid.", cause = error)
        }
    }

    private fun utisDate(date: LocalDate): Int =
        date.format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    private data class ParsedUserData(
        val displayName: String,
        val schoolName: String,
        val students: List<UntisChild>,
    )

    private object UserDataParser {
        fun parse(result: JsonElement): ParsedUserData {
            val root = result.asJsonObjectOrNull()
                ?: throw UntisParseException("The login response is not an object.")
            val data = root.objectOrNull("userData") ?: root

            val ownId = data.longOrNull("elemId", "elementId", "id") ?: 0L
            val ownType = data.stringOrNull("elemType", "elementType")
                ?: typeName((data.get("elemType") as? JsonPrimitive)?.takeIf { it.isNumber }?.asInt)
            val displayName = data.stringOrNull("displayName", "name", "username").orEmpty()
            val schoolName = data.stringOrNull("schoolName", "school").orEmpty()
            val students = mutableListOf<UntisChild>()

            data.arrayOrNull("children", "students")?.forEach { raw ->
                val child = raw.asJsonObjectOrNull() ?: return@forEach
                val id = child.longOrNull("id", "studentId", "elemId") ?: return@forEach
                val firstName = child.stringOrNull(
                    "firstName",
                    "firstname",
                    "foreName",
                    "forename",
                    "name",
                    "displayName",
                ).orEmpty()
                val lastName = child.stringOrNull("lastName", "lastname").orEmpty()
                val type = child.stringOrNull("type", "elemType")
                    ?: typeName((child.get("type") as? JsonPrimitive)?.takeIf { it.isNumber }?.asInt)
                    ?: "STUDENT"
                students += UntisChild(id, firstName, lastName, normalizeElementType(type))
            }

            if (students.isEmpty() && ownId != 0L) {
                val cleanType = normalizeElementType(ownType ?: "STUDENT")
                students += UntisChild(
                    id = ownId,
                    firstName = displayName.ifBlank { "Student" },
                    type = cleanType,
                )
            }

            val distinct = students.distinctBy { it.id }
            if (distinct.isEmpty()) {
                throw UntisNoStudentException(
                    "No student was linked to this WebUntis account.",
                )
            }

            return ParsedUserData(
                displayName = displayName,
                schoolName = schoolName,
                students = distinct,
            )
        }

        private fun typeName(value: Int?): String? = when (value) {
            1 -> "CLASS"
            2 -> "TEACHER"
            3 -> "SUBJECT"
            4 -> "ROOM"
            5 -> "STUDENT"
            12 -> "LEGAL_GUARDIAN"
            else -> null
        }

        private fun normalizeElementType(value: String): String {
            val normalized = value.uppercase()
            return when (normalized) {
                "STUDENT", "TEACHER", "CLASS" -> normalized
                else -> "STUDENT"
            }
        }
    }
}
