package com.bdersolutions.daybreak.data.haptics

/** What the phone's vibration motor can do, for the settings screen and for choosing a fallback. */
data class HapticCapabilities(
    val hasVibrator: Boolean,
    val amplitudeControl: Boolean,
    /** How many of the [HapticPrimitive] values the motor plays natively. */
    val primitivesSupported: Int,
    val primitivesTotal: Int,
) {
    enum class Tier { NONE, ADVANCED, BASIC, MINIMAL }

    val tier: Tier
        get() = when {
            !hasVibrator -> Tier.NONE
            primitivesSupported >= ADVANCED_PRIMITIVES -> Tier.ADVANCED
            amplitudeControl -> Tier.BASIC
            else -> Tier.MINIMAL
        }

    companion object {
        /** CLICK, TICK and the rises are the minimum for the effects to keep their shape. */
        const val ADVANCED_PRIMITIVES = 4
        val None = HapticCapabilities(false, false, 0, HapticPrimitive.entries.size)
    }
}

/** Plays finished effects on a motor. The Android implementation is [VibratorHapticOutput]. */
interface HapticOutput {
    val capabilities: HapticCapabilities
    fun play(steps: List<HapticStep>)
    fun cancel()
}

/**
 * The one place the app asks for haptics. [perform] checks the settings, the phone's own touch
 * feedback switch and the repeat limit, then plays the planned effect; [preview] skips those checks
 * so a slider can be felt while it is dragged, even for an event that is switched off.
 */
class Haptics(
    private val settings: () -> HapticSettings,
    private val output: HapticOutput,
    private val systemAllows: () -> Boolean = { true },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    @Volatile
    private var lastPlayedAt = Long.MIN_VALUE / 2

    val capabilities: HapticCapabilities get() = output.capabilities

    /** Whether the phone's own "touch feedback" switch is on. */
    fun systemTouchFeedbackOn(): Boolean = systemAllows()

    fun perform(event: HapticEvent, fraction: Float = 1f) {
        val current = settings()
        if (!current.isEventActive(event)) return
        if (current.followSystem && !systemAllows()) return
        val now = clock()
        if (event.category.throttled && now - lastPlayedAt < current.minGapMs) return
        val steps = HapticPlanner.plan(event, current, fraction)
        if (steps.isEmpty()) return
        lastPlayedAt = now
        output.play(steps)
    }

    /** Plays [event] with [with] (or the stored settings), ignoring switches, the system setting and the repeat limit. */
    fun preview(event: HapticEvent, with: HapticSettings = settings(), fraction: Float = 1f) {
        val steps = HapticPlanner.plan(event, with, fraction)
        if (steps.isEmpty()) return
        lastPlayedAt = clock()
        output.play(steps)
    }

    fun cancel() = output.cancel()

    companion object {
        /** A silent instance for previews and tests that have no motor. */
        val None = Haptics(
            settings = { HapticSettings(enabled = false) },
            output = object : HapticOutput {
                override val capabilities = HapticCapabilities.None
                override fun play(steps: List<HapticStep>) = Unit
                override fun cancel() = Unit
            },
        )
    }
}
