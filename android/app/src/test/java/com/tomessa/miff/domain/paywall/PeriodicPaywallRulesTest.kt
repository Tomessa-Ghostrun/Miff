package com.tomessa.miff.domain.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The periodic offer, rule by rule (ticket docs/tickets/paywall-periodique.md, criteria PP1 to PP17), on the JVM with a clock moved by hand: nothing waits for 30 minutes or 1.5 seconds.
 */
class PeriodicPaywallRulesTest {
    private class Clock(
        var now: Long = 1_000_000L,
    ) : MonotonicClock {
        override fun nowMillis(): Long = now
    }

    private class MemoryStore(
        var value: Int = 0,
    ) : PeriodicPaywallCounterStore {
        override fun read() = value

        override fun write(count: Int) {
            value = count
        }

        override fun clear() {
            value = 0
        }
    }

    private val clock = Clock()
    private val store = MemoryStore()

    private fun rules(
        openings: Int = 10,
        counter: Int = 0,
    ): PeriodicPaywallRules {
        store.value = counter
        return PeriodicPaywallRules(store, clock, openingsBetween = openings)
    }

    /** A calm situation of a person without Miff+. */
    private fun calm(
        miffPlus: Boolean? = false,
        tabsInFront: Boolean = true,
        updateScreenOpen: Boolean = false,
        keyboardOpen: Boolean = false,
        messageOpen: Boolean = false,
        ownAlertInProgress: Boolean = false,
        lovedOneAlertUnseen: Boolean = false,
        offerReachable: Boolean = true,
    ) = PeriodicPaywallSituation(
        miffPlus = miffPlus,
        tabsInFront = tabsInFront,
        updateScreenOpen = updateScreenOpen,
        keyboardOpen = keyboardOpen,
        messageOpen = messageOpen,
        ownAlertInProgress = ownAlertInProgress,
        lovedOneAlertUnseen = lovedOneAlertUnseen,
        offerReachable = offerReachable,
    )

    /** A cold start with the signed-in app on screen. */
    private fun PeriodicPaywallRules.coldStart() {
        onForeground()
        onShellShown()
    }

    /** The first tap, then 1.5 s of calm. */
    private fun PeriodicPaywallRules.tapAndSettle() {
        onTouch(pressed = true)
        onActivation()
        onTouch(pressed = false)
        clock.now += PeriodicPaywallDefaults.QUIET_AFTER_TOUCH_MILLIS
    }

    private fun PeriodicPaywallRules.away(millis: Long) {
        onBackground()
        clock.now += millis
        onForeground()
    }

    // ---- the constants ---------------------------------------------------------------------------------------------------------------

    @Test
    fun theConstants_areTheOnesOfThePm() {
        assertEquals(10, PeriodicPaywallDefaults.OPENINGS_BETWEEN)
        assertEquals(30 * 60 * 1000L, PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        assertEquals(1_500L, PeriodicPaywallDefaults.QUIET_AFTER_TOUCH_MILLIS)
        assertEquals(3_000L, PaywallCloseDelay.MILLIS)
    }

    @Test
    fun theDebugOverride_canOnlyLowerTheThreshold() {
        assertEquals(10, PeriodicPaywallDefaults.openingsFor(0))
        assertEquals(3, PeriodicPaywallDefaults.openingsFor(3))
        assertEquals(9, PeriodicPaywallDefaults.openingsFor(9))
        assertEquals(10, PeriodicPaywallDefaults.openingsFor(10))
        assertEquals(10, PeriodicPaywallDefaults.openingsFor(40))
        assertEquals(10, PeriodicPaywallDefaults.openingsFor(-2))
    }

    // ---- PP1, PP2, PP5: what is an opening ---------------------------------------------------------------------------------------------

    @Test
    fun aColdStart_withTheAppOnScreen_countsOne() {
        val rules = rules(counter = 4)
        rules.coldStart()
        assertEquals(5, store.value)
    }

    @Test
    fun theCountIsDoneOnceWhateverTheOrderOfTheTwoEvents() {
        val rules = rules()
        rules.onShellShown()
        rules.onForeground()
        assertEquals(1, store.value)
        rules.onShellShown()
        assertEquals(1, store.value)
    }

    @Test
    fun aWakeUpWithoutAScreen_changesNothing() {
        // The geofence, a push or a background task wake the process without any activity: nothing calls onForeground, and no shell is shown.
        rules(counter = 4)
        assertEquals(4, store.value)
    }

    @Test
    fun signInAndOnboarding_areNeverCounted_thenTheFirstOpeningOfTheSignedInAppCountsOne() {
        val rules = rules()
        rules.onForeground()
        assertEquals("the sign-in screen is not an opening", 0, store.value)
        rules.onShellShown()
        assertEquals(1, store.value)
    }

    @Test
    fun aReturnAfterThirtyMinutesExactly_counts_andAShorterOneDoesNot() {
        val rules = rules()
        rules.coldStart()
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS - 1)
        assertEquals(1, store.value)
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        assertEquals(2, store.value)
        rules.away(60 * 60_000L)
        assertEquals(3, store.value)
    }

