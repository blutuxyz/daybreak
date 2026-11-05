package com.bdersolutions.daybreak.data.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

/**
 * Plays effects on the phone's vibration motor, in three tiers:
 *
 * 1. Android 11+ with motor support: a `VibrationEffect.Composition` of real primitives, which is
 *    where the sharp, tuned feel comes from. A primitive the motor lacks is swapped for its nearest
 *    neighbour (for example LOW_TICK becomes TICK) before giving up on the tier.
 * 2. A motor with amplitude control: a short waveform whose strength follows the pulse scale.
 * 3. A plain on/off motor: the same waveform with only the pulse length following the scale.
 */
class VibratorHapticOutput(context: Context) : HapticOutput {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    private val supported: Set<HapticPrimitive> by lazy { detectSupportedPrimitives() }

    override val capabilities: HapticCapabilities by lazy {
        val motor = vibrator
        HapticCapabilities(
            hasVibrator = motor?.hasVibrator() == true,
            amplitudeControl = motor?.hasAmplitudeControl() == true,
            primitivesSupported = supported.size,
            primitivesTotal = HapticPrimitive.entries.size,
        )
    }

    override fun play(steps: List<HapticStep>) {
        val motor = vibrator ?: return
        if (steps.isEmpty() || !motor.hasVibrator()) return
        val effect = composition(steps) ?: waveform(steps, motor.hasAmplitudeControl()) ?: return
        runCatching { vibrate(motor, effect) }
    }

    override fun cancel() {
        runCatching { vibrator?.cancel() }
    }

    private fun vibrate(motor: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            motor.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            motor.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    // ---- Tier 1: composed primitives ----

    private fun composition(steps: List<HapticStep>): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || supported.isEmpty()) return null
        val resolved = steps.map { step ->
            val primitive = nearestSupported(step.primitive) ?: return null
            step to primitive
        }
        return buildComposition(resolved)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildComposition(resolved: List<Pair<HapticStep, HapticPrimitive>>): VibrationEffect? {
        val builder = VibrationEffect.startComposition()
        resolved.forEach { (step, primitive) ->
            val id = primitiveId(primitive) ?: return null
            builder.addPrimitive(id, step.scale.coerceIn(0f, 1f), step.delayMs.coerceAtLeast(0))
        }
        return runCatching { builder.compose() }.getOrNull()
    }

    /** The primitive itself if the motor plays it, else the closest one it does play. */
    private fun nearestSupported(primitive: HapticPrimitive): HapticPrimitive? =
        fallbackChain(primitive).firstOrNull { it in supported }

    private fun fallbackChain(primitive: HapticPrimitive): List<HapticPrimitive> = when (primitive) {
        HapticPrimitive.LOW_TICK -> listOf(HapticPrimitive.LOW_TICK, HapticPrimitive.TICK, HapticPrimitive.CLICK)
        HapticPrimitive.TICK -> listOf(HapticPrimitive.TICK, HapticPrimitive.LOW_TICK, HapticPrimitive.CLICK)
        HapticPrimitive.CLICK -> listOf(HapticPrimitive.CLICK, HapticPrimitive.TICK)
        HapticPrimitive.THUD -> listOf(HapticPrimitive.THUD, HapticPrimitive.CLICK, HapticPrimitive.TICK)
        HapticPrimitive.SPIN -> listOf(HapticPrimitive.SPIN, HapticPrimitive.CLICK)
        HapticPrimitive.QUICK_RISE -> listOf(HapticPrimitive.QUICK_RISE, HapticPrimitive.SLOW_RISE, HapticPrimitive.CLICK)
        HapticPrimitive.SLOW_RISE -> listOf(HapticPrimitive.SLOW_RISE, HapticPrimitive.QUICK_RISE, HapticPrimitive.CLICK)
        HapticPrimitive.QUICK_FALL -> listOf(HapticPrimitive.QUICK_FALL, HapticPrimitive.TICK)
    }

    /** The platform constant, or null when this Android version does not have the primitive yet. */
    private fun primitiveId(primitive: HapticPrimitive): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return when (primitive) {
            HapticPrimitive.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
            HapticPrimitive.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
            HapticPrimitive.SPIN -> VibrationEffect.Composition.PRIMITIVE_SPIN
            HapticPrimitive.QUICK_RISE -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
            HapticPrimitive.SLOW_RISE -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
            HapticPrimitive.QUICK_FALL -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            // Added with Android 12.
            HapticPrimitive.THUD ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_THUD else null
            HapticPrimitive.LOW_TICK ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else null
        }
    }

    private fun detectSupportedPrimitives(): Set<HapticPrimitive> {
        val motor = vibrator ?: return emptySet()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !motor.hasVibrator()) return emptySet()
        return runCatching {
            HapticPrimitive.entries.filter { primitive ->
                val id = primitiveId(primitive)
                id != null && motor.areAllPrimitivesSupported(id)
            }.toSet()
        }.getOrDefault(emptySet())
    }

    // ---- Tiers 2 and 3: waveforms ----

    private fun waveform(steps: List<HapticStep>, amplitudeControl: Boolean): VibrationEffect? {
        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        steps.forEach { step ->
            if (step.delayMs > 0) {
                timings += step.delayMs.toLong()
                amplitudes += 0
            }
            // Without amplitude control only the length can carry the strength.
            val lengthGain = if (amplitudeControl) 1f else 0.5f + step.scale
            shape(step.primitive).forEach { (lengthMs, level) ->
                timings += (lengthMs * lengthGain).roundToInt().coerceAtLeast(1).toLong()
                amplitudes += when {
                    level <= 0f -> 0 // a pause inside the primitive
                    amplitudeControl -> (level * step.scale * MAX_AMPLITUDE).roundToInt().coerceIn(1, MAX_AMPLITUDE)
                    else -> MAX_AMPLITUDE
                }
            }
        }
        if (timings.isEmpty()) return null
        return runCatching {
            VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), NO_REPEAT)
        }.getOrNull()
    }

    /** A primitive as (length in ms, relative strength) segments. */
    private fun shape(primitive: HapticPrimitive): List<Pair<Int, Float>> = when (primitive) {
        HapticPrimitive.LOW_TICK -> listOf(7 to 1f)
        HapticPrimitive.TICK -> listOf(9 to 1f)
        HapticPrimitive.CLICK -> listOf(14 to 1f)
        HapticPrimitive.THUD -> listOf(24 to 1f)
        HapticPrimitive.SPIN -> listOf(14 to 1f, 18 to 0f, 14 to 1f)
        HapticPrimitive.QUICK_RISE -> listOf(22 to 0.3f, 22 to 0.6f, 22 to 1f)
        HapticPrimitive.SLOW_RISE -> listOf(40 to 0.2f, 40 to 0.4f, 40 to 0.6f, 40 to 0.8f, 40 to 1f)
        HapticPrimitive.QUICK_FALL -> listOf(22 to 1f, 22 to 0.6f, 22 to 0.3f)
    }

    companion object {
        private const val MAX_AMPLITUDE = 255
        private const val NO_REPEAT = -1

        /** The phone's own "touch feedback" switch in the system settings. */
        fun systemTouchFeedbackOn(context: Context): Boolean = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        }.getOrDefault(true)
    }
}
