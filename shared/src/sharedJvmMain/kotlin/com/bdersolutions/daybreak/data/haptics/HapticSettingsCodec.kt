package com.bdersolutions.daybreak.data.haptics

import com.google.gson.Gson

/**
 * Reads and writes [HapticSettings] as JSON. Gson ignores Kotlin defaults, so the stored shape has
 * only nullable fields and every missing, unknown or out-of-range value falls back to its default.
 */
object HapticSettingsCodec {
    private val gson = Gson()

    private class Stored(
        var enabled: Boolean? = null,
        var preset: String? = null,
        var intensity: Float? = null,
        var texture: String? = null,
        var categoryIntensity: Map<String, Float?>? = null,
        var eventIntensity: Map<String, Float?>? = null,
        var disabledCategories: List<String?>? = null,
        var disabledEvents: List<String?>? = null,
        var spacing: Float? = null,
        var minGapMs: Int? = null,
        var followSystem: Boolean? = null,
    )

    fun encode(settings: HapticSettings): String = gson.toJson(
        Stored(
            enabled = settings.enabled,
            preset = settings.preset.name,
            intensity = settings.intensity,
            texture = settings.texture.name,
            categoryIntensity = settings.categoryIntensity.mapKeys { it.key.name },
            eventIntensity = settings.eventIntensity.mapKeys { it.key.name },
            disabledCategories = settings.disabledCategories.map { it.name },
            disabledEvents = settings.disabledEvents.map { it.name },
            spacing = settings.spacing,
            minGapMs = settings.minGapMs,
            followSystem = settings.followSystem,
        ),
    )

    fun decode(json: String?): HapticSettings {
        if (json.isNullOrBlank()) return HapticSettings()
        val stored = runCatching { gson.fromJson(json, Stored::class.java) }.getOrNull() ?: return HapticSettings()
        val defaults = HapticSettings()
        return HapticSettings(
            enabled = stored.enabled ?: defaults.enabled,
            preset = enumOrNull<HapticPreset>(stored.preset) ?: defaults.preset,
            intensity = finite(stored.intensity)?.coerceIn(0f, HapticSettings.MAX_INTENSITY) ?: defaults.intensity,
            texture = enumOrNull<HapticTexture>(stored.texture) ?: defaults.texture,
            categoryIntensity = gains<HapticCategory>(stored.categoryIntensity),
            eventIntensity = gains<HapticEvent>(stored.eventIntensity),
            disabledCategories = names<HapticCategory>(stored.disabledCategories),
            disabledEvents = names<HapticEvent>(stored.disabledEvents),
            spacing = finite(stored.spacing)?.coerceIn(HapticSettings.MIN_SPACING, HapticSettings.MAX_SPACING)
                ?: defaults.spacing,
            minGapMs = stored.minGapMs?.coerceIn(0, HapticSettings.MAX_MIN_GAP_MS) ?: defaults.minGapMs,
            followSystem = stored.followSystem ?: defaults.followSystem,
        )
    }

    private fun finite(value: Float?): Float? = value?.takeIf { it.isFinite() }

    private inline fun <reified E : Enum<E>> enumOrNull(raw: String?): E? =
        enumValues<E>().firstOrNull { it.name == raw }

    private inline fun <reified E : Enum<E>> names(raw: List<String?>?): Set<E> =
        raw.orEmpty().mapNotNull { enumOrNull<E>(it) }.toSet()

    private inline fun <reified E : Enum<E>> gains(raw: Map<String, Float?>?): Map<E, Float> {
        val result = LinkedHashMap<E, Float>()
        raw.orEmpty().forEach { (name, value) ->
            val key = enumOrNull<E>(name)
            val gain = finite(value)
            if (key != null && gain != null) result[key] = gain.coerceIn(0f, HapticSettings.MAX_INTENSITY)
        }
        return result
    }
}
