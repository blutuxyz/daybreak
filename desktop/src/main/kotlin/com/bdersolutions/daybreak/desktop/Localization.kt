package com.bdersolutions.daybreak.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.bdersolutions.daybreak.data.settings.AppLanguage
import java.util.Locale

/** The language the desktop UI shows. It follows the app's language setting. */
val LocalLanguage = compositionLocalOf { AppLanguage.GERMAN }

/** The desktop's own strings: German (the app's default) or English. */
@Composable
fun tr(de: String, en: String): String = trFor(LocalLanguage.current, de, en)

/** For code that runs outside composition, such as notifications and background jobs. */
fun trFor(language: AppLanguage, de: String, en: String): String =
    if (language == AppLanguage.ENGLISH) en else de

fun localeOf(language: AppLanguage): Locale =
    if (language == AppLanguage.ENGLISH) Locale.ENGLISH else Locale.GERMAN

/** The Android app's string with this key, in the current language (see [AppStrings]). */
@Composable
fun str(key: String): String = AppStrings.text(key, LocalLanguage.current)

/** The Android app's string with this key and its placeholders filled in. */
@Composable
fun strf(key: String, vararg args: Any): String = AppStrings.format(key, LocalLanguage.current, *args)
