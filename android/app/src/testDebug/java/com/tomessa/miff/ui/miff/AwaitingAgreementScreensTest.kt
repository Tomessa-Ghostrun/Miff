package com.tomessa.miff.ui.miff

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.ui.feed.FeedScreen
import com.tomessa.miff.ui.settings.LocationBlock
import com.tomessa.miff.ui.settings.LocationPrivacyUi
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** "En attente de ton accord" (design 23) on the JVM: what the Feed, the screen of a Miff and Settings say while "Pas maintenant" is the answer. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-xhdpi")
class AwaitingAgreementScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val directory =
        DirectoryState.Ready(
            miffs = listOf(MiffSummary("miffA", "Famille Mfouka")),
            activeId = "miffA",
            premium = false,
            fromCache = false,
        )

    private fun feed(
        agreement: AgreementUi,
        state: DirectoryState = directory,
    ) = show {
        FeedScreen(
            state = state,
            scrollToTopSignal = 0,
            onRetry = {},
            onSelect = {},
            onCreate = {},
            onJoin = {},
            agreement = agreement,
        )
    }

    @Test
    fun feed_whileTheAgreementWaits_showsTheCard_theTruth_andOneWayBack() {
        var reviews = 0
        feed(AgreementUi(setOf("miffA"), onReview = { reviews++ }))
        shoot("loc2b-23a-feed-en-attente")
        composeRule.onNodeWithText(text(R.string.miff_waiting_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Miff ne met plus à jour ton statut avec « Famille Mfouka » tant que tu n'as pas donné ton accord.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_waiting_feed_note)).assertIsDisplayed()
        // The provisional text of the Feed gives way to the one that tells the truth.
        composeRule.onAllNodesWithText(text(R.string.feed_empty_title)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.miff_waiting_review)).performClick()
        assertEquals(1, reviews)
    }

    @Test
    fun feed_withNothingWaiting_hasNoCard() {
        feed(AgreementUi())
        composeRule.onAllNodesWithText(text(R.string.miff_waiting_title)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.feed_empty_title)).assertIsDisplayed()
    }

    @Test
    fun feed_anotherMiffWaiting_isNotTheOneShown_noCard() {
        feed(AgreementUi(setOf("miffB")))
        composeRule.onAllNodesWithText(text(R.string.miff_waiting_title)).assertCountEquals(0)
    }

    @Test
    fun feed_theFeedTextNoLongerSaysTheLocationIsNotUsed() {
        feed(AgreementUi())
        composeRule.onAllNodesWithText("position n'est pas utilisée", substring = true).assertCountEquals(0)
    }

    @Test
    fun card_neverUsesTheWordsRefusedSuspendedOrDisabled() {
        val fr =
            listOf(
                R.string.miff_waiting_title,
                R.string.miff_waiting_feed_note,
                R.string.miff_waiting_miff_body,
                R.string.miff_waiting_miff_body_owner,
                R.string.miff_sharing_waiting_description,
                R.string.settings_privacy_miff_waiting_body,
            ).joinToString(" ") { text(it) }.lowercase()
        listOf("refus", "suspendu", "désactiv", "bloqu").forEach {
            assert(it !in fr) { "the texts of the card must not say \"$it\"" }
        }
    }

    @Test
    fun settings_theLocationBlock_saysTheStateOfThePermission_andTheMiffsThatWait() {
        var reviews = 0
        show {
            LocationBlock(
                location = LocationPrivacyUi(permissionComplete = true, waitingMiffNames = listOf("Famille Mfouka")),
                onReviewAgreement = { reviews++ },
                onOpenPhoneSettings = {},
                onOpenDetectionHelp = {},
            )
        }
        shoot("loc2b-23c-reglages-en-attente")
        composeRule.onNodeWithText(text(R.string.settings_privacy_location_status_ok)).assertIsDisplayed()
        composeRule.onNodeWithText("Famille Mfouka").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_privacy_miff_waiting_pill)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_waiting_review)).performClick()
        assertEquals(1, reviews)
    }

    @Test
    fun settings_theHelpLinkIsThere_andOpensTheHelp() {
        var opened = 0
        show {
            LocationBlock(
                location = LocationPrivacyUi(),
                onReviewAgreement = {},
                onOpenPhoneSettings = {},
                onOpenDetectionHelp = { opened++ },
            )
        }
        composeRule.onNodeWithText(text(R.string.settings_privacy_detection_help_link)).performClick()
        assertEquals(1, opened)
        // No Miff waits: no "Revoir et accepter".
        composeRule.onAllNodesWithText(text(R.string.miff_waiting_review)).assertCountEquals(0)
    }

    @Test
    fun card_isNotJustColour_aPauseIconAndWords_andTheSwitchOfTheMiffIsOffAndCannotBeUsed() {
        show {
            AwaitingAgreementCard(body = text(R.string.miff_waiting_miff_body), onReview = {})
        }
        composeRule.onNodeWithText(text(R.string.miff_waiting_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_waiting_miff_body)).assertIsDisplayed()
    }

    @Test
    fun theSwitchOfTheMiff_inThatState_staysUsable_theLineSaysWhatIsTrue_reviewI6() {
        var clicks = 0
        show {
            MiffSwitchRow(
                title = text(R.string.miff_sharing_title),
                description = text(R.string.miff_sharing_waiting_description),
                checked = true,
                enabled = true,
                stateText = text(R.string.miff_sharing_state_on),
                onClick = { clicks++ },
            )
        }
        composeRule.onNodeWithText(text(R.string.miff_sharing_waiting_description)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_sharing_title)).performClick()
        assertEquals(1, clicks)
        // The texts never say that "nothing is shared": the last status stays visible until sharing is stopped.
        val all =
            listOf(
                R.string.miff_waiting_feed_body,
                R.string.miff_waiting_miff_body,
                R.string.miff_waiting_miff_body_owner,
                R.string.miff_sharing_waiting_description,
                R.string.settings_privacy_miff_waiting_body,
            ).joinToString(" ") { text(it) }
        assert("Rien n'est partagé" !in all) { "the texts of the waiting state must not claim that nothing is shared" }
    }
}
