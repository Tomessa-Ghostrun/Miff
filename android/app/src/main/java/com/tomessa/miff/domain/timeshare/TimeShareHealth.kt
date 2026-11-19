package com.tomessa.miff.domain.timeshare

/*
 * Why the chart of a Miff may not be updated right now (section 7 of docs/tickets/profil-repartition-temps.md). PURE: the facts come from what the phone already knows
 * (the local copy of the tracking, the permissions, the trip); nothing is read from the server.
 */

/** The state of the tracking for ONE Miff, in the order of precedence of the card: a trip, then a sharing stopped, then a location that is not enough. */
enum class TrackingHealth { ACTIVE, TRAVEL, SHARING_STOPPED, LOCATION_INSUFFICIENT }

/** What the card needs to know of the tracking of one Miff. */
class TimeShareTracking(
    val health: TrackingHealth = TrackingHealth.ACTIVE,
    /** More zones than the system lets the phone watch at once (20, all Miffs together): the chart may be incomplete (D18). */
    val overLimit: Boolean = false,
    /** Only "all the time" is missing and the phone can ask for it on its own (Android 10+): the button then asks the system. */
    val backgroundRequestable: Boolean = false,
) {
    override fun toString(): String = "TimeShareTracking(redacted)"
}

object TimeShareHealth {
    fun resolve(
        travelOn: Boolean,
        sharingStopped: Boolean,
        locationComplete: Boolean,
    ): TrackingHealth =
        when {
            travelOn -> TrackingHealth.TRAVEL
            sharingStopped -> TrackingHealth.SHARING_STOPPED
            !locationComplete -> TrackingHealth.LOCATION_INSUFFICIENT
            else -> TrackingHealth.ACTIVE
        }

    /**
     * "I stopped sharing with this Miff", deduced WITHOUT any reading: the tracking has made at least one COMPLETE look at the server ([checkedOnce]) and this Miff is not
     * one it watches ([watched]: sharing on, consent of the right version) nor one that waits for the person's agreement ([waitingAgreement]: the consent has to be renewed).
     * Before the first look nothing is concluded: the card then shows nothing wrong rather than a guess.
     */
    fun sharingStopped(
        checkedOnce: Boolean,
        watched: Boolean,
        waitingAgreement: Boolean,
    ): Boolean = checkedOnce && !watched && !waitingAgreement
}
