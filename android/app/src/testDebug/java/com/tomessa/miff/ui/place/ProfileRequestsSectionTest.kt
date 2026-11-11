package com.tomessa.miff.ui.place

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.place.RequestsView
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.testsupport.testRequest
import com.tomessa.miff.ui.profile.ProfilePlacesState
import com.tomessa.miff.ui.profile.ProfileRequestsState
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
 * The list of places of a Miff for a member who is NOT its creator, with its pending requests (ticket LIEU-REQ-B, criteria M1, M2, M4, M5, T1, T2): the button
 * "Demander l'ajout d'un lieu" in place of "Ajouter un lieu", the requests BEFORE the places, the limit said calmly, nothing of it for the creator.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class ProfileRequestsSectionTest {
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

    private fun requestsOf(count: Int) =
        ProfileRequestsState(
            selectedMiffId = famille.id,
            views =
                mapOf(
                    famille.id to
                        RequestsView.Ready(
                            (1..count).map { testRequest(slot = it, name = "Demande $it", createdAtMillis = 1L) },
                        ),
                ),
        )

    private fun section(
        myUid: String,
        requests: ProfileRequestsState = ProfileRequestsState(),
        places: List<com.tomessa.miff.domain.place.Place> = this.places,
        notices: PlaceNotices = PlaceNotices(),
        onAdd: (String) -> Unit = {},
        onOpenRequest: (String, String) -> Unit = { _, _ -> },
        onRequestPlace: (String) -> Unit = {},
    ) = composeRule.setContent {
        MiffTheme {
            Box(Modifier.fillMaxSize()) {
                ProfilePlacesSection(
                    state = ProfilePlacesState(famille.id, MiffPlacesView.Ready(places)),
                    directory = directory,
                    myUid = myUid,
                    notices = notices,
                    onOpenPlace = { _, _ -> },
                    onAddPlace = onAdd,
                    onRetry = {},
                    requests = requests,
                    onOpenRequest = onOpenRequest,
                    onRequestPlace = onRequestPlace,
                )
            }
        }
    }

    // --- M1, M2: the button ---------------------------------------------------------------------------------------------------

    @Test
    fun member_seesAskToAddAPlace_neverAddAPlace_oneOfIt() {
        section(myUid = "u2")
        composeRule.onAllNodesWithText(text(R.string.place_request_cta_member)).assertCountEquals(1)
        composeRule.onAllNodesWithText(text(R.string.place_list_add_action)).assertCountEquals(0)
    }

    @Test
    fun creator_keepsAddAPlace_neverTheRequestButton() {
        section(myUid = "u1")
        composeRule.onNodeWithText(text(R.string.place_list_add_action)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.place_request_cta_member)).assertCountEquals(0)
    }

    @Test
    fun member_theButtonOpensTheFormOfARequest_ofThisMiff() {
        var asked: String? = null
        section(myUid = "u2", onRequestPlace = { asked = it }, onAdd = { error("a member never adds a place") })
        composeRule.onNodeWithText(text(R.string.place_request_cta_member)).performClick()
        assertEquals("miff-a", asked)
    }

    // --- M4: the pending requests -----------------------------------------------------------------------------------------------

    @Test
    fun member_pendingRequests_areAboveThePlaces_andSaidInWords() {
        section(myUid = "u2", requests = requestsOf(2))
        composeRule.onNodeWithText("Demande 1").assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.place_request_badge_pending)).assertCountEquals(2)
        val request = composeRule.onNodeWithText("Demande 1").getUnclippedBoundsInRoot()
        val place = composeRule.onNodeWithText("Maison de Papa").getUnclippedBoundsInRoot()
        assertTrue("the requests come first", request.top < place.top)
        // The button stays under everything.
        val button = composeRule.onNodeWithText(text(R.string.place_request_cta_member)).getUnclippedBoundsInRoot()
        assertTrue(place.top < button.top)
    }

    @Test
    fun member_aRequestCardIsOneBlockForTheScreenReader_nameThenAddress() {
        section(myUid = "u2", requests = requestsOf(1))
        val named = context().getString(R.string.place_request_a11y_pending_mine, "Demande 1")
        val spoken =
            context().getString(
                R.string.place_list_row_type_a11y,
                named,
                "12 rue des Lilas, 75020 Paris, France",
            )
        composeRule.onNodeWithContentDescription(spoken).assertIsDisplayed()
    }

    @Test
    fun member_aDangerZoneRequestIsReadWithItsTag() {
        val risk = testRequest(slot = 1, name = "Chantier", type = PlaceType.RISK, createdAtMillis = 1L)
        section(
            myUid = "u2",
            requests = ProfileRequestsState("miff-a", mapOf("miff-a" to RequestsView.Ready(listOf(risk)))),
        )
        val named = context().getString(R.string.place_request_a11y_pending_mine, "Chantier")
        val withAddress =
            context().getString(
                R.string.place_list_row_type_a11y,
                named,
                "12 rue des Lilas, 75020 Paris, France",
            )
        val spoken =
            context().getString(
                R.string.place_list_row_type_a11y,
                withAddress,
                text(R.string.place_type_danger),
            )
        composeRule.onNodeWithContentDescription(spoken).assertIsDisplayed()
    }

    @Test
    fun member_touchingARequest_opensItsDetail() {
        var opened: Pair<String, String>? = null
        section(myUid = "u2", requests = requestsOf(1), onOpenRequest = { miff, id -> opened = miff to id })
        composeRule.onNodeWithText("Demande 1").performClick()
        assertEquals("miff-a" to "u2_1", opened)
    }

    @Test
    fun member_noPlaceYetButARequest_showsTheRequest_thenTheCalmEmptySentence() {
        section(myUid = "u2", requests = requestsOf(1), places = emptyList())
        composeRule.onNodeWithText("Demande 1").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_list_empty_member)).assertIsDisplayed()
    }

    @Test
    fun member_aDangerZoneRequest_saysItInWords() {
        val risk = testRequest(slot = 1, name = "Chantier", type = PlaceType.RISK, createdAtMillis = 1L)
        section(
            myUid = "u2",
            requests = ProfileRequestsState(famille.id, mapOf(famille.id to RequestsView.Ready(listOf(risk)))),
        )
        composeRule.onNodeWithText(text(R.string.place_type_danger)).assertIsDisplayed()
    }

    // --- M5: the limit of three ---------------------------------------------------------------------------------------------------

    @Test
    fun member_withThreeRequests_theButtonSaysTheLimitCalmly_andOpensNoForm() {
        var asked = false
        section(myUid = "u2", requests = requestsOf(3), onRequestPlace = { asked = true })
        composeRule.onAllNodesWithText(text(R.string.place_request_limit_member)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.place_request_cta_member)).performClick()
        composeRule.onNodeWithText(text(R.string.place_request_limit_member)).assertIsDisplayed()
        assertTrue("no form is opened at the 4th request", !asked)
    }

    @Test
    fun member_withTwoRequests_theButtonStillOpensTheForm() {
        var asked = false
        section(myUid = "u2", requests = requestsOf(2), onRequestPlace = { asked = true })
        composeRule.onNodeWithText(text(R.string.place_request_cta_member)).performClick()
        assertTrue(asked)
    }

    // --- The confirmation -----------------------------------------------------------------------------------------------------------

    @Test
    fun member_afterSending_aCalmConfirmation_orTheOneForLater() {
        val notices = PlaceNotices()
        notices.requestSent(queued = false)
        section(myUid = "u2", notices = notices)
        composeRule.onNodeWithText(text(R.string.place_request_sent)).assertIsDisplayed()
    }

    @Test
    fun member_afterSendingWithNoNetwork_saysItWillLeaveWhenBackOnline() {
        val notices = PlaceNotices()
        notices.requestSent(queued = true)
        section(myUid = "u2", notices = notices)
        composeRule.onNodeWithText(text(R.string.place_request_sent_offline)).assertIsDisplayed()
    }

    @Test
    fun creator_neverSeesTheConfirmationOfARequest_norTheRequestsOfAMember() {
        val notices = PlaceNotices()
        notices.requestSent(queued = false)
        section(myUid = "u1", requests = requestsOf(2), notices = notices)
        composeRule.onAllNodesWithText(text(R.string.place_request_sent)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Demande 1").assertCountEquals(0)
    }
}
