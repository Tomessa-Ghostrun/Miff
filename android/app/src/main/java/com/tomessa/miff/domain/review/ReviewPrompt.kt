package com.tomessa.miff.domain.review

import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.StateLabel

/*
 * The review window "Que penses-tu de Miff ?" (ticket docs/tickets/pop-ins-avis.md; decision of the PM of 2026-11-07: built AS DRAWN, risk
 * assumed: Apple 5.6.1 / 5.6.3 and Google advise against any filtering of reviews). PURE LOGIC: when the window may appear, and what is
 * remembered about it. No Android class here, so that every rule is tested on the JVM.
 *
 * PRIVACY: nothing in this file is personal data. The only thing kept on the phone is three small values (see [ReviewState]); what the person
 * answered leaves the phone nowhere, and there is no analytics event about the window (ticket, criterion AV4).
 */

/**
 * The thresholds of the window. 7 days and 90 days are defaults chosen by the developer ("choix par défaut à confirmer", ticket §8); the 30 days after a
 * closure and the 90 days after "Peux faire mieux..." are confirmed by the PM (2026-11-08). One place to change them.
 */
object ReviewPromptDefaults {
    private const val DAY_MILLIS = 86_400_000L

    /** The person joined or created a Miff at least this long ago (measured from the first time this phone showed the Feed with a Miff). */
    const val MIN_MIFF_AGE_MILLIS = 7 * DAY_MILLIS

    /** At most one appearance per this period after "Peux faire mieux..." (after "J'adore !" the window never comes back). */
    const val MIN_INTERVAL_AFTER_ANSWER_MILLIS = 90 * DAY_MILLIS

    /** The window comes back after this period when it was closed WITHOUT an answer (cross or back gesture), again and again: no limit (PM, 2026-11-08). */
    const val MIN_INTERVAL_AFTER_CLOSE_MILLIS = 30 * DAY_MILLIS

    /** Time given to the Feed to settle after the app comes back to the foreground, before the window may appear. */
    const val SETTLE_MILLIS = 1_500L

    /** How long the short "Merci !" stays before the request of the store (the person can read it, nothing to press). */
    const val THANKS_MILLIS = 1_600L
}

/** How the last appearance ended. [SHOWN] stays when the app was stopped while the window was on screen. */
enum class ReviewOutcome { SHOWN, CLOSED, LOVED, CAN_DO_BETTER }

/**
 * What the phone remembers, in three small values:
 * - [firstMiffSeenAtMillis]: when this phone first showed the Feed with a Miff (stands for "joined or created a Miff": no extra reading of the server);
 * - [lastShownAtMillis] and [lastOutcome]: the last appearance and how it ended.
 * Nothing counts the closures: there is no limit to them.
 */
data class ReviewState(
    val firstMiffSeenAtMillis: Long? = null,
    val lastShownAtMillis: Long? = null,
    val lastOutcome: ReviewOutcome? = null,
) {
    /** The Feed was shown with a Miff: the date is kept the first time only. */
    fun withMiffSeen(nowMillis: Long): ReviewState =
        if (firstMiffSeenAtMillis != null) this else copy(firstMiffSeenAtMillis = nowMillis)

    fun shown(nowMillis: Long): ReviewState = copy(lastShownAtMillis = nowMillis, lastOutcome = ReviewOutcome.SHOWN)

    /** Closed without an answer: the window comes back after 30 days, however many times it was closed. */
    fun closed(): ReviewState = copy(lastOutcome = ReviewOutcome.CLOSED)

    fun answered(outcome: ReviewOutcome): ReviewState {
        require(outcome == ReviewOutcome.LOVED || outcome == ReviewOutcome.CAN_DO_BETTER) { "not an answer" }
        return copy(lastOutcome = outcome)
    }
}

