package com.bdersolutions.daybreak.data.session

import com.bdersolutions.daybreak.platform.KeyValueStore
import com.bdersolutions.daybreak.platform.SecretStore
import com.bdersolutions.daybreak.untis.UntisChild
import com.bdersolutions.daybreak.untis.UntisLoginResult
import com.bdersolutions.daybreak.untis.UntisSession
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DaybreakSession(
    val server: String,
    val school: String,
    val username: String,
    val displayName: String,
    val schoolName: String,
    val studentId: Long,
    val studentName: String,
    val studentType: String,
    val children: List<UntisChild>,
    val usedPasswordLogin: Boolean,
)

data class SessionCredentials(
    val session: DaybreakSession,
    val secret: String,
)

/**
 * Persists non-secret profile data and delegates the WebUntis key to [SecretStore].
 */
class SessionStore(
    private val preferences: KeyValueStore,
    private val vault: SecretStore,
) {
    private val lock = Any()
    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<DaybreakSession?> = _session.asStateFlow()

    fun isLoggedIn(): Boolean = _session.value != null && vault.getStoredSecret() != null

    fun currentCredentials(): SessionCredentials? {
        val current = _session.value ?: return null
        val secret = vault.getStoredSecret() ?: return null
        return SessionCredentials(current, secret)
    }

    fun saveLogin(result: UntisLoginResult, student: UntisChild): Boolean {
        if (!vault.storeSecret(result.secret)) return false
        val session = DaybreakSession(
            server = result.server,
            school = result.school,
            username = result.username,
            displayName = result.displayName.ifBlank { result.username },
            schoolName = result.schoolName.ifBlank { result.school },
            studentId = student.id,
            studentName = student.displayName,
            studentType = student.type.ifBlank { "STUDENT" },
            children = result.children,
            usedPasswordLogin = result.usedPasswordLogin,
        )
        synchronized(lock) {
            preferences.edit()
                .putString(KEY_SERVER, session.server)
                .putString(KEY_SCHOOL, session.school)
                .putString(KEY_USERNAME, session.username)
                .putString(KEY_DISPLAY_NAME, session.displayName)
                .putString(KEY_SCHOOL_NAME, session.schoolName)
                .putLong(KEY_STUDENT_ID, session.studentId)
                .putString(KEY_STUDENT_NAME, session.studentName)
                .putString(KEY_STUDENT_TYPE, session.studentType)
                .putString(KEY_CHILDREN, encodeChildren(session.children))
                .putBoolean(KEY_PASSWORD_LOGIN, session.usedPasswordLogin)
                .apply()
            _session.value = session
        }
        return true
    }

    fun selectStudent(student: UntisChild) {
        val current = _session.value ?: return
        val updated = current.copy(
            studentId = student.id,
            studentName = student.displayName,
            studentType = student.type.ifBlank { "STUDENT" },
        )
        synchronized(lock) {
            preferences.edit()
                .putLong(KEY_STUDENT_ID, updated.studentId)
                .putString(KEY_STUDENT_NAME, updated.studentName)
                .putString(KEY_STUDENT_TYPE, updated.studentType)
                .apply()
            _session.value = updated
        }
    }

    fun clear() {
        synchronized(lock) {
            preferences.edit().clear().apply()
            vault.clear()
            _session.value = null
        }
    }

    fun timetableSession(): UntisSession? {
        val credentials = currentCredentials() ?: return null
        val current = credentials.session
        return UntisSession(
            server = current.server,
            school = current.school,
            username = current.username,
            secret = credentials.secret,
            studentId = current.studentId,
            studentName = current.studentName,
            studentType = current.studentType,
        )
    }

    private fun loadSession(): DaybreakSession? {
        val server = preferences.getString(KEY_SERVER, null) ?: return null
        val school = preferences.getString(KEY_SCHOOL, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        if (vault.getStoredSecret() == null) return null
        val children = decodeChildren(preferences.getString(KEY_CHILDREN, null))
        return DaybreakSession(
            server = server,
            school = school,
            username = username,
            displayName = preferences.getString(KEY_DISPLAY_NAME, username) ?: username,
            schoolName = preferences.getString(KEY_SCHOOL_NAME, school) ?: school,
            studentId = preferences.getLong(KEY_STUDENT_ID, 0L),
            studentName = preferences.getString(KEY_STUDENT_NAME, "") ?: "",
            studentType = preferences.getString(KEY_STUDENT_TYPE, "STUDENT") ?: "STUDENT",
            children = children,
            usedPasswordLogin = preferences.getBoolean(KEY_PASSWORD_LOGIN, false),
        )
    }

    private fun encodeChildren(children: List<UntisChild>): String {
        val array = JsonArray()
        for (child in children) {
            array.add(com.google.gson.JsonObject().apply {
                addProperty("id", child.id)
                addProperty("firstName", child.firstName)
                addProperty("lastName", child.lastName)
                addProperty("type", child.type)
            })
        }
        return array.toString()
    }

    private fun decodeChildren(raw: String?): List<UntisChild> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JsonParser.parseString(raw) as JsonArray
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                val id = obj.get("id")?.asLong ?: return@mapNotNull null
                UntisChild(
                    id = id,
                    firstName = obj.get("firstName")?.asString.orEmpty(),
                    lastName = obj.get("lastName")?.asString.orEmpty(),
                    type = obj.get("type")?.asString ?: "STUDENT",
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val PREFS_NAME = "daybreak_session"
        const val KEY_SERVER = "server"
        const val KEY_SCHOOL = "school"
        const val KEY_USERNAME = "username"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_SCHOOL_NAME = "school_name"
        const val KEY_STUDENT_ID = "student_id"
        const val KEY_STUDENT_NAME = "student_name"
        const val KEY_STUDENT_TYPE = "student_type"
        const val KEY_CHILDREN = "children"
        const val KEY_PASSWORD_LOGIN = "password_login"
    }
}
