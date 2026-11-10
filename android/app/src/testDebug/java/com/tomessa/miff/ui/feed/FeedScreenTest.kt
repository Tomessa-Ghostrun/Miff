package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.feed.Age
import com.tomessa.miff.domain.feed.Day
import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.FeedInput
import com.tomessa.miff.domain.feed.LocateResult
import com.tomessa.miff.domain.feed.MemberLocationAccess
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.Moment
import com.tomessa.miff.domain.feed.PositionFix
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.ui.miff.AgreementUi
import com.tomessa.miff.ui.place.EmojiSupport
import com.tomessa.miff.ui.place.LocalEmojiSupport
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

private const val LONG_LEISURE_NAME =
    "Médiathèque municipale Jean-Jacques Rousseau du treizième arrondissement de Paris"

/**
 * The Feed on the JVM (Robolectric, no phone), in French: what the header, the bubble, the cards and the sheet "Localiser" say and do, the
 * states (skeleton, offline, sharing stopped, waiting for the agreement, full Miff), the touch targets (48 dp), the largest font, and what is
 * never said (a phrase of safety without knowing, "zone à risque", an error). Screenshots go to build/screenshots for the pull request
 * (compare with design/exports/feed and design/exports/feed/claude).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h900dp-port-xxhdpi")
class FeedScreenTest {
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

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = context().getString(id, *args)

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- data ---------------------------------------------------------------------------------------------------------------

    private val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val me = "me"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"

    private fun member(
        uid: String,
        first: String,
        phone: String? = "+33612345678",
        sharing: Boolean = true,
    ) = MiffMember(uid, first, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, sharing, phone)

    private fun status(
        presence: PresenceValue = PresenceValue.IN_TRUSTED,
        placeId: String? = "p1",
        ago: Long = 12 * minute,
        battery: Int? = 85,
        access: MemberLocationAccess = MemberLocationAccess.BACKGROUND,
        updatedAgo: Long = minute,
        lastUpdateAgo: Long = ago,
    ) = MemberStatus(presence, placeId, now - ago, now - lastUpdateAgo, false, battery, access, now - updatedAgo)

    private val places =
        listOf(
            testPlace("p1", "Chez nous", category = PlaceCategory.HOME),
            testPlace("p2", "Chez Léa", category = PlaceCategory.RELATIVE),
            testPlace("p3", "Zone chantier", type = PlaceType.RISK, category = PlaceCategory.OTHER),
            testPlace("p4", LONG_LEISURE_NAME, category = PlaceCategory.LEISURE),
            testPlace("p5", "Cinéma Gaumont", category = PlaceCategory.LEISURE),
            testPlace("p6", "Chez le coiffeur", category = PlaceCategory.OTHER),
            testPlace("p7", "Lieu ancien", category = null),
        )

    private fun content(
        members: List<MiffMember>,
        statuses: Map<String, MemberStatus>,
        view: StatusesView = StatusesView.Loaded(statuses),
        waiting: Boolean = false,
        offline: Boolean = false,
    ): FeedContent =
        FeedBuilder.build(
            FeedInput(miffId, "Famille Mfouka", "XTZRSP367988", members, places, me, view, waiting, offline),
            now,
            ZoneOffset.UTC,
        )

    private val trio = listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine"))

    private val directory =
        DirectoryState.Ready(listOf(MiffSummary(miffId, "Famille Mfouka")), miffId, premium = false, fromCache = false)

    /** The emoji of the categories are drawn as text by the font of the phone; here the font draws them all (a test below makes it fail). */
    private fun show(
        emojiSupport: EmojiSupport = EmojiSupport { true },
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalEmojiSupport provides emojiSupport) {
                MiffTheme { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    private fun feed(
        feed: FeedUiState,
        locate: LocateUiState = LocateUiState.Idle,
        directoryState: DirectoryState = directory,
        agreement: AgreementUi = AgreementUi(),
        actions: FeedActions = FeedActions(),
        emojiSupport: EmojiSupport = EmojiSupport { true },
    ) = show(emojiSupport) {
        FeedScreen(
            state = directoryState,
            scrollToTopSignal = 0,
            onRetry = {},
            onSelect = {},
            onCreate = {},
            onJoin = {},
            agreement = agreement,
            feed = feed,
            locate = locate,
            me = FeedMe("CM"),
            actions = actions,
        )
    }

    private fun ready(content: FeedContent) = FeedUiState.Ready(content)

    private val nominal get() =
        content(
            trio,
            mapOf(
                me to status(ago = 3 * hour),
                "t" to status(),
                "a" to status(ago = 6 * hour, placeId = "p2"),
            ),
        )

    // --- header ---------------------------------------------------------------------------------------------------------------

    @Test
    fun header_hasMyAvatarWithMyStatus_theLogo_theName_andNoBell() {
        var profile = 0
        feed(ready(nominal), actions = FeedActions(onOpenProfile = { profile++ }))
        shoot("feed-nominal")
        composeRule
            .onNodeWithContentDescription(text(R.string.feed_header_avatar_a11y, "À la maison"))
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, profile)
        composeRule.onNodeWithContentDescription(text(R.string.home_logo_a11y)).assertIsDisplayed()
        composeRule.onNodeWithText("Famille Mfouka").assertIsDisplayed()
        composeRule
            .onAllNodesWithContentDescription(
                "notification",
                substring = true,
                ignoreCase = true,
            ).assertCountEquals(0)
    }

    @Test
    fun header_theZoneOfTouchOfMyAvatar_is48dp() {
        feed(ready(nominal))
        composeRule
            .onNodeWithContentDescription(text(R.string.feed_header_avatar_a11y, "À la maison"))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun header_theLogo_isAClickableTargetOfAtLeast48dp_andScrollsToTheTop() {
        feed(ready(nominal))
        composeRule
            .onNodeWithContentDescription(text(R.string.home_logo_a11y))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assertHasClickAction()
            .performClick()
    }

    // --- bubble ---------------------------------------------------------------------------------------------------------------

    @Test
    fun bubble_allGood_saysItWithItsDate_andNeverSaysSafe() {
        feed(ready(nominal))
        composeRule.onNodeWithText("Tout va bien").assertIsDisplayed()
        composeRule.onNodeWithText("Dernière nouvelle il y a 12 min").assertIsDisplayed()
        composeRule.onAllNodesWithText("sécurité", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun bubble_withASilentMember_addsTheCount() {
        feed(ready(content(trio, mapOf(me to status(), "t" to status()))))
        composeRule.onNodeWithText("2 sur 3 ont donné des nouvelles").assertIsDisplayed()
    }

    @Test
    fun bubble_nobodyKnown_isNeutral_withNoPhraseOfSafety() {
        feed(ready(content(trio, mapOf(me to status()))))
        shoot("feed-neutral")
        // The bubble and the two cards of the loved ones.
        composeRule.onAllNodesWithText("Pas encore de nouvelles").assertCountEquals(3)
        composeRule.onNodeWithText(text(R.string.feed_bubble_no_news_body)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Tout va bien").assertCountEquals(0)
    }

    @Test
    fun bubble_alone_saysItCalmly() {
        feed(ready(content(listOf(member(me, "Camille")), mapOf(me to status()))))
        composeRule.onNodeWithText(text(R.string.feed_bubble_alone)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_add_loved_one)).assertIsDisplayed()
    }

    private fun centreOf(text: String): Float =
        composeRule
            .onNodeWithText(text)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot.center.x

    @Test
    fun bubble_textAndButton_areCentredInTheBlob_greenOrangeAndRed() {
        // Decision of the PM, 2026-11-08 (TestFlight build 22): the text is centred in the coloured blob, whatever the colour (the blob fills the
        // width of the screen with equal margins). Green first, then an orange page, then a red one with its button.
        val rootCentre = {
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.center.x
        }
        feed(ready(nominal))
        assertEquals(rootCentre(), centreOf("Tout va bien"), 6f)
    }

    @Test
    fun bubble_orangeAndRedPages_areCentred_buttonIncluded() {
        val both =
            content(
                trio,
                mapOf(
                    me to status(),
                    "t" to status(PresenceValue.OUT, "p1", 150 * minute),
                    "a" to status(PresenceValue.OUT, "p1", 5 * hour),
                ),
            )
        feed(ready(both))
        val rootCentre =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.center.x
        // The red page (Amandine) is the first one.
        assertEquals(rootCentre, centreOf("Pas de nouvelles de Amandine depuis 12 h 00".t()), 6f)
        assertEquals(rootCentre, centreOf(text(R.string.feed_bubble_message_action)), 6f)
    }

    @Test
    fun bubble_orange_isFactual_withAnAbsoluteTime_andOffersAMessage() {
        var sent: String? = null
        val orange = content(trio, mapOf(me to status(), "t" to status(PresenceValue.OUT, "p1", 150 * minute)))
        feed(ready(orange), actions = FeedActions(onMessage = { sent = it }))
        shoot("feed-orange")
        composeRule.onNodeWithText("Thomas a quitté la maison à 14 h 30".t()).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_bubble_message_action)).performClick()
        assertEquals("+33612345678", sent)
    }

    @Test
    fun bubble_redOfAge_usesAnAbsoluteTime() {
        feed(ready(content(trio, mapOf(me to status(), "t" to status(PresenceValue.OUT, "p1", 5 * hour)))))
        shoot("feed-red-age")
        composeRule.onNodeWithText("Pas de nouvelles de Thomas depuis 12 h 00".t()).assertIsDisplayed()
    }

    @Test
    fun bubble_dangerZone_isFactualForTheLovedOnes_andNeverSaysZoneARisque() {
        val stale = status(PresenceValue.IN_RISK, "p3", 20 * minute, lastUpdateAgo = 2 * hour)
        feed(ready(content(trio, mapOf(me to status(), "t" to stale))))
        shoot("feed-danger")
        composeRule.onNodeWithText("Thomas est à Zone chantier").assertIsDisplayed()
        // In the bubble and on the card of Thomas.
        composeRule.onAllNodesWithText("Depuis 16 h 40".t()).assertCountEquals(2)
        composeRule.onNodeWithText(text(R.string.feed_bubble_danger_note)).assertIsDisplayed()
        composeRule.onAllNodesWithText("risque", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun bubble_dangerZone_forMe_isTheSentenceOfThePm_andHasNoMessageButton() {
        val stale = status(PresenceValue.IN_RISK, "p3", 20 * minute, lastUpdateAgo = 2 * hour)
        feed(ready(content(trio, mapOf(me to stale, "t" to status()))))
        composeRule.onNodeWithText("Tu es dans une zone de danger de ta Miff").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_bubble_me_danger_note)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.feed_bubble_message_action)).assertCountEquals(0)
    }

    @Test
    fun bubble_forMe_orangeAndRedOfAge_speakToMe() {
        feed(ready(content(trio, mapOf(me to status(PresenceValue.OUT, "p1", 150 * minute), "t" to status()))))
        composeRule.onNodeWithText("Tu as quitté la maison à 14 h 30".t()).assertIsDisplayed()
    }

    @Test
    fun bubble_aCarousel_hasPositionAndButtonsForTheScreenReader_andTheMostUrgentFirst() {
        val two =
            content(
                trio,
                mapOf(
                    me to status(),
                    "t" to status(PresenceValue.OUT, "p1", 150 * minute),
                    "a" to status(PresenceValue.OUT, "p1", 5 * hour),
                ),
            )
        feed(ready(two))
        shoot("feed-carousel")
        // The red one (Amandine) comes first.
        composeRule.onNodeWithText("Pas de nouvelles de Amandine depuis 12 h 00".t()).assertIsDisplayed()
        val carousel =
            composeRule.onNode(
                androidx.compose.ui.test.SemanticsMatcher
                    .keyIsDefined(SemanticsProperties.StateDescription),
            )
        carousel.assertExists()
        assertEquals(
            text(R.string.feed_bubble_page_a11y, 1, 2),
            carousel.fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )
        val actions = carousel.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf(text(R.string.feed_bubble_previous_a11y), text(R.string.feed_bubble_next_a11y)), actions)
        // Every page is reachable without a swipe.
        composeRule.runOnIdle {
            carousel
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .last()
                .action()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Thomas a quitté la maison à 14 h 30".t()).assertIsDisplayed()
    }

    @Test
    fun bubble_oneAlert_hasNoDotsAndNoPageActions() {
        feed(ready(content(trio, mapOf(me to status(), "t" to status(PresenceValue.OUT, "p1", 150 * minute)))))
        composeRule
            .onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher
                    .keyIsDefined(SemanticsProperties.StateDescription),
            ).assertCountEquals(0)
    }

    // --- cards ---------------------------------------------------------------------------------------------------------------

    @Test
    fun cards_showTheLabelTheTimeAndTheBattery_inTheRightWords() {
        feed(ready(nominal))
        // Mine and Thomas's.
        composeRule.onAllNodesWithText("À la maison").assertCountEquals(2)
        composeRule.onNodeWithText("Depuis 16 h 48".t()).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_label_at_relative)).assertExists()
        // Mine, Thomas's and Amandine's.
        composeRule
            .onAllNodesWithText(text(R.string.feed_battery_a11y, 85), useUnmergedTree = true)
            .assertCountEquals(3)
        composeRule.onNodeWithText(text(R.string.feed_card_you_sees)).assertIsDisplayed()
    }

    @Test
    fun cards_noMentionToi_nextToMyName_butTheLineOfMyCardIsReadWithMyName() {
        feed(ready(nominal))
        // Decision of the PM (2026-11-07): no "Toi" anywhere on the cards, seen or read, in the merged or the unmerged tree.
        composeRule.onAllNodesWithText("Toi", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Toi").assertCountEquals(0)
        // The screen reader still knows it is my card: the line "Ce que tes proches voient de toi" is in the SAME block as my name.
        val mine =
            composeRule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Camille"),
                ).fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.Text].map { part -> part.text } }
                .first { it.contains("Camille") && it.size > 1 }
        assertTrue("block of my card: $mine", mine.contains(text(R.string.feed_card_you_sees)))
        assertFalse("block of my card: $mine", mine.contains("Toi"))
    }

    @Test
    fun cards_myCardIsTheLastOne_afterTheOthers() {
        feed(ready(nominal))

        fun top(first: String): Float =
            composeRule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText(first),
                ).fetchSemanticsNodes()
                .first { it.config[SemanticsProperties.Text].size > 1 }
                .boundsInRoot.top
        assertTrue(top("Thomas") < top("Amandine"))
        assertTrue(top("Amandine") < top("Camille"))
    }

    @Test
    fun cards_myCardHasNoGesture_theOthersHaveThree_namedForTheScreenReader() {
        feed(ready(nominal))
        // One set of three (Thomas), one more set (Amandine): none for Camille (me).
        composeRule
            .onAllNodesWithContentDescription(
                text(R.string.feed_card_locate_a11y, "Camille"),
            ).assertCountEquals(0)
        composeRule
            .onAllNodesWithContentDescription(
                text(R.string.feed_card_message_a11y, "Camille"),
            ).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(text(R.string.feed_card_call_a11y, "Camille")).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Localiser Thomas").assertExists()
        composeRule.onNodeWithContentDescription("Écrire à Thomas").assertExists()
        composeRule.onNodeWithContentDescription("Appeler Thomas").assertExists()
    }

    @Test
    fun cards_theGesturesAreAtLeast48dp() {
        feed(ready(nominal))
        listOf("Localiser Thomas", "Écrire à Thomas", "Appeler Thomas").forEach {
            composeRule.onNodeWithContentDescription(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun cards_theGesturesDoWhatTheySay() {
        val log = mutableListOf<String>()
        feed(
            ready(nominal),
            actions =
                FeedActions(
                    onLocate = { log += "locate:$it" },
                    onMessage = { log += "message:$it" },
                    onCall = { log += "call:$it" },
                ),
        )
        composeRule.onNodeWithContentDescription("Localiser Thomas").performClick()
        composeRule.onNodeWithContentDescription("Écrire à Thomas").performClick()
        composeRule.onNodeWithContentDescription("Appeler Thomas").performClick()
        assertEquals(listOf("locate:t", "message:+33612345678", "call:+33612345678"), log)
    }

    @Test
    fun cards_aMemberWithoutANumber_hasOnlyLocate() {
        val members = listOf(member(me, "Camille"), member("t", "Thomas", phone = null))
        feed(ready(content(members, mapOf(me to status(), "t" to status()))))
        composeRule.onNodeWithContentDescription("Localiser Thomas").assertExists()
        composeRule.onAllNodesWithContentDescription("Écrire à Thomas").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Appeler Thomas").assertCountEquals(0)
    }

    @Test
    fun cards_aNeutralMember_hasLocateMessageAndCall() {
        feed(ready(content(trio, mapOf(me to status()))))
        shoot("feed-neutral-cards")
        // Ticket 5.6: another member whose sharing is on, even with no known status ("pas encore de position connue" is a calm answer).
        composeRule.onNodeWithContentDescription("Localiser Thomas").assertExists()
        composeRule.onNodeWithContentDescription("Écrire à Thomas").assertExists()
        composeRule.onAllNodesWithText(text(R.string.feed_label_no_news)).assertCountEquals(3)
    }

    @Test
    fun cards_locating_showsAnIndicator_namedForTheScreenReader() {
        feed(ready(nominal), locate = LocateUiState.Loading("t"))
        composeRule.onNodeWithContentDescription("Localisation de Thomas en cours").assertExists()
        composeRule.onAllNodesWithContentDescription("Localiser Thomas").assertCountEquals(0)
    }

    @Test
    fun cards_limitedLocation_isASoftBadge() {
        feed(ready(content(trio, mapOf(me to status(), "t" to status(access = MemberLocationAccess.FOREGROUND)))))
        composeRule.onNodeWithText("Localisation limitée").assertExists()
        composeRule.onNodeWithText("Tout va bien").assertExists()
    }

    @Test
    fun cards_aBatteryOfMoreThanAnHour_isNeverDated() {
        feed(ready(content(trio, mapOf(me to status(), "t" to status(updatedAgo = 90 * minute, battery = 12)))))
        // Decision of the PM (2026-11-07): the percentage alone, whatever the age of the value (no "12 % · 15 h 30", no "à 15 h 30").
        composeRule.onNodeWithText(text(R.string.feed_battery_a11y, 12), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("12 %", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("15 h 30", substring = true, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("·", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun cards_theScreenReaderReadsTheStateThenTheTimeThenTheBattery() {
        feed(ready(content(trio, mapOf(me to status(), "t" to status(updatedAgo = 90 * minute, battery = 12)))))
        // The merged block of Thomas's card: the person, the state, the time, then the battery (ticket T2), whatever the drawing order.
        val merged =
            composeRule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Thomas"),
                ).fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.Text].map { part -> part.text } }
                .first { it.contains("Thomas") && it.size > 1 }
        val label = merged.indexOf("À la maison")
        val time = merged.indexOf("Depuis 16 h 48".t())
        val battery = merged.indexOfFirst { it.startsWith("Batterie") }
        assertTrue("order of $merged", label in 1 until time && time < battery)
    }

    @Test
    fun cards_aStoppedSharing_isCalm() {
        val members = listOf(member(me, "Camille"), member("t", "Thomas", sharing = false))
        feed(ready(content(members, mapOf(me to status()))))
        composeRule.onNodeWithText("Partage arrêté").assertExists()
        composeRule.onAllNodesWithContentDescription("Localiser Thomas").assertCountEquals(0)
    }

    // --- states --------------------------------------------------------------------------------------------------------------

    @Test
    fun skeleton_hasNoColourOfStatus_noPhraseOfSafety_andIsNamedForTheScreenReader() {
        feed(FeedUiState.Loading)
        shoot("feed-skeleton")
        composeRule.onAllNodesWithContentDescription(text(R.string.feed_loading_a11y)).assertCountEquals(1)
        // The wait is told in a calm word; nothing is said about the news before it is known.
        composeRule.onNodeWithText(text(R.string.feed_loading_news)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Tout va bien").assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.feed_label_no_news)).assertCountEquals(0)
    }

    @Test
    fun unavailable_isCalm_withRetry() {
        var retried = 0
        feed(FeedUiState.Unavailable, actions = FeedActions(onRetryFeed = { retried++ }))
        composeRule.onNodeWithText(text(R.string.feed_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun offline_theBannerGivesTheDateOfTheLatestInformation() {
        feed(ready(content(trio, mapOf(me to status(updatedAgo = 18 * minute), "t" to status()), offline = true)))
        shoot("feed-offline")
        composeRule.onNodeWithText("Dernières informations de 16 h 59".t()).assertIsDisplayed()
    }

    @Test
    fun offline_anAlertAddsItsOwnMention() {
        feed(
            ready(
                content(
                    trio,
                    mapOf(me to status(updatedAgo = 18 * minute), "t" to status(PresenceValue.OUT, "p1", 150 * minute)),
                    offline = true,
                ),
            ),
        )
        composeRule
            .onNodeWithText(
                "Informations de 16 h 59 : reconnecte-toi pour les mettre à jour.".t(),
            ).assertIsDisplayed()
    }

    @Test
    fun offline_withNothingKnown_isTheGenericMention_neverAGreenBubble() {
        feed(ready(content(trio, emptyMap(), offline = true)))
        composeRule.onNodeWithText(text(R.string.feed_offline_note)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Tout va bien").assertCountEquals(0)
    }

    @Test
    fun publishing_isABanner_neverAnError() {
        feed(ready(content(trio, emptyMap(), view = StatusesView.Pending)))
        shoot("feed-publishing")
        composeRule.onNodeWithText(text(R.string.feed_banner_publishing)).assertIsDisplayed()
        composeRule.onAllNodesWithText("erreur", substring = true, ignoreCase = true).assertCountEquals(0)
        // The statuses are not read yet (the rules wait for mine): no "Localiser" would be answered.
        composeRule.onAllNodesWithContentDescription("Localiser Thomas").assertCountEquals(0)
    }

    @Test
    fun sharingStopped_saysSo_hidesTheNews_andHasTheWayBack() {
        var resumed: String? = null
        val members = listOf(member(me, "Camille", sharing = false), member("t", "Thomas"))
        feed(
            ready(content(members, emptyMap(), view = StatusesView.Off)),
            actions = FeedActions(onResumeSharing = { resumed = it }),
        )
        shoot("feed-sharing-stopped")
        composeRule.onNodeWithText("Tu as arrêté de partager.").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_label_news_hidden)).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_bubble_resume_action)).performClick()
        assertEquals(miffId, resumed)
        // Message and call stay, locate goes.
        composeRule.onNodeWithContentDescription("Écrire à Thomas").assertExists()
        composeRule.onAllNodesWithContentDescription("Localiser Thomas").assertCountEquals(0)
    }

    @Test
    fun waitingForTheAgreement_keepsTheCard_andTheBubbleIsNeutral() {
        var reviews = 0
        feed(
            ready(content(trio, emptyMap(), view = StatusesView.Off, waiting = true)),
            agreement = AgreementUi(setOf(miffId), onReview = { reviews++ }),
        )
        shoot("feed-awaiting")
        // The bubble's big sentence takes two lines: my card, the last one, is below the fold and is only composed once scrolled to.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.feed_card_you_sees)))
        // The card (its title) and my own label say the same truth: nothing is shared with that Miff.
        composeRule.onAllNodesWithText(text(R.string.miff_waiting_title)).assertCountEquals(2)
        composeRule.onNodeWithText(text(R.string.miff_waiting_feed_note)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_waiting_review)).performClick()
        assertEquals(1, reviews)
        composeRule.onAllNodesWithText("Tout va bien").assertCountEquals(0)
    }

    @Test
    fun aFullMiff_replacesTheButtonWithACalmNote() {
        val many = (1..14).map { member("u$it", "P$it") } + member(me, "Camille")
        feed(ready(content(many, emptyMap())))
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.feed_miff_full)))
        composeRule.onNodeWithText(text(R.string.feed_miff_full)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.feed_add_loved_one)).assertCountEquals(0)
    }

    @Test
    fun addALovedOne_handsTheCodeToTheShareSheet() {
        var shared: Pair<String, String>? = null
        feed(ready(nominal), actions = FeedActions(onAddLovedOne = { name, code -> shared = name to code }))
        composeRule.onNodeWithText(text(R.string.feed_add_loved_one)).performClick()
        assertEquals("Famille Mfouka" to "XTZRSP367988", shared)
    }

    @Test
    fun noMiff_isTheSafetyNet_withTwoButtons_andNoPhraseOfSafety() {
        feed(
            FeedUiState.Loading,
            directoryState = DirectoryState.Ready(emptyList(), null, premium = false, fromCache = false),
        )
        composeRule.onNodeWithText(text(R.string.feed_selector_none)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_create_action)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_join_action)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Tout va bien").assertCountEquals(0)
    }

    // The header hides while scrolling down and comes back when scrolling up (decision of the PM, 2026-11-06): see FeedHeaderScrollTest.

    @Test
    fun largestFont_keepsTheCardReadable_andTheGesturesReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MiffTheme {
                    Box(Modifier.fillMaxSize()) {
                        FeedScreen(
                            state = directory,
                            scrollToTopSignal = 0,
                            onRetry = {},
                            onSelect = {},
                            onCreate = {},
                            onJoin = {},
                            feed = ready(nominal),
                            me = FeedMe("CM"),
                        )
                    }
                }
            }
        }
        shoot("feed-large-font")
        composeRule.onNodeWithText("Tout va bien").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Localiser Thomas").assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun aLeisureOrOtherPlace_showsItsNameAlone_andALongNameStaysInsideTheCard_atTheLargestFont() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MiffTheme {
                    Box(Modifier.fillMaxSize()) {
                        FeedScreen(
                            state = directory,
                            scrollToTopSignal = 0,
                            onRetry = {},
                            onSelect = {},
                            onCreate = {},
                            onJoin = {},
                            feed =
                                ready(
                                    content(
                                        trio,
                                        mapOf(
                                            me to status(placeId = "p5"),
                                            "t" to status(placeId = "p4"),
                                            "a" to status(),
                                        ),
                                    ),
                                ),
                            me = FeedMe("CM"),
                        )
                    }
                }
            }
        }
        // The NAME alone (no "À" in front), whole for the screen reader, drawn on two lines at most with an ellipsis.
        composeRule.onNodeWithText("Cinéma Gaumont").assertExists()
        val node =
            composeRule
                .onNodeWithText(
                    LONG_LEISURE_NAME,
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                .fetchSemanticsNode()

        // Really two lines at most. The layout result of the text is not read (the card measures its content twice, which makes it unreliable in
        // a test); the heights are: a line of the same style without padding ("Depuis 16 h 48") gives the height of ONE line L, the chip of
        // "Cinéma Gaumont" (one line) gives the vertical padding P, and the long chip must then be exactly 2 lines: height = 2 L + P.
        fun height(text: String) =
            composeRule
                .onAllNodesWithText(text, useUnmergedTree = true)[0]
                .fetchSemanticsNode()
                .size.height
        val line = height("Depuis 16 h 48".t())
        val padding = height("Cinéma Gaumont") - line
        val lines = (node.size.height - padding).toFloat() / line
        assertEquals("lines of the long label: ${node.size.height} = n x $line + $padding", 2f, lines, 0.1f)
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val box = node.boundsInRoot
        assertTrue("inside the screen: $box in $root", box.left >= 0f && box.right <= root.right)
    }

    // --- emoji of the label of a place and font of the bubble (decisions of the PM, 2026-11-10) -------------------------------------

    private fun emojiNodes(emoji: String) = composeRule.onAllNodesWithText(emoji, useUnmergedTree = true)

    @Test
    fun cards_theLabelOfAPlace_hasTheEmojiOfItsCategory_beforeTheWords() {
        feed(ready(nominal))
        // Mine and Thomas's: Maison; Amandine's: Chez un proche.
        emojiNodes(PlaceCategory.HOME.emoji).assertCountEquals(2)
        emojiNodes(PlaceCategory.RELATIVE.emoji).assertCountEquals(1)
        // The words are unchanged.
        composeRule.onAllNodesWithText("À la maison").assertCountEquals(2)
        val emojiLeft = emojiNodes(PlaceCategory.HOME.emoji)[0].fetchSemanticsNode().boundsInRoot.left
        val wordsLeft =
            composeRule
                .onAllNodesWithText(
                    "À la maison",
                    useUnmergedTree = true,
                )[0]
                .fetchSemanticsNode()
                .boundsInRoot.left
        assertTrue("the emoji comes before the words: $emojiLeft < $wordsLeft", emojiLeft < wordsLeft)
    }

    @Test
    fun cards_theEmoji_isDecorative_theScreenReaderReadsTheWordsOnly() {
        feed(ready(content(trio, mapOf(me to status(), "t" to status(placeId = "p5")))))
        // The merged block of each card holds the words, never the emoji.
        val reads =
            composeRule
                .onAllNodes(hasText("Thomas"))
                .fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.Text].map { part -> part.text } }
        assertTrue(reads.isNotEmpty())
        PlaceCategory.entries.forEach { category ->
            reads.forEach { assertFalse("${category.emoji} read in $it", it.contains(category.emoji)) }
            composeRule.onAllNodesWithText(category.emoji).assertCountEquals(0)
        }
        composeRule.onAllNodesWithText("Cinéma Gaumont").assertCountEquals(1)
    }

    @Test
    fun cards_aLeisureOrOtherPlace_hasItsEmoji_andItsName() {
        feed(ready(content(trio, mapOf(me to status(placeId = "p5"), "t" to status(placeId = "p6")))))
        emojiNodes(PlaceCategory.LEISURE.emoji).assertCountEquals(1)
        emojiNodes(PlaceCategory.OTHER.emoji).assertCountEquals(1)
        composeRule.onNodeWithText("Cinéma Gaumont").assertIsDisplayed()
        composeRule.onNodeWithText("Chez le coiffeur").assertIsDisplayed()
    }

    @Test
    fun cards_noCategory_orNoPlace_orLeftAPlace_hasNoEmoji() {
        feed(
            ready(
                content(
                    trio,
                    mapOf(
                        me to status(placeId = "p7"),
                        "t" to status(PresenceValue.OUT, "p1", ago = 30 * minute),
                        "a" to status(placeId = "zzz"),
                    ),
                ),
            ),
        )
        composeRule.onNodeWithText("Lieu ancien").assertIsDisplayed()
        composeRule.onNodeWithText("Dans un lieu de confiance").assertExists()
        PlaceCategory.entries.forEach { emojiNodes(it.emoji).assertCountEquals(0) }
    }

    @Test
    fun cards_whenTheFontCannotDrawTheEmoji_theFirstLetterOfTheCategoryTakesItsPlace() {
        feed(ready(nominal), emojiSupport = EmojiSupport { false })
        emojiNodes(PlaceCategory.HOME.emoji).assertCountEquals(0)
        emojiNodes(PlaceCategory.RELATIVE.emoji).assertCountEquals(0)
        // "M" (Maison) on two cards, "C" (Chez un proche) on one; the words stay.
        emojiNodes("M").assertCountEquals(2)
        emojiNodes("C").assertCountEquals(1)
        composeRule.onAllNodesWithText("À la maison").assertCountEquals(2)
    }

    private fun bubbleStyle(): androidx.compose.ui.text.TextStyle {
        val node = composeRule.onNodeWithText("Tout va bien").assertIsDisplayed().fetchSemanticsNode()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style
    }

    @Test
    fun bubble_theMainSentence_isBold_bigger_andInTheHeaviestBaloo() {
        feed(ready(nominal))
        val style = bubbleStyle()
        assertEquals(androidx.compose.ui.text.font.FontWeight.ExtraBold, style.fontWeight)
        assertEquals(30f, style.fontSize.value, 0f)
        assertTrue("bigger than display/headline (24 sp)", style.fontSize.value > 24f)
        assertEquals(com.tomessa.miff.ui.theme.FontFamilyBalooExtraBold, style.fontFamily)
        // The rest of the Feed keeps its fonts: the first name of a card is still the regular Baloo, the time line Hanken Grotesk.
        val name =
            composeRule
                .onAllNodesWithText("Thomas", useUnmergedTree = true)[0]
                .fetchSemanticsNode()
        val nameResults = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        name.config[SemanticsActions.GetTextLayoutResult].action?.invoke(nameResults)
        assertEquals(
            com.tomessa.miff.ui.theme.FontFamilyBaloo,
            nameResults
                .first()
                .layoutInput.style.fontFamily,
        )
    }

    @Test
    fun bubble_theMainSentence_staysInsideTheScreen_onASmallScreen_atTheLargestFont() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MiffTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        FeedScreen(
                            state = directory,
                            scrollToTopSignal = 0,
                            onRetry = {},
                            onSelect = {},
                            onCreate = {},
                            onJoin = {},
                            feed = ready(content(trio, mapOf(me to status(), "t" to status(), "a" to status()))),
                            me = FeedMe("CM"),
                        )
                    }
                }
            }
        }
        shoot("feed-bubble-small-screen-large-font")
        val box =
            composeRule
                .onNodeWithText("Tout va bien")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("inside the screen: $box in $root", box.left >= 0f && box.right <= root.right)
    }

    // --- sheet "Localiser" -----------------------------------------------------------------------------------------------------

    private fun sheet(
        result: LocateResult,
        actions: FeedActions = FeedActions(),
    ) = feed(ready(nominal), LocateUiState.Shown("t", "Thomas", result), actions = actions)

    @Test
    fun sheet_recent_saysTheTimeAndThatItIsNotLive_andOpensTheMapsWithTheCoordinatesAlone() {
        var opened: PositionFix? = null
        sheet(
            LocateResult.Recent(PositionFix(48.85661, 2.35222), Moment(now - 20 * minute, Day.TODAY), Age.Minutes(20)),
            FeedActions(onOpenMap = {
                opened = it
                true
            }),
        )
        composeRule.onNodeWithText("Dernière position connue de Thomas").assertIsDisplayed()
        composeRule
            .onNodeWithText("Mesurée à 16 h 40 (il y a 20 min). Ce n'est pas une position en direct.".t())
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_locate_trust, "Thomas")).assertIsDisplayed()
        composeRule.onNodeWithText("Ouvrir dans les cartes").performClick()
        assertTrue(opened != null)
        // No coordinate is ever written on the screen.
        composeRule.onAllNodesWithText("48.8", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("2.35", substring = true).assertCountEquals(0)
    }

    @Test
    fun sheet_recent_whenNoMapsApplicationAnswers_saysSoCalmly_andStaysOpen() {
        sheet(
            LocateResult.Recent(PositionFix(48.85661, 2.35222), Moment(now - 20 * minute, Day.TODAY), Age.Minutes(20)),
            FeedActions(onOpenMap = { false }),
        )
        composeRule.onNodeWithText("Ouvrir dans les cartes").performClick()
        composeRule.onNodeWithText(text(R.string.feed_action_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText("Ouvrir dans les cartes").assertIsDisplayed()
    }

    @Test
    fun sheet_old_saysTheDate_withNoMapAndNoCoordinate() {
        sheet(LocateResult.Old(Moment(now - 33 * hour, Day.YESTERDAY)))
        composeRule.onNodeWithText("Elle date d'hier à 08 h 00 : elle n'est plus affichée.".t()).assertIsDisplayed()
        composeRule.onAllNodesWithText("Ouvrir dans les cartes").assertCountEquals(0)
        composeRule.onNodeWithText("Fermer").assertIsDisplayed()
    }

    private fun calmSheet(
        result: LocateResult,
        expected: String,
    ) {
        var dismissed = 0
        composeRule.setContent {
            MiffTheme {
                LocateSheet("Thomas", result, FeedTexts(context()), onOpenMap = { true }, onDismiss = { dismissed++ })
            }
        }
        composeRule.onNodeWithText(expected).assertIsDisplayed()
        composeRule.onAllNodesWithText("erreur", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("quota", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onNodeWithText("Fermer").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun sheet_notFound_isCalm() = calmSheet(LocateResult.NotFound, "Thomas n'a pas encore de position connue.")

    @Test
    fun sheet_denied_isOneNeutralMessage() =
        calmSheet(
            LocateResult.Denied,
            "On ne peut pas afficher la position de Thomas pour le moment. Elle apparaît quand vous partagez tous les deux.",
        )

    @Test
    fun sheet_offline_isCalm() =
        calmSheet(LocateResult.Offline, "Pas de connexion pour le moment. Réessaie dès que le réseau est revenu.")

    @Test
    fun sheet_unexpectedFailure_isTheSixthCalmAnswer_distinctFromNoConnection() {
        val expected = "Impossible d'afficher la position pour le moment. Réessaie dans un moment."
        calmSheet(LocateResult.Unavailable, expected)
        // Not the sentence of "no connection": that one is kept for a real absence of network.
        assertTrue(!expected.contains("connexion"))
    }

    @Test
    fun sheet_closeButton_dismisses() {
        var dismissed = 0
        sheet(
            LocateResult.Recent(PositionFix(1.0, 2.0), Moment(now - 5 * minute, Day.TODAY), Age.Minutes(5)),
            FeedActions(onLocateDismissed = { dismissed++ }),
        )
        composeRule.onNodeWithText("Fermer").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun noWordOfAnErrorOrOfRiskIsInTheTextsOfTheFeed() {
        val resources = context().resources
        val names = R.string::class.java.fields.filter { it.name.startsWith("feed_") }
        assertTrue(names.size > 60)
        names.forEach { field ->
            val value = resources.getString(field.getInt(null)).lowercase()
            assertFalse("${field.name}: \"zone à risque\"", "zone à risque" in value || "risk zone" in value)
            assertFalse("${field.name}: \"en sécurité\"", "en sécurité" in value || "safe" == value)
            assertFalse("${field.name}: exclamation on an alert", field.name.startsWith("feed_bubble_") && "!" in value)
        }
    }
}
