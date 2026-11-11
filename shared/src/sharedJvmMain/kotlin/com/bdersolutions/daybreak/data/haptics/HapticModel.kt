package com.bdersolutions.daybreak.data.haptics

import kotlin.math.roundToInt

/** Groups of haptic events. Each group can be switched off or made stronger or weaker as a whole. */
enum class HapticCategory(
    /** Fast, repeating events are held back by the repeat limit; confirmations are never dropped. */
    val throttled: Boolean,
) {
    NAVIGATION(throttled = true),
    TOUCH(throttled = true),
    CONTROLS(throttled = true),
    STATUS(throttled = false),
    MOMENTS(throttled = false),
}

/** Everything in the app that can vibrate. Every entry is played from at least one place in the UI. */
enum class HapticEvent(val category: HapticCategory) {
    TAB_SWITCH(HapticCategory.NAVIGATION),
    PAGE_SNAP(HapticCategory.NAVIGATION),
    BACK(HapticCategory.NAVIGATION),

    TAP(HapticCategory.TOUCH),
    SHEET_OPEN(HapticCategory.TOUCH),
    REORDER_PICKUP(HapticCategory.TOUCH),
    REORDER_SWAP(HapticCategory.TOUCH),

    TOGGLE_ON(HapticCategory.CONTROLS),
    TOGGLE_OFF(HapticCategory.CONTROLS),
    SELECT(HapticCategory.CONTROLS),
    STEP(HapticCategory.CONTROLS),

    REFRESH_PULL(HapticCategory.STATUS),
    REFRESH_DONE(HapticCategory.STATUS),
    ERROR(HapticCategory.STATUS),

    LOGIN_SUCCESS(HapticCategory.MOMENTS),
    MESSAGE_SENT(HapticCategory.MOMENTS),
    UPDATE_READY(HapticCategory.MOMENTS),
    /** One piece of an assistant answer as it arrives: a heavy knock per token, so the answer can be felt. */
    ANSWER_TOKEN(HapticCategory.MOMENTS),
    DEBUG_TAP(HapticCategory.MOMENTS),
    DEBUG_UNLOCK(HapticCategory.MOMENTS),
    ;

    companion object {
        fun inCategory(category: HapticCategory): List<HapticEvent> = entries.filter { it.category == category }
    }
}

/**
 * How the same effect is voiced. SOFT moves every effect one step toward a gentler motor
 * primitive and a little weaker; PUNCHY moves one step toward a harder one and a little stronger.
 */
enum class HapticTexture(val gain: Float) {
    SOFT(0.85f),
    CRISP(1.0f),
    PUNCHY(1.15f),
}

/** Ready-made mixes. [CUSTOM] is never chosen by hand: it is what any edit that matches no other preset becomes. */
enum class HapticPreset { PREMIUM, SUBTLE, PUNCHY, MINIMAL, CUSTOM }

/**
 * All haptic choices. A missing entry in a map means "leave at 100 %". [intensity] and the
 * per-category and per-event values multiply, so each level scales everything below it.
 */
