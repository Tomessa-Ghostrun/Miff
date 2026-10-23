package com.tomessa.miff.ui.diagnostic

import com.tomessa.miff.data.AppCheckOutcome
import com.tomessa.miff.data.DiagnosticRepository
import com.tomessa.miff.data.Test2Result
import com.tomessa.miff.data.privacy.BooleanStore
import com.tomessa.miff.data.privacy.CollectionSwitch
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.domain.analytics.AnalyticsSink
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.auth.CredentialsInputProblem
import com.tomessa.miff.domain.diagnostic.OfferState
import com.tomessa.miff.domain.diagnostic.RulesProbeOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticViewModelTest {
    private class FakeRepository : DiagnosticRepository {
        override val firebaseProjectId: String = "miff-3d283"
        var probeResult = RulesProbeOutcome.RULES_ACTIVE
        var test2Result: Test2Result = Test2Result.Success(profileExists = false, offer = OfferState.Classic)
        val signInCalls = mutableListOf<Pair<String, String>>()
        var probeCalls = 0

        override suspend fun probeRulesUnauthenticated(): RulesProbeOutcome {
            probeCalls++
            return probeResult
        }

        override suspend fun signInAndReadOwnDocuments(
            email: String,
            password: String,
        ): Test2Result {
            signInCalls += email to password
            return test2Result
        }

        override suspend fun fetchAppCheckToken(): AppCheckOutcome = AppCheckOutcome.NoToken
    }

    private class MemoryStore : BooleanStore {
        val values = mutableMapOf<String, Boolean>()

        override fun get(
            key: String,
            default: Boolean,
        ) = values[key] ?: default

        override fun put(
            key: String,
            value: Boolean,
        ) {
            values[key] = value
        }
    }

    private class NoopSwitch : CollectionSwitch {
        override fun setEnabled(enabled: Boolean) = Unit
    }

    private class RecordingSink : AnalyticsSink {
        val logged = mutableListOf<String>()

        override fun log(
            eventName: String,
            params: Map<String, Any>,
        ) {
            logged += eventName
        }
    }

    private val repository = FakeRepository()
    private val store = MemoryStore()
    private val sink = RecordingSink()
    private lateinit var privacy: PrivacySettings
    private lateinit var viewModel: DiagnosticViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        privacy = PrivacySettings(store, NoopSwitch(), NoopSwitch())
        viewModel =
            DiagnosticViewModel(
                repository = repository,
                privacy = privacy,
                tracker = AnalyticsTracker(sink) { privacy.analyticsEnabled.value },
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsIdleWithCollectionsOff() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val state = viewModel.uiState.value
            assertEquals("miff-3d283", state.firebaseProjectId)
            assertEquals(Test1State.Idle, state.test1)
            assertEquals(Test2State.Idle, state.test2)
            assertEquals(AppCheckState.Idle, state.appCheck)
            assertFalse(state.analyticsEnabled)
            assertFalse(state.crashReportsEnabled)
        }

    @Test
    fun test1ReportsTheOutcomeOfTheAnonymousRead() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            viewModel.runTest1()
            assertEquals(Test1State.Done(RulesProbeOutcome.RULES_ACTIVE), viewModel.uiState.value.test1)

            repository.probeResult = RulesProbeOutcome.RULES_OPEN
            viewModel.runTest1()
            assertEquals(Test1State.Done(RulesProbeOutcome.RULES_OPEN), viewModel.uiState.value.test1)
        }

    @Test
    fun test2WithEmptyInputNeverContactsFirebase() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            viewModel.runTest2("  ", "secret")
            assertEquals(
                Test2State.InputProblem(CredentialsInputProblem.EMAIL_EMPTY),
                viewModel.uiState.value.test2,
            )
            assertTrue(repository.signInCalls.isEmpty())
        }

    @Test
    fun test2PassesCredentialsToTheRepositoryButNeverKeepsThePassword() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val password = "correct-horse-battery-staple"
            viewModel.runTest2("test@example.com", password)

            assertEquals(listOf("test@example.com" to password), repository.signInCalls)
            val state = viewModel.uiState.value
            assertTrue(state.test2 is Test2State.Done)
            assertFalse("the password must not appear in the UI state", state.toString().contains(password))
        }

    @Test
    fun test2ShowsAFailureWithoutExposingDetails() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            repository.test2Result = Test2Result.ReadDenied
            viewModel.runTest2("test@example.com", "pw")
            assertEquals(Test2State.Done(Test2Result.ReadDenied), viewModel.uiState.value.test2)
        }

    @Test
    fun testEventIsNotSentWhileAnalyticsIsOffThenSentWhenOn() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            viewModel.sendTestEvent()
            assertEquals(EventFeedback.Disabled, viewModel.uiState.value.eventFeedback)
            assertTrue(sink.logged.isEmpty())

            viewModel.setAnalyticsEnabled(true)
            viewModel.sendTestEvent()
            assertEquals(EventFeedback.Sent, viewModel.uiState.value.eventFeedback)
            assertEquals(listOf("analytics_diagnostic_test"), sink.logged)
        }

    @Test
    fun aPositionIsRefusedEvenWhenAnalyticsIsOn() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            viewModel.setAnalyticsEnabled(true)
            viewModel.tryToSendPosition()
            assertEquals(EventFeedback.LocationRefused, viewModel.uiState.value.eventFeedback)
            assertTrue(sink.logged.isEmpty())
        }

    @Test
    fun switchesAreReflectedInTheStateAndPersisted() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            viewModel.setAnalyticsEnabled(true)
            viewModel.setCrashReportsEnabled(true)
            val state = viewModel.uiState.value
            assertTrue(state.analyticsEnabled)
            assertTrue(state.crashReportsEnabled)
            assertEquals(true, store.values[PrivacySettings.KEY_ANALYTICS])
            assertEquals(true, store.values[PrivacySettings.KEY_CRASH])
        }
}
