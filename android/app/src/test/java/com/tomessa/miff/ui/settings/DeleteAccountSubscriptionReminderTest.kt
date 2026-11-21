package com.tomessa.miff.ui.settings

import com.tomessa.miff.domain.subscription.StoreState
import com.tomessa.miff.domain.subscription.SubscriptionReminder
import com.tomessa.miff.domain.subscription.SubscriptionSource
import com.tomessa.miff.domain.subscription.SubscriptionStatus
import com.tomessa.miff.testsupport.FakeAuthGateway
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakeProcedure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The reminder of the deletion of the account (ticket premium-achat-reel.md section 9.5, criterion AN14): deleting the account does NOT cancel a subscription that the store keeps
 * billing. The sentence exists only for a subscription that can still be billed, is stronger during the free trial, never blocks the deletion, and is prudent (not silent) when
 * the server cannot say.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountSubscriptionReminderTest {
    private val dispatcher = StandardTestDispatcher()
    private val network = FakeNetwork()
    private val procedure = FakeProcedure()
    private val gateway = FakeAuthGateway().also { it.setSignedIn("uid-1") }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun info(read: (suspend () -> SubscriptionStatus?)?) =
        DeleteAccountInfoViewModel(
            procedure,
            { gateway.currentUid },
            network,
            appleLinked = false,
            subscriptionStatus = read,
        )

    private fun store(
        state: StoreState,
        source: SubscriptionSource = SubscriptionSource.GOOGLE_PLAY,
    ) = SubscriptionStatus.Store(source, state, plan = null, expiresAtMillis = 1L, test = false)

    @Test
    fun anActiveSubscription_isRemembered_withTheStoreItLivesIn() =
        runTest(dispatcher) {
            val vm = info { store(StoreState.ACTIVE) }
            advanceUntilIdle()
            assertEquals(SubscriptionReminder(SubscriptionSource.GOOGLE_PLAY, trial = false), vm.uiState.value.reminder)
            assertFalse(vm.uiState.value.reminderUnknown)
        }

    @Test
    fun duringTheTrial_theReminderIsTheStrongerOne() =
        runTest(dispatcher) {
            val vm = info { store(StoreState.TRIAL) }
            advanceUntilIdle()
            assertEquals(SubscriptionReminder(SubscriptionSource.GOOGLE_PLAY, trial = true), vm.uiState.value.reminder)
        }

    @Test
    fun aSubscriptionBoughtInTheAppStore_namesTheAppStore() =
        runTest(dispatcher) {
            val vm = info { store(StoreState.ACTIVE, SubscriptionSource.APP_STORE) }
            advanceUntilIdle()
            assertEquals(
                SubscriptionSource.APP_STORE,
                vm.uiState.value.reminder
                    ?.source,
            )
        }

    @Test
    fun noReminder_forAFreeAccount_aCancelledOrOverSubscription_orAGrantByHand() =
        runTest(dispatcher) {
            listOf<SubscriptionStatus>(
                SubscriptionStatus.Free,
                SubscriptionStatus.Manual(1L),
                store(StoreState.CANCELED),
                store(StoreState.EXPIRED),
                store(StoreState.REVOKED),
            ).forEach { status ->
                val vm = info { status }
                advanceUntilIdle()
                assertNull(status.toString(), vm.uiState.value.reminder)
                assertFalse(status.toString(), vm.uiState.value.reminderUnknown)
            }
        }

    @Test
    fun whenTheServerCannotSay_theSentenceIsPrudent_notSilent() =
        runTest(dispatcher) {
            val vm = info { null }
            advanceUntilIdle()
            assertNull(vm.uiState.value.reminder)
            assertTrue(vm.uiState.value.reminderUnknown)
            // The deletion itself is not held up: the list of the Miffs is read and "Continuer" is available.
            assertTrue(vm.uiState.value.canContinue)
        }

    @Test
    fun aReadThatNeverAnswers_isGivenUp_andTheScreenIsUsable() =
        runTest(dispatcher) {
            val vm = info { awaitCancellation() }
            runCurrent()
            advanceTimeBy(60_000)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.canContinue)
            assertTrue(vm.uiState.value.reminderUnknown)
        }

    @Test
    fun whilePurchaseIsOff_nothingIsReadAndNothingIsSaid() =
        runTest(dispatcher) {
            val vm = info(null)
            advanceUntilIdle()
            assertNull(vm.uiState.value.reminder)
            assertFalse(vm.uiState.value.reminderUnknown)
        }

    @Test
    fun theSubscriptionIsOnlyReadOnACompleteAnswer_offlineReadsNothing() =
        runTest(dispatcher) {
            network.online = false
            var reads = 0
            val vm = info { reads++.let { null } }
            advanceUntilIdle()
            assertEquals(0, reads)
            assertFalse(vm.uiState.value.canContinue)
        }

    @Test
    fun theSentencesAreCalm_noAlarm_noBlame_inEveryLanguage() {
        val keys =
            listOf(
                "settings_delete_sub_title",
                "settings_delete_sub_body_play",
                "settings_delete_sub_body_appstore",
                "settings_delete_sub_trial",
                "settings_delete_sub_body_unknown",
            )
        com.tomessa.miff.testsupport.I18nResources.present.forEach { language ->
            val text = language.text()
            keys.forEach { key ->
                val value = Regex("""<string name="$key">(.*?)</string>""").find(text)?.groupValues?.get(1)
                assertTrue("${language.code} lacks $key", value != null)
                assertFalse("${language.code} $key: no exclamation mark", value!!.contains("!"))
                assertFalse(
                    "${language.code} $key: no alarm word",
                    Regex("""(?i)attention|urgent|danger|perdre|perdu|warning|achtung""").containsMatchIn(value),
                )
            }
        }
        // The sources of the screen carry the manage button and the three bodies.
        val screen = File("src/main/java/com/tomessa/miff/ui/settings/DeleteAccountScreens.kt").readText()
        assertTrue(screen.contains("settings_delete_sub_manage") && screen.contains("settings_delete_sub_body_unknown"))
    }
}
