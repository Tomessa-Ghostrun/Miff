package com.tomessa.miff.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.review.ReviewStateStore
import com.tomessa.miff.domain.review.FeedbackForm
import com.tomessa.miff.domain.review.ReviewOutcome
import com.tomessa.miff.domain.review.ReviewPromptDefaults
import com.tomessa.miff.domain.review.ReviewPromptRules
import com.tomessa.miff.domain.review.ReviewSituation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/*
 * PRIVACY, for this file: no event of analytics, no log, nothing about WHAT the person answered leaves the phone. The only trace is the local state
 * of [ReviewStateStore] (three small values).
 */

/** What the window shows. */
sealed interface ReviewStage {
    data object Hidden : ReviewStage

    /** "Que penses-tu de Miff ?" and two buttons: "J'adore !" and "Peux faire mieux..." (no stars: decision of the PM, 2026-11-08). */
    data object Question : ReviewStage

    /** "Merci !": a few seconds, nothing to press; the request of the store follows. */
    data object ThanksForLove : ReviewStage

    /** "Merci de nous le dire !": "Nous écrire" and "Plus tard". Only while no feedback form is set. The store's window is NOT opened. */
    data object ThanksForFeedback : ReviewStage
}

/** What the screen must do with the phone (it holds the Activity; this class does not). */
sealed interface ReviewEffect {
    /** Ask the store for a review (Play In-App Review, then the page of the app as a fallback). */
    data object RequestStoreReview : ReviewEffect

    /** Open the e-mail application on the support address, with nothing else in the message. */
    data object OpenMail : ReviewEffect

    /** Open the external feedback form in the browser: [url] is HTTPS and is opened as it is, nothing about the person is added. */
    data class OpenFeedbackForm(
        val url: String,
    ) : ReviewEffect
}

/**
 * The review window of the Feed (decision of the PM of 2026-11-07, see `docs/tickets/pop-ins-avis.md`). It decides WHEN the window appears
 * (rules in [ReviewPromptRules]) and moves it from one screen to the next; the screen only draws.
 *
 * [debugForceAllowed] is `BuildConfig.DEBUG`: only a debug build honours "show the window at the next return" (the option of the diagnostic screen,
 * to see the window without waiting 7 or 90 days). Nothing else in the rules is bypassed: the window still never covers the Update or the keyboard.
 */
class ReviewPromptViewModel(
    private val store: ReviewStateStore,
    private val nowMillis: () -> Long,
    private val debugForceAllowed: Boolean,
    /** `BuildConfig.FEEDBACK_FORM_URL`: the PM's address, empty only on a build made with -Pmiff.feedbackFormUrl= ([FeedbackForm]); the default here is "" for tests. */
    feedbackFormUrl: String = "",
    private val thanksMillis: Long = ReviewPromptDefaults.THANKS_MILLIS,
) : ViewModel() {
    private val feedbackForm: String? = FeedbackForm.openableUrl(feedbackFormUrl)

    private val mutableStage = MutableStateFlow<ReviewStage>(ReviewStage.Hidden)
    val stage: StateFlow<ReviewStage> = mutableStage.asStateFlow()

    private val mutableEffects = Channel<ReviewEffect>(Channel.BUFFERED)
    val effects: Flow<ReviewEffect> = mutableEffects.receiveAsFlow()

    /** The last return to the foreground that was looked at: a rotation or a trip to the Settings never looks again. */
    private var handledTick = 0

    fun hasHandled(tick: Int): Boolean = tick <= handledTick

    /** The Feed was shown with a Miff: the date is kept the first time only ("a Miff for at least 7 days"). */
    fun onMiffSeen() {
        val state = store.read()
        if (state.firstMiffSeenAtMillis == null) store.write(state.withMiffSeen(nowMillis()))
    }

    /** The app came back to the foreground (the [tick] counts them) and the Feed had time to settle: shows the window if the rules say so. */
    fun onForeground(
        tick: Int,
        situation: ReviewSituation,
    ) {
        if (hasHandled(tick)) return
        if (mutableStage.value != ReviewStage.Hidden) return
        val now = nowMillis()
        val forced = debugForceAllowed && store.forceNext()
        val free = !situation.updateScreenOpen && !situation.keyboardOpen && !situation.blockingMessageOpen
        // A forced window that cannot appear now (Update, keyboard, a message) waits for the next return: the tick is NOT consumed.
        if (forced && !free) return
        handledTick = tick
        val state = store.read()
        if (forced || ReviewPromptRules.mayAppear(state, situation, now)) {
            if (forced) store.setForceNext(false)
            // Written BEFORE the window is drawn: a process killed in front of it still counts as an appearance (no loop).
            store.write(state.shown(now))
            mutableStage.value = ReviewStage.Question
        }
    }

    fun onLove() {
        if (mutableStage.value != ReviewStage.Question) return
        store.write(store.read().answered(ReviewOutcome.LOVED))
        mutableStage.value = ReviewStage.ThanksForLove
        viewModelScope.launch {
            delay(thanksMillis)
            mutableStage.value = ReviewStage.Hidden
            mutableEffects.send(ReviewEffect.RequestStoreReview)
        }
    }

    /**
     * "Peux faire mieux...": the store's window is NOT opened. With a feedback form set, the window closes and the form opens in the browser; without one, a calm
     * thank-you with a way to write to us.
     */
    fun onCanDoBetter() {
        if (mutableStage.value != ReviewStage.Question) return
        store.write(store.read().answered(ReviewOutcome.CAN_DO_BETTER))
        val form = feedbackForm
        if (form != null) {
            mutableStage.value = ReviewStage.Hidden
            mutableEffects.trySend(ReviewEffect.OpenFeedbackForm(form))
        } else {
            mutableStage.value = ReviewStage.ThanksForFeedback
        }
    }

    /** The cross or the back gesture. Without an answer the window comes back after 30 days (no limit); after "Peux faire mieux..." it only closes. */
    fun onClose() {
        when (mutableStage.value) {
            ReviewStage.Question -> {
                store.write(store.read().closed())
                mutableStage.value = ReviewStage.Hidden
            }

            ReviewStage.ThanksForFeedback -> {
                mutableStage.value = ReviewStage.Hidden
            }

            // "Merci !" lasts a moment and goes on by itself; Hidden has nothing to close.
            ReviewStage.ThanksForLove, ReviewStage.Hidden -> {
                // Nothing to do.
            }
        }
    }

    fun onWrite() {
        if (mutableStage.value != ReviewStage.ThanksForFeedback) return
        mutableStage.value = ReviewStage.Hidden
        mutableEffects.trySend(ReviewEffect.OpenMail)
    }

    fun onLater() = onClose()
}
