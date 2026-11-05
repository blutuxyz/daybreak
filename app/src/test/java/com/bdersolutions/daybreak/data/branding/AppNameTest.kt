package com.bdersolutions.daybreak.data.branding

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNameTest {
    @Test
    fun withoutAPaymentTheAppIsAlwaysDaybreak() {
        assertEquals("Daybreak", AppNaming.resolve(paid = false, preset = AppNamePreset.SCHULE, custom = "Mein Plan"))
        assertEquals(AppNamePreset.DAYBREAK, AppNaming.effectivePreset(paid = false, preset = AppNamePreset.PLAN))
    }

    @Test
    fun withoutAPaymentTheIconIsAlwaysTheStandardLogo() {
        assertEquals(AppIconStyle.STANDARD, AppNaming.effectiveIconStyle(paid = false, style = AppIconStyle.CLASSIC))
        assertEquals(AppIconStyle.CLASSIC, AppNaming.effectiveIconStyle(paid = true, style = AppIconStyle.CLASSIC))
    }

    @Test
    fun aSupporterGetsTheirCustomNameOtherwiseThePresetName() {
        assertEquals("Mein Plan", AppNaming.resolve(true, AppNamePreset.SCHULE, "Mein Plan"))
        assertEquals("Schule", AppNaming.resolve(true, AppNamePreset.SCHULE, ""))
        assertEquals("Schule", AppNaming.resolve(true, AppNamePreset.SCHULE, "   "))
        assertEquals(AppNamePreset.PLAN, AppNaming.effectivePreset(paid = true, preset = AppNamePreset.PLAN))
    }

    @Test
    fun customNamesAreTidied() {
        assertEquals("Mein Plan", AppNaming.sanitizeCustom("  Mein    Plan \n"))
        assertEquals("AB", AppNaming.sanitizeCustom("A\u0000B"))
        assertEquals(AppNaming.MAX_CUSTOM_LENGTH, AppNaming.sanitizeCustom("x".repeat(100)).length)
        assertEquals("", AppNaming.sanitizeCustom("\t \n"))
    }

    @Test
    fun anUnknownStoredChoiceFallsBackToTheDefault() {
        assertEquals(AppNamePreset.DAYBREAK, AppNamePreset.fromName(null))
        assertEquals(AppNamePreset.DAYBREAK, AppNamePreset.fromName("GONE"))
        assertEquals(AppNamePreset.KALENDER, AppNamePreset.fromName("KALENDER"))
        assertEquals(AppIconStyle.STANDARD, AppIconStyle.fromName(null))
        assertEquals(AppIconStyle.STANDARD, AppIconStyle.fromName("GONE"))
        assertEquals(AppIconStyle.CLASSIC, AppIconStyle.fromName("CLASSIC"))
    }

    /** The launcher name and icon come from the manifest, so every name and style needs an alias. */
    @Test
    fun everyNameAndStyleHasAnAliasWithTheRightLabelAndIcon() {
        val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        val strings = File("src/main/res/values/strings.xml").readText(Charsets.UTF_8)
        AppNamePreset.entries.forEach { preset ->
            val key = "launcher_name_${preset.name.lowercase()}"
            val label = Regex("""<string name="$key"[^>]*>(.*?)</string>""").find(strings)?.groupValues?.get(1)
            assertEquals("label of $key", preset.displayName, label)
            AppIconStyle.entries.forEach { style ->
                val alias = aliasNameFor(preset, style)
                val block = Regex("""<activity-alias[^>]*android:name="\.$alias"[^>]*>""", RegexOption.DOT_MATCHES_ALL)
                    .find(manifest)?.value
                assertTrue("alias $alias", block != null)
                assertTrue("label of $alias", block!!.contains("@string/$key"))
                val icon = if (style == AppIconStyle.STANDARD) "ic_launcher" else "ic_launcher_${style.name.lowercase()}"
                assertTrue("icon of $alias", block.contains("android:icon=\"@mipmap/$icon\""))
                assertTrue("round icon of $alias", block.contains("android:roundIcon=\"@mipmap/${icon}_round\""))
            }
        }
    }

    /** Every style needs its adaptive icon and the three layers it points at. */
    @Test
    fun everyStyleHasItsIconFiles() {
        AppIconStyle.entries.forEach { style ->
            val name = if (style == AppIconStyle.STANDARD) "ic_launcher" else "ic_launcher_${style.name.lowercase()}"
            listOf("mipmap-anydpi/$name.xml", "mipmap-anydpi/${name}_round.xml").forEach { path ->
                val file = File("src/main/res/$path")
                assertTrue("$path exists", file.exists())
                val xml = file.readText(Charsets.UTF_8)
                listOf("background", "foreground", "monochrome").forEach { layer ->
                    val drawable = Regex("<$layer android:drawable=\"@drawable/([a-z_]+)\"").find(xml)?.groupValues?.get(1)
                    assertTrue("$path $layer", drawable != null && File("src/main/res/drawable/$drawable.xml").exists())
                }
            }
        }
    }

    @Test
    fun onlyTheDefaultAliasIsEnabledInTheManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        val enabled = Regex("""<activity-alias[^>]*android:name="\.([A-Za-z]+)"[^>]*android:enabled="(true|false)"""", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest)
            .filter { it.groupValues[2] == "true" }
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf(aliasNameFor(AppNamePreset.DAYBREAK, AppIconStyle.STANDARD)), enabled)
    }
}
