package com.tomessa.miff.domain.tracking

/*
 * PRIVACY, for this file: the local journal of the detection is a tool for the PM's recette (criterion VIE2): the TYPE of event, the identifier of
 * a place, the time and the battery. NO coordinate, NO address, NO name, no free text (the only things that can be written are the values of
 * [JournalKind]). It exists in the debug build only: the release build gets [NoDetectionJournal] (see data/tracking/DetectionJournals.kt, one
 * file in src/debug and one in src/release) and a test checks that the file writer is not in the release sources.
 */

/** Everything the journal can say. A closed list on purpose: a free text could carry a place or a position one day. */
enum class JournalKind {
    ARMED,
    DISARMED,
    TRANSITION_ENTER,
    TRANSITION_EXIT,
    POSITION_READ,
    POSITION_UNAVAILABLE,
    STATUS_WRITTEN,
    STATUS_WAITING,
    STATUS_GIVEN_UP,
    STATUS_REFUSED,
    POSITION_WRITTEN,
    POSITION_SKIPPED_GUARD,
    BREAKER_STATUS,
    BREAKER_POSITION,
    PRESENCE_UNKNOWN,
    NOT_ELIGIBLE,
    WAKE_NETWORK,
    WAKE_REARM,
    BATTERY_WRITTEN,
    BATTERY_REFUSED,
    BREAKER_BATTERY,
}

/** A line of the journal as it is kept and shown. */
class JournalLine(
    val atMillis: Long,
    val kind: JournalKind,
    val placeId: String?,
    val batteryPct: Int?,
)

interface DetectionJournal {
    fun record(
        kind: JournalKind,
        placeId: String? = null,
        batteryPct: Int? = null,
    )

    /** The lines kept, oldest first. */
    fun lines(): List<JournalLine>

    /** Erases everything (sign-out, deletion of the account, loss of access). */
    fun clear()
}

/** The journal of the release build: it records nothing and has nothing to show. */
object NoDetectionJournal : DetectionJournal {
    override fun record(
        kind: JournalKind,
        placeId: String?,
        batteryPct: Int?,
    ) = Unit

    override fun lines(): List<JournalLine> = emptyList()

    override fun clear() = Unit
}
