package com.tomessa.miff.domain.update

/*
 * What the Update screen shows for the permission of the camera (ticket Update, 5.2 states A to C, criteria P1 and P2). Pure: the system gives three facts,
 * this gives the screen. The permission is asked ONLY when the person opens the Update screen and after the explanation, never at the start of the app
 * ("jamais de demande à froid").
 *
 * Android shows its own box twice at the most: after two refusals there is no box any more and only the settings of the phone can allow the camera. The
 * system does not say "refused for good" directly: after a refusal [shouldShowRationale] is true (one more request is possible) and it is false once the box
 * will not come back.
 */
enum class CameraAccessScreen {
    /** Allowed: the viewfinder. */
    VIEWFINDER,

    /** First time (the explanation was never accepted): the pop-in that says why, who sees the photo and for how long, BEFORE the box of the system. */
    EXPLAIN,

    /** Refused once, the box can come back: "On réessaie ?". */
    RETRY,

    /** Refused for good: the camera is off for Miff, with the settings and the camera application of the phone as ways out. */
    DISABLED,
}

object CameraAccess {
    /** The screen when the Update opens. [explained]: the person already accepted the explanation once (so the box of the system was shown). */
    fun forOpening(
        granted: Boolean,
        explained: Boolean,
        shouldShowRationale: Boolean,
    ): CameraAccessScreen =
        when {
            granted -> CameraAccessScreen.VIEWFINDER
            !explained -> CameraAccessScreen.EXPLAIN
            shouldShowRationale -> CameraAccessScreen.RETRY
            else -> CameraAccessScreen.DISABLED
        }

    /** The screen after the box of the system answered. */
    fun afterRequest(
        granted: Boolean,
        shouldShowRationale: Boolean,
    ): CameraAccessScreen =
        when {
            granted -> CameraAccessScreen.VIEWFINDER
            shouldShowRationale -> CameraAccessScreen.RETRY
            else -> CameraAccessScreen.DISABLED
        }
}
