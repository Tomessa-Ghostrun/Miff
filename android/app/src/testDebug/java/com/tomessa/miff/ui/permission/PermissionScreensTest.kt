package com.tomessa.miff.ui.permission

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.RenewalMiff
import com.tomessa.miff.domain.miff.MiffSharingConsent
import com.tomessa.miff.domain.permission.LocationButton
import com.tomessa.miff.domain.permission.LocationScreenModel
import com.tomessa.miff.ui.miff.ActionMessage
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

/** The screens of consent before the Feed on the JVM (Robolectric): what they say, what the buttons do, what is never shown. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-xhdpi")
class PermissionScreensTest {
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

    private val first = LocationScreenModel(LocationButton.SYSTEM_DIALOG, steps = false, error = false)
    private val refused = first.copy(error = true)
    private val settings = LocationScreenModel(LocationButton.OPEN_SETTINGS, steps = true, error = false)

    @Test
    fun notifications_showTheTextOfThePhase0_oneRealExample_andTwoButtons_noPromiseAboutLovedOnes() {
        var allowed = 0
        var later = 0
        show { NotificationsPopIn(onAllow = { allowed++ }, onLater = { later++ }) }
        shoot("notif-1a-prime")
        composeRule.onNodeWithText(text(R.string.notif_prime_title)).assertIsDisplayed()
        // The example is the reminder the person would really get (N1, in the words of the PM), not a promise about a loved one.
        composeRule.onNodeWithText(text(R.string.notif_n1_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_n1_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_prime_body)).assertIsDisplayed()
        // The old sentences ("n'est toujours pas rentrée", the vouvoiement, the examples about Thomas and Amandine) are nowhere.
        composeRule.onAllNodesWithText("toujours pas", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Vous ", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Thomas", substring = true).assertCountEquals(0)
        composeRule
            .onNodeWithText(
                text(R.string.notif_prime_later),
            ).assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, later)
        assertEquals("\"Plus tard\" does not launch the system window", 0, allowed)
        composeRule.onNodeWithText(text(R.string.notif_prime_allow)).performClick()
        assertEquals(1, allowed)
    }

    @Test
    fun location_first_hasTheDisclosureBeforeTheButton_noMessage_noWayOut_whenBlocking() {
        var actions = 0
        show { LocationPopIn(first, onAction = { actions++ }, onPutOff = null, onSignOut = null) }
        shoot("loc2a-11-location-first")
        composeRule.onNodeWithText(text(R.string.permission_location_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.permission_location_disclosure)).assertIsDisplayed()
        val disclosure =
            composeRule
                .onNodeWithText(
                    text(R.string.permission_location_disclosure),
                ).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.permission_location_action)).getUnclippedBoundsInRoot()
        assertTrue("the disclosure comes BEFORE the button", disclosure.bottom <= button.top)
        composeRule.onAllNodesWithText(text(R.string.permission_location_error)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.permission_location_later)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.permission_location_action)).performClick()
        assertEquals(1, actions)
    }

    @Test
    fun location_afterATry_showsTheRedMessageOfTheTicket_andTheCalmLine() {
        show { LocationPopIn(refused, onAction = {}, onPutOff = null, onSignOut = null) }
        shoot("loc2a-12-location-error")
        composeRule.onNodeWithText(text(R.string.permission_location_error)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.permission_location_error_calm)).assertIsDisplayed()
        assertEquals(
            "Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner",
            text(R.string.permission_location_error),
        )
    }

    @Test
    fun location_settingsStep_hasThreeSteps_andTheButtonOfTheSettings() {
        show { LocationPopIn(settings, onAction = {}, onPutOff = null, onSignOut = null) }
        shoot("loc2a-13a-location-settings")
        composeRule.onNodeWithText(text(R.string.permission_location_steps_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.permission_location_step_1)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.permission_location_step_3)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.permission_location_action_settings)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.permission_location_error)).assertCountEquals(0)
    }

    @Test
    fun location_systemScreenStep_keepsTheDisclosure_andSaysWhatToChooseInTheScreenOfTheSystem() {
        var actions = 0
        val system =
            LocationScreenModel(
                LocationButton.SYSTEM_BACKGROUND_SCREEN,
                steps = true,
                error = false,
                systemScreen = true,
            )
        show { LocationPopIn(system, onAction = { actions++ }, onPutOff = null, onSignOut = null) }
        shoot("perm-lieu-13c-location-system-screen")
        // The disclosure stays, before the button (Google Play requirement): the request is still preceded by it.
        composeRule.onNodeWithText(text(R.string.permission_location_disclosure)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.android_permission_location_system_step_1)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.android_permission_location_system_step_2)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.android_permission_location_system_step_3)).assertIsDisplayed()
        // The steps of the settings of the app ("Choisis Autorisations, puis Position") are not shown: the system opens the right screen itself.
        composeRule.onAllNodesWithText(text(R.string.permission_location_step_2)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.permission_location_error)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.permission_location_action_settings)).performClick()
        assertEquals(1, actions)
    }

    @Test
    fun location_settingsStep_afterAWrongChoice_showsTheMessage() {
        show { LocationPopIn(settings.copy(error = true), onAction = {}, onPutOff = null, onSignOut = null) }
        shoot("loc2a-13b-location-settings-error")
        composeRule.onNodeWithText(text(R.string.permission_location_error)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.permission_location_steps_error_calm)).assertIsDisplayed()
    }

    @Test
    fun location_relaxedBuild_hasALaterLink_andTheSignOutLinkIsThere() {
        var later = 0
        var out = 0
        show { LocationPopIn(first, onAction = {}, onPutOff = { later++ }, onSignOut = { out++ }) }
        composeRule.onNodeWithText(text(R.string.permission_location_later)).performClick()
        composeRule.onNodeWithText(text(R.string.auth_signout_action)).performClick()
        assertEquals(1, later)
        assertEquals(1, out)
    }

    @Test
    fun renewal_oneMiff_namesIt_hasTwoButtonsOfEqualReach_andNotNowWritesNothing() {
        var accepted = 0
        var later = 0
        show {
            RenewalScreen(
                RenewalUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille Mfouka"))),
                onAccept = { accepted++ },
                onLater = { later++ },
            )
        }
        shoot("loc2a-21-renewal")
        composeRule.onNodeWithText(text(R.string.miff_renewal_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                ApplicationProvider.getApplicationContext<Context>().getString(
                    R.string.miff_renewal_intro_one,
                    "Famille Mfouka",
                ),
            ).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_renewal_accept)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_renewal_later)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_consent3_places_body)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_renewal_later)).performClick()
        assertEquals(1, later)
        assertEquals(0, accepted)
        composeRule.onNodeWithText(text(R.string.miff_renewal_accept)).performClick()
        assertEquals(1, accepted)
    }

    @Test
    fun renewal_aMemberWithOneMiff_hasTheNoteAndTheLeaveLink_askingFirst() {
        var asked = 0
        var confirmed = 0
        show {
            RenewalScreen(
                RenewalUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille Mfouka")), leaveAsked = true),
                onAccept = {},
                onLater = {},
                onLeave = { asked++ },
                onLeaveConfirmed = { confirmed++ },
            )
        }
        composeRule.onNodeWithText(text(R.string.miff_renewal_exit_note)).performScrollTo().assertIsDisplayed()
        // The link and the confirm button of the question carry the same words: the link is in the screen, the button in the open question.
        composeRule.onAllNodesWithText(text(R.string.miff_leave_link)).assertCountEquals(2)
        composeRule.onAllNodesWithText(text(R.string.miff_leave_action)).onLast().performClick()
        assertEquals(1, confirmed)
        assertEquals(0, asked)
    }

    @Test
    fun renewal_theCreator_hasNoLeaveLink() {
        show {
            RenewalScreen(
                RenewalUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille Mfouka", isOwner = true))),
                onAccept = {},
                onLater = {},
            )
        }
        composeRule.onAllNodesWithText(text(R.string.miff_leave_link)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.miff_renewal_exit_note)).assertCountEquals(0)
    }

    @Test
    fun location_settingsStep_repeatsTheFullDisclosure_forAPersonWhoseFirstScreenItIs() {
        show { LocationPopIn(settings, onAction = {}, onPutOff = null, onSignOut = null) }
        composeRule.onNodeWithText(text(R.string.permission_location_disclosure)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.permission_location_seen_body)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.permission_location_control_body)).performScrollTo().assertExists()
    }

    @Test
    fun renewal_severalMiffs_listsThem_busyDisablesBoth_errorIsCalmAndSaysNothingIsShared() {
        show {
            RenewalScreen(
                RenewalUiState(
                    listOf(
                        RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille Mfouka"),
                        RenewalMiff("ZyXwVuTsRqPoNmLkJiHg", "Amis du lundi"),
                    ),
                    busy = true,
                ),
                onAccept = {},
                onLater = {},
            )
        }
        composeRule.onNodeWithText("Amis du lundi").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_renewal_intro_many)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_renewal_saving)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.miff_renewal_later)).assertIsNotEnabled()
    }

    @Test
    fun renewal_failure_keepsTheScreen_andSaysNothingIsSharedForNow() {
        show {
            RenewalScreen(
                RenewalUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "")), message = ActionMessage.GENERIC),
                onAccept = {},
                onLater = {},
            )
        }
        composeRule.onNodeWithText(text(R.string.miff_renewal_intro_unnamed)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_message_generic)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_renewal_error_extra)).assertIsDisplayed()
    }

    @Test
    fun theVersionShownIsTheOneWritten() {
        // The text of the screens is the version 3; the constant the writes carry is the same one (criterion K8).
        assertEquals("2026-11-v03", MiffSharingConsent.VERSION)
    }
}
