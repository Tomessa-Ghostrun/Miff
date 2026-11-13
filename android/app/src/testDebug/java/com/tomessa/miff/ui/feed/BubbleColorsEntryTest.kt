package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * COULEURS-1, version 2 (decision of the PM, 2026-11-13): the page "Les couleurs de ta Miff" opens with the small "i" at the bottom right of the big bubble,
 * NOT with a touch on the bubble. Here: the "i" exists in the seven variants of the bubble and on the carousel of alerts (ONE "i", at the same place
 * whatever the page); it opens the page; a touch on the bubble opens nothing and the bubble has no action for the screen reader; "Contacter" and "Reprendre
 * le partage" keep their behaviour; the touch area is 48 dp; the "i" never covers a word or a button, at the largest font; the screen reader finds a button
 * "Comprendre les couleurs". The bubble is driven with the real builder of the Feed (the same cases as the Feed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h900dp-port-xxhdpi")
class BubbleColorsEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var defaultZone: TimeZone = TimeZone.getDefault()

    @Before
    fun fixTheZone() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTheZone() {
        TimeZone.setDefault(defaultZone)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private val infoLabel get() = text(R.string.feed_colors_bubble_action_a11y)

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

    private val allGood get() = content(trio, mapOf(me to status(), "t" to status(), "a" to status()))
    private val noNews get() = content(trio, mapOf(me to status()))
    private val alone get() = content(listOf(member(me, "Camille")), mapOf(me to status()))
    private val awaiting get() = content(trio, emptyMap(), view = StatusesView.Off, waiting = true)
    private val stopped
        get() =
            content(
                listOf(member(me, "Camille", sharing = false), member("t", "Thomas")),
                emptyMap(),
                view = StatusesView.Off,
            )
    private val travelers get() = content(trio, mapOf(me to status(), "t" to trip(), "a" to trip()))
    private val oneOrange get() = content(trio, mapOf(me to status(), "t" to status(PresenceValue.OUT, 150 * minute)))
    private val oneRed get() = content(trio, mapOf(me to status(), "t" to status(PresenceValue.OUT, 5 * hour)))
    private val redAndOrange
        get() =
            content(
                trio,
                mapOf(
                    me to status(),
                    "t" to status(PresenceValue.OUT, 150 * minute),
                    "a" to status(PresenceValue.OUT, 5 * hour),
                ),
            )

    private var opened = 0
    private var messaged: String? = null
    private var resumed = 0

    private fun show(
        content: FeedContent,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            val provided = if (fontScale == null) density else Density(density.density, fontScale = fontScale)
            CompositionLocalProvider(LocalDensity provides provided) {
                MiffTheme {
                    Box(Modifier.fillMaxSize()) { Bubble(content) }
                }
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

    private fun touchTheInfo() {
        composeRule.onNodeWithContentDescription(infoLabel).performClick()
    }

    private fun boundsOf(description: String): Rect =
        composeRule
            .onNodeWithContentDescription(description)
            .fetchSemanticsNode()
            .boundsInRoot

    private fun boundsOfText(word: String): Rect =
        composeRule
            .onNodeWithText(word)
            .fetchSemanticsNode()
            .boundsInRoot

    // --- the "i" is in the seven variants, opens the page, and a touch on the bubble opens nothing ------------------------------------

    private fun assertInfoOpensThePage_andTheBubbleDoesNot(
        feed: FeedContent,
        word: String,
    ) {
        show(feed)
        composeRule.onAllNodesWithContentDescription(infoLabel).assertCountEquals(1)
        // A touch on the words of the bubble does nothing (the page of the colours is NOT opened).
        composeRule.onAllNodesWithText(word, substring = true)[0].performClick()
        assertEquals("a touch on the bubble opens nothing", 0, opened)
        touchTheInfo()
        assertEquals(1, opened)
    }

    @Test
    fun allGood_hasTheInfo() {
        assertTrue(allGood.bubble is Bubble.AllGood)
        assertInfoOpensThePage_andTheBubbleDoesNot(allGood, "Tout va bien")
    }

    @Test
    fun noNews_hasTheInfo() =
        assertInfoOpensThePage_andTheBubbleDoesNot(noNews, text(R.string.feed_bubble_no_news_title))

    @Test
    fun alone_hasTheInfo() = assertInfoOpensThePage_andTheBubbleDoesNot(alone, text(R.string.feed_bubble_alone))

    @Test
    fun awaitingAgreement_hasTheInfo() =
        assertInfoOpensThePage_andTheBubbleDoesNot(awaiting, text(R.string.miff_waiting_feed_note))

    @Test
    fun travelers_hasTheInfo() {
        assertTrue(travelers.bubble is Bubble.Travelers)
        assertInfoOpensThePage_andTheBubbleDoesNot(travelers, "Tes proches sont en voyage.")
    }

    @Test
    fun sharingStopped_hasTheInfo_andItsButtonStillOnlyResumesTheSharing() {
        assertInfoOpensThePage_andTheBubbleDoesNot(stopped, text(R.string.feed_bubble_sharing_stopped_title))
        // "Reprendre le partage" keeps its behaviour and does NOT open the page.
        composeRule.onNodeWithText(text(R.string.feed_bubble_resume_action)).performClick()
        assertEquals(1, resumed)
        assertEquals("the button does not open the page", 1, opened)
    }

    @Test
    fun oneOrangeAlert_hasTheInfo_andContacterStillMessages() {
        assertTrue(oneOrange.bubble is Bubble.Alerts)
        assertInfoOpensThePage_andTheBubbleDoesNot(oneOrange, "Thomas")
        composeRule.onNodeWithText(text(R.string.feed_bubble_message_action)).performClick()
        assertEquals("+33612345678", messaged)
        assertEquals("Contacter does not open the page", 1, opened)
    }

    @Test
    fun oneRedAlert_hasTheInfo() {
        assertTrue(oneRed.bubble is Bubble.Alerts)
        show(oneRed)
        composeRule.onAllNodesWithContentDescription(infoLabel).assertCountEquals(1)
        touchTheInfo()
        assertEquals(1, opened)
    }

    @Test
    fun carousel_hasOneInfo_onEveryPage_atTheSamePlace_andContacterDoesNotOpenIt() {
        assertTrue(redAndOrange.bubble is Bubble.Alerts)
        show(redAndOrange)
        // Red page first (Amandine): ONE "i".
        composeRule.onAllNodesWithContentDescription(infoLabel).assertCountEquals(1)
        val before = boundsOf(infoLabel)
        touchTheInfo()
        assertEquals(1, opened)
        // "Contacter" sends the message and does not open the page; a touch on the words opens nothing.
        composeRule.onNodeWithText(text(R.string.feed_bubble_message_action)).performClick()
        assertEquals("+33612345678", messaged)
        composeRule.onNodeWithText("Pas de nouvelles de Amandine depuis 12 h 00".t()).performClick()
        assertEquals(1, opened)
        // The orange page (Thomas), after a swipe: still ONE "i", at the same place (a few dp of the slow drift of the bubble at most), and it opens the page.
        composeRule.onNodeWithText("Pas de nouvelles de Amandine depuis 12 h 00".t()).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertTrue(
            "the orange page is shown",
            composeRule.onAllNodesWithText("Thomas", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onAllNodesWithContentDescription(infoLabel).assertCountEquals(1)
        val after = boundsOf(infoLabel)
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.top, after.top, 6f * 3f)
        touchTheInfo()
        assertEquals(2, opened)
    }

    // --- the bubble itself: no action; nothing but the "i" is added ----------------------------------------------------------------

    @Test
    fun theBubble_hasNoActionForTheScreenReader_andNothingTextualIsAdded() {
        show(allGood)
        val node = composeRule.onNodeWithText("Tout va bien")
        node.assertHasNoClickAction()
        assertNull(node.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        composeRule.onAllNodesWithText(infoLabel).assertCountEquals(0)
        composeRule.onAllNodesWithText("couleur", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("En savoir plus", substring = true, ignoreCase = true).assertCountEquals(0)
        // The letter of the "i" is decorative: not a node of text for the screen reader (the button is named by its description).
        composeRule.onAllNodesWithText("i").assertCountEquals(0)
    }

    @Test
    fun theInfo_isAButtonNamedLikeThePage_withATouchAreaOf48dp() {
        show(allGood)
        val info = composeRule.onNodeWithContentDescription(infoLabel)
        info.assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val config = info.fetchSemanticsNode().config
        assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role))
        assertEquals(listOf(infoLabel), config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun theInfo_isAtTheBottomRight_insideTheBubbleAndClearOfItsWords() {
        show(allGood)
        val info = boundsOf(infoLabel)
        val words = boundsOfText("Tout va bien")
        assertTrue("at the right of the middle of the bubble", info.center.x > words.center.x)
        assertTrue("under the words", info.top >= words.bottom - 1f)
    }

    @Test
    fun theButtonsOfTheBubble_stayOnTheirOwn_notMergedWithTheInfo() {
        show(oneOrange)
        val button = composeRule.onNodeWithText(text(R.string.feed_bubble_message_action)).fetchSemanticsNode().config
        val click = button.getOrNull(SemanticsActions.OnClick)
        assertNotNull(click)
        assertFalse(click?.label == infoLabel)
    }

    // --- the largest font: the "i" hides nothing and is hidden by nothing --------------------------------------------------------------

    private fun assertClear(
        a: Rect,
        b: Rect,
        what: String,
    ) = assertFalse("$what overlaps: $a and $b", a.overlaps(b))

    @Test
    fun largestFont_theInfoDoesNotOverlapTheWords_allGood() {
        show(allGood, fontScale = 2f)
        val info = boundsOf(infoLabel)
        assertClear(info, boundsOfText("Tout va bien"), "the headline")
        composeRule.onNodeWithContentDescription(infoLabel).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun largestFont_theInfoDoesNotOverlapResumeSharing() {
        show(stopped, fontScale = 2f)
        val info = boundsOf(infoLabel)
        assertClear(info, boundsOfText(text(R.string.feed_bubble_resume_action)), "Reprendre le partage")
        assertClear(info, boundsOfText(text(R.string.feed_bubble_sharing_stopped_title)), "the title")
        // The button is still reachable.
        composeRule.onNodeWithText(text(R.string.feed_bubble_resume_action)).performClick()
        assertEquals(1, resumed)
        assertEquals(0, opened)
    }

    @Test
    fun largestFont_theInfoDoesNotOverlapContacter_orTheDots_inTheCarousel() {
        show(redAndOrange, fontScale = 2f)
        val info = boundsOf(infoLabel)
        assertClear(info, boundsOfText(text(R.string.feed_bubble_message_action)), "Contacter")
        assertClear(info, boundsOfText("Pas de nouvelles de Amandine depuis 12 h 00".t()), "the headline of the alert")
        // The dots sit below the pages: the "i" is inside the pager, above them.
        composeRule.onNodeWithText(text(R.string.feed_bubble_message_action)).performClick()
        assertEquals("+33612345678", messaged)
        assertEquals(0, opened)
        touchTheInfo()
        assertEquals(1, opened)
    }

    // --- E7: the page is pushed (no tab bar), one copy only, and the way back finds the Feed ---------------------------------------

    @Test
    fun touchingTheInfo_pushesThePageOnce_andTheWayBackFindsTheFeed() {
        val feed = allGood
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
        // A touch on the bubble: nothing happens.
        composeRule.onNodeWithText("Tout va bien").performClick()
        composeRule.onAllNodesWithText(text(R.string.feed_colors_title)).assertCountEquals(0)
        touchTheInfo()
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
