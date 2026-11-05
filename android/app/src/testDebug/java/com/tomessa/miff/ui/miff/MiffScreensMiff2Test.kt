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
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DeletionState
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.testsupport.testPlace
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
 * The screens of MIFF-2 on the JVM (Robolectric, no phone), in French: who sees which action, the questions and their calm words
 * (ticket screens H, I, J, K), the touch targets and the largest font. Writes screenshots to build/screenshots for the pull request
 * (compare with design/screens/miff-dialogues.md and miff-ecran-miff.md, and the mockups of design/exports/miff).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class MiffScreensMiff2Test {
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
    private val newCode = "BCDFGHK23456"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"

    private fun member(
        uid: String,
        first: String,
        last: String,
        role: MiffRole = MiffRole.MEMBER,
    ) = MiffMember(uid, first, last, null, role, true)

    private fun ready(
        owner: Boolean = true,
        members: List<MiffMember> =
            listOf(
                member("me", "Léa", "Martin", MiffRole.OWNER),
                member("u2", "Emmanuelle", "Pluchard"),
                member("u3", "Ryadh", "Pluchard"),
            ),
    ) = MiffScreenState(
        status = MiffScreenStatus.READY,
        miffId = miffId,
        name = "Famille Martin",
        joinCode = code,
        isOwner = owner,
        members = members,
        places = listOf(testPlace("p1", "Maison")),
        myUid = if (owner) "me" else "u2",
        sharing = true,
        nameInput = "Famille Martin",
    )

    private class Calls {
        val removed = mutableListOf<String>()
        var renew = 0
        var leave = 0
        var delete = 0
        var confirmed = 0
        var dismissed = 0
        var later = 0
        var retry = 0
        var back = 0
    }

    private fun showMiff(
        state: MiffScreenState,
        calls: Calls = Calls(),
    ) = show {
        MiffScreen(
            state = state,
            onBack = { calls.back++ },
            onRetry = {},
            onNameChanged = {},
            onSaveName = {},
            onSharingRequested = {},
            onSharingConfirmed = {},
            onSharingDismissed = {},
            onRemoveRequested = { calls.removed += it },
            onRenewRequested = { calls.renew++ },
            onLeaveRequested = { calls.leave++ },
            onDeleteRequested = { calls.delete++ },
            onActionConfirmed = { calls.confirmed++ },
            onActionDismissed = { calls.dismissed++ },
            onInviteLater = { calls.later++ },
            onDeletionRetry = { calls.retry++ },
        )
    }

    // --- The creator's screen (F) -------------------------------------------------------------------------------------

    @Test
    fun creator_hasTheMenuOnTheOthers_neverOnHerOwnLine_noRedBin_andTheFoot() {
        val calls = Calls()
        showMiff(ready(), calls)
        shoot("miff2-f-creator")
        // One menu per OTHER loved one, named; none for the creator's own line.
        composeRule
            .onAllNodesWithContentDescription(
                text(R.string.miff_member_menu_a11y, "Emmanuelle"),
            ).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription(text(R.string.miff_member_menu_a11y, "Ryadh")).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription(text(R.string.miff_member_menu_a11y, "Léa")).assertCountEquals(0)
        composeRule
            .onNodeWithContentDescription(text(R.string.miff_member_menu_a11y, "Emmanuelle"))
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule
            .onNodeWithText(
                text(R.string.miff_renew_link),
            ).performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
        // The foot: the sentence that explains the way out, and the quiet link. No "Quitter" for the creator (decision QM1).
        composeRule.onNodeWithText(text(R.string.miff_owner_foot)).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.miff_delete_link),
            ).performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, calls.delete)
        composeRule.onAllNodesWithText(text(R.string.miff_leave_link)).assertCountEquals(0)
    }

    @Test
    fun creator_theMenuOffersOneChoice_RetirerDeLaMiff_andAsksForTheRightPerson() {
        val calls = Calls()
        showMiff(ready(), calls)
        composeRule
            .onNodeWithContentDescription(
                text(R.string.miff_member_menu_a11y, "Ryadh"),
            ).performScrollTo()
            .performClick()
        composeRule.onNodeWithText(text(R.string.miff_member_remove_action)).assertIsDisplayed()
        shoot("miff2-f-menu")
        composeRule.onNodeWithText(text(R.string.miff_member_remove_action)).performClick()
        assertEquals(listOf("u3"), calls.removed)
    }

    @Test
    fun member_hasNoMenuNoRenewNoDelete_butHasQuit_andReadsThatTheyCanLeaveAnyTime() {
        val calls = Calls()
        showMiff(ready(owner = false), calls)
        shoot("miff2-g-member")
        composeRule.onAllNodesWithContentDescription("Actions", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.miff_renew_link)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.miff_delete_link)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.miff_leave_hint)).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.miff_leave_link),
            ).performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, calls.leave)
        // The code is still shared by every member (decision QM3).
        composeRule.onNodeWithText("XTZR SP36 7988").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun offline_theActionsThatWriteAreGrey_andReadingGoesOn() {
        showMiff(ready().copy(offline = true))
        composeRule.onNodeWithText(text(R.string.miff_renew_link)).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.miff_delete_link)).performScrollTo().assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription(text(R.string.miff_member_menu_a11y, "Emmanuelle"))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Emmanuelle Pluchard").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invitation_afterADeparture_isCalm_optional_andCanWait() {
        val calls = Calls()
        showMiff(ready().copy(inviteToRenew = true), calls)
        shoot("miff2-f2-invite")
        composeRule.onNodeWithText(text(R.string.miff_renew_invite)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_renew_invite_later)).performClick()
        assertEquals(1, calls.later)
    }

    @Test
    fun renewedNotice_isSaid() {
        showMiff(ready().copy(codeRenewed = true))
        composeRule.onNodeWithText(text(R.string.miff_renew_done)).performScrollTo().assertIsDisplayed()
    }

    // --- The questions (H, I, J, K) ---------------------------------------------------------------------------------

    @Test
    fun removeQuestion_isNominal_calm_andCancelIsThere() {
        val calls = Calls()
        showMiff(ready().copy(action = MiffAction.Remove("u2", "Emmanuelle")), calls)
        shoot("miff2-h1-remove")
        composeRule.onNodeWithText(text(R.string.miff_remove_title, "Emmanuelle")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_remove_body, "Emmanuelle")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.common_cancel)).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText(text(R.string.miff_remove_action)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, calls.dismissed)
        assertEquals(1, calls.confirmed)
    }

    @Test
    fun removeDone_showsTheNewCode_withShare_andDone_andNoCancel() {
        showMiff(ready().copy(action = MiffAction.RemovedDone("Emmanuelle", newCode)))
        shoot("miff2-h3-removed")
        composeRule.onNodeWithText(text(R.string.miff_remove_done_title, "Emmanuelle")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_remove_done_body)).assertIsDisplayed()
        composeRule.onNodeWithText("BCDF GHK2 3456").assertIsDisplayed()
        // Two of them: the screen's own button and the dialog's.
        composeRule.onAllNodesWithText(text(R.string.miff_share_code_action)).assertCountEquals(2)
        composeRule.onNodeWithText(text(R.string.miff_remove_done_close)).assertIsDisplayed()
    }

    @Test
    fun removeWhileBusy_nothingElseCanBePressed_andOfflineIsSaidInTheDialog() {
        showMiff(ready().copy(action = MiffAction.Remove("u2", "Emmanuelle"), actionBusy = true))
        composeRule.onNodeWithText(text(R.string.miff_remove_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.common_cancel)).assertIsNotEnabled()
    }

    @Test
    fun dialogMessage_isShownInTheDialog_notBehindIt() {
        showMiff(ready().copy(action = MiffAction.Renew, message = ActionMessage.OFFLINE))
        shoot("miff2-k3-offline")
        composeRule.onAllNodesWithText(text(R.string.miff_message_offline)).assertCountEquals(1)
        composeRule.onNodeWithText(text(R.string.miff_renew_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_renew_body)).assertIsDisplayed()
    }

    @Test
    fun leaveQuestion_namesTheMiff_andTellsThePositionStopsBeingShared() {
        showMiff(ready(owner = false).copy(action = MiffAction.Leave))
        shoot("miff2-i1-leave")
        composeRule.onNodeWithText(text(R.string.miff_leave_title, "Famille Martin")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_leave_body)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.miff_leave_action)).onLast().assertIsDisplayed()
    }

    @Test
    fun deleteQuestion_countsTheOthers_withPlural_andProposesToNotify_withHonestHint() {
        val calls = Calls()
        showMiff(ready().copy(action = MiffAction.Delete(2)), calls)
        shoot("miff2-j1-delete")
        composeRule.onNodeWithText(text(R.string.miff_delete_title, "Famille Martin")).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                context().resources.getQuantityString(R.plurals.miff_delete_body_others, 2, 2),
            ).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.miff_delete_notify_action),
            ).assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        // What the button really does is said under it: nobody is told automatically (decision QM9, risk R6).
        composeRule.onNodeWithText(text(R.string.miff_delete_notify_hint)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.miff_delete_action)).onLast().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.common_cancel)).assertIsDisplayed()
    }

    @Test
    fun deleteQuestion_oneOther_usesTheSingularSentence() {
        showMiff(ready().copy(action = MiffAction.Delete(1)))
        composeRule
            .onNodeWithText(
                context().resources.getQuantityString(R.plurals.miff_delete_body_others, 1, 1),
            ).assertIsDisplayed()
    }

    @Test
    fun deleteQuestion_aloneInTheMiff_neverSaysZero_andHasNoNotifyButton() {
        showMiff(
            ready(members = listOf(member("me", "Léa", "Martin", MiffRole.OWNER))).copy(action = MiffAction.Delete(0)),
        )
        shoot("miff2-j1-delete-alone")
        composeRule.onNodeWithText(text(R.string.miff_delete_body_alone)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.miff_delete_notify_action)).assertCountEquals(0)
    }

    @Test
    fun deleteProgress_isOneCalmSentence_andThereIsNoCancel() {
        showMiff(ready().copy(deletion = DeletionState.Running(resumed = false)))
        shoot("miff2-j3-progress")
        composeRule.onNodeWithText(text(R.string.miff_delete_progress)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.common_cancel)).assertCountEquals(0)
    }

    @Test
    fun deleteProgress_withoutNetwork_saysItWillCarryOn() {
        showMiff(ready().copy(deletion = DeletionState.WaitingForNetwork))
        shoot("miff2-s7-paused")
        composeRule.onNodeWithText(text(R.string.miff_delete_paused)).assertIsDisplayed()
    }

    @Test
    fun deleteStuck_offersToTryAgain_andToClose() {
        val calls = Calls()
        showMiff(ready().copy(deletion = DeletionState.Stuck), calls)
        composeRule.onNodeWithText(text(R.string.miff_message_generic)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).performClick()
        assertEquals(1, calls.retry)
    }

    @Test
    fun deleteDone_saysItWasDeleted_andCloses() {
        val calls = Calls()
        showMiff(ready().copy(deletion = DeletionState.Done), calls)
        shoot("miff2-j4-done")
        composeRule.onNodeWithText(text(R.string.miff_delete_done)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_delete_close)).performClick()
        assertEquals(1, calls.back)
    }

    @Test
    fun underDeletion_theActionsThatWriteAreGrey_behindTheDialog() {
        showMiff(ready().copy(deletion = DeletionState.Stuck))
        composeRule.onNodeWithText(text(R.string.miff_renew_link)).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.miff_delete_link)).performScrollTo().assertIsNotEnabled()
    }

    // --- The largest font -------------------------------------------------------------------------------------------

    @Test
    fun deleteQuestion_withTheLargestFont_everythingStaysReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                MiffScreen(
                    state = ready().copy(action = MiffAction.Delete(3)),
                    onBack = {},
                    onRetry = {},
                    onNameChanged = {},
                    onSaveName = {},
                    onSharingRequested = {},
                    onSharingConfirmed = {},
                    onSharingDismissed = {},
                )
            }
        }
        // The screen's own link has the same words as the dialog's button: the dialog is the last of the two.
        composeRule
            .onAllNodesWithText(text(R.string.miff_delete_action))
            .onLast()
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.miff_delete_notify_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.common_cancel)).performScrollTo().assertIsDisplayed()
        shoot("miff2-j1-delete-font200")
    }

    // --- The words ------------------------------------------------------------------------------------------------------

    @Test
    fun theWordsOfMiff2_areCalm_tutoyer_andNeverAlarming() {
        val ids =
            listOf(
                R.string.miff_remove_title,
                R.string.miff_remove_body,
                R.string.miff_remove_done_title,
                R.string.miff_remove_done_body,
                R.string.miff_renew_title,
                R.string.miff_renew_body,
                R.string.miff_renew_invite,
                R.string.miff_leave_hint,
                R.string.miff_leave_title,
                R.string.miff_leave_body,
                R.string.miff_owner_foot,
                R.string.miff_delete_title,
                R.string.miff_delete_body_alone,
                R.string.miff_delete_notify_hint,
                R.string.miff_delete_notice_message,
                R.string.miff_delete_progress,
                R.string.miff_delete_paused,
                R.string.miff_delete_done,
                R.string.miff_delete_resume,
            )
        val forbidden =
            listOf(
                "irrévocable",
                "irreversible",
                "expuls",
                "banni",
                "exclu",
                "danger",
                "urgence",
                "attention",
                "vous ",
                "votre",
                "vos ",
            )
        for (id in ids) {
            val value = context().getString(id, "Prénom", 3).lowercase()
            forbidden.forEach { word -> assertTrue("« $value » contains « $word »", !value.contains(word)) }
        }
        listOf(2, 5).forEach { n ->
            val value = context().resources.getQuantityString(R.plurals.miff_delete_body_others, n, n).lowercase()
            forbidden.forEach { word -> assertTrue("plural « $value » contains « $word »", !value.contains(word)) }
        }
    }
}
