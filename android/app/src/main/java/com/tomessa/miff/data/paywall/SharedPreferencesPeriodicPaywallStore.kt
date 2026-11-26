package com.tomessa.miff.data.paywall

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.domain.paywall.PeriodicPaywallCounterStore

/**
 * The one whole number of the periodic offer (ticket docs/tickets/paywall-periodique.md, section 4.3): the openings counted since the offer was last shown. NOTHING else is in the file:
 * no date, no identifier, no name, no position (criterion PP4, checked by a test on the file itself). The file is private to the app and part of no backup (backup_rules.xml and
 * data_extraction_rules.xml exclude every shared-preferences file). Never sent anywhere, never logged, erased at the sign-out and with the account.
 */
class SharedPreferencesPeriodicPaywallStore(
    context: Context,
) : PeriodicPaywallCounterStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // A value that is not a small whole number (a damaged file, a future version) reads as zero: the offer then waits, it never appears from a guess.
    override fun read(): Int =
        runCatching { prefs.getInt(KEY_OPENINGS, 0) }
            .getOrDefault(0)
            .coerceIn(0, MAX_STORED)

    // commit(): a process killed right after the offer was shown must find the counter at zero (no second offer on the next opening).
    override fun write(count: Int) {
        prefs.edit(commit = true) { putInt(KEY_OPENINGS, count.coerceIn(0, MAX_STORED)) }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    companion object {
        const val FILE = "miff_periodic_paywall"
        const val KEY_OPENINGS = "openings"
        private const val MAX_STORED = 1_000
    }
}
