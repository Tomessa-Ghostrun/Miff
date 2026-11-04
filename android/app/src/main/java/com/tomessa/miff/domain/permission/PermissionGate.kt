package com.tomessa.miff.domain.permission

/**
 * Choices of the PM that are STILL WAITING for a decision (message of the coordinator of 2026-11-04). Each is a constant, so that
 * changing it is one line (and one test). They are listed as open points in the pull request; none is decided here.
 */
object GatePolicy {
    /**
     * Agreement (renewal of the consent) BEFORE the "Toujours" screen (recommendation of the design: no permission asked while nothing
     * would use it). While the person has not agreed (and said "Pas maintenant"), the "Toujours" screen is not shown. False: the "Toujours"
     * screen comes first and stays blocking whatever the answer to the consent.
     */
    const val CONSENT_BEFORE_LOCATION = true

    /** A discreet "Se déconnecter" link on the blocking screens of the permissions (recommendation: yes). "Supprimer mon compte" cannot exist yet (AUTH-5). */
    const val EXIT_LINKS_ON_BLOCKING_SCREENS = true
}

/**
 * What the renewal of the consent looks like for the gate. "Pas maintenant" is kept in memory only, so a renewal that is due comes back at
 * every launch of the app (recommendation of the design; the "once a day" option is not built: it needs a stored date).
 */
enum class RenewalState {
    /**
     * The check is under way (it starts with the screen and is bounded in time). The "Toujours" screen waits for its end: a member who
     * shares under an older text must not be able to give the background location BEFORE the agreement (review of the PR).
     */
    CHECKING,

    /** The check ended without an answer (no network, too slow, refused): nothing is claimed and nothing more is waited for. */
    UNKNOWN,

    NOTHING_DUE,

    /** The person shares under an older text: the renewal screen is due. */
    DUE,
}

/** The screen the gate puts in front of the Feed (NONE: the Feed itself). */
enum class GateScreen { NONE, NOTIFICATIONS, CHECKING, LOCATION, RENEWAL }

data class GateInput(
    val snapshot: PermissionSnapshot,
    /** The notification screen was already shown to this account on this phone (it is never blocking, never shown twice). */
    val notificationsHandled: Boolean,
    /** `BuildConfig.LOCATION_BLOCKING`: true = the location screen cannot be left without "all the time" + precise (decision of the PM). */
    val locationBlocking: Boolean,
    /** "Plus tard" was tapped (possible only when [locationBlocking] is false). */
    val locationDeferred: Boolean,
    val renewal: RenewalState,
    /** "Pas maintenant" was tapped in this launch of the app. */
    val renewalDeferred: Boolean,
    val consentBeforeLocation: Boolean = GatePolicy.CONSENT_BEFORE_LOCATION,
)

/**
 * What stands between the person and the Feed, in order (ticket Tracking, decisions of the PM): the notifications (never blocking, skipped
 * when granted, when Android is older than 13, or when they were already asked), the renewal of the consent, then the location (blocking:
 * AUT1, AUT2, AUT5; relaxed by the build switch: AUT6). Pure: tested on every branch.
 */
fun resolveGate(input: GateInput): GateScreen {
    val snapshot = input.snapshot
    val renewalPending = input.renewal == RenewalState.DUE
    val renewalAsked = renewalPending && !input.renewalDeferred
    val locationAsked = !snapshot.locationComplete && (input.locationBlocking || !input.locationDeferred)
    return when {
        snapshot.notificationsMissing && !input.notificationsHandled -> GateScreen.NOTIFICATIONS

        renewalAsked && input.consentBeforeLocation -> GateScreen.RENEWAL

        // Agreement first: the "Toujours" screen is not shown while the check runs, nor while the agreement is due (put off or not):
        // no permission is asked while nothing would use it, and none before the agreement.
        locationAsked && input.consentBeforeLocation && input.renewal == RenewalState.CHECKING -> GateScreen.CHECKING

        locationAsked && !(input.consentBeforeLocation && renewalPending) -> GateScreen.LOCATION

        renewalAsked -> GateScreen.RENEWAL

        else -> GateScreen.NONE
    }
}