/** What the screen can tell at the moment of the decision (all of it is state, none is personal). */
data class ReviewSituation(
    /** The person has a Miff (the Feed was loaded for it). */
    val hasMiff: Boolean,
    /** At least one loved one's status is on the Feed (not "pas encore de nouvelles"). */
    val friendStatusSeen: Boolean,
    /** The viewfinder of the Update is the screen in use: the window never covers it. */
    val updateScreenOpen: Boolean,
    val keyboardOpen: Boolean,
    /**
     * A message of the app is on screen or waits for the person (the lost-access message, "En attente de ton accord", the sheet "Localiser", "Tu as arrêté de partager", the pop-in "Pas de connexion"):
     * the window never covers it and never hides it ([messageOpen]).
     */
    val blockingMessageOpen: Boolean = false,
) {
    companion object {
        /** True when one of the messages that must not be covered is there: see [blockingMessageOpen]. */
        fun messageOpen(
            lostAccessNotice: Boolean,
            awaitingAgreement: Boolean,
            locateSheetOpen: Boolean,
            myLabel: StateLabel?,
            /** The pop-in "Pas de connexion" is on screen: a window as well, never two at once (C5). */
            connectionPopInOpen: Boolean = false,
        ): Boolean =
            lostAccessNotice || awaitingAgreement || locateSheetOpen || connectionPopInOpen ||
                myLabel == StateLabel.SharingStopped || myLabel == StateLabel.WaitingAgreement

        /** "Has seen the status of a loved one": a card of someone else says where they are, or that they left or are in a danger zone. */
        fun friendStatusSeen(content: FeedContent?): Boolean =
            content?.cards?.any { card ->
                !card.isMe &&
                    (card.label is StateLabel.At || card.label is StateLabel.Left || card.label == StateLabel.Danger)
            } == true
    }
}

object ReviewPromptRules {
    /**
     * True when the window may appear NOW, at a return to the foreground. Every condition is a "no" if it fails:
     * 1. a Miff for at least [ReviewPromptDefaults.MIN_MIFF_AGE_MILLIS], and a loved one's status already seen;
     * 2. not on the Update, not with the keyboard open, not over (or hiding) a message of the app;
     * 3. nothing more once the person said "J'adore" (the request of the store was made);
     * 4. a pause since the last appearance: 90 days after an answer, 30 days after a closure (or a window the app was stopped on).
     * A clock that went backwards (last appearance in the future) counts as "too soon": the window waits rather than appearing twice.
     */
    fun mayAppear(
        state: ReviewState,
        situation: ReviewSituation,
        nowMillis: Long,
    ): Boolean {
        if (!situation.hasMiff || !situation.friendStatusSeen) return false
        if (situation.updateScreenOpen || situation.keyboardOpen || situation.blockingMessageOpen) return false
        val firstSeen = state.firstMiffSeenAtMillis ?: return false
        if (nowMillis - firstSeen < ReviewPromptDefaults.MIN_MIFF_AGE_MILLIS) return false
        if (state.lastOutcome == ReviewOutcome.LOVED) return false
        val last = state.lastShownAtMillis ?: return true
        val pause =
            when (state.lastOutcome) {
                ReviewOutcome.CAN_DO_BETTER -> ReviewPromptDefaults.MIN_INTERVAL_AFTER_ANSWER_MILLIS
                else -> ReviewPromptDefaults.MIN_INTERVAL_AFTER_CLOSE_MILLIS
            }
        val elapsed = nowMillis - last
        return elapsed >= pause
    }
}

/**
 * "Peux faire mieux..." leads to an EXTERNAL FORM (decision of the PM, 2026-11-08) whose address the PM gives later: it is the build setting
 * `BuildConfig.FEEDBACK_FORM_URL` (one place: `miff.feedbackFormUrl` in app/build.gradle.kts, empty for now). While it is empty, or if it is not a plain
 * HTTPS address, the window shows the calm "Merci de nous le dire !" screen with "Nous écrire" instead. The address is opened AS IT IS: nothing about the
 * person (name, e-mail, Miff, position, identifier) is ever added to it.
 */
object FeedbackForm {
    private val HTTPS_ADDRESS = Regex("""^https://[^\s/?#]+[^\s]*$""")

    /** The address to open in the browser, or null when none is set (or when it is not HTTPS: never a clear-text address, never another scheme). */
    fun openableUrl(configured: String?): String? = configured?.trim()?.takeIf { HTTPS_ADDRESS.matches(it) }
}
