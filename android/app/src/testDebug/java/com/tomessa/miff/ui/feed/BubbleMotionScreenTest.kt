package com.tomessa.miff.ui.feed

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.FeedInput
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

private class FixedLifecycle(
    state: Lifecycle.State,
) : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).also { it.currentState = state }
    override val lifecycle: Lifecycle get() = registry
}

/**
 * The movement of the bubble on the JVM: none with "Animations supprimées", none when the screen is not on view, none off screen, and nothing of
 * what the Feed says changes (texts and states, whatever the setting). The amplitudes and the cycle are checked in BubbleMotionTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h900dp-port-xxhdpi")
class BubbleMotionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun restoreTheScale() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun setScale(scale: Float) =
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)

    private fun probe(state: Lifecycle.State): BubblePhases? {
        var phases: BubblePhases? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides FixedLifecycle(state)) {
                phases = rememberBubblePhases()
            }
        }
        composeRule.waitForIdle()
        return phases
    }

    @Test
    fun withTheAnimationsOn_andTheScreenStarted_thereIsAMovement() {
        setScale(1f)
        assertNotNull(probe(Lifecycle.State.RESUMED))
    }

    @Test
    fun withAnimationsRemoved_thereIsNoMovementAtAll() {
        // "Animations supprimées" / animation scale "off": the animator duration scale is 0.
        setScale(0f)
        assertNull(probe(Lifecycle.State.RESUMED))
    }

    @Test
    fun whenTheScreenIsNotStarted_inTheBackground_thereIsNoMovement() {
        setScale(1f)
        assertNull(probe(Lifecycle.State.CREATED))
    }

    private val me = "me"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"
    private val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()

    private fun member(
        uid: String,
        first: String,
    ) = MiffMember(uid, first, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, true, "+33612345678")

    private val directory =
        DirectoryState.Ready(
            listOf(MiffSummary(miffId, "Famille")),
            miffId,
            premium = false,
            fromCache = false,
        )

    private fun content(
        members: List<MiffMember>,
        statuses: Map<String, MemberStatus> = emptyMap(),
    ): FeedContent =
        FeedBuilder.build(
            FeedInput(
                miffId,
                "Famille",
                "XTZRSP367988",
                members,
                emptyList(),
                me,
                StatusesView.Loaded(statuses),
                false,
                false,
            ),
            now,
            ZoneOffset.UTC,
        )

    private fun show(content: FeedContent) =
        composeRule.setContent {
            MiffTheme {
                Box(Modifier.fillMaxSize()) {
                    FeedScreen(
                        state = directory,
                        scrollToTopSignal = 0,
                        onRetry = {},
                        onSelect = {},
                        onCreate = {},
                        onJoin = {},
                        feed = FeedUiState.Ready(content),
                    )
                }
            }
        }

    @Test
    fun offScreen_theBubbleIsNotComposed_soNothingRuns() {
        setScale(0f)
        val many = listOf(member(me, "Camille")) + (1..9).map { member("u$it", "Proche$it") }
        show(content(many))
        composeRule
            .onNodeWithText(
                "Dès que tes proches donneront des nouvelles, tu les verras ici.",
            ).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Ajouter un proche"))
        // Scrolled away: the bubble is no longer composed (a LazyColumn drops it), so no animation can run for it.
        composeRule
            .onAllNodesWithText(
                "Dès que tes proches donneront des nouvelles, tu les verras ici.",
            ).assertCountEquals(0)
    }

    @Test
    fun theTexts_areTheSame_withTheMovementOn() {
        setScale(1f)
        val status =
            MemberStatus(PresenceValue.IN_TRUSTED, null, now - 720_000, now - 720_000, false, 85, null, now)
        show(content(listOf(member(me, "Camille"), member("t", "Thomas")), mapOf("t" to status)))
        composeRule.onNodeWithText("Tout va bien").assertIsDisplayed()
        composeRule.onNodeWithText("Dernière nouvelle il y a 12 min").assertIsDisplayed()
    }

    @Test
    fun theTexts_areTheSame_withTheMovementRemoved() {
        setScale(0f)
        val status =
            MemberStatus(PresenceValue.IN_TRUSTED, null, now - 720_000, now - 720_000, false, 85, null, now)
        show(content(listOf(member(me, "Camille"), member("t", "Thomas")), mapOf("t" to status)))
        composeRule.onNodeWithText("Tout va bien").assertIsDisplayed()
        composeRule.onNodeWithText("Dernière nouvelle il y a 12 min").assertIsDisplayed()
    }
}
