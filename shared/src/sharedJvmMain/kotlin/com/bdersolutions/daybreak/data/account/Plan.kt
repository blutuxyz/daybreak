package com.bdersolutions.daybreak.data.account

import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.AppSettings
import java.time.Instant

/** The three plans of Daybreak 2. The server decides which one an account has (`GET /v1/account`). */
enum class Plan(val wire: String) {
    FREE("free"),
    PLUS("plus"),
    PRO("pro"),
    ;

    fun atLeast(other: Plan): Boolean = ordinal >= other.ordinal

    companion object {
        /** An unknown or missing value is [FREE], so a newer server never unlocks anything by accident. */
        fun parse(text: String?): Plan = entries.firstOrNull { it.wire == text?.lowercase() } ?: FREE
    }
}

/**
 * Everything a plan can unlock, in one list (see docs/v2-spec.md). What the plan table says about a
 * feature lives here and nowhere else: screens ask [Access.allows], never the plan itself.
 *
 * [supporterExtra] marks the three extras that people who paid through Ko-fi before 2.0 keep for good.
 */
enum class Entitlement(val minPlan: Plan, val supporterExtra: Boolean = false) {
    APP_NAME(Plan.PLUS, supporterExtra = true),
    APP_ICON(Plan.PLUS, supporterExtra = true),
    CALENDAR_EXPORT(Plan.PLUS, supporterExtra = true),
    WIDGET_CUSTOMISATION(Plan.PLUS),
    SHARE_PICTURE(Plan.PLUS),
    SYNC(Plan.PLUS),
    DAWN_REASONING(Plan.PRO),
    DAWN_DRAFTS(Plan.PRO),
    HAPTICS_STUDIO(Plan.PRO),
    ACCENT_PALETTES(Plan.PRO),
    AMOLED(Plan.PRO),
}

/** What the signed-in (or not signed-in) person may use right now. */
data class Access(
    val plan: Plan = Plan.FREE,
    /** An old Ko-fi supporter: keeps the supporter extras whatever the plan. */
    val supporter: Boolean = false,
) {
    fun allows(entitlement: Entitlement): Boolean =
        plan.atLeast(entitlement.minPlan) || (supporter && entitlement.supporterExtra)

    /** The plan a person has to take to use [entitlement] (what the plans sheet recommends). */
    fun planFor(entitlement: Entitlement): Plan = entitlement.minPlan

    /** The settings with what a locked feature changed put back to the free choice (the accent, AMOLED black). */
    fun apply(settings: AppSettings): AppSettings {
        var out = settings
        if (!allows(Entitlement.ACCENT_PALETTES) && out.accent != AccentColor.OCEAN) out = out.copy(accent = AccentColor.OCEAN)
        if (!allows(Entitlement.AMOLED) && out.amoledBlack) out = out.copy(amoledBlack = false)
        return out
    }

    companion object {
        val NONE = Access()

        /**
         * The access of [state] at [nowMillis]. A plan whose end has passed counts as free even if the cached
         * answer still says otherwise (the phone may be offline for days). [legacyPaid] is the old Ko-fi flag, honoured
         * until the account exists.
         */
        fun of(state: AccountState?, legacyPaid: Boolean, nowMillis: Long): Access {
            val plan = when {
                state == null -> Plan.FREE
                state.planUntil != null && parseInstant(state.planUntil)?.let { it <= nowMillis } == true -> Plan.FREE
                else -> state.plan
            }
            return Access(plan = plan, supporter = legacyPaid || state?.grandfathered == true)
        }

        internal fun parseInstant(text: String?): Long? =
            text?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }
}
