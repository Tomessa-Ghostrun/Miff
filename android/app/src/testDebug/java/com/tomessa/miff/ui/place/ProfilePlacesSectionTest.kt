package com.tomessa.miff.ui.place

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.place.PlaceNotices
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.place.MiffPlacesView
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.ui.profile.ProfilePlacesState
import com.tomessa.miff.ui.profile.ProfileTabScreen
import com.tomessa.miff.ui.profile.ProfileTabUiState
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The places of the Miff under the carousel of the Profile tab, for EVERY member (decision of the PM, 2026-11-11): what a member sees and does not
 * see, what the creator keeps, and that the card "Mode voyage" stays the last block of the tab.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class ProfilePlacesSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = context().getString(id, *args)

    private val famille = MiffSummary("miff-a", "Famille Martin", ownerUid = "u1")
    private val amis = MiffSummary("miff-b", "Les amis", ownerUid = "u2")

    private fun directory(premium: Boolean? = false) =
        DirectoryState.Ready(
            miffs = listOf(famille, amis),
            activeId = famille.id,
            premium = premium == true,
            fromCache = false,
            premiumKnown = premium != null,
        )

    private val three =
        listOf(
            testPlace("p1", "Maison de Papa", address = "26 rue du Volnay, 21000 Dijon"),
            testPlace("p2", "Chez Léa", address = "3 rue des Lilas"),
            testPlace("p3", "Chantier rue Haute", type = PlaceType.RISK, address = "2 rue Haute"),
        )

    private fun section(
        state: ProfilePlacesState,
        myUid: String?,
        directory: DirectoryState.Ready = directory(),
        notices: PlaceNotices = PlaceNotices(),
        onOpen: (String, String) -> Unit = { _, _ -> },
        onAdd: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) = composeRule.setContent {
        MiffTheme {
            Box(Modifier.fillMaxSize()) {
                ProfilePlacesSection(state, directory, myUid, notices, onOpen, onAdd, onRetry)
            }
        }
    }

    private fun ready(
        miff: MiffSummary,
        places: List<com.tomessa.miff.domain.place.Place>,
    ) = ProfilePlacesState(miff.id, MiffPlacesView.Ready(places))

    // --- A member (not the creator) ----------------------------------------------------------------------------------------

    @Test
    fun member_seesTheNamedTitle_theList_andTheHintWhoSetsThePlaces() {
        var opened: Pair<String, String>? = null
        section(ready(famille, three), myUid = "u2", onOpen = { miff, place -> opened = miff to place })
        composeRule.onNodeWithText("Lieux de confiance de Famille Martin").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_list_member_hint)).assertIsDisplayed()
        composeRule.onNodeWithText("Maison de Papa").performClick()
        assertEquals("miff-a" to "p1", opened)
    }

    private fun assertNoAddNoCounterNoOffer() {
        composeRule.onAllNodesWithText(text(R.string.place_list_add_action)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_premium_pill)).assertCountEquals(0)
        composeRule.onAllNodesWithText("formule gratuite", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("lieux sur 3", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Miff+", substring = true).assertCountEquals(0)
    }

    @Test
    fun member_neverSeesAdd_theCounter_orAnOfferMiffPlus_onTheFreePlan() {
        section(ready(famille, three), myUid = "u2", directory = directory(premium = false))
        assertNoAddNoCounterNoOffer()
    }

    @Test
    fun member_neverSeesAdd_theCounter_orAnOfferMiffPlus_onMiffPlus() {
        section(ready(famille, three), myUid = "u2", directory = directory(premium = true))
        assertNoAddNoCounterNoOffer()
    }

    @Test
    fun member_neverSeesAdd_theCounter_orAnOfferMiffPlus_whenThePlanIsUnknown() {
        section(ready(famille, three), myUid = "u2", directory = directory(premium = null))
        assertNoAddNoCounterNoOffer()
    }

    @Test
    fun member_withAFreeCreatorAtTheLimit_stillSeesNoCounterAndNoAdd() {
        section(ready(famille, three), myUid = "u2", directory = directory(premium = false))
        composeRule.onAllNodesWithText("3 lieux sur 3 avec la formule gratuite").assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_list_add_action)).assertCountEquals(0)
    }

    @Test
    fun member_doesNotSeeTheCreatorsSavedConfirmation() {
        val notices = PlaceNotices()
        notices.saved("Maison de Papa")
        section(ready(famille, three), myUid = "u2", notices = notices)
        composeRule
            .onAllNodesWithText(text(R.string.place_saved_notice, "Maison de Papa"))
            .assertCountEquals(0)
    }

    @Test
    fun member_emptyList_saysItCalmly_withNoAddButton() {
        section(ready(famille, emptyList()), myUid = "u2")
        composeRule.onNodeWithText("Aucun lieu de confiance pour l'instant.").assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.place_list_add_action)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.miff_places_empty)).assertCountEquals(0)
    }

    @Test
    fun member_aDangerZone_isSaidInPlainWords_onScreenAndToTalkBack() {
        section(ready(famille, three), myUid = "u2")
        composeRule.onNodeWithText(text(R.string.place_type_danger)).assertIsDisplayed()
        assertEquals("Zone de danger", text(R.string.place_type_danger))
        // The row is read as ONE block: the type is part of what is spoken.
        composeRule
            .onNodeWithContentDescription("Chantier rue Haute, catégorie Maison, 2 rue Haute, Zone de danger")
            .assertIsDisplayed()
        // A trusted place says nothing about its type: nothing alarming where nothing is wrong.
        composeRule
            .onNodeWithContentDescription("Chez Léa, catégorie Maison, 3 rue des Lilas")
            .assertIsDisplayed()
    }

    @Test
    fun member_showsNoExactCoordinates_onTheList() {
        section(ready(famille, three), myUid = "u2")
        composeRule.onAllNodesWithText("48.8292", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("2.3567", substring = true).assertCountEquals(0)
    }

    @Test
    fun anUnknownCreator_isTreatedAsAMember_onADoubt() {
        val unknownOwner = MiffSummary("miff-c", "Chez nous", ownerUid = null)
        val dir =
            DirectoryState.Ready(listOf(unknownOwner), activeId = unknownOwner.id, premium = true, fromCache = false)
        section(ready(unknownOwner, three), myUid = "u1", directory = dir)
        composeRule.onAllNodesWithText(text(R.string.place_list_add_action)).assertCountEquals(0)
    }

    // --- The creator keeps what they had ------------------------------------------------------------------------------------

    @Test
    fun creator_keepsAddAndTheCounter_andAddAsksForTheSelectedMiff() {
        var added: String? = null
        section(ready(amis, three), myUid = "u2", onAdd = { added = it })
        composeRule.onNodeWithText("Lieux de confiance de Les amis").assertIsDisplayed()
        composeRule.onNodeWithText("3 lieux sur 3 avec la formule gratuite").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_list_add_action)).performClick()
        assertEquals("miff-b", added)
        composeRule.onAllNodesWithText(text(R.string.place_list_member_hint)).assertCountEquals(0)
    }

    @Test
    fun creator_emptyList_keepsItsOwnSentence_andCanAdd() {
        section(ready(amis, emptyList()), myUid = "u2", directory = directory(premium = true))
        composeRule.onNodeWithText(text(R.string.miff_places_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_list_add_action)).assertIsDisplayed()
    }

    // --- Loading, failure -----------------------------------------------------------------------------------------------------

    @Test
    fun whileTheListIsRead_nothingIsDrawn_noTitleNoEmptySentence() {
        section(ProfilePlacesState(famille.id, MiffPlacesView.Loading), myUid = "u2")
        composeRule.onAllNodesWithText("Lieux de confiance", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Aucun lieu", substring = true).assertCountEquals(0)
    }

    @Test
    fun noCardYet_nothingIsDrawn() {
        section(ProfilePlacesState(), myUid = "u2")
        composeRule.onAllNodesWithText("Lieux de confiance", substring = true).assertCountEquals(0)
    }

    @Test
    fun aListThatCannotBeRead_saysItCalmly_andCanBeTriedAgain() {
        var retried = 0
        section(
            ProfilePlacesState(famille.id, MiffPlacesView.Unavailable),
            myUid = "u2",
            onRetry = { retried++ },
        )
        composeRule.onNodeWithText("Lieux de confiance de Famille Martin").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_profile_unavailable)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("erreur", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).performClick()
        assertEquals(1, retried)
    }

    // --- The order of the blocks of the tab ------------------------------------------------------------------------------------

    @Test
    fun theTravelCard_staysUnderTheCarouselAndUnderThePlaces_theLastBlockOfTheTab() {
        composeRule.setContent {
            MiffTheme {
                ProfileTabScreen(
                    state = ProfileTabUiState(),
                    scrollToTopSignal = 0,
                    onOpenMyInfo = {},
                    onOpenSettings = {},
                    onRetry = {},
                    placesContent = {
                        ProfilePlacesSection(
                            ready(famille, three),
                            directory(),
                            "u2",
                            PlaceNotices(),
                            { _, _ -> },
                            {},
                            {},
                        )
                    },
                    travelContent = { Marker("MARQUEUR-VOYAGE") },
                )
            }
        }
        val places = composeRule.onNodeWithText("Lieux de confiance de Famille Martin").getUnclippedBoundsInRoot()
        val lastPlace = composeRule.onNodeWithText("Chantier rue Haute").getUnclippedBoundsInRoot()
        val travel = composeRule.onNodeWithText("MARQUEUR-VOYAGE").getUnclippedBoundsInRoot()
        assertTrue("the travel card is under the title of the places", travel.top > places.top)
        assertTrue("the travel card is under the last place", travel.top > lastPlace.bottom)
    }

    @Composable
    private fun Marker(label: String) {
        Text(label)
    }
}
