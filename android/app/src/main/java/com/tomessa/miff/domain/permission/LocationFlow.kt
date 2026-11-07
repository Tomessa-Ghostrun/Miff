package com.tomessa.miff.domain.permission

/** What the button of the location screen does when it is tapped. */
enum class LocationButton {
    /** The window of the system ("Toujours autoriser" of the mockups, screens 11 and 12). */
    SYSTEM_DIALOG,

    /** The settings of the app in the system (Android 11+, or when the system will not show its window any more): screen 13. */
    OPEN_SETTINGS,
}

/** What the location screen shows: which button, the three steps of the settings (screen 13) or the facts (11, 12), the red message. */
data class LocationScreenModel(
    val button: LocationButton,
    /** True: screen 13 ("Autoriser la localisation en arrière-plan", three steps). False: screens 11 and 12. */
    val steps: Boolean,
    /** The message of the ticket under the button, in red (screens 12 and 13B). */
    val error: Boolean,
)

/**
 * What happened so far on the location screen, kept by the view model for the life of the screen. [canAskAgain] says whether the system
 * will still show its window for what is missing (false after a refusal with "don't ask again", or after two refusals on Android 11+).
 */
data class LocationAttempt(
    /** The window of the system was answered at least once. */
    val answered: Boolean = false,
    val canAskAgain: Boolean = true,
    /** The person went to the settings of the app: the state is read again when they come back, and only then is an error possible. */
    val awaitingSettings: Boolean = false,
    /** Show the message under the button. Never shown before a try: the first display is calm. */
    val error: Boolean = false,
)

/**
 * The location flow of ticket Tracking (criteria AUT2, AUT3) as a pure machine. Nothing here reads a position or asks the system;
 * the screen (ui/permission) calls the system and tells this machine what came back.
 */
object LocationFlow {
    /**
     * The permissions to put in the window of the system, in one request. Android 12+ wants the approximate one with the precise one.
     * "All the time" goes with them on Android 10 only (it can be asked together there); from Android 11 asking it with the others
     * makes the system ignore the whole request, so it is never put here.
     */
    fun requestedPermissions(sdk: Int): List<String> =
        buildList {
            add(PermissionNames.FINE_LOCATION)
            add(PermissionNames.COARSE_LOCATION)
            if (sdk == ApiLevel.Q) add(PermissionNames.BACKGROUND_LOCATION)
        }

    /** What the button does now. Only meaningful while [PermissionSnapshot.locationComplete] is false. */
    fun button(
        snapshot: PermissionSnapshot,
        attempt: LocationAttempt,
    ): LocationButton =
        when {
            // The precise location is missing: the window of the system, as long as the system still shows it.
            !snapshot.fineLocation -> {
                if (!attempt.answered ||
                    attempt.canAskAgain
                ) {
                    LocationButton.SYSTEM_DIALOG
                } else {
                    LocationButton.OPEN_SETTINGS
                }
            }

            // Precise granted, "all the time" missing: Android 11+ only sends to the settings; Android 10 can still ask.
            snapshot.sdk >= ApiLevel.R -> {
                LocationButton.OPEN_SETTINGS
            }

            attempt.canAskAgain -> {
                LocationButton.SYSTEM_DIALOG
            }

            else -> {
                LocationButton.OPEN_SETTINGS
            }
        }

    fun model(
        snapshot: PermissionSnapshot,
        attempt: LocationAttempt,
    ): LocationScreenModel {
        val button = button(snapshot, attempt)
        return LocationScreenModel(
            button = button,
            steps = button == LocationButton.OPEN_SETTINGS,
            error = attempt.error,
        )
    }

    /**
     * The window of the system was answered. [after] is the state read right after. An error is shown when what the window was asked
     * for was not given. On Android 11+ the window only asks for the precise location: if it is given, the next step is the settings
     * screen (13), which is normal and never an error.
     */
    fun afterDialog(
        after: PermissionSnapshot,
        canAskAgain: Boolean,
    ): LocationAttempt {
        val error =
            when {
                after.locationComplete -> false
                !after.fineLocation -> true
                after.sdk >= ApiLevel.R -> false
                else -> true
            }
        return LocationAttempt(answered = true, canAskAgain = canAskAgain, awaitingSettings = false, error = error)
    }

    /** The person leaves for the settings of the app. */
    fun settingsOpened(attempt: LocationAttempt): LocationAttempt = attempt.copy(awaitingSettings = true)

    /** The app is in front again: the state is read. Coming back from the settings without the right choice shows the message (13B). */
    fun afterResume(
        now: PermissionSnapshot,
        attempt: LocationAttempt,
    ): LocationAttempt =
        if (attempt.awaitingSettings) {
            attempt.copy(awaitingSettings = false, error = !now.locationComplete)
        } else {
            attempt
        }
}
