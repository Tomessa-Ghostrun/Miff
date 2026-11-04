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

    /**
     * Frequency of the renewal screen (recommendation: at every launch of the app). True: "Pas maintenant" is kept in memory only, so the
     * screen comes back at the next launch. The other option of the design (once a day) is NOT built: it needs a stored date.
     */
    const val RENEWAL_EVERY_LAUNCH = true

    /** A discreet "Se déconnecter" link on the blocking screens of the permissions (recommendation: yes). "Supprimer mon compte" cannot exist yet (AUTH-5). */
    const val EXIT_LINKS_ON_BLOCKING_SCREENS = true
}

/** What the renewal of the consent looks like for the gate. */
enum class RenewalState {
    /** Not looked at yet (or the server could not be asked): nothing is claimed, nothing is blocked. */
    UNKNOWN,

    NOTHING_DUE,

    /** The person shares under an older text: the renewal screen is due. */
    DUE,
}

/** The screen the gate puts in front of the Feed (NONE: the Feed itself). */
enum class GateScreen { NONE, NOTIFICATIONS, LOCATION, RENEWAL }

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

        // Agreement first: while it is pending (and put off), no permission is asked: nothing would use it yet.
        locationAsked && !(input.consentBeforeLocation && renewalPending) -> GateScreen.LOCATION

        renewalAsked -> GateScreen.RENEWAL

        else -> GateScreen.NONE
    }
}
