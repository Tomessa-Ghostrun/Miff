package com.tomessa.miff.data.timeshare

import android.content.Context
import com.tomessa.miff.domain.timeshare.TimeShareRules

/*
 * The choice of the window of "Répartition de ton temps" (7, 14 or 30 days) is kept on the phone (decision D6): a private preference file, excluded from the backups like
 * every preference, erased at sign-out and at the deletion of the account. A number, nothing else: nothing personal, never sent anywhere.
 */
interface TimeShareWindowStore {
    /** The window chosen, always one of [TimeShareRules.WINDOWS_DAYS] (7 when nothing was chosen or the stored value is not one of them). */
    fun read(): Int

    fun write(days: Int)

    fun clear()
}

class SharedPreferencesTimeShareWindowStore(
    context: Context,
) : TimeShareWindowStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_timeshare_window", Context.MODE_PRIVATE)

    override fun read(): Int = TimeShareRules.windowOrDefault(if (prefs.contains(KEY)) prefs.getInt(KEY, 0) else null)

    override fun write(days: Int) {
        if (days in TimeShareRules.WINDOWS_DAYS) prefs.edit().putInt(KEY, days).apply()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY = "days"
    }
}
