package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.untis.UntisAuthException
import com.bdersolutions.daybreak.untis.UntisNetworkException
import com.bdersolutions.daybreak.untis.UntisNoStudentException
import com.bdersolutions.daybreak.untis.UntisParseException
import com.bdersolutions.daybreak.untis.UntisPermissionException

/**
 * A message a student can understand, for a failed load. Mirrors the Android app's `toUserMessage`:
 * a school that switches a view off for students is told so, instead of "sign-in failed" or a raw
 * server sentence. [fallback] is used when the exception carries nothing better.
 */
fun Throwable.userMessage(language: AppLanguage, fallback: String): String = when (this) {
    is UntisPermissionException -> trFor(
        language,
        "Deine Schule erlaubt diese Ansicht oder Funktion für Schülerinnen und Schüler nicht. Das ist eine Einstellung deiner Schule in WebUntis, kein Fehler der App.",
        "Your school does not allow students to use this view or function. It is a setting of your school in WebUntis, not a problem with the app.",
    )
    is UntisNetworkException -> trFor(
        language,
        "Der WebUntis-Server ist nicht erreichbar. Prüfe deine Verbindung.",
        "The WebUntis server cannot be reached. Check your connection.",
    )
    is UntisAuthException -> trFor(
        language,
        "Die Anmeldung bei WebUntis ist fehlgeschlagen. Melde dich bei Bedarf erneut an.",
        "Signing in to WebUntis failed. Sign in again if this keeps happening.",
    )
    is UntisNoStudentException -> trFor(
        language,
        "Mit diesem Konto ist keine Schülerin und kein Schüler verknüpft.",
        "No student is linked to this account.",
    )
    is UntisParseException -> trFor(
        language,
        "WebUntis hat eine unerwartete Antwort geschickt.",
        "WebUntis sent an unexpected answer.",
    )
    else -> message?.takeIf { it.isNotBlank() } ?: fallback
}
