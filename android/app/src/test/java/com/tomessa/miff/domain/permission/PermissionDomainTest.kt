package com.tomessa.miff.domain.permission

import com.tomessa.miff.domain.miff.MiffSharingConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LOC-2a: the gate (order, blocking switch in both positions), the location flow (criteria AUT1 to AUT6) and the consent version. */
class PermissionDomainTest {
    private fun snap(
        sdk: Int = 34,
        fine: Boolean = false,
        coarse: Boolean = false,
        background: Boolean = false,
        notifications: Boolean = false,
    ) = PermissionSnapshot(sdk, fine, coarse, background, notifications)

    private val full = snap(fine = true, coarse = true, background = true, notifications = true)

    private fun gate(
        snapshot: PermissionSnapshot,
        handled: Boolean = false,
        blocking: Boolean = true,
        deferred: Boolean = false,
        renewal: RenewalState = RenewalState.NOTHING_DUE,
        renewalDeferred: Boolean = false,
        consentFirst: Boolean = true,
    ) = resolveGate(GateInput(snapshot, handled, blocking, deferred, renewal, renewalDeferred, consentFirst))

    // --- Snapshot ----------------------------------------------------------------------------------------------------

    @Test
    fun locationIsCompleteOnlyWithThePreciseOneAndTheBackgroundOne() {
        assertTrue(full.locationComplete)
        assertFalse(snap(fine = true, coarse = true).locationComplete)
        // The approximate position alone (Android 12+) is never enough.
        assertFalse(snap(coarse = true, background = true).locationComplete)
        // Before Android 10 the background permission does not exist: the precise one is enough.
        assertTrue(snap(sdk = 28, fine = true).locationComplete)
        assertTrue(snap(sdk = 29, fine = true, background = true).locationComplete)
        assertFalse(snap(sdk = 29, fine = true).locationComplete)
    }

    @Test
    fun notificationsAreAskedOnlyFromAndroid13() {
        assertFalse(snap(sdk = 32).notificationsMissing)
        assertTrue(snap(sdk = 33).notificationsMissing)
        assertFalse(snap(sdk = 33, notifications = true).notificationsMissing)
    }

    // --- Gate ---------------------------------------------------------------------------------------------------------

    @Test
    fun nothingIsAskedWhenEverythingIsGranted() {
        assertEquals(GateScreen.NONE, gate(full))
    }

    @Test
    fun notificationsComeFirst_areSkippedWhenGrantedOldOrAlreadyShown() {
        val none = snap()
        assertEquals(GateScreen.NOTIFICATIONS, gate(none))
        assertEquals(GateScreen.LOCATION, gate(none, handled = true))
        assertEquals(GateScreen.LOCATION, gate(snap(sdk = 32)))
        assertEquals(GateScreen.LOCATION, gate(snap(notifications = true)))
    }

    @Test
    fun theLocationScreenBlocks_untilBothAreGiven_orComesBackWhenWithdrawn() {
        val notificationsOnly = snap(notifications = true)
        assertEquals(GateScreen.LOCATION, gate(notificationsOnly))
        assertEquals(GateScreen.LOCATION, gate(snap(notifications = true, fine = true, coarse = true)))
        assertEquals(GateScreen.NONE, gate(full))
        // AUT5: the same state after a withdrawal.
        assertEquals(GateScreen.LOCATION, gate(full.copy(fineLocation = false)))
        assertEquals(GateScreen.LOCATION, gate(full.copy(backgroundLocation = false)))
    }

    @Test
    fun theBuildSwitch_inBothPositions() {
        val missing = snap(notifications = true)
        // Blocking (the default): putting it off does nothing.
        assertEquals(GateScreen.LOCATION, gate(missing, blocking = true, deferred = true))
        // Relaxed: shown once, then "Plus tard" lets the person in.
        assertEquals(GateScreen.LOCATION, gate(missing, blocking = false, deferred = false))
        assertEquals(GateScreen.NONE, gate(missing, blocking = false, deferred = true))
    }

