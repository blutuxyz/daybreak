package com.bdersolutions.daybreak.data.haptics

import kotlin.math.roundToInt

/**
 * The motor primitives the effects are built from. They mirror `VibrationEffect.Composition`, but
 * this file knows nothing about Android so the effect design can be unit-tested.
 */
enum class HapticPrimitive {
    LOW_TICK,
    TICK,
    CLICK,
    THUD,
    SPIN,
    QUICK_RISE,
    SLOW_RISE,
    QUICK_FALL,
}

/** One pulse of an effect: a primitive at [scale] (0..1), started [delayMs] after the previous one ended. */
data class HapticStep(
    val primitive: HapticPrimitive,
    val scale: Float,
    val delayMs: Int = 0,
)

/**
 * The design of every effect, and how settings reshape it. The base scales are the Premium feel at
 * 100 %: light things stay light (0.35 to 0.6), confirmations land harder (0.7 to 0.9), and only
 * errors and the debug unlock reach for the heavy THUD.
 */
object HapticPlanner {
    /** Pulses weaker than this cannot be felt, so they are dropped instead of played as noise. */
    private const val FELT_THRESHOLD = 0.05f

    /** [fraction] (0..1) fades one event's strength, for effects that build up such as the debug taps. */
    fun plan(event: HapticEvent, settings: HapticSettings, fraction: Float = 1f): List<HapticStep> {
        val ramp = 0.35f + 0.65f * fraction.coerceIn(0f, 1f)
        val gain = settings.gainFor(event) * settings.texture.gain * ramp
        return design(event).mapNotNull { step ->
            val scale = (step.scale * gain).coerceIn(0f, 1f)
            if (scale < FELT_THRESHOLD) {
                null
            } else {
                HapticStep(
                    primitive = voice(step.primitive, settings.texture),
                    scale = scale,
                    delayMs = (step.delayMs * settings.spacing).roundToInt().coerceAtLeast(0),
                )
            }
        }
    }

    /** SOFT moves a primitive one step gentler, PUNCHY one step harder. Rises and falls stay as they are. */
    internal fun voice(primitive: HapticPrimitive, texture: HapticTexture): HapticPrimitive = when (texture) {
        HapticTexture.CRISP -> primitive
        HapticTexture.SOFT -> when (primitive) {
            HapticPrimitive.CLICK -> HapticPrimitive.TICK
            HapticPrimitive.TICK -> HapticPrimitive.LOW_TICK
            HapticPrimitive.THUD -> HapticPrimitive.CLICK
            else -> primitive
        }
        HapticTexture.PUNCHY -> when (primitive) {
            HapticPrimitive.LOW_TICK -> HapticPrimitive.TICK
            HapticPrimitive.TICK -> HapticPrimitive.CLICK
            else -> primitive
        }
    }

    /** The effect at full design strength, before any setting is applied. */
    internal fun design(event: HapticEvent): List<HapticStep> = when (event) {
        // Navigation: a ratchet clicking home, never a buzz.
        HapticEvent.TAB_SWITCH -> listOf(HapticStep(HapticPrimitive.TICK, 0.55f))
        HapticEvent.PAGE_SNAP -> listOf(HapticStep(HapticPrimitive.LOW_TICK, 0.7f))
        HapticEvent.BACK -> listOf(HapticStep(HapticPrimitive.TICK, 0.5f))

        // Touch: lighter the more often it happens.
        HapticEvent.TAP -> listOf(HapticStep(HapticPrimitive.TICK, 0.35f))
        HapticEvent.SHEET_OPEN -> listOf(
            HapticStep(HapticPrimitive.CLICK, 0.45f),
            HapticStep(HapticPrimitive.LOW_TICK, 0.35f, delayMs = 45),
        )
        HapticEvent.REORDER_PICKUP -> listOf(HapticStep(HapticPrimitive.CLICK, 0.8f))
        HapticEvent.REORDER_SWAP -> listOf(HapticStep(HapticPrimitive.TICK, 0.6f))

        // Controls: on rises in two beats, off falls in one.
        HapticEvent.TOGGLE_ON -> listOf(
            HapticStep(HapticPrimitive.LOW_TICK, 0.5f),
            HapticStep(HapticPrimitive.CLICK, 0.75f, delayMs = 45),
        )
        HapticEvent.TOGGLE_OFF -> listOf(HapticStep(HapticPrimitive.LOW_TICK, 0.55f))
        HapticEvent.SELECT -> listOf(HapticStep(HapticPrimitive.TICK, 0.5f))
        HapticEvent.STEP -> listOf(HapticStep(HapticPrimitive.LOW_TICK, 0.45f))

        // Status: a threshold, a soft confirmation, a head shake.
        HapticEvent.REFRESH_PULL -> listOf(HapticStep(HapticPrimitive.CLICK, 0.6f))
        HapticEvent.REFRESH_DONE -> listOf(
            HapticStep(HapticPrimitive.TICK, 0.5f),
            HapticStep(HapticPrimitive.CLICK, 0.7f, delayMs = 60),
        )
        HapticEvent.ERROR -> listOf(
            HapticStep(HapticPrimitive.THUD, 0.8f),
            HapticStep(HapticPrimitive.THUD, 0.6f, delayMs = 90),
        )

        // Moments: the few things that deserve a flourish.
        HapticEvent.LOGIN_SUCCESS -> listOf(
            HapticStep(HapticPrimitive.QUICK_RISE, 0.55f),
            HapticStep(HapticPrimitive.CLICK, 0.9f, delayMs = 20),
        )
        HapticEvent.MESSAGE_SENT -> listOf(
            HapticStep(HapticPrimitive.CLICK, 0.7f),
            HapticStep(HapticPrimitive.TICK, 0.45f, delayMs = 55),
        )
        HapticEvent.UPDATE_READY -> listOf(
            HapticStep(HapticPrimitive.SLOW_RISE, 0.5f),
            HapticStep(HapticPrimitive.CLICK, 0.9f, delayMs = 30),
        )
        // Heavy on purpose: the assistant's answer arrives piece by piece, and each piece lands with weight.
        HapticEvent.ANSWER_TOKEN -> listOf(HapticStep(HapticPrimitive.THUD, 0.85f))
        HapticEvent.DEBUG_TAP -> listOf(HapticStep(HapticPrimitive.TICK, 0.8f))
        HapticEvent.DEBUG_UNLOCK -> listOf(
            HapticStep(HapticPrimitive.THUD, 1f),
            HapticStep(HapticPrimitive.CLICK, 0.8f, delayMs = 80),
        )
    }
}
