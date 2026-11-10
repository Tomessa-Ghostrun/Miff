package com.tomessa.miff.data.review

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.domain.review.ReviewOutcome
import com.tomessa.miff.domain.review.ReviewState

/**
 * What the review window remembers on the phone (see [ReviewState]): three small values, no name, no identifier, no account. The file is private
 * to the app and part of no backup (backup_rules.xml excludes everything). Not secret, never sent anywhere.
 */
interface ReviewStateStore {
    fun read(): ReviewState

    fun write(state: ReviewState)

    /** Forgets everything (sign-out, deletion of the account: the next account starts from zero). Nothing to do when nothing was ever written. */
    fun clear()

    /** DEBUG BUILD ONLY (see [com.tomessa.miff.ui.review.ReviewPromptViewModel]): "show the window at the next return to the app", whatever the rules say. */
    fun forceNext(): Boolean

    fun setForceNext(value: Boolean)
}

class SharedPreferencesReviewStateStore(
    context: Context,
) : ReviewStateStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): ReviewState =
        ReviewState(
            firstMiffSeenAtMillis = prefs.getLong(KEY_FIRST_MIFF_SEEN, NONE).takeIf { it != NONE },
            lastShownAtMillis = prefs.getLong(KEY_LAST_SHOWN, NONE).takeIf { it != NONE },
            // An unknown value (a future version) reads as "not answered": the rules then wait like after a closure.
            lastOutcome =
                prefs.getString(KEY_OUTCOME, null)?.let { name ->
                    ReviewOutcome.entries.firstOrNull {
                        it.name ==
                            name
                    }
                },
        )

    override fun write(state: ReviewState) {
        prefs.edit(commit = true) {
            putLong(KEY_FIRST_MIFF_SEEN, state.firstMiffSeenAtMillis ?: NONE)
            putLong(KEY_LAST_SHOWN, state.lastShownAtMillis ?: NONE)
            if (state.lastOutcome == null) remove(KEY_OUTCOME) else putString(KEY_OUTCOME, state.lastOutcome.name)
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    override fun forceNext(): Boolean = prefs.getBoolean(KEY_FORCE_NEXT, false)

    override fun setForceNext(value: Boolean) {
        prefs.edit { putBoolean(KEY_FORCE_NEXT, value) }
    }

    private companion object {
        const val FILE = "miff_review_prompt"
        const val KEY_FIRST_MIFF_SEEN = "first_miff_seen_at"
        const val KEY_LAST_SHOWN = "last_shown_at"
        const val KEY_OUTCOME = "last_outcome"
        const val KEY_FORCE_NEXT = "debug_force_next"
        const val NONE = -1L
    }
}
