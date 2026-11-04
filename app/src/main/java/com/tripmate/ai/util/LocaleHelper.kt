package com.tripmate.ai.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {
    /** "system", "en" or "ar". Persisted by AppCompat (autoStoreLocales) and our DataStore. */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }

    fun current(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "system" }.substringBefore('-')
}
