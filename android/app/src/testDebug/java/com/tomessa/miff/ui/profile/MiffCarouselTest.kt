package com.tomessa.miff.ui.profile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.profile.CardFaces
import com.tomessa.miff.domain.profile.MemberFace
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The carousel "Mes Miffs" of the Profile tab on the JVM (Robolectric): a card per Miff with its name, its number of members and their faces, the
 * LAST card "Créer une Miff" and what it does for a free account (the offer), for Miff+ and for an unknown plan, the lazy reading (only the page the
 * person settles on is "shown"), the screen reader (position, previous / next, faces and dots are decoration) and the largest font.
 * Writes screenshots to build/screenshots for the pull request (compare with design/exports/profil).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class MiffCarouselTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private val famille = MiffSummary("m1", "Famille Martin")
    private val copains = MiffSummary("m2", "Les copains")

    private fun directory(
        vararg miffs: MiffSummary,
        premium: Boolean = true,
        premiumKnown: Boolean = true,
        activeId: String? = miffs.firstOrNull()?.id,
    ) = DirectoryState.Ready(miffs.toList(), activeId, premium, fromCache = false, premiumKnown = premiumKnown)

    private fun faces(
        count: Int,
        shown: Int = minOf(count, 5),
    ) = CardFaces.Ready(
        count,
        (1..shown).map {
            MemberFace("u$it", listOf("EM", "RP", "LB", "TM", "AD")[(it - 1) % 5], null)
        },
    )

    private class Calls {
        val shown = mutableListOf<String>()
        val known = mutableListOf<Set<String>>()
        val opened = mutableListOf<String>()
        var created = 0
        var offered = 0
    }

    private fun show(
        state: DirectoryState,
        calls: Calls = Calls(),
        faces: Map<String, CardFaces> = emptyMap(),
        fontScale: Float = 1f,
    ): Calls {
        composeRule.setContent {
            MiffTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        MiffCarouselSection(
                            miffs = state,
                            host =
                                MiffCarouselHost(
                                    faces = faces,
                                    onCardShown = { calls.shown += it },
                                    onMiffsKnown = { calls.known += it },
                                    onCreate = { calls.created++ },
                                    onOffer = { calls.offered++ },
                                ),
                            onOpenMiff = { calls.opened += it },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return calls
    }

    private fun swipeToNext() {
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun create() = text(R.string.profile_tab_miff_create)

    // --- What the carousel holds -------------------------------------------------------------------------------------------------

    @Test
    fun carousel_showsTheTitle_aCardPerMiff_withNameAndCount_andTheCreateCardLast() {
        show(directory(famille, copains), faces = mapOf("m1" to faces(4)))
        shoot("profil-0-carrousel-miff")
        composeRule.onNodeWithText(text(R.string.profile_tab_miffs_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Famille Martin").assertIsDisplayed()
        composeRule.onNodeWithText("4 membres").assertIsDisplayed()
        swipeToNext()
        composeRule.onNodeWithText("Les copains").assertIsDisplayed()
        swipeToNext()
        shoot("profil-1-carrousel-creer")
        composeRule.onNodeWithText(create()).assertIsDisplayed()
    }

    @Test
    fun carousel_withNoMiff_theCreateCardIsAlone_andOpensTheCreation() {
        val calls = show(directory())
        composeRule.onNodeWithText(create()).assertIsDisplayed().performClick()
        assertEquals(1, calls.created)
        assertEquals(0, calls.offered)
    }

    @Test
    fun carousel_whileTheDirectoryIsNotKnown_drawsNothing() {
        show(DirectoryState.Loading)
        composeRule.onAllNodesWithText(text(R.string.profile_tab_miffs_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(create()).assertCountEquals(0)
    }

    @Test
    fun card_aSingleMember_isSingular_andANumberOfMembersNobodyKnowsIsNotInvented() {
        show(directory(famille, copains), faces = mapOf("m1" to faces(1), "m2" to CardFaces.Unavailable))
        composeRule.onNodeWithText("1 membre").assertIsDisplayed()
        swipeToNext()
        // The card says its name and nothing about a count (the previous card, still composed, has its own).
        composeRule.onNodeWithText("Les copains").assertIsDisplayed().assertTextEquals("Les copains")
    }

    @Test
    fun card_whileItsMembersAreRead_showsItsName_andNoCount() {
        show(directory(famille), faces = mapOf("m1" to CardFaces.Loading))
        composeRule.onNodeWithText("Famille Martin").assertIsDisplayed()
        composeRule.onAllNodesWithText("membre", substring = true).assertCountEquals(0)
    }

    @Test
    fun card_moreThanFiveMembers_showsFourFacesAndPlusN() {
        show(directory(famille), faces = mapOf("m1" to faces(7, shown = 5)))
        composeRule.onNodeWithText("7 membres").assertIsDisplayed()
        // The faces are decoration: they live in the unmerged tree only.
        composeRule.onNodeWithText("+3", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("TM", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("AD", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun card_fiveMembersOrFewer_everyoneIsDrawn_noPlusN() {
        show(directory(famille), faces = mapOf("m1" to faces(5)))
        composeRule.onNodeWithText("AD", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("+", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    // --- What the cards do --------------------------------------------------------------------------------------------------------

    @Test
    fun touchingACard_opensTheScreenOfThatMiff() {
        val calls = show(directory(famille, copains), faces = mapOf("m1" to faces(4)))
        composeRule.onNodeWithText("Famille Martin").performClick()
        assertEquals(listOf("m1"), calls.opened)
    }

    @Test
    fun createCard_freeAccountWithAMiff_opensTheOffer_notTheCreation() {
        val calls = show(directory(famille, premium = false))
        swipeToNext()
        composeRule.onNodeWithText(create()).performClick()
        assertEquals(1, calls.offered)
        assertEquals(0, calls.created)
    }

    @Test
    fun createCard_miffPlus_opensTheCreation() {
        val calls = show(directory(famille, copains, premium = true))
        swipeToNext()
        swipeToNext()
        composeRule.onNodeWithText(create()).performClick()
        assertEquals(1, calls.created)
        assertEquals(0, calls.offered)
    }

    @Test
    fun createCard_anUnknownPlan_isNeverTakenForFree_theCreationDecides() {
        val calls = show(directory(famille, premium = false, premiumKnown = false))
        swipeToNext()
        composeRule.onNodeWithText(create()).performClick()
        assertEquals(1, calls.created)
        assertEquals(0, calls.offered)
    }

    // --- The reading cost ----------------------------------------------------------------------------------------------------------

    @Test
    fun cardShown_isOnlyThePageThePersonSettlesOn_aComposedNeighbourReadsNothing() {
        val calls = show(directory(famille, copains, activeId = "m1"))
        assertEquals("opens on the Miff of the Feed", listOf("m1"), calls.shown)
        // The neighbour is composed (a screen reader can reach it) but is not "shown": no reading is asked for it.
        composeRule.onNodeWithText("Les copains").assertExists()
        assertTrue("m2" !in calls.shown)
        swipeToNext()
        assertEquals(listOf("m1", "m2"), calls.shown)
    }

    @Test
    fun carousel_opensOnTheMiffTheFeedShows() {
        val calls = show(directory(famille, copains, activeId = "m2"))
        composeRule.onNodeWithText("Les copains").assertIsDisplayed()
        assertEquals(listOf("m2"), calls.shown)
    }

    @Test
    fun theCreateCard_isNeverReportedAsAMiffToRead() {
        val calls = show(directory(famille, activeId = "m1"))
        swipeToNext()
        composeRule.onNodeWithText(create()).assertIsDisplayed()
        assertEquals(listOf("m1"), calls.shown)
    }

    @Test
    fun theHostIsToldWhichMiffsExist() {
        val calls = show(directory(famille, copains))
        assertEquals(setOf("m1", "m2"), calls.known.last())
    }

    // --- The screen reader ---------------------------------------------------------------------------------------------------------

    private fun hasState(expected: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    @Test
    fun cards_sayWhereTheyAre_andOfferPreviousAndNext_withoutASwipe() {
        show(directory(famille, copains), faces = mapOf("m1" to faces(4)))
        composeRule.onNodeWithText("Famille Martin").assert(hasState("Carte 1 sur 3"))
        composeRule
            .onNodeWithText("Famille Martin")
            .assert(
                SemanticsMatcher("has previous and next actions") { node ->
                    val labels = node.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label }
                    labels ==
                        listOf(
                            text(R.string.profile_tab_carousel_previous_a11y),
                            text(R.string.profile_tab_carousel_next_a11y),
                        )
                },
            )
    }

    @Test
    fun cards_areButtons_thatSayWhichMiffTheyOpen() {
        show(directory(famille), faces = mapOf("m1" to faces(4)))
        composeRule
            .onNodeWithText("Famille Martin")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun faces_areNotReadByAScreenReader_theCountIs() {
        show(directory(famille), faces = mapOf("m1" to faces(7, shown = 5)))
        // In the merged tree (what a screen reader walks) there is the name and the count, never the initials or "+3".
        composeRule.onAllNodesWithText("EM").assertCountEquals(0)
        composeRule.onAllNodesWithText("+3").assertCountEquals(0)
        composeRule.onNodeWithText("7 membres").assertExists()
    }

    @Test
    fun singleCard_hasNoPreviousNext_andNoDots() {
        show(directory())
        composeRule
            .onNodeWithText(create())
            .assert(
                SemanticsMatcher("no custom actions") { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty()
                },
            )
    }

    // --- The largest font ----------------------------------------------------------------------------------------------------------

    @Test
    fun largestFont_nameAndCountStayReadable_theCardGrows_theTouchTargetHolds() {
        show(directory(famille, copains), faces = mapOf("m1" to faces(7, shown = 5)), fontScale = 2f)
        shoot("profil-0-carrousel-grand-texte")
        composeRule.onNodeWithText("Famille Martin").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("7 membres").assertIsDisplayed()
        swipeToNext()
        swipeToNext()
        composeRule.onNodeWithText(create()).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aVeryLongName_wrapsOnTwoLines_andStaysInsideTheCard() {
        val long = MiffSummary("m3", "La famille de Marie-Charlotte et Jean-Baptiste de la Roche-Guyon Saint-Martin")
        show(directory(long), faces = mapOf("m3" to faces(2)))
        composeRule.onNodeWithText(long.name).assertIsDisplayed()
        composeRule.onNodeWithText("2 membres").assertIsDisplayed()
    }
}
