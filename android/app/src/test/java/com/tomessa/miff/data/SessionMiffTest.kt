package com.tomessa.miff.data

import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.testsupport.SessionHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The onboarding is complete only once a Miff was created or joined (CLAUDE.md, ticket Miff 6): the session says when. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionMiffTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(dispatcher + SupervisorJob())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.harness(analyticsConsent: Boolean = true): SessionHarness =
        SessionHarness(sessionScope).also {
            it.gateway.setSignedIn("uid-1")
            it.accounts.setLoaded(profileExists = true, hasMembership = false, analyticsConsent = analyticsConsent)
            it.manager.start()
            advanceUntilIdle()
        }

    private fun SessionHarness.destination() = (manager.state.value as SessionState.Ready).destination

    @Test
    fun withoutAMiff_theAppStaysOnTheChoice_untilTheMiffScreensSayOtherwise() =
        runTest(dispatcher) {
            val h = harness()
            assertEquals(AppDestination.MIFF_CHOICE, h.destination())
            // Creating a Miff does not move the app by itself: the "Ta Miff est prête" screen still has to be seen.
            h.manager.onMiffEntered(AnalyticsEvent.OnboardingVia.CREATED)
            assertEquals(AppDestination.MIFF_CHOICE, h.destination())
            assertTrue(h.manager.accountSnapshot?.hasMembership == true)
            h.manager.continueAfterMiff()
            assertEquals(AppDestination.FEED, h.destination())
        }

    @Test
    fun onboardingCompleted_isCountedOnce_withHowItEnded_andOnlyFromTheChoiceStep() =
        runTest(dispatcher) {
            val h = harness()
            h.manager.onMiffEntered(AnalyticsEvent.OnboardingVia.JOINED)
            assertEquals(listOf("onboarding_completed"), h.sink.names)
            assertEquals(
                mapOf<String, Any>("via" to "joined"),
                h.sink.events
                    .single()
                    .second,
            )
            h.manager.continueAfterMiff()
            // A second Miff created later, from the Feed, is not the end of the onboarding.
            h.manager.onMiffEntered(AnalyticsEvent.OnboardingVia.CREATED)
            assertEquals(1, h.sink.events.size)
        }

    @Test
    fun withoutTheStatisticsConsent_nothingIsSent() =
        runTest(dispatcher) {
            val h = harness(analyticsConsent = false)
            h.manager.onMiffEntered(AnalyticsEvent.OnboardingVia.CREATED)
            assertTrue(h.sink.events.isEmpty())
        }

    @Test
    fun continueAfterMiff_doesNothingOutsideTheChoiceStep() =
        runTest(dispatcher) {
            val h = harness()
            h.manager.continueAfterMiff()
            h.manager.continueAfterMiff()
            assertEquals(AppDestination.FEED, h.destination())
        }
}
