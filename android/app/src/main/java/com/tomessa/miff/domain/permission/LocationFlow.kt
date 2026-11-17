package com.tomessa.miff.domain.permission

/** What the button of the location screen does when it is tapped. */
enum class LocationButton {
    /** The window of the system ("Toujours autoriser" of the mockups, screens 11 and 12). */
    SYSTEM_DIALOG,

    /**
     * Android 11+, precise location given, "all the time" missing: the standard request for "all the time". The system then opens ITS OWN
     * screen of the location permission of Miff (the choices "Toujours autoriser" and so on, then the precise position); Miff shows no window
     * of its own there (screen 13).
     */
    SYSTEM_BACKGROUND_SCREEN,

    /** The settings of the app in the system, only when the system will not open its own screen (or no longer shows its window): screen 13, three steps. */
    OPEN_SETTINGS,
}

/** What the location screen shows: which button, the steps of screen 13 or the facts (11, 12), the red message. */
data class LocationScreenModel(
    val button: LocationButton,
    /** True: screen 13 ("Autoriser la localisation en arrière-plan", steps). False: screens 11 and 12. */
    val steps: Boolean,
    /** The message of the ticket under the button, in red (screens 12 and 13B). */
    val error: Boolean,
    /** True: the button leads to the screen of the permission that the SYSTEM opens (the steps say so); false: the settings of the app. */
    val systemScreen: Boolean = false,
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
    /** Android 11+: how many times the system screen of "all the time" came back without the right choice (bounded, see [LocationFlow.MAX_SYSTEM_SCREEN_TRIES]). */
    val systemScreenTries: Int = 0,
    /** Android 11+: the system did not open its screen (refused at once, or too many tries): the settings of the app take over, with no loop. */
    val systemScreenUnavailable: Boolean = false,
)

/** What the system did with a request for "all the time" (see [LocationFlow.backgroundOutcome]). */
enum class BackgroundRequestOutcome {
    /** "All the time" is granted. */
    GRANTED,

    /** The system showed something (its screen on Android 11+, its window on Android 10) and the person came back without the right choice. */
    SYSTEM_UI_SEEN,

    /** The request came back at once with nothing shown: the system will not ask (refused for good, or a request without effect). */
    NO_UI,
}

/** Where a tap on "enable the background location" leads (the screen of the gate, Settings > Confidentialité). */
enum class BackgroundRoute {
    /** The standard request: the system opens its own screen of the location permission (a window on Android 10). */
    REQUEST_FROM_SYSTEM,

    /** The settings page of the app. */
    OPEN_APP_SETTINGS,
}

/**
 * The location flow of ticket Tracking (criteria AUT2, AUT3) as a pure machine. Nothing here reads a position or asks the system;
 * the screen (ui/permission) calls the system and tells this machine what came back.
 */
object LocationFlow {
    /**
     * A request that comes back in less than this, without the app having left the front, showed nothing: the system answered at once.
     * Reading a screen and choosing takes the person longer; a slow phone needs a few hundred milliseconds to refuse.
     */
    const val MIN_SYSTEM_UI_MS = 1_000L

    /**
     * The system screen of "all the time" is offered this many times in a row at most while the person comes back without the right choice;
     * after that the settings of the app take over. A safety against a loop if a phone answers in a way we misjudge, never a punishment.
     */
    const val MAX_SYSTEM_SCREEN_TRIES = 3

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

            // Precise granted, "all the time" missing, Android 11+: the standard request, by which the SYSTEM opens its own screen of the
            // permission. The settings of the app only when the system did not open it (or after too many tries: no loop).
            snapshot.sdk >= ApiLevel.R -> {
                if (attempt.systemScreenUnavailable) {
                    LocationButton.OPEN_SETTINGS
                } else {
                    LocationButton.SYSTEM_BACKGROUND_SCREEN
                }
            }