    @Test
    fun theRenewalComesBeforeTheLocation_whenTheAgreementIsFirst() {
        val missing = snap(notifications = true)
        assertEquals(GateScreen.RENEWAL, gate(missing, renewal = RenewalState.DUE))
        // "Pas maintenant": nothing is written and the "Toujours" screen is not shown either (nothing would use it).
        assertEquals(GateScreen.NONE, gate(missing, renewal = RenewalState.DUE, renewalDeferred = true))
        // With the location complete the renewal still comes.
        assertEquals(GateScreen.RENEWAL, gate(full, renewal = RenewalState.DUE))
        assertEquals(GateScreen.NONE, gate(full, renewal = RenewalState.DUE, renewalDeferred = true))
        // The check ended without an answer: nothing is claimed, the "Toujours" screen is shown (after the end of the check only).
        assertEquals(GateScreen.LOCATION, gate(missing, renewal = RenewalState.UNKNOWN))
    }

    @Test
    fun whileTheCheckRuns_neitherTheLocationScreenNorTheFeedWhenTheLocationIsMissing() {
        val missing = snap(notifications = true)
        // A member who shares under an older text must not be able to give the background location BEFORE the agreement (review of the PR).
        assertEquals(GateScreen.CHECKING, gate(missing, renewal = RenewalState.CHECKING))
        assertEquals(GateScreen.CHECKING, gate(missing, blocking = false, renewal = RenewalState.CHECKING))
        // The notifications never wait for the check; with the location complete there is nothing to wait for (the Feed, then the renewal).
        assertEquals(GateScreen.NOTIFICATIONS, gate(snap(), renewal = RenewalState.CHECKING))
        assertEquals(GateScreen.NONE, gate(full, renewal = RenewalState.CHECKING))
        // Relaxed build and "Plus tard" already said: nothing to wait for either.
        assertEquals(GateScreen.NONE, gate(missing, blocking = false, deferred = true, renewal = RenewalState.CHECKING))
        // The other order (location first, decision pending): the check does not delay the location screen.
        assertEquals(GateScreen.LOCATION, gate(missing, renewal = RenewalState.CHECKING, consentFirst = false))
        // The end of the check, whatever it answers, lets the right screen through.
        assertEquals(GateScreen.LOCATION, gate(missing, renewal = RenewalState.NOTHING_DUE))
        assertEquals(GateScreen.LOCATION, gate(missing, renewal = RenewalState.UNKNOWN))
        assertEquals(GateScreen.RENEWAL, gate(missing, renewal = RenewalState.DUE))
    }

    @Test
    fun theOtherOrder_theLocationStaysBlockingWhateverTheConsent() {
        val missing = snap(notifications = true)
        assertEquals(GateScreen.LOCATION, gate(missing, renewal = RenewalState.DUE, consentFirst = false))
        assertEquals(
            GateScreen.LOCATION,
            gate(missing, renewal = RenewalState.DUE, renewalDeferred = true, consentFirst = false),
        )
        assertEquals(GateScreen.RENEWAL, gate(full, renewal = RenewalState.DUE, consentFirst = false))
    }

    // --- Location flow -----------------------------------------------------------------------------------------------

    @Test
    fun theWindowAsksForWhatTheVersionOfAndroidAllows() {
        assertEquals(
            listOf(PermissionNames.FINE_LOCATION, PermissionNames.COARSE_LOCATION),
            LocationFlow.requestedPermissions(28),
        )
        assertEquals(
            listOf(PermissionNames.FINE_LOCATION, PermissionNames.COARSE_LOCATION, PermissionNames.BACKGROUND_LOCATION),
            LocationFlow.requestedPermissions(29),
        )
        // From Android 11 asking "all the time" together with the others makes the system ignore the request.
        assertEquals(
            listOf(PermissionNames.FINE_LOCATION, PermissionNames.COARSE_LOCATION),
            LocationFlow.requestedPermissions(33),
        )
    }

    @Test
    fun firstDisplay_isCalm_theWindowOfTheSystem_noMessage() {
        val model = LocationFlow.model(snap(), LocationAttempt())
        assertEquals(LocationButton.SYSTEM_DIALOG, model.button)
        assertFalse(model.steps)
        assertFalse(model.error)
    }

