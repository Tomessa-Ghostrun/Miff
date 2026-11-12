package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.Bubble
import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.FeedInput
import com.tomessa.miff.domain.feed.MemberLocationAccess
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.ui.main.ColorsInfoRoute
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * COULEURS-1 (criteria E1 to E5, A2): a touch on the big bubble of the Feed opens "Les couleurs de ta Miff", in the seven variants of the bubble and on every
 * page of a carousel of alerts; "Contacter" and "Reprendre le partage" keep their behaviour and do NOT open it; the screen reader is offered the action
 * "Comprendre les couleurs" and still reaches the buttons separately; nothing visible is added to the bubble. The bubble is driven here with the real
 * builder of the Feed (the same cases as the Feed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h900dp-port-xxhdpi")
class BubbleColorsEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val me = "me"

    private fun member(
        uid: String,
        first: String,
        sharing: Boolean = true,
    ) = MiffMember(uid, first, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, sharing, "+33612345678")

    private fun status(
        presence: PresenceValue = PresenceValue.IN_TRUSTED,
        ago: Long = 12 * minute,
    ) = MemberStatus(presence, "p1", now - ago, now - ago, false, 85, MemberLocationAccess.BACKGROUND, now - minute)

    private fun trip() =
        MemberStatus(
            PresenceValue.TRAVEL,
            null,
            now - 2 * hour,
            now - 3 * hour,
            false,
            null,
            MemberLocationAccess.DENIED,
            now - hour,
            Instant.parse("2026-11-16T00:00:00Z").toEpochMilli(),
            "2026-11-15",
        )

    private val places = listOf(testPlace("p1", "Chez nous", category = PlaceCategory.HOME))
    private val trio = listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine"))

    private fun content(
        members: List<MiffMember>,
        statuses: Map<String, MemberStatus>,
        view: StatusesView = StatusesView.Loaded(statuses),
        waiting: Boolean = false,
    ): FeedContent =
        FeedBuilder.build(
            FeedInput("m1", "Famille", "XTZRSP367988", members, places, me, view, waiting, false),
            now,
            ZoneOffset.UTC,
        )

    private var opened = 0
    private var messaged: String? = null
    private var resumed = 0

    private fun show(content: FeedContent) {
        composeRule.setContent {
            MiffTheme {
                Box(Modifier.fillMaxSize()) { Bubble(content) }
            }
        }
    }

    @Composable
    private fun Bubble(content: FeedContent) {
        BubbleView(
            bubble = content.bubble,
            announceKey = content.announceKey,
            texts = rememberFeedTexts(),
            onMessage = { messaged = it },
            onResumeSharing = { resumed++ },
            travelNote = content.travelNote,
            onOpenColors = { opened++ },
        )
    }

    /** Touches the bubble by one of its words (a touch on a text of the blob is a touch on the blob). */
    private fun touch(word: String) {
        composeRule.onNodeWithText(word).performClick()
    }

    // --- E1: the seven variants ---------------------------------------------------------------------------------------------

    @Test
    fun allGood_opensTheColors() {
        show(content(trio, mapOf(me to status(), "t" to status(), "a" to status())))
        assertTrue(composeRule.onAllNodesWithText("Tout va bien").fetchSemanticsNodes().isNotEmpty())
        touch("Tout va bien")
        assertEquals(1, opened)
    }

    @Test
    fun noNews_opensTheColors() {
        show(content(trio, mapOf(me to status())))
        touch(text(R.string.feed_bubble_no_news_title))
        assertEquals(1, opened)
    }

    @Test
    fun alone_opensTheColors() {
        show(content(listOf(member(me, "Camille")), mapOf(me to status())))
        touch(text(R.string.feed_bubble_alone))
        assertEquals(1, opened)
    }

    @Test
    fun awaitingAgreement_opensTheColors() {
        show(content(trio, emptyMap(), view = StatusesView.Off, waiting = true))
        touch(text(R.string.miff_waiting_feed_note))
        assertEquals(1, opened)
    }

    @Test
    fun sharingStopped_opensTheColors_butItsButtonOnlyResumesTheSharing() {
        show(
            content(
                listOf(member(me, "Camille", sharing = false), member("t", "Thomas")),
                emptyMap(),
                view = StatusesView.Off,
            ),
        )
        touch(text(R.string.feed_bubble_sharing_stopped_title))
        assertEquals(1, opened)
        // E2: "Reprendre le partage" keeps its behaviour and does NOT open the page.
        touch(text(R.string.feed_bubble_resume_action))
        assertEquals(1, resumed)
        assertEquals("the button does not open the page", 1, opened)
    }

    @Test
    fun travelers_opensTheColors() {
        show(content(trio, mapOf(me to status(), "t" to trip(), "a" to trip())))
        assertTrue(content(trio, mapOf(me to status(), "t" to trip(), "a" to trip())).bubble is Bubble.Travelers)
        touch("Tes proches sont en voyage.")
        assertEquals(1, opened)
    }

    @Test
    fun carousel_opensTheColors_onEveryPage_andTheButtonDoesNot() {
        val both =
            content(
                trio,
                mapOf(
                    me to status(),
                    "t" to status(PresenceValue.OUT, 150 * minute),
                    "a" to status(PresenceValue.OUT, 5 * hour),
                ),
            )
        assertTrue(both.bubble is Bubble.Alerts)
        show(both)
        // Red page first (Amandine): a touch on its words opens the page ...
        touch("Pas de nouvelles de Amandine depuis 12 h 00".t())
        assertEquals(1, opened)
        // ... while "Contacter" sends the message and does not.
        composeRule.onNodeWithText(text(R.string.feed_bubble_message_action)).performClick()
        assertEquals("+33612345678", messaged)
        assertEquals(1, opened)
        // The orange page (Thomas), after a swipe.
        composeRule.onNodeWithText("Pas de nouvelles de Amandine depuis 12 h 00".t()).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        val orange = composeRule.onAllNodesWithText("Thomas", substring = true)
        assertTrue("the orange page is shown after the swipe", orange.fetchSemanticsNodes().isNotEmpty())
        orange[0].performClick()
        assertEquals(2, opened)
    }

    // --- E4, E5, A2: nothing visible is added; a big touch area; the action of the screen reader --------------------------------

    @Test
    fun theBubbleGainsNoVisibleHint_noTextNoLink() {
        show(content(trio, mapOf(me to status(), "t" to status(), "a" to status())))
        composeRule.onAllNodesWithText(text(R.string.feed_colors_bubble_action_a11y)).assertCountEquals(0)
        composeRule.onAllNodesWithText("couleur", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("En savoir plus", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun theBubble_isBigEnoughToTouch_andOffersTheActionToTheScreenReader() {
        show(content(trio, mapOf(me to status(), "t" to status(), "a" to status())))
        val node = composeRule.onNodeWithText("Tout va bien")
        node.assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        // TalkBack says "Appuyez deux fois pour Comprendre les couleurs".
        val click = node.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)
        assertNotNull(click)
        assertEquals(text(R.string.feed_colors_bubble_action_a11y), click?.label)
    }

    @Test
    fun theButtonOfTheCarousel_staysASeparateNodeForTheScreenReader() {
        val orange = content(trio, mapOf(me to status(), "t" to status(PresenceValue.OUT, 150 * minute)))
        show(orange)
        val button = composeRule.onNodeWithText(text(R.string.feed_bubble_message_action)).fetchSemanticsNode().config
        // The button has its OWN click action, with no label of the colors: it is not merged into the bubble.
        val click = button.getOrNull(SemanticsActions.OnClick)
        assertNotNull(click)
        assertTrue(click?.label != text(R.string.feed_colors_bubble_action_a11y))
    }

    // --- E7: the page is pushed (no tab bar), one copy only, and the way back finds the Feed ---------------------------------------

    @Test
    fun touchingTheBubble_pushesThePageOnce_andTheWayBackFindsTheFeed() {
        val feed = content(trio, mapOf(me to status(), "t" to status(), "a" to status()))
        lateinit var open: () -> Unit
        composeRule.setContent {
            MiffTheme {
                val navController = rememberNavController()
                open = { navController.navigate(ColorsInfoRoute) { launchSingleTop = true } }
                NavHost(navController = navController, startDestination = "feed") {
                    composable("feed") {
                        BubbleView(
                            bubble = feed.bubble,
                            announceKey = feed.announceKey,
                            texts = rememberFeedTexts(),
                            onMessage = {},
                            onResumeSharing = {},
                            onOpenColors = open,
                        )
                    }
                    composable<ColorsInfoRoute> { ColorsInfoScreen(onBack = { navController.popBackStackIfSafe() }) }
                }
            }
        }
        // A fast double touch: ONE page.
        composeRule.onNodeWithText("Tout va bien").performClick()
        composeRule.onNodeWithText(text(R.string.feed_colors_title)).assertExists()
        composeRule.onAllNodesWithText("Tout va bien").assertCountEquals(0)
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).performClick()
        composeRule.onNodeWithText("Tout va bien").assertExists()
        composeRule.onAllNodesWithText(text(R.string.feed_colors_title)).assertCountEquals(0)
        // Opened twice in a row without waiting (a fast double touch): still ONE page, so one touch of the way back finds the Feed.
        composeRule.runOnUiThread {
            open()
            open()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).performClick()
        composeRule.onNodeWithText("Tout va bien").assertExists()
    }
}
