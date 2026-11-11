package com.tomessa.miff.ui.place

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.place.RequesterCard
import com.tomessa.miff.domain.place.RequesterState
import com.tomessa.miff.domain.place.RequestsView
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.testsupport.testRequest
import com.tomessa.miff.ui.profile.ProfilePlacesState
import com.tomessa.miff.ui.profile.ProfileRequestsState
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
 * The list of places of a Miff for its CREATOR, with the requests it received (ticket LIEU-REQ-C, criteria C2 to C5, T1, T2): the block "À valider" in first
 * position, a card that tells them apart from the places and says who asked, "Accepter" / "Refuser" in the list, nothing of it for a member.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class ProfileReceivedRequestsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private val famille = MiffSummary("miff-a", "Famille Martin", ownerUid = "u1")

    private val directory =
        DirectoryState.Ready(
            miffs = listOf(famille),
            activeId = famille.id,
            premium = false,
            fromCache = false,
            premiumKnown = true,
        )

    private val places = listOf(testPlace("p1", "Maison de Papa"), testPlace("p2", "Chez Léa"))

    private val lea = RequesterState.Known(RequesterCard.of("u2", "Léa", "Martin", null))

    private fun requestsState(
        vararg requests: PlaceRequest,
        requesters: Map<String, RequesterState> = mapOf("miff-a|u2" to lea),
        busy: String? = null,
    ) = ProfileRequestsState(
        selectedMiffId = famille.id,
        views = mapOf(famille.id to RequestsView.Ready(requests.toList())),
        requesters = requesters,
        busyRequestId = busy,
    )

    private fun asked(
        slot: Int = 1,
        name: String = "Chez Mamie",
        type: PlaceType = PlaceType.TRUSTED,
    ) = testRequest(uid = "u2", slot = slot, name = name, type = type, miffId = famille.id, createdAtMillis = 1L)

    private fun section(
        myUid: String,
        requests: ProfileRequestsState,
        places: List<com.tomessa.miff.domain.place.Place> = this.places,
        onOpenRequest: (String, String) -> Unit = { _, _ -> },
        onAccept: (PlaceRequest) -> Unit = {},
        onDecline: (PlaceRequest) -> Unit = {},
        onSeen: (String) -> Unit = {},
        onAdd: (String) -> Unit = {},
    ) = composeRule.setContent {
        MiffTheme {
            Box(Modifier.fillMaxSize()) {
                ProfilePlacesSection(
                    state = ProfilePlacesState(famille.id, MiffPlacesView.Ready(places)),
                    directory = directory,
                    myUid = myUid,
                    notices = PlaceNotices(),
                    onOpenPlace = { _, _ -> },
                    onAddPlace = onAdd,
                    onRetry = {},
                    requests = requests,
                    onOpenRequest = onOpenRequest,
                    onAcceptRequest = onAccept,
                    onDeclineRequest = onDecline,
                    onRequestsSeen = onSeen,
                )
            }
        }
    }

    // --- C2: first position, a card that is not a place, who asked ---------------------------------------------------------------------

    @Test
    fun creator_seesTheBlockToReview_inFirstPosition_beforeThePlaces() {
        section("u1", requestsState(asked()))
        composeRule.onNodeWithText(text(R.string.place_request_section_title)).assertIsDisplayed()
        val title = composeRule.onNodeWithText(text(R.string.place_request_section_title)).getUnclippedBoundsInRoot()
        val card = composeRule.onNodeWithText("Chez Mamie").getUnclippedBoundsInRoot()
        val place = composeRule.onNodeWithText("Maison de Papa").getUnclippedBoundsInRoot()
        assertTrue("the block comes first", title.top < card.top && card.top < place.top)
        // "Ajouter un lieu" is unchanged, under everything.
        composeRule.onNodeWithText(text(R.string.place_list_add_action)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.place_request_cta_member)).assertCountEquals(0)
    }

    @Test
    fun creator_theCardSaysWhoAsked_withTheFirstName() {
        section("u1", requestsState(asked()))
        composeRule.onNodeWithText(context().getString(R.string.place_request_by, "Léa")).assertIsDisplayed()
    }

    @Test
    fun creator_aRequesterNotReadYet_isDrawnWithoutAName_neverHidden() {
        section("u1", requestsState(asked(), requesters = emptyMap()))
        composeRule.onNodeWithText(text(R.string.place_request_by_unknown)).assertIsDisplayed()
        composeRule.onNodeWithText("Chez Mamie").assertIsDisplayed()
    }

    @Test
    fun creator_theCardIsOneBlockForTheScreenReader_andItsButtonsNameThePlace() {
        section("u1", requestsState(asked()))
        composeRule
            .onNodeWithContentDescription(
                context().getString(R.string.place_request_a11y_pending_card, "Chez Mamie", "Léa"),
            ).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context().getString(R.string.place_request_accept_a11y, "Chez Mamie"))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context().getString(R.string.place_request_decline_a11y, "Chez Mamie"))
            .assertIsDisplayed()
    }

    @Test
    fun creator_aDangerZoneRequest_saysItInWords_noWarningColourNeeded() {
        section("u1", requestsState(asked(type = PlaceType.RISK)))
        composeRule.onNodeWithText(text(R.string.place_type_danger)).assertIsDisplayed()
    }

    @Test
    fun creator_noRequest_noBlock_noTitle() {
        section("u1", requestsState())
        composeRule.onAllNodesWithText(text(R.string.place_request_section_title)).assertCountEquals(0)
    }

    @Test
    fun creator_severalRequests_areAllThere_inTheOrderGiven() {
        section("u1", requestsState(asked(1, "Premier"), asked(2, "Second")))
        val first = composeRule.onNodeWithText("Premier").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithText("Second").getUnclippedBoundsInRoot()
        assertTrue(first.top < second.top)
    }

    // --- C3, C4: answering from the list ----------------------------------------------------------------------------------------------------

    @Test
    fun creator_accept_andDecline_callTheRightRequest() {
        var accepted: PlaceRequest? = null
        var declined: PlaceRequest? = null
        section(
            "u1",
            requestsState(asked(1, "Premier"), asked(2, "Second")),
            onAccept = { accepted = it },
            onDecline = { declined = it },
        )
        composeRule
            .onNodeWithContentDescription(context().getString(R.string.place_request_accept_a11y, "Second"))
            .performClick()
        composeRule
            .onNodeWithContentDescription(context().getString(R.string.place_request_decline_a11y, "Premier"))
            .performClick()
        assertEquals("u2_2", accepted?.id)
        assertEquals("u2_1", declined?.id)
    }

    @Test
    fun creator_whileAnAnswerIsOnItsWay_theButtonsWait() {
        section("u1", requestsState(asked(), busy = "u2_1"))
        composeRule
            .onNodeWithContentDescription(context().getString(R.string.place_request_accept_a11y, "Chez Mamie"))
            .assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription(context().getString(R.string.place_request_decline_a11y, "Chez Mamie"))
            .assertIsNotEnabled()
    }

    @Test
    fun creator_touchingTheCard_opensTheDetail() {
        var opened: Pair<String, String>? = null
        section("u1", requestsState(asked()), onOpenRequest = { miff, id -> opened = miff to id })
        composeRule.onNodeWithText("Chez Mamie").performClick()
        assertEquals("miff-a" to "u2_1", opened)
    }

    // --- The list is seen (the dot and the lines of the centre go) ---------------------------------------------------------------------------

    @Test
    fun creator_drawingTheListWithRequests_tellsItWasSeen() {
        var seen: String? = null
        section("u1", requestsState(asked()), onSeen = { seen = it })
        composeRule.waitForIdle()
        assertEquals("miff-a", seen)
    }

    @Test
    fun creator_noRequest_nothingIsSeen() {
        var seen: String? = null
        section("u1", requestsState(), onSeen = { seen = it })
        composeRule.waitForIdle()
        assertEquals(null, seen)
    }

    // --- Nothing for a member --------------------------------------------------------------------------------------------------------------------

    @Test
    fun member_neverSeesTheRequestsReceivedByTheCreator_noBlock_noAcceptButton() {
        section("u2", requestsState(asked(), requesters = mapOf("miff-a|u2" to lea)))
        // A member's list shows ITS OWN pending requests (with the pill), never the block of the creator, never the two answers.
        composeRule.onAllNodesWithText(text(R.string.place_request_section_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_request_accept)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_request_decline)).assertCountEquals(0)
    }

    // --- The order of the blocks of the tab (decision of the PM, 2026-11-11) ---------------------------------------------------------------------

    @Test
    fun theTravelCard_staysTheLastBlock_afterTheRequestsAndThePlaces() {
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
                            state = ProfilePlacesState(famille.id, MiffPlacesView.Ready(places)),
                            directory = directory,
                            myUid = "u1",
                            notices = PlaceNotices(),
                            onOpenPlace = { _, _ -> },
                            onAddPlace = {},
                            onRetry = {},
                            requests = requestsState(asked(1, "Chez Mamie")),
                        )
                    },
                    travelContent = { androidx.compose.material3.Text("MARQUEUR-VOYAGE") },
                )
            }
        }
        val request = composeRule.onNodeWithText("Chez Mamie").getUnclippedBoundsInRoot()
        val lastPlace = composeRule.onNodeWithText("Chez Léa").getUnclippedBoundsInRoot()
        val travel = composeRule.onNodeWithText("MARQUEUR-VOYAGE").getUnclippedBoundsInRoot()
        assertTrue("the requests are above the places", request.top < lastPlace.top)
        assertTrue("the travel card is the last block", travel.top > lastPlace.bottom)
    }
}