    @Test
    fun severalShortReturns_areOneSession_oneCount() {
        val rules = rules()
        rules.coldStart()
        repeat(6) { rules.away(5 * 60_000L) }
        assertEquals(1, store.value)
    }

    @Test
    fun aSecondForegroundCallWithoutABackground_changesNothing() {
        val rules = rules()
        rules.coldStart()
        rules.onForeground()
        assertEquals(1, store.value)
    }

    @Test
    fun theCounter_isCappedAtTheThreshold() {
        val rules = rules(counter = 9)
        rules.coldStart()
        assertEquals(10, store.value)
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        assertEquals(10, store.value)
    }

    @Test
    fun theOpeningIsCounted_whenTheSignedInAppIsAlreadyThereAtTheReturn() {
        val rules = rules()
        rules.coldStart()
        rules.onBackground()
        clock.now += PeriodicPaywallDefaults.BACKGROUND_MILLIS
        rules.onForeground()
        assertEquals(2, store.value)
    }

    @Test
    fun aSignOutSession_neverCountsWhileTheShellIsGone() {
        val rules = rules()
        rules.coldStart()
        rules.onShellGone()
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        assertEquals("the sign-in screen after a sign-out is not counted", 1, store.value)
        rules.onShellShown()
        assertEquals(2, store.value)
    }

    @Test
    fun aRotation_keepsTheFirstTap_andCountsNothing() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.onTouch(pressed = true)
        rules.onActivation()
        rules.onTouch(pressed = false)
        // The screen is built again: the shell goes and comes back, with no return to the foreground in between.
        rules.onShellGone()
        rules.onShellShown()
        assertEquals(10, store.value)
        clock.now += PeriodicPaywallDefaults.QUIET_AFTER_TOUCH_MILLIS
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    // ---- PP8, PP9, PP10: the moment ---------------------------------------------------------------------------------------------------

    @Test
    fun withoutAnyTap_theOfferNeverComes_whateverTheDuration() {
        val rules = rules(counter = 9)
        rules.coldStart()
        clock.now += 3 * 60 * 60_000L
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
    }

    @Test
    fun aScrollOrATouchOnNothing_isNotAnActivation() {
        val rules = rules(counter = 9)
        rules.coldStart()
        // The view layer only calls onActivation for a tap that something consumed: touches alone never make the offer possible.
        rules.onTouch(pressed = true)
        rules.onTouch(pressed = false)
        clock.now += 10_000L
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
    }

