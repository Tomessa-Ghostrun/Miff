package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.permission.ApiLevel

/*
 * The "second chance" of the notification permission (ticket section 8.1, step 3; criterion N-A3): when the person said "Plus tard" on the pop-in, ONE
 * calm card is offered in the Feed after the FIRST update they publish ("Un rappel si tu oublies de donner de tes nouvelles ?"). Never a window, never
 * more than once. A person who answered the system window (yes or no) never gets the card: that was their choice.
 */
enum class SecondChance {
    /** The pop-in was not put off (or not seen yet): no card. */
    NONE,

    /** "Plus tard" was tapped on the pop-in; waiting for the first update. */
    DEFERRED,

    /** The first update has been published since: the card is offered until it is answered. */
    READY,

    /** The card was answered ("Activer" or "Pas maintenant"): never again. */
    DONE,
}

object SecondChanceRules {
    /** "Plus tard" on the pop-in. Only the first time counts; a card already answered never comes back. */
    fun afterLater(current: SecondChance): SecondChance =
        if (current ==
            SecondChance.NONE
        ) {
            SecondChance.DEFERRED
        } else {
            current
        }

    /** The person published an update (any update): the card becomes due, once, for someone who had put the pop-in off. */
    fun afterUpdatePublished(current: SecondChance): SecondChance =
        if (current == SecondChance.DEFERRED) SecondChance.READY else current

    /** "Activer" or "Pas maintenant" on the card, or the permission changed by another way: nothing more to offer. */
    fun afterAnswer(current: SecondChance): SecondChance =
        if (current ==
            SecondChance.READY
        ) {
            SecondChance.DONE
        } else {
            current
        }

    /** Is the card on the Feed right now? Only while the permission can still be asked (Android 13+, never asked). */
    fun visible(
        current: SecondChance,
        permission: NotificationPermissionState,
    ): Boolean = current == SecondChance.READY && permission == NotificationPermissionState.NOT_ASKED
}

/** What the button of a row "Notifications ne sont pas actives" does, according to the state: one rule for the Settings, the centre and the card. */
enum class AuthorizationAction {
    /** Nothing to do (allowed). */
    NONE,

    /** Android 13+, never asked: the system window. */
    ASK_SYSTEM,

    /** Refused or turned off: only the settings of the phone can change it (the calm help screen leads there). */
    OPEN_PHONE_SETTINGS,
    ;

    companion object {
        fun of(state: NotificationPermissionState): AuthorizationAction =
            when (state) {
                NotificationPermissionState.ALLOWED -> NONE
                NotificationPermissionState.NOT_ASKED -> ASK_SYSTEM
                NotificationPermissionState.OFF -> OPEN_PHONE_SETTINGS
            }
    }
}

/** What the button "Activer les notifications" of the centre does when the notifications are refused or never asked. */
enum class EnableStep {
    /** The native window of Android (it can still be shown). */
    ASK_SYSTEM,

    /** Android cannot show its window any more (refused for good, or turned off in the phone): the calm help that opens the notification settings of the app. */
    OPEN_HELP,
    ;

    companion object {
        /**
         * [systemWindowStillPossible]: Android would still show the window (after ONE refusal it still
         * does, once more; after a second one, or "don't ask again", it never does). Before Android 13
         * there is no window at all.
         */
        fun of(
            state: NotificationPermissionState,
            sdk: Int,
            systemWindowStillPossible: Boolean,
        ): EnableStep =
            when {
                state == NotificationPermissionState.NOT_ASKED -> ASK_SYSTEM

                state == NotificationPermissionState.OFF &&
                    sdk >= ApiLevel.TIRAMISU &&
                    systemWindowStillPossible -> ASK_SYSTEM

                else -> OPEN_HELP
            }
    }
}
