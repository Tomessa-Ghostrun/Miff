package com.tomessa.miff.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.tomessa.miff.domain.settings.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The language chosen in Settings > Langue (Onboarding ticket 6.10, decision Q4): French, English or the phone's. The
 * choice applies AT ONCE, without a button to confirm and without restarting the app by hand. It follows the system's own
 * "app languages" mechanism, so the same choice can also be made (and is shown) in Android's settings.
 */
interface LanguageStore {
    /** The language in force now. */
    val selected: StateFlow<AppLanguage>

    /** Applies and remembers the choice. */
    fun select(language: AppLanguage)

    /** Reads the language in force again (it can also be changed in the settings of the phone, outside the app). */
    fun refresh()
}

/**
 * Android 13 and later: the system keeps the choice (per-app languages, [LocaleManager]) and restarts the screen by itself.
 *
 * Android 8 to 12 (minSdk 26): there is no such mechanism, so the choice is kept in a small private file of the app and
 * applied by [wrap] when the screen is created; [com.tomessa.miff.MainActivity] re-creates the screen when it changes. This
 * is the same thing AppCompat does on those versions, without its dependency and without changing the theme of the screen
 * (the app's theme is the platform one on purpose). The choice is a language code, never personal data.
 */
class AndroidLanguageStore(
    private val context: Context,
) : LanguageStore {
    private val mutable = MutableStateFlow(read(context))
    override val selected: StateFlow<AppLanguage> = mutable.asStateFlow()

    override fun refresh() {
        mutable.value = read(context)
    }

    override fun select(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            // An empty list = "no choice of our own": the language of the phone applies again.
            manager.applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            preferences(context).edit().putString(KEY, language.tag.orEmpty()).apply()
        }
        mutable.value = language
    }

    companion object {
        private const val FILE = "miff_language"
        private const val KEY = "tag"

        private fun preferences(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        /** What is in force now, read from the right place for this version of Android. */
        fun read(context: Context): AppLanguage =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
                if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].toLanguageTag())
            } else {
                AppLanguage.fromTag(preferences(context).getString(KEY, null))
            }

        /**
         * Android 12 and earlier: the context the screen is built on, in the chosen language. Called by
         * `MainActivity.attachBaseContext`. From Android 13 the system does this itself: the context is returned as it is.
         */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = AppLanguage.fromTag(preferences(base).getString(KEY, null)).tag
            if (tag == null) {
                // "Langue du téléphone": the default language of the process goes back to the phone's own (it may still be
                // the one that was forced before), so that everything that reads it (the e-mail of Firebase) follows.
                Locale.setDefault(base.resources.configuration.locales[0])
                return base
            }
            val configuration = Configuration(base.resources.configuration)
            val locale = Locale.forLanguageTag(tag)
            configuration.setLocales(LocaleList(locale))
            // The language of the process too: the e-mail that Firebase sends follows it (Locale.getDefault()).
            Locale.setDefault(locale)
            return base.createConfigurationContext(configuration)
        }
    }
}
