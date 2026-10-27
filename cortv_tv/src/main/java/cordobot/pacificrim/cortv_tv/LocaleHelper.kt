package cordobot.pacificrim.cortv_tv

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import cordobot.pacificrim.cortv.common.data.PreferencesManager
import java.util.Locale

/** Applies the language chosen in Ajustes on top of the system one. */
object LocaleHelper {

    const val SYSTEM = "system"

    /** Languages the UI is translated to (each has a values-xx/strings.xml). */
    val SUPPORTED_LANGUAGES = listOf("es", "en", "pt", "fr", "de", "it")

    fun wrap(base: Context): Context {
        val tag = base.getSharedPreferences(PreferencesManager.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PreferencesManager.KEY_APP_LANGUAGE, PreferencesManager.DEFAULT_APP_LANGUAGE) ?: PreferencesManager.DEFAULT_APP_LANGUAGE
        val locale = if (tag == SYSTEM) systemLocale() else Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        if (tag == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Name of a language written in that same language, e.g. "Deutsch". */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    private fun systemLocale(): Locale =
        ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0] ?: Locale.getDefault()
}
