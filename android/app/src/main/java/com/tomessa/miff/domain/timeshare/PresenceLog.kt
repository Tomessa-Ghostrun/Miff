package com.tomessa.miff.domain.timeshare

import com.tomessa.miff.domain.tracking.PresenceValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * PRIVACY, for this file (ticket docs/tickets/profil-repartition-temps.md, TIME-1): the LOCAL journal of the presence. One fact per CHANGE of the presence that the
 * tracking engine works out for a Miff: (Miff, state, place identifier, instant). NO coordinate, NO name, NO address, NO identifier of a person: the name, the category
 * and the type of a place shown by the chart always come from the CURRENT list of places. Nothing here leaves the phone (no Firestore read or write, no analytics event,
 * no log line). It is NOT the journal of the detection (`DetectionJournal`, debug builds only) and shares no code with it.
 */

/** One fact of the journal: from [atMillis] the person was in [presence] (a trusted place, a danger zone, out, or "unknown": not measured). Holds no personal data. */
class LoggedPresence(
    val atMillis: Long,
    val presence: PresenceValue,
    /** The place the fact names (a trusted place or a danger zone); null for `out` and `unknown`. Only an identifier, never a name. */
    val placeId: String?,
) {
    override fun toString(): String = "LoggedPresence(redacted)"
}

/**
 * The single entry point "the presence worked out for Miff X became (state, place) at instant t" (criterion TS-8b: the instant is the one the engine writes in the
 * status) and what the chart reads. Used from the engine (any thread, with its lock) and from the Profile.
 */
interface PresenceLog {
    /** Goes up each time the journal changes (a fact added, something forgotten): the chart is worked out again. */
    val revision: StateFlow<Int>

    /**
     * Records the presence of [miffId]. [PresenceValue.TRAVEL] is kept as `unknown` (not measured). A fact that repeats the previous one of the Miff (same state, same
     * place) is not kept twice; an `unknown` with nothing before it is not kept at all.
     */
    fun record(
        miffId: String,
        presence: PresenceValue,
        placeId: String?,
        atMillis: Long,
    )

    /**
     * The detection is armed for [miffId] at [atMillis]: the follow-up of this Miff on this phone starts (only the FIRST call counts, the following ones change nothing). It is
     * the start of the history of the chart, so that the days left count down even when no arrival or departure has been seen yet. Only an identifier and an instant.
     */
    fun begin(
        miffId: String,
        atMillis: Long,
    ) = Unit

    /** When the follow-up of [miffId] started (the older of its first [begin] and its first fact); null when it never did. */
    fun startedAt(miffId: String): Long? = null

    /** The facts of [miffId], in the order they were recorded (the order is meaningful: see the rule of a fact dated before the previous one). */
    fun facts(miffId: String): List<LoggedPresence>

    /**
     * A place of [miffId] does not exist any more: what the journal says about it is forgotten (its facts become `unknown`, never reattributed or counted as "Ailleurs",
     * criterion T11).
     */
    fun forgetPlace(
        miffId: String,
        placeId: String,
    )

    /** The Miff is gone (left, removed, deleted): everything about it is erased. */
    fun forgetMiff(miffId: String)

    /** The Miffs the person is in now: the journal of any other is erased. */
    fun retainOnly(miffIds: Set<String>)

    /** Erases everything (sign-out, deletion of the account, session lost, "Effacer mon historique de temps"). */
    fun clear()
}

/** The journal of a build or a test that records nothing. */
object NoPresenceLog : PresenceLog {
    override val revision: StateFlow<Int> = MutableStateFlow(0)

    override fun record(
        miffId: String,
        presence: PresenceValue,
        placeId: String?,
        atMillis: Long,
    ) = Unit

    override fun facts(miffId: String): List<LoggedPresence> = emptyList()

    override fun forgetPlace(
        miffId: String,
        placeId: String,
    ) = Unit

    override fun forgetMiff(miffId: String) = Unit

    override fun retainOnly(miffIds: Set<String>) = Unit

    override fun clear() = Unit
}
