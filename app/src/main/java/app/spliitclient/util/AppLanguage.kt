package app.spliitclient.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language: Android 13+ uses the system per-app language (also editable from
 * system settings); older versions apply the saved language when the activity starts.
 */
object AppLanguage {
    val supported = listOf("", "en", "it") // "" = follow the system

    fun current(context: Context, stored: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            val tag = if (locales == null || locales.isEmpty) "" else locales.get(0).language
            return if (tag in supported) tag else ""
        }
        return if (stored in supported) stored else ""
    }

    /** Applies the language; on Android 12 and older the activity is recreated. */
    fun apply(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.recreate()
        }
    }

    /** Used by MainActivity.attachBaseContext on Android 12 and older. */
    fun wrap(base: Context, tag: String): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