data class HapticSettings(
    val enabled: Boolean = true,
    val preset: HapticPreset = HapticPreset.PREMIUM,
    /** Overall strength, 0.0 to [MAX_INTENSITY]. Above 1.0 boosts weak effects; no pulse goes over full power. */
    val intensity: Float = 1f,
    val texture: HapticTexture = HapticTexture.CRISP,
    val categoryIntensity: Map<HapticCategory, Float> = emptyMap(),
    val eventIntensity: Map<HapticEvent, Float> = emptyMap(),
    val disabledCategories: Set<HapticCategory> = emptySet(),
    val disabledEvents: Set<HapticEvent> = emptySet(),
    /** Stretches or squeezes the pauses between the pulses of one effect. */
    val spacing: Float = 1f,
    /** Shortest time between two fast effects, so quick swiping does not turn into a buzz. */
    val minGapMs: Int = DEFAULT_MIN_GAP_MS,
    /** Stay silent when the phone's own "touch feedback" setting is off. */
    val followSystem: Boolean = true,
) {
    fun isEventActive(event: HapticEvent): Boolean =
        enabled && event.category !in disabledCategories && event !in disabledEvents

    fun categoryGain(category: HapticCategory): Float = categoryIntensity[category] ?: 1f

    fun eventGain(event: HapticEvent): Float = eventIntensity[event] ?: 1f

    /** The strength multiplier for [event], before the texture and the effect's own design are applied. */
    fun gainFor(event: HapticEvent): Float = intensity * categoryGain(event.category) * eventGain(event)

    /** How many events of [category] are switched on. */
    fun activeCount(category: HapticCategory): Int =
        if (category in disabledCategories) 0 else HapticEvent.inCategory(category).count { it !in disabledEvents }

    /** This settings object with the tuning of [preset]; the master switch and system follow-up are kept. */
    fun withPreset(preset: HapticPreset): HapticSettings {
        if (preset == HapticPreset.CUSTOM) return copy(preset = HapticPreset.CUSTOM)
        return HapticPresets.tuning(preset).copy(enabled = enabled, followSystem = followSystem)
    }

    /** Restores the preset name after an edit: the preset it now equals, or [HapticPreset.CUSTOM]. */
    fun normalized(): HapticSettings {
        val key = tuningKey()
        val match = HapticPreset.entries.firstOrNull { it != HapticPreset.CUSTOM && HapticPresets.tuning(it).tuningKey() == key }
        return copy(preset = match ?: HapticPreset.CUSTOM)
    }

    /** A comparable summary of everything a preset decides, rounded the way the sliders round. */
    fun tuningKey(): String {
        fun percent(value: Float) = (value * 100f).roundToInt()
        return listOf(
            percent(intensity),
            texture.name,
            percent(spacing),
            minGapMs,
            categoryIntensity.filter { percent(it.value) != 100 }.entries
                .sortedBy { it.key.name }.joinToString(",") { "${it.key.name}=${percent(it.value)}" },
            eventIntensity.filter { percent(it.value) != 100 }.entries
                .sortedBy { it.key.name }.joinToString(",") { "${it.key.name}=${percent(it.value)}" },
            disabledCategories.map { it.name }.sorted().joinToString(","),
            disabledEvents.map { it.name }.sorted().joinToString(","),
        ).joinToString("|")
    }

    companion object {
        const val MAX_INTENSITY = 1.5f
        const val MIN_SPACING = 0.5f
        const val MAX_SPACING = 2f
        const val MAX_MIN_GAP_MS = 120
        const val DEFAULT_MIN_GAP_MS = 30
    }
}

/** The tuning of each preset. */
object HapticPresets {
    /** Events that stay on in [HapticPreset.MINIMAL]: what tells you that something happened or went wrong. */
    private val essential = setOf(
        HapticEvent.ERROR,
        HapticEvent.REFRESH_DONE,
        HapticEvent.LOGIN_SUCCESS,
        HapticEvent.MESSAGE_SENT,
        HapticEvent.UPDATE_READY,
        HapticEvent.TOGGLE_ON,
        HapticEvent.TOGGLE_OFF,
        HapticEvent.REORDER_PICKUP,
    )

    fun tuning(preset: HapticPreset): HapticSettings = when (preset) {
        HapticPreset.PREMIUM, HapticPreset.CUSTOM -> HapticSettings(preset = preset)

        HapticPreset.SUBTLE -> HapticSettings(
            preset = preset,
            intensity = 0.6f,
            texture = HapticTexture.SOFT,
            spacing = 1.1f,
            disabledEvents = setOf(HapticEvent.TAP, HapticEvent.REFRESH_PULL, HapticEvent.DEBUG_TAP),
        )

        HapticPreset.PUNCHY -> HapticSettings(
            preset = preset,
            intensity = 1.3f,
            texture = HapticTexture.PUNCHY,
            spacing = 0.85f,
            categoryIntensity = mapOf(HapticCategory.CONTROLS to 1.1f),
        )

        HapticPreset.MINIMAL -> HapticSettings(
            preset = preset,
            intensity = 0.9f,
            disabledEvents = HapticEvent.entries.toSet() - essential,
        )
    }
}
