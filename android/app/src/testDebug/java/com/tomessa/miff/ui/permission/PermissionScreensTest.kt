package com.tomessa.miff.ui.permission

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
import androidx.compose.ui.unit.Density
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
    fun location_theDisclosure_hasTheLocalLine_beforeTheButton_andStillTheGooglePhrase() {
        show { LocationPopIn(first, onAction = {}, onPutOff = null, onSignOut = null) }
        // CONS-4-A: "Pour toi seul(e)" is on the disclosure, after "Ce que voient tes proches" and before the button; the phrase Google requires is unchanged.
        composeRule.onNodeWithText(text(R.string.permission_location_disclosure)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.permission_location_local_title)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.permission_location_local_body)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.permission_location_action)).assertIsDisplayed()
    }

    @Test
    fun location_theDisclosure_withTheLargestFont_keepsTheButtonReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                LocationPopIn(first, onAction = {}, onPutOff = null, onSignOut = null)
            }
        }
        composeRule.onNodeWithText(text(R.string.permission_location_local_body)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.permission_location_action)).assertIsDisplayed().assertHasClickAction()
    }

    // --- The calm card for the accounts under the version 3 text (CONS-4-A) ---------------------------------------------------

    @Test
    fun clarify_oneMiff_namesIt_saysEverythingContinues_hasTwoButtons_andLaterWritesNothing() {
        var accepted = 0
        var later = 0
        show {
            ClarifyScreen(
                ClarifyUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille Mfouka"))),
                onAccept = { accepted++ },
                onLater = { later++ },
            )
        }
        shoot("cons4a-clarify")
        composeRule.onNodeWithText(text(R.string.miff_clarify_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                ApplicationProvider.getApplicationContext<Context>().getString(
                    R.string.miff_clarify_body_one,
                    "Famille Mfouka",
                ),
            ).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_clarify_continue)).assertIsDisplayed()
        // Nothing here speaks of a pause, of waiting or of a missing agreement: nothing is paused.
        composeRule.onAllNodesWithText(text(R.string.miff_waiting_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.miff_renewal_error_extra)).assertCountEquals(0)
        composeRule
            .onNodeWithText(text(R.string.miff_clarify_later))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, later)
        assertEquals(0, accepted)
        composeRule.onNodeWithText(text(R.string.miff_clarify_accept)).performClick()
        assertEquals(1, accepted)
    }

    @Test
    fun clarify_unnamed_usesItsOwnSentence_andBusyDisablesBothButtons() {
        show {
            ClarifyScreen(
                ClarifyUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "")), busy = true),
                onAccept = {},
                onLater = {},
            )
        }
        composeRule.onNodeWithText(text(R.string.miff_clarify_body_unnamed)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_clarify_saving)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.miff_clarify_later)).assertIsNotEnabled()
    }

    @Test
    fun clarify_severalMiffs_listsThem() {
        show {
            ClarifyScreen(
                ClarifyUiState(
                    listOf(
                        RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille Mfouka"),
                        RenewalMiff("ZyXwVuTsRqPoNmLkJiHg", "Amis du lundi"),
                    ),
                ),
                onAccept = {},
                onLater = {},
            )
        }
        composeRule.onNodeWithText("Famille Mfouka").assertIsDisplayed()
        composeRule.onNodeWithText("Amis du lundi").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_clarify_body_many)).assertIsDisplayed()
    }

    @Test
    fun clarify_failure_isCalm_andSaysEverythingContinues() {
        show {
            ClarifyScreen(
                ClarifyUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille")), message = ActionMessage.OFFLINE),
                onAccept = {},
                onLater = {},
            )
        }
        composeRule.onNodeWithText(text(R.string.miff_clarify_error_extra)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.miff_clarify_accept)).assertIsDisplayed()
    }

    @Test
    fun clarify_withTheLargestFont_theBodyScrolls_andBothButtonsStayReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                ClarifyScreen(
                    ClarifyUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille Mfouka"))),
                    onAccept = {},
                    onLater = {},
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.miff_clarify_accept)).assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText(text(R.string.miff_clarify_later)).assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText(text(R.string.miff_clarify_continue)).performScrollTo().assertExists()
    }

    @Test
    fun renewal_theBlockingScreen_carriesThePhotoAndLocalLines_ofTheVersion4() {
        show {
            RenewalScreen(
                RenewalUiState(listOf(RenewalMiff("AbCdEfGhIjKlMnOpQrSt", "Famille Mfouka"))),
                onAccept = {},
                onLater = {},
            )
        }
        composeRule.onNodeWithText(text(R.string.miff_consent3_photo_body)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.miff_consent3_local_body)).performScrollTo().assertExists()
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
        // The text of the screens is the version 4 (CONS-4-A); the constant the writes carry is the same one (criterion K8).
        assertEquals("2026-11-v04", MiffSharingConsent.VERSION)
    }
}
