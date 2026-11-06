package com.bdersolutions.daybreak.ui

import android.content.Context
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.untis.UntisAuthException
import com.bdersolutions.daybreak.untis.UntisException
import com.bdersolutions.daybreak.untis.UntisNetworkException
import com.bdersolutions.daybreak.untis.UntisNoStudentException
import com.bdersolutions.daybreak.untis.UntisParseException
import com.bdersolutions.daybreak.untis.UntisPermissionException

fun Throwable.toUserMessage(context: Context): String = when (this) {
    is UntisNetworkException -> context.getString(R.string.error_network)
    is UntisAuthException -> if (requiresSecondFactor) {
        context.getString(R.string.error_second_factor)
    } else {
        context.getString(R.string.error_auth)
    }
    is UntisPermissionException -> context.getString(R.string.error_no_permission)
    is UntisNoStudentException -> context.getString(R.string.error_no_student)
    is UntisParseException -> context.getString(R.string.error_parse)
    is UntisException -> message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.error_generic)
    else -> context.getString(R.string.error_generic)
}