    @Test
    fun afterTheFirstTap_theOfferComesOnlyAfterOneAndAHalfSecondsWithoutTouching() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.onTouch(pressed = true)
        rules.onActivation()
        rules.onTouch(pressed = false)
        assertEquals(PeriodicPaywallDecision.Wait(1_500L), rules.evaluate(calm()))
        clock.now += 1_000L
        assertEquals(PeriodicPaywallDecision.Wait(500L), rules.evaluate(calm()))
        clock.now += 500L
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    @Test
    fun aFingerStillOnTheScreen_isNeverACalmMoment_evenAfterALongTime() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.onTouch(pressed = true)
        rules.onActivation()
        clock.now += 60_000L
        assertEquals(PeriodicPaywallDecision.Wait(1_500L), rules.evaluate(calm()))
    }

    @Test
    fun aNewTouch_startsTheWaitAgain() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
        rules.onTouch(pressed = false)
        assertEquals(PeriodicPaywallDecision.Wait(1_500L), rules.evaluate(calm()))
    }

    @Test
    fun theTap_thatIsNotOnTheSignedInApp_doesNotCount() {
        val rules = rules(counter = 9)
        rules.onForeground()
        rules.onActivation()
        rules.onShellShown()
        clock.now += 10_000L
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
    }

    @Test
    fun theCounterBelowTheThreshold_neverShows() {
        val rules = rules(counter = 7)
        rules.coldStart()
        assertEquals(8, store.value)
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
    }

    @Test
    fun theTenthOpening_isTheFirstThatCanShow() {
        val rules = rules()
        rules.coldStart()
        repeat(8) { rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS) }
        assertEquals(9, store.value)
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        assertEquals(10, store.value)
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    @Test
    fun oneAppearancePerSession_thenAnotherSessionOnlyWhenTheCounterIsFullAgain() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
        rules.onLaunched()
        assertEquals(
            "a second opening cannot start while the first is on its way",
            PeriodicPaywallDecision.No,
            rules.evaluate(calm()),
        )
        rules.onPaywallDisplayed()
        assertEquals(0, store.value)
        // Same session, whatever happens next: nothing more.
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        // A new session with a counter that is not full again: nothing.
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        rules.tapAndSettle()
        assertEquals(1, store.value)
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        // A new session with a full counter: it may show again.
        store.value = 9
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    @Test
    fun anOfferThatIsDueButNeverShown_staysDueForTheNextSession_andTheCounterIsNotReset() {
        val rules = rules(counter = 9)
        rules.coldStart()
        // No tap in this session.
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        assertEquals(10, store.value)
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
        assertEquals(10, store.value)
    }

    // ---- PP11: reset at the display ---------------------------------------------------------------------------------------------------

    @Test
    fun theCounterGoesToZeroAtTheDisplay_notBefore() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
        rules.onLaunched()
        assertEquals("not yet: the offer is not on view", 10, store.value)
        rules.onPaywallDisplayed()
        assertEquals(0, store.value)
    }

    @Test
    fun anOfferOpenedByThePerson_resetsTheCounter_andCountsAsShownInTheSession() {
        // A 4th place, a 2nd Miff, a danger zone or the Abonnement screen: the same call at the display.
        val rules = rules(counter = 4)
        rules.coldStart()
        rules.onPaywallDisplayed()
        assertEquals(0, store.value)
        store.value = 10
        rules.tapAndSettle()
        assertEquals(
            "the person has just seen the offer: not again in this session",
            PeriodicPaywallDecision.No,
            rules.evaluate(calm()),
        )
    }

    // ---- PP12: never in these situations, and the counter is left alone ------------------------------------------------------------------

    private fun assertNeverIn(
        name: String,
        situation: PeriodicPaywallSituation,
    ) {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        assertEquals("$name: no offer", PeriodicPaywallDecision.No, rules.evaluate(situation))
        assertEquals("$name: the counter is left alone", 10, store.value)
        // The situation ends: the offer is still due and comes.
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    @Test
    fun neverOverAScreenPushedOnTheTabs_theCreationOrJoiningOfAMiff_aPlaceForm_theDeletionOfTheAccount_aSystemWindow() =
        assertNeverIn("tabs not in front", calm(tabsInFront = false))

    @Test
    fun neverOnTheUpdate_theViewfinderOrThePreview() = assertNeverIn("update", calm(updateScreenOpen = true))

    @Test
    fun neverWithTheKeyboardOpen() = assertNeverIn("keyboard", calm(keyboardOpen = true))

    @Test
    fun neverOverAMessageOfTheApp() = assertNeverIn("message", calm(messageOpen = true))

    @Test
    fun neverDuringMyOwnAlert() = assertNeverIn("own alert", calm(ownAlertInProgress = true))

    @Test
    fun neverWhileALovedOnesAlertIsOnTheFeedOrWaitsUnread() =
        assertNeverIn("loved one's alert", calm(lovedOneAlertUnseen = true))

    @Test
    fun neverWhenThePricesWouldNotBeThere() = assertNeverIn("offer not reachable", calm(offerReachable = false))

    @Test
    fun aSessionOpenedByAnAlertNotification_neverShows_evenWhenCalm() {
        val rules = rules(counter = 9)
        rules.excludeSession()
        rules.coldStart()
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        assertEquals(10, store.value)
        // The next session is a normal one.
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    @Test
    fun anAlertNotificationTapped_afterTheSessionStarted_excludesIt_andTheNextSessionIsFree() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        rules.excludeSession()
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        clock.now += 20_000L
        rules.away(PeriodicPaywallDefaults.BACKGROUND_MILLIS)
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    @Test
    fun anAlertNotificationTapped_whileTheAppWasAway_excludesTheSessionItOpens() {
        // onNewIntent comes BEFORE the return to the foreground.
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.onBackground()
        clock.now += PeriodicPaywallDefaults.BACKGROUND_MILLIS
        rules.excludeSession()
        rules.onForeground()
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
    }

    // ---- the overlays over the tabs (a dialog, a sheet, a menu, the full-screen photo): their window hides the tabs without changing their state

    @Test
    fun anOverlayOpenOverTheTabs_blocksTheOffer_andTheCounterIsLeftAlone() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        rules.onOverlayOpened()
        clock.now += 60_000L
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        assertEquals(10, store.value)
    }

    @Test
    fun afterTheOverlayCloses_theOfferWaitsForACalmMomentCountedFromTheClosing() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        rules.onOverlayOpened()
        clock.now += 10_000L
        rules.onOverlayClosed()
        assertEquals(PeriodicPaywallDecision.Wait(1_500L), rules.evaluate(calm()))
        clock.now += 1_500L
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    @Test
    fun twoOverlaysAtOnce_blockUntilTheLastOneCloses_andACloseWithoutAnOpenNeverGoesBelowZero() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        rules.onOverlayOpened()
        rules.onOverlayOpened()
        rules.onOverlayClosed()
        clock.now += 5_000L
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        rules.onOverlayClosed()
        rules.onOverlayClosed()
        clock.now += 1_500L
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
        rules.onOverlayOpened()
        assertEquals("one open overlay is still one", PeriodicPaywallDecision.No, rules.evaluate(calm()))
    }

    @Test
    fun aFirstTapThatOpensAnOverlay_doesNotBringTheOfferOverIt() {
        // The first tap on the selector of Miffs or on a photo: the tap counts, the overlay it opens holds the offer back.
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.onTouch(pressed = true)
        rules.onActivation()
        rules.onTouch(pressed = false)
        rules.onOverlayOpened()
        clock.now += 30_000L
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
    }

    @Test
    fun withTheRuleOff_theOverlaysChangeNothing() {
        val rules = rules(openings = 0)
        rules.onOverlayOpened()
        rules.onOverlayClosed()
        assertFalse(rules.changes.value > 0)
    }

    // ---- PP13, PP14, PP15: Miff+ ---------------------------------------------------------------------------------------------------------

    @Test
    fun withMiffPlus_neverShown_andTheCounterGoesBackToZero_atFifteenToo() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        assertEquals(10, store.value)
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm(miffPlus = true)))
        assertEquals(0, store.value)
    }

    @Test
    fun afterTheEndOfASubscription_thePersonStartsFromZero_notFromAnOfferThatWasDue() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        rules.evaluate(calm(miffPlus = true))
        // The subscription ends: the person is "without Miff+" from now on, but the counter is at zero.
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm(miffPlus = false)))
        assertEquals(0, store.value)
    }

    @Test
    fun anUnknownRight_neverShows_andTheCounterIsLeftAlone() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm(miffPlus = null)))
        assertEquals(10, store.value)
        assertEquals(
            "known later in the same session: it comes",
            PeriodicPaywallDecision.Show,
            rules.evaluate(calm(miffPlus = false)),
        )
    }

    @Test
    fun miffPlusAppearingDuringTheWait_cancelsTheOffer() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.onTouch(pressed = true)
        rules.onActivation()
        rules.onTouch(pressed = false)
        assertTrue(rules.evaluate(calm()) is PeriodicPaywallDecision.Wait)
        clock.now += 1_500L
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm(miffPlus = true)))
    }

    // ---- PP6: erasure -------------------------------------------------------------------------------------------------------------------

    @Test
    fun signOutAndDeletionOfTheAccount_eraseTheCounter_andTheNextPersonStartsFromZero() {
        val rules = rules(counter = 9)
        rules.coldStart()
        rules.tapAndSettle()
        rules.clear()
        assertEquals(0, store.value)
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        // The next account in the same session: the session is already counted, so the counter stays at zero until the next opening.
        rules.onShellShown()
        assertEquals(0, store.value)
    }

    // ---- PP17: the rule can be turned off by a constant -------------------------------------------------------------------------------

    @Test
    fun aThresholdOfZero_switchesTheWholeRuleOff_countingIncluded() {
        val rules = rules(openings = 0, counter = 0)
        assertFalse(rules.isOn)
        rules.coldStart()
        rules.tapAndSettle()
        rules.excludeSession()
        rules.onTouch(pressed = true)
        rules.onPaywallDisplayed()
        assertEquals("nothing is counted", 0, store.value)
        store.value = 99
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm()))
        assertEquals("nothing is touched", 99, store.value)
    }

    @Test
    fun aLoweredThreshold_isUsedForTheCap_andTheDecision() {
        val rules = rules(openings = 3, counter = 2)
        rules.coldStart()
        assertEquals(3, store.value)
        rules.tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm()))
    }

    // ---- the diagnostic tools (debug build screen) -----------------------------------------------------------------------------------

    @Test
    fun theDiagnosticCounter_isClampedBetweenZeroAndTheThreshold() {
        val rules = rules()
        rules.setCounterForDiagnostic(40)
        assertEquals(10, rules.counterForDiagnostic())
        rules.setCounterForDiagnostic(-3)
        assertEquals(0, rules.counterForDiagnostic())
    }

    @Test
    fun theChangesFlow_movesWhenSomethingThatCanChangeTheDecisionHappens() {
        val rules = rules()
        val before = rules.changes.value
        rules.onTouch(pressed = true)
        assertTrue(rules.changes.value > before)
    }
}
