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

    /** The detection did not write my position: offline at the time of the write. */
    POSITION_SKIPPED_OFFLINE,

    /** The detection did not write my position: the session of the device was not confirmed (SESS-2). */
    POSITION_SKIPPED_SESSION,

    /** The server refused the write of my position (consent, sharing, rules, App Check). */
    POSITION_REFUSED,

    /** The Update found that my position is not published in at least one Miff. */
    UPDATE_POSITION_NOT_PUBLISHED,

    /** The Update stopped on a permission ("Toujours" + precise location, or the location of the phone is off). */
    UPDATE_PERMISSION_BLOCKED,

    /** The measure taken at "Publier" gave nothing usable (no signal in time, or older than 10 minutes). */
    UPDATE_MEASURE_FAILED,

    /** The Update handed its measure over and the detection published my position (the photo can leave). */
    UPDATE_HAND_OVER_WORKED,

    /** The detection could not publish my position anywhere: see the lines of the detection just before. */
    UPDATE_HAND_OVER_FAILED,
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
