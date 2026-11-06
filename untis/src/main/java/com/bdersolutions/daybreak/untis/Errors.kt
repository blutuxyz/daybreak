package com.bdersolutions.daybreak.untis

/** Base type for failures that can be shown safely in the UI. */
open class UntisException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class UntisAuthException(
    message: String,
    val apiCode: Int? = null,
    val requiresSecondFactor: Boolean = false,
    cause: Throwable? = null,
) : UntisException(message, cause)

/**
 * The school does not let this account use a view or a function (for example the timetables of
 * teachers and rooms, or messages, which many schools switch off for students). The sign-in is fine;
 * the screens say so instead of "sign-in failed".
 */
class UntisPermissionException(
    message: String,
    val apiCode: Int? = null,
) : UntisException(message)

class UntisNetworkException(message: String, cause: Throwable? = null) : UntisException(message, cause)

class UntisProtocolException(
    message: String,
    val apiCode: Int? = null,
) : UntisException(message)

class UntisParseException(message: String, cause: Throwable? = null) : UntisException(message, cause)

class UntisNoDataException(message: String) : UntisException(message)

class UntisNoStudentException(message: String) : UntisException(message)
