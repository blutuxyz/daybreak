package com.bdersolutions.daybreak.untis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    ): UntisTimetable {
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