            // Android 10: a normal window, as long as the system still shows it.
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
            steps = button != LocationButton.SYSTEM_DIALOG,
            error = attempt.error,
            systemScreen = button == LocationButton.SYSTEM_BACKGROUND_SCREEN,
        )
    }

    /**
     * The window of the system was answered. [after] is the state read right after. An error is shown when what the window was asked
     * for was not given. On Android 11+ the window only asks for the precise location: if it is given, the next step is the system screen
     * of "all the time" (13), which is normal and never an error.
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

    // --- "All the time", asked by itself (Android 11+: the system opens its own screen) ----------------------------------------

    /** Can "all the time" be asked of the system on its own? The precise location is given, "all the time" is not, and the phone has the permission (Android 10+). */
    fun canRequestBackground(snapshot: PermissionSnapshot): Boolean =
        snapshot.sdk >= ApiLevel.Q && snapshot.fineLocation && !snapshot.backgroundLocation

    /**
     * Where a tap on "enable the background location" leads: the standard request while the system can still answer it, the settings of the app
     * otherwise (already granted: the person wants to change something; before Android 10 or without the precise location: nothing to ask on its own;
     * [systemScreenUnavailable]: the system already answered at once, asking again would only repeat it).
     */
    fun backgroundRoute(
        requestable: Boolean,
        systemScreenUnavailable: Boolean,
    ): BackgroundRoute =
        if (requestable && !systemScreenUnavailable) {
            BackgroundRoute.REQUEST_FROM_SYSTEM
        } else {
            BackgroundRoute.OPEN_APP_SETTINGS
        }

    /**
     * What the system did with the request: granted; or something was shown (the app left the front, [activityStopped], or the answer took at least
     * [MIN_SYSTEM_UI_MS]); or nothing was shown (it came back at once: the person already refused for good, or the request has no effect).
     * [elapsedMs] is null when the start of the request is unknown (the screen was rebuilt meanwhile): the system is then assumed to have shown something,
     * so that the person is never sent to the settings by a doubt.
     */
    fun backgroundOutcome(
        granted: Boolean,
        elapsedMs: Long?,
        activityStopped: Boolean,
    ): BackgroundRequestOutcome =
        when {
            granted -> {
                BackgroundRequestOutcome.GRANTED
            }

            activityStopped || elapsedMs == null || elapsedMs >= MIN_SYSTEM_UI_MS -> {
                BackgroundRequestOutcome.SYSTEM_UI_SEEN
            }

            else -> {
                BackgroundRequestOutcome.NO_UI
            }
        }

    /**
     * The request for "all the time" came back. [after] is the state read right after.
     * - Granted: no message, the gate closes the screen on its own.
     * - Nothing shown: the settings of the app take over (the screen opens them at once, [awaitingSettings] is set): no message yet, the state is
     *   judged when the person comes back.
     * - Something shown, wrong choice: the calm message of the ticket, and the screen may be offered again, [MAX_SYSTEM_SCREEN_TRIES] times at most.
     */
    fun afterBackgroundRequest(
        after: PermissionSnapshot,
        outcome: BackgroundRequestOutcome,
        attempt: LocationAttempt,
    ): LocationAttempt =
        when {
            after.locationComplete || outcome == BackgroundRequestOutcome.GRANTED -> {
                attempt.copy(answered = true, awaitingSettings = false, error = false, systemScreenTries = 0)
            }

            outcome == BackgroundRequestOutcome.NO_UI -> {
                attempt.copy(
                    answered = true,
                    awaitingSettings = true,
                    error = false,
                    systemScreenUnavailable = true,
                )
            }

            else -> {
                val tries = attempt.systemScreenTries + 1
                attempt.copy(
                    answered = true,
                    awaitingSettings = false,
                    error = true,
                    systemScreenTries = tries,
                    systemScreenUnavailable = tries >= MAX_SYSTEM_SCREEN_TRIES,
                )
            }
        }

    /** True when the app must now open the settings of the app itself (the system opened nothing). */
    fun mustOpenSettings(
        after: PermissionSnapshot,
        outcome: BackgroundRequestOutcome,
    ): Boolean = outcome == BackgroundRequestOutcome.NO_UI && !after.locationComplete

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