    @Test
    fun android11Plus_afterTheFirstWindowGaveThePreciseOne_theButtonGoesToTheSettings_withoutAnError() {
        val after = snap(fine = true, coarse = true)
        val attempt = LocationFlow.afterDialog(after, canAskAgain = true)
        assertFalse("the settings step is normal, not an error", attempt.error)
        val model = LocationFlow.model(after, attempt)
        assertEquals(LocationButton.OPEN_SETTINGS, model.button)
        assertTrue(model.steps)
    }

    @Test
    fun refusal_showsTheMessage_andTheWindowCanComeAgain_untilTheSystemStopsShowingIt() {
        val after = snap()
        val first = LocationFlow.afterDialog(after, canAskAgain = true)
        assertTrue(first.error)
        assertEquals(LocationButton.SYSTEM_DIALOG, LocationFlow.button(after, first))
        // "Don't ask again": the button then leads to the settings.
        val second = LocationFlow.afterDialog(after, canAskAgain = false)
        assertTrue(second.error)
        assertEquals(LocationButton.OPEN_SETTINGS, LocationFlow.button(after, second))
    }

    @Test
    fun theApproximatePositionAlone_isARefusalOfTheExactOne() {
        val after = snap(coarse = true)
        assertTrue(LocationFlow.afterDialog(after, canAskAgain = true).error)
        assertFalse(after.locationComplete)
    }

    @Test
    fun android10_asksBothInOneWindow_thenTheBackgroundAlone() {
        val whileUsing = snap(sdk = 29, fine = true, coarse = true)
        val attempt = LocationFlow.afterDialog(whileUsing, canAskAgain = true)
        assertTrue(attempt.error)
        assertEquals(LocationButton.SYSTEM_DIALOG, LocationFlow.button(whileUsing, attempt))
        assertEquals(LocationButton.OPEN_SETTINGS, LocationFlow.button(whileUsing, attempt.copy(canAskAgain = false)))
    }

    @Test
    fun returningFromTheSettings_withoutTheRightChoice_showsTheMessage_andWithItRelaxes() {
        val partial = snap(fine = true, coarse = true)
        val away = LocationFlow.settingsOpened(LocationAttempt())
        assertTrue(away.awaitingSettings)
        val back = LocationFlow.afterResume(partial, away)
        assertTrue(back.error)
        assertFalse(back.awaitingSettings)
        // The right choice: no message (the gate closes the screen).
        assertFalse(LocationFlow.afterResume(full, away).error)
        // An ordinary return to the front (no settings visit) changes nothing.
        assertEquals(LocationAttempt(), LocationFlow.afterResume(partial, LocationAttempt()))
    }

    // --- Consent version ---------------------------------------------------------------------------------------------

    @Test
    fun theVersionIsV3_inTheFixedWidthFormat_andCoversThePosition() {
        assertEquals("2026-11-v03", MiffSharingConsent.VERSION)
        assertTrue(MiffSharingConsent.isFixedWidth(MiffSharingConsent.VERSION))
        assertTrue(MiffSharingConsent.coversPosition(MiffSharingConsent.VERSION))
        assertFalse(MiffSharingConsent.coversPosition("2026-10-v2"))
        assertFalse(MiffSharingConsent.coversPosition("2026-10-v1"))
        assertFalse(MiffSharingConsent.coversPosition(null))
        assertTrue(MiffSharingConsent.coversPosition("2027-01-v10"))
    }

    @Test
    fun aRenewalIsDueOnlyForAnOlderKnownText() {
        assertTrue(MiffSharingConsent.isRenewalDue("2026-10-v1"))
        assertTrue(MiffSharingConsent.isRenewalDue("2026-10-v2"))
        assertFalse(MiffSharingConsent.isRenewalDue(MiffSharingConsent.VERSION))
        // A newer text (another build) is never renewed backwards; garbage is not touched.
        assertFalse(MiffSharingConsent.isRenewalDue("2026-12-v04"))
        assertFalse(MiffSharingConsent.isRenewalDue("abc"))
        assertFalse(MiffSharingConsent.isRenewalDue(null))
        // What the rules compare: the new version is strictly greater than each old one (as strings).
        assertTrue(MiffSharingConsent.VERSION > "2026-10-v1")
        assertTrue(MiffSharingConsent.VERSION > "2026-10-v2")
    }
}
