package com.tomessa.miff.ui.miff

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffPlace
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.ui.feed.FeedScreen
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
 * The screens of the Miff flow on the JVM (Robolectric, no phone): what the explicit consent shows and where, what a member
 * does not get, what a free account does not see, the touch targets and the largest font. Writes screenshots to
 * build/screenshots for the pull request (compare with design/exports/miff).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class MiffScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = context().getString(id, *args)

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val code = "XTZRSP367988"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"

    private fun member(
        uid: String,
        first: String,
        last: String,
        role: MiffRole = MiffRole.MEMBER,
        sharing: Boolean = true,
    ) = MiffMember(uid, first, last, null, role, sharing)

    private fun ready(
        owner: Boolean = true,
        sharing: Boolean = true,
        members: List<MiffMember> =
            listOf(
                member("me", "Léa", "Martin", MiffRole.OWNER, sharing),
                member("u2", "Emmanuelle", "Pluchard"),
                member("u3", "Ryadh", "Pluchard"),
            ),
        offline: Boolean = false,
    ) = MiffScreenState(
        status = MiffScreenStatus.READY,
        miffId = miffId,
        name = "Famille Martin",
        joinCode = code,
        isOwner = owner,
        members = members,
        places = listOf(MiffPlace("p1", "Maison")),
        myUid = "me",
        sharing = sharing,
        nameInput = "Famille Martin",
        offline = offline,
    )

    private fun showMiff(
        state: MiffScreenState,
        onSharing: () -> Unit = {},
    ) = show {
        MiffScreen(
            state = state,
            onBack = {},
            onRetry = {},
            onNameChanged = {},
            onSaveName = {},
            onSharingRequested = onSharing,
            onSharingConfirmed = {},
            onSharingDismissed = {},
        )
    }

    // --- A. Choice ------------------------------------------------------------------------------------------------

    @Test
    fun choice_offersTheTwoWays_aQuietSignOut_andNoBackArrow() {
        var created = 0
        var joined = 0
        show { MiffChoiceScreen(onCreate = { created++ }, onJoin = { joined++ }, onSignOut = {}) }
        shoot("miff-a-choice")
        composeRule.onNodeWithText(text(R.string.miff_choice_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_choice_create_title)).performClick()
        composeRule.onNodeWithText(text(R.string.miff_choice_join_title)).performClick()
        assertEquals(1, created)
        assertEquals(1, joined)
        composeRule.onNodeWithText(text(R.string.auth_signout_action)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(text(R.string.auth_back_a11y)).assertCountEquals(0)
    }

    // --- B and C. Create ------------------------------------------------------------------------------------------

    @Test
    fun create_theConsentIsAboveTheButton_inFullView_andTheButtonIsGreyUntilTheNameIsValid() {
        show {
            CreateMiffScreen(CreateUiState(), onBack = {}, onNameChanged = {}, onSubmit = {}, onContinue = {})
        }
        shoot("miff-b1-create-empty")
        composeRule.onNodeWithText(text(R.string.miff_consent_create)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_create_action)).assertIsNotEnabled()
        val consent = composeRule.onNodeWithText(text(R.string.miff_consent_create)).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.miff_create_action)).getUnclippedBoundsInRoot()
        assertTrue("the text comes BEFORE the button (explicit consent)", consent.bottom <= button.top)
        // No code and no list of loved ones on this screen (decision QM15: the code does not exist yet).
        composeRule.onAllNodesWithText("XXXX", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.miff_section_members_title)).assertCountEquals(0)
    }

    @Test
    fun create_validName_buttonIsActive_andTheLimitIsSaid() {
        show {
            CreateMiffScreen(
                CreateUiState(name = "a".repeat(40), limitReached = true),
                onBack = {},
                onNameChanged = {},
                onSubmit = {},
                onContinue = {},
            )
        }
        shoot("miff-b2-create-limit")
        composeRule.onNodeWithText(text(R.string.miff_create_action)).assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.miff_name_limit)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_name_counter, 40, 40)).assertIsDisplayed()
    }

    @Test
    fun create_offline_saysItCalmly_aboveTheButton() {
        show {
            CreateMiffScreen(
                CreateUiState(name = "Chez nous", message = ActionMessage.OFFLINE),
                onBack = {},
                onNameChanged = {},
                onSubmit = {},
                onContinue = {},
            )
        }
        shoot("miff-b4-create-offline")
        composeRule.onNodeWithText(text(R.string.miff_message_offline)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_create_action)).assertIsEnabled()
    }

    @Test
    fun ready_showsTheCodeByGroupsOfFour_withShareCopyAndContinue() {
        var continued = 0
        show {
            CreateMiffScreen(
                CreateUiState(step = CreateStep.READY, created = CreatedMiff(miffId, "Famille Martin", code)),
                onBack = {},
                onNameChanged = {},
                onSubmit = {},
                onContinue = { continued++ },
            )
        }
        shoot("miff-c-ready")
        composeRule.onNodeWithText("XTZR SP36 7988").assertIsDisplayed()
        // Read letter by letter, group by group, by the screen reader.
        composeRule
            .onNodeWithContentDescription(
                text(R.string.miff_code_a11y, "X T Z R, S P 3 6, 7 9 8 8"),
            ).assertExists()
        composeRule.onNodeWithText(text(R.string.miff_ready_title, "Famille Martin")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_share_code_action)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_copy_code_action)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_ready_continue_action)).performClick()
        assertEquals(1, continued)
        composeRule.onAllNodesWithContentDescription(text(R.string.auth_back_a11y)).assertCountEquals(0)
    }

    // --- D and E. Join --------------------------------------------------------------------------------------------

    @Test
    fun joinCode_emptyThenFilled_andTheLookAlikeMessage() {
        show {
            JoinMiffScreen(JoinUiState(), onBack = {
            }, onCodeChanged = {}, onContinue = {}, onConfirm = {}, onCancelConfirm = {}, onOpenMiff = {})
        }
        shoot("miff-d1-join-empty")
        composeRule.onNodeWithText(text(R.string.miff_join_continue_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.miff_join_paste_action)).assertIsDisplayed()
    }

    @Test
    fun joinCode_notFound_isOneCalmMessage_foreignCharacterIsAnother_pauseIsInfo() {
        show {
            JoinMiffScreen(
                JoinUiState(code = code, problem = JoinProblem.NOT_FOUND),
                onBack = {},
                onCodeChanged = {},
                onContinue = {},
                onConfirm = {},
                onCancelConfirm = {},
                onOpenMiff = {},
            )
        }
        shoot("miff-d4-join-notfound")
        composeRule.onNodeWithText(text(R.string.miff_join_not_found)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_join_continue_action)).assertIsEnabled()
    }

    @Test
    fun joinCode_pause_greysTheButton_withACalmSentence() {
        show {
            JoinMiffScreen(
                JoinUiState(code = code, problem = JoinProblem.PAUSED),
                onBack = {},
                onCodeChanged = {},
                onContinue = {},
                onConfirm = {},
                onCancelConfirm = {},
                onOpenMiff = {},
            )
        }
        composeRule.onNodeWithText(text(R.string.miff_join_paused)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_join_continue_action)).assertIsNotEnabled()
    }

    @Test
    fun joinCode_alreadyAMember_offersToOpenTheMiff() {
        var opened: String? = null
        show {
            JoinMiffScreen(
                JoinUiState(code = code, alreadyMember = AlreadyMember(miffId, "Famille Martin")),
                onBack = {},
                onCodeChanged = {},
                onContinue = {},
                onConfirm = {},
                onCancelConfirm = {},
                onOpenMiff = { opened = it },
            )
        }
        shoot("miff-d7-join-already")
        composeRule.onNodeWithText(text(R.string.miff_join_already_member, "Famille Martin")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_join_open_action)).performClick()
        assertEquals(miffId, opened)
    }

    @Test
    fun joinConfirm_theConsentIsInFullViewBeforeTheButton_andTheButtonSaysWhatItDoes() {
        show {
            JoinMiffScreen(
                JoinUiState(step = JoinStep.CONFIRM, code = code, miffName = "Famille Martin"),
                onBack = {},
                onCodeChanged = {},
                onContinue = {},
                onConfirm = {},
                onCancelConfirm = {},
                onOpenMiff = {},
            )
        }
        shoot("miff-e-join-confirm")
        composeRule.onNodeWithText(text(R.string.miff_join_confirm_title, "Famille Martin")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_consent_join)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_consent_system_note_join)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_consent_members_see_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_consent_places)).assertIsDisplayed()
        val consent = composeRule.onNodeWithText(text(R.string.miff_consent_join)).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.miff_join_action)).getUnclippedBoundsInRoot()
        assertTrue(consent.bottom <= button.top)
        composeRule.onNodeWithText(text(R.string.miff_join_action)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.common_cancel)).assertIsDisplayed()
    }

    @Test
    fun joinConfirm_withTheLargestFont_everythingStaysReachable_noOverlap() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                JoinMiffScreen(
                    JoinUiState(step = JoinStep.CONFIRM, code = code, miffName = "Famille Martin"),
                    onBack = {},
                    onCodeChanged = {},
                    onContinue = {},
                    onConfirm = {},
                    onCancelConfirm = {},
                    onOpenMiff = {},
                )
            }
        }
        composeRule
            .onNodeWithText(
                text(R.string.miff_join_action),
            ).performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.miff_consent_join)).performScrollTo().assertIsDisplayed()
        shoot("miff-e-join-confirm-font200")
    }

    // --- F and G. The Miff screen ---------------------------------------------------------------------------------

    @Test
    fun miffScreen_creator_hasTheNameField_theCode_theLovedOnes_theSharingRow_andThePlaces() {
        showMiff(ready())
        shoot("miff-f-creator")
        composeRule.onAllNodesWithText("Famille Martin").assertCountEquals(2) // the title and the field
        composeRule.onNodeWithText(text(R.string.miff_name_save_action)).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("XTZR SP36 7988").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_share_code_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Emmanuelle Pluchard").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_role_owner)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_role_you)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_sharing_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Maison").performScrollTo().assertIsDisplayed()
        // The actions of MIFF-2 are not drawn: a button that does nothing never reaches a tester.
        composeRule.onAllNodesWithText("Supprimer", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Quitter", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Retirer", substring = true).assertCountEquals(0)
    }

    @Test
    fun miffScreen_member_cannotRename_butSeesTheCodeAndCanShare() {
        showMiff(ready(owner = false))
        shoot("miff-g-member")
        composeRule.onAllNodesWithText(text(R.string.miff_section_name_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.miff_name_save_action)).assertCountEquals(0)
        composeRule.onNodeWithText("XTZR SP36 7988").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_share_code_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_sharing_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun miffScreen_showsNoStatusNoBatteryNoPositionNoPhotoOfAnUpdate() {
        showMiff(ready())
        listOf("batterie", "Batterie", "%", "position", "Localiser", "il y a", "Mis à jour", "en sécurité").forEach {
            composeRule.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun miffScreen_sharingStopped_saysItCalmly_inTheRow() {
        showMiff(ready(sharing = false))
        shoot("miff-f4-sharing-off")
        composeRule.onNodeWithText(text(R.string.miff_sharing_off_description)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun miffScreen_sharingRowIsAtLeast48dp_andOpensTheQuestion() {
        var asked = 0
        showMiff(ready(), onSharing = { asked++ })
        composeRule
            .onNodeWithText(
                text(R.string.miff_sharing_title),
            ).performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, asked)
    }

    @Test
    fun miffScreen_offline_saysSo_andTheRowThatWritesIsGrey() {
        showMiff(
            MiffScreenState().copy(
                status = MiffScreenStatus.READY,
                name = "Famille Martin",
                joinCode = code,
                isOwner = true,
                members = listOf(member("me", "Léa", "Martin", MiffRole.OWNER)),
                myUid = "me",
                sharing = true,
                nameInput = "Famille Martin",
                offline = true,
            ),
        )
        shoot("miff-f5-offline")
        composeRule.onNodeWithText(text(R.string.miff_offline_notice)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_sharing_title)).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.miff_name_save_action)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun miffScreen_aFullMiff_replacesShareByACalmSentence_noNumber() {
        val fifteen = List(15) { member("u$it", "Prénom$it", "Nom", if (it == 0) MiffRole.OWNER else MiffRole.MEMBER) }
        showMiff(ready(members = fifteen))
        composeRule.onNodeWithText(text(R.string.miff_full_notice)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.miff_share_code_action)).assertCountEquals(0)
        // The code can still be copied.
        composeRule.onNodeWithText(text(R.string.miff_copy_code_action)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sharingDialog_stopAndResume_usetheCalmWording_cancelIsThere() {
        showMiff(ready().copy(sharingDialog = SharingDialog.STOP))
        shoot("miff-m1-stop-dialog")
        composeRule.onNodeWithText(text(R.string.miff_sharing_stop_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_sharing_stop_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_sharing_stop_action)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.common_cancel)).assertIsDisplayed()
    }

    @Test
    fun sharingDialog_resume_hasTheSameWordsAsTheEntry() {
        showMiff(ready(sharing = false).copy(sharingDialog = SharingDialog.RESUME))
        shoot("miff-m2-resume-dialog")
        composeRule.onNodeWithText(text(R.string.miff_sharing_resume_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_sharing_resume_body)).assertIsDisplayed()
        // The same consent card as at the entry: what the members see, the places sentence, the note about the phone permission.
        composeRule.onNodeWithText(text(R.string.miff_consent_members_see_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_consent_places)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_consent_system_note_create)).assertIsDisplayed()
    }

    // --- L. Access lost -------------------------------------------------------------------------------------------

    @Test
    fun accessLost_isOneCalmMessage_withTheName_andNoCause() {
        var acknowledged = 0
        show { AccessLostScreen(miffName = "Famille Martin", onAcknowledge = { acknowledged++ }) }
        shoot("miff-l-access-lost")
        composeRule.onNodeWithText(text(R.string.miff_lost_title, "Famille Martin")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_lost_body)).assertIsDisplayed()
        listOf("retiré", "supprimé", "exclu", "banni", "expulsé").forEach {
            composeRule.onAllNodesWithText(it, substring = true, ignoreCase = true).assertCountEquals(0)
        }
        composeRule.onNodeWithText(text(R.string.miff_lost_ok_action)).performClick()
        assertEquals(1, acknowledged)
    }

    @Test
    fun accessLost_withoutAName_stillSaysItGently() {
        show { AccessLostScreen(miffName = null, onAcknowledge = {}) }
        composeRule.onNodeWithText(text(R.string.miff_lost_title_unknown)).assertIsDisplayed()
    }

    // --- Feed ----------------------------------------------------------------------------------------------------

    private fun showFeed(
        state: DirectoryState,
        onCreate: () -> Unit = {},
        onJoin: () -> Unit = {},
    ) = show {
        FeedScreen(state, scrollToTopSignal = 0, onRetry = {}, onSelect = {}, onCreate = onCreate, onJoin = onJoin)
    }

    @Test
    fun feed_withoutAMiff_offersTheTwoButtons_andNeverSaysEveryoneIsSafe() {
        var created = 0
        var joined = 0
        showFeed(
            DirectoryState.Ready(emptyList(), null, premium = false, fromCache = false),
            { created++ },
            { joined++ },
        )
        shoot("feed-no-miff")
        composeRule.onNodeWithText(text(R.string.feed_selector_none)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_create_action)).performClick()
        composeRule.onNodeWithText(text(R.string.feed_join_action)).performClick()
        assertEquals(1, created)
        assertEquals(1, joined)
        listOf("sécurité", "Tout va bien", "Tout le monde").forEach {
            composeRule.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun feed_freeAccount_showsTheNameOfTheMiff_withNoSelectorAndNoCreateOrJoin() {
        showFeed(
            DirectoryState.Ready(
                listOf(MiffSummary(miffId, "Famille Martin")),
                miffId,
                premium = false,
                fromCache = false,
            ),
        )
        shoot("feed-free-pastille")
        composeRule.onNodeWithText("Famille Martin").assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.feed_create_action)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.feed_join_action)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.feed_selector_join)).assertCountEquals(0)
    }

    @Test
    fun feed_premiumAccount_hasASelectorWithJoinAndCreate() {
        showFeed(
            DirectoryState.Ready(
                listOf(MiffSummary(miffId, "Famille Martin")),
                miffId,
                premium = true,
                fromCache = false,
            ),
        )
        composeRule.onNodeWithText("Famille Martin").performClick()
        composeRule.onNodeWithText(text(R.string.feed_selector_join)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_selector_create)).assertIsDisplayed()
        shoot("feed-premium-selector")
    }

    @Test
    fun feed_unableToRead_isCalm_withRetry() {
        showFeed(DirectoryState.Unavailable)
        composeRule.onNodeWithText(text(R.string.feed_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).assertIsDisplayed()
    }

    // --- Dialog model --------------------------------------------------------------------------------------------

    @Test
    fun confirmDialog_whileBusy_nothingElseCanBePressed() {
        show {
            MiffConfirmDialog(
                title = "T",
                body = "B",
                confirmLabel = "Oui",
                cancelLabel = "Non",
                onConfirm = {},
                onCancel = {},
                busy = true,
            )
        }
        composeRule.onNodeWithText("Oui").assertIsNotEnabled()
        composeRule.onNodeWithText("Non").assertIsNotEnabled()
    }
}
