package com.tomessa.miff.ui.alert

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.tomessa.miff.data.alert.AlertController
import com.tomessa.miff.data.alert.AlertStore
import com.tomessa.miff.data.alert.AlertsHeard
import com.tomessa.miff.data.alert.EndResult
import com.tomessa.miff.data.alert.MemoryAlertLocalStore
import com.tomessa.miff.data.alert.MineRead
import com.tomessa.miff.data.alert.MyAlert
import com.tomessa.miff.data.alert.SendStep
import com.tomessa.miff.data.alert.StartResult
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.miff.FacesRead
import com.tomessa.miff.data.miff.MemberFacesReader
import com.tomessa.miff.domain.alert.AlertFailure
import com.tomessa.miff.testsupport.FakeActiveDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screen of the daily limit with a REAL view model (review of #275 v2, 1): choosing the emergency service must happen ONCE. A new state object at each call used to make the effect
 * of the screen run again and again (about 115 times a second: the CPU and the battery at full speed on the screen of the 4th alert). If it looped, this test would never get idle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class AlertSentHostLoopTest {
    @get:Rule
    val rule = createComposeRule()

    private class LimitStore : AlertStore {
        override suspend fun start(
            uid: String,
            miffId: String,
            deviceId: String,
            nowMillis: Long,
        ): StartResult = StartResult.Failed(AlertFailure.QuotaReached)

        override suspend fun end(
            uid: String,
            miffId: String,
            deviceId: String,
            nowMillis: Long,
        ): EndResult = EndResult.Ended

        override suspend fun readMine(
            uid: String,
            miffId: String,
        ): MineRead = MineRead.Value(null)

        override fun observe(miffId: String): Flow<AlertsHeard> = emptyFlow()
    }

    @Test
    fun theScreenOfTheDailyLimit_choosesTheEmergencyServiceOnce_andGoesIdle() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller =
            AlertController(
                store = LimitStore(),
                device = FakeActiveDevice(),
                network =
                    object : NetworkMonitor {
                        override fun isOnline() = true
                    },
                local = MemoryAlertLocalStore(),
                currentUid = { "me" },
                scope = scope,
            )
        val model =
            AlertViewModel(
                controller,
                MemoryAlertLocalStore(),
                object : MemberFacesReader {
                    override suspend fun read(miffId: String): FacesRead = FacesRead.Loaded(emptyList())
                },
                { "me" },
                { "FR" },
            )
        controller.trigger("m1")
        assertEquals(SendStep.QUOTA, (controller.state.value as MyAlert.Sending).step)
        var emissions = 0
        val counting = scope.launch { model.call.collect { emissions++ } }
        rule.setContent {
            AlertSentHost(model, AlertSentRoute("m1"), "Family", several = false, onBack = {}, onResumeSharing = {})
        }
        // a loop would never let the composition get idle: this call would fail ("did not get idle")
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        counting.cancel()
        assertTrue("the choice was made $emissions times", emissions <= 4)
        assertEquals("112", (model.call.value as CallState.Ready).selected.number)
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).performScrollTo()
    }
}
