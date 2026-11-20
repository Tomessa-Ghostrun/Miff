package com.tomessa.miff.data.miff

import android.content.Context

/*
 * PRIVACY, for this file: a COUNTER per account (how many times the calm card "Une précision sur ce que tu partages" was shown on this phone). No
 * position, no name of Miff, no text; it never leaves the phone and is erased with the account. The preference file is not part of any backup
 * (backup_rules.xml and data_extraction_rules.xml exclude every domain).
 */

/**
 * CONS-4-A, option D of the PM: the card for the accounts under the version 3 text comes back AT MOST [MAX_SHOWS] times, then only "Revoir et accepter"
 * in Settings > Confidentialité remains. A launch shows it once at most. No pressure: no countdown, nothing is paused, nothing is written to the server.
 */
object ClarifyPolicy {
    const val MAX_SHOWS = 3

    /** Is the card still offered by itself, given how many times it was already shown to this account? */
    fun mayShowByItself(shownCount: Int): Boolean = shownCount < MAX_SHOWS
}

/** How many times the calm card was shown to an account on this phone (a double in tests). */
interface ClarifyAskStore {
    fun shownCount(uid: String): Int

    /** The card was shown (once per launch at most): one more. Never goes down. */
    fun markShown(uid: String)

    /** The account was deleted or the session ended for good: nothing is kept for it. */
    fun forget(uid: String)
}

class SharedPreferencesClarifyAskStore(
    context: Context,
) : ClarifyAskStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "miff_consent_clarify",
            Context.MODE_PRIVATE,
        )

    override fun shownCount(uid: String): Int = prefs.getInt(key(uid), 0)

    override fun markShown(uid: String) {
        val next = (shownCount(uid) + 1).coerceAtMost(ClarifyPolicy.MAX_SHOWS)
        // commit(): the count must be on the disk before the card is on the screen, so that a kill of the app never gives a fourth display.
        prefs.edit().putInt(key(uid), next).commit()
    }

    override fun forget(uid: String) {
        prefs.edit().remove(key(uid)).apply()
    }

    private fun key(uid: String) = "shown:$uid"
}

/** The count kept in memory only: for tests, and the default of the view model before the container gives it the real one. */
class InMemoryClarifyAskStore : ClarifyAskStore {
    private val counts = mutableMapOf<String, Int>()

    override fun shownCount(uid: String): Int = counts[uid] ?: 0

    override fun markShown(uid: String) {
        counts[uid] = (shownCount(uid) + 1).coerceAtMost(ClarifyPolicy.MAX_SHOWS)
    }

    override fun forget(uid: String) {
        counts.remove(uid)
    }
}
