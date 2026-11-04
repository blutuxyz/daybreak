package com.bdersolutions.daybreak.ui.components

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Number of hue slots subjects are spread across in the accent-hue palette. */
const val SUBJECT_HUE_SLOTS = 8

/** A colour in OKLCH: lightness 0..1, chroma roughly 0..0.4, hue in degrees. */
data class OkLch(
    val lightness: Double,
    val chroma: Double,
    val hueDegrees: Double,
)

/**
 * Converts an sRGB colour (channels 0..1) to OKLCH, using the Björn Ottosson
 * OKLab matrices. Pure maths so it can be unit tested without Compose.
 */
fun srgbToOkLch(red: Double, green: Double, blue: Double): OkLch {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    val lRoot = Math.cbrt(l)
    val mRoot = Math.cbrt(m)
    val sRoot = Math.cbrt(s)
    val lightness = 0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot
    val a = 1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot
    val bLab = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
    var hue = Math.toDegrees(atan2(bLab, a))
    if (hue < 0) hue += 360.0
    return OkLch(lightness, sqrt(a * a + bLab * bLab), hue)
}

/**
 * Converts OKLCH back to sRGB (channels 0..1). Out-of-gamut values are clamped
 * per channel, which is good enough for the saturated accents used here.
 */
fun oklchToSrgb(color: OkLch): DoubleArray {
    val hueRadians = Math.toRadians(color.hueDegrees)
    val a = color.chroma * cos(hueRadians)
    val b = color.chroma * sin(hueRadians)
    val lRoot = color.lightness + 0.3963377774 * a + 0.2158037573 * b
    val mRoot = color.lightness - 0.1055613458 * a - 0.0638541728 * b
    val sRoot = color.lightness - 0.0894841775 * a - 1.2914855480 * b
    val l = lRoot * lRoot * lRoot
    val m = mRoot * mRoot * mRoot
    val s = sRoot * sRoot * sRoot
    val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    val blue = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return doubleArrayOf(linearToSrgb(r), linearToSrgb(g), linearToSrgb(blue))
}

/**
 * Hue for a subject: the accent hue plus a fixed offset per slot, so every subject
 * lands on one of [SUBJECT_HUE_SLOTS] harmonious hues around the accent.
 */
fun subjectHueDegrees(subject: String, primaryHueDegrees: Double): Double {
    val slot = stableSubjectHash(subject) % SUBJECT_HUE_SLOTS
    return (primaryHueDegrees + slot * 360.0 / SUBJECT_HUE_SLOTS) % 360.0
}

/** Deterministic, non-negative hash so a subject keeps its colour across launches. */
fun stableSubjectHash(value: String): Int {
    var hash = 7
    for (char in value.lowercase(Locale.ROOT)) {
        hash = hash * 31 + char.code
    }
    return hash and 0x7fffffff
}

private fun srgbToLinear(channel: Double): Double {
    val c = channel.coerceIn(0.0, 1.0)
    return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}

private fun linearToSrgb(channel: Double): Double {
    val c = channel.coerceIn(0.0, 1.0)
    val encoded = if (c <= 0.0031308) 12.92 * c else 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055
    return encoded.coerceIn(0.0, 1.0)
}
