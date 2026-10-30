package com.tomessa.miff.ui.profile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.onboarding.DraftStep
import com.tomessa.miff.data.profile.BitmapPhoto
import com.tomessa.miff.domain.profile.ProfileForm
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * Screens of the profile steps on the JVM (Robolectric, no phone): structure against the mockups "-1", "-2", "-6", the
 * mandatory mention of who sees the number, the arrow that stays grey, no way out on the first step, touch targets,
 * large font, right-to-left, TalkBack semantics. Writes screenshots to build/screenshots for the review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class ProfileScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun profileState(
        first: String = "",
        last: String = "",
        avatar: AvatarImage? = null,
        message: ProfileMessage? = null,
        firstLimit: Boolean = false,
    ) = ProfileUiState(
        mode = OnboardingMode.FULL,
        step = DraftStep.PROFILE,
        firstName = first,
        lastName = last,
        avatar = avatar,
        message = message,
        firstNameLimitHit = firstLimit,
    )

    private fun phoneState(
        phone: String = "",
        region: String = "FR",
        invalid: Boolean = false,
        writing: Boolean = false,
        message: ProfileMessage? = null,
        mode: OnboardingMode = OnboardingMode.FULL,
    ) = ProfileUiState(
        mode = mode,
        step = DraftStep.PHONE,
        regionCode = region,
        phoneText = phone,
        phoneInvalid = invalid,
        isWriting = writing,
        message = message,
    )

    private fun avatarBytes(): AvatarImage {
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 220, 170))
        canvas.drawCircle(80f, 66f, 34f, Paint().apply { color = Color.rgb(120, 80, 50) })
        canvas.drawCircle(80f, 190f, 80f, Paint().apply { color = Color.rgb(40, 90, 200) })
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return AvatarImage(out.toByteArray())
    }

    /** A profile step that keeps its own state, like the real one does through the view model. */
    @Composable
    private fun StatefulProfile(onContinue: () -> Unit = {}) {
        var state by remember { mutableStateOf(profileState()) }
        ProfileStepScreen(
            state = state,
            onFirstNameChanged = {
                val limited = ProfileForm.limitInput(it)
                state = state.copy(firstName = limited.text, firstNameLimitHit = limited.wasCut)
            },
            onLastNameChanged = { state = state.copy(lastName = ProfileForm.limitInput(it).text) },
            onContinue = onContinue,
            onPhotoChosen = {},
            onRemovePhoto = {},
            onCameraUnavailable = {},
        )
    }

    private fun profileStep(
        state: ProfileUiState,
        onContinue: () -> Unit = {},
    ) = show {
        ProfileStepScreen(
            state = state,
            onFirstNameChanged = {},
            onLastNameChanged = {},
            onContinue = onContinue,
            onPhotoChosen = {},
            onRemovePhoto = {},
            onCameraUnavailable = {},
        )
    }

    private fun phoneStep(
        state: ProfileUiState,
        onBack: (() -> Unit)? = {},
        onSubmit: () -> Unit = {},
        onCountrySelected: (String) -> Unit = {},
    ) = show {
        PhoneStepScreen(
            state = state,
            onBack = onBack,
            onCountrySelected = onCountrySelected,
            onPhoneChanged = {},
            onSubmit = onSubmit,
        )
    }

    // --- Profile step (mockup -1) ------------------------------------------------------------------------------------------

    @Test
    fun profile_followsTheMockup_photoThenFirstNameThenLastName_andTheArrowIsGrey() {
        profileStep(profileState())
        shoot("profile-empty")
        val photo = composeRule.onNodeWithText(text(R.string.profile_photo_add)).getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithText(text(R.string.profile_first_name_label)).getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithText(text(R.string.profile_last_name_label)).getUnclippedBoundsInRoot()
        assertTrue("photo, then first name, then last name", photo.top < first.top && first.top < last.top)
        composeRule.onNodeWithText(text(R.string.profile_title)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).assertIsNotEnabled()
    }

    @Test
    fun profile_hasNoBackArrow_andNoWayOut_PR9_PR10() {
        profileStep(profileState())
        composeRule.onAllNodesWithContentDescription(text(R.string.auth_back_a11y)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.auth_signout_action)).assertCountEquals(0)
        // The wordmark stays, as in the mockup.
        composeRule.onNodeWithContentDescription(text(R.string.home_logo_a11y)).assertExists()
    }

    @Test
    fun profile_thePhotoIsOptional_andSaysWhoSeesIt_PR3_PR6() {
        profileStep(profileState())
        composeRule.onNodeWithText("Add a photo (optional)").assertExists()
        composeRule.onNodeWithText("Visible to the members of your Miffs").assertExists()
    }

    @Test
    fun profile_withAPhoto_theCaptionOffersToChangeIt_andThePhotoIsDescribed() {
        profileStep(profileState(first = "Léa", last = "Martin", avatar = avatarBytes()))
        shoot("profile-with-photo")
        composeRule.onNodeWithText("Change photo").assertExists()
        composeRule.onNodeWithText("Visible to the members of your Miffs").assertExists()
        composeRule.onAllNodesWithText("Add a photo (optional)").assertCountEquals(0)
    }

    @Test
    fun profile_theArrowBecomesActiveWhenBothNamesAreFilled_andCanBePressed() {
        var continued = 0
        show { StatefulProfile(onContinue = { continued++ }) }
        composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).assertIsNotEnabled()
        composeRule
            .onNode(
                hasSetTextAction() and hasText(text(R.string.profile_first_name_label)),
            ).performTextInput("Léa")
        composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).assertIsNotEnabled()
        composeRule
            .onNode(
                hasSetTextAction() and hasText(text(R.string.profile_last_name_label)),
            ).performTextInput("Martin")
        shoot("profile-filled")
        composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).assertIsEnabled().performClick()
        assertEquals(1, continued)
    }

    @Test
    fun profile_tooLongName_saysItCalmly_inWords_andTheCounterAppearsNearTheLimit() {
        show { StatefulProfile() }
        composeRule
            .onNode(
                hasSetTextAction() and hasText(text(R.string.profile_first_name_label)),
            ).performTextInput("a".repeat(46))
        composeRule.onNodeWithText("46/50").assertExists()
        composeRule
            .onNode(
                hasSetTextAction() and hasText(text(R.string.profile_first_name_label)),
            ).performTextInput("a".repeat(10))
        composeRule.onNodeWithText("50 characters maximum.").assertExists()
        shoot("profile-too-long")
    }

    @Test
    fun profile_fieldsAreLabelled_forTheScreenReader_andAnErrorIsAnnounced() {
        profileStep(profileState(first = "a".repeat(50), firstLimit = true))
        val node =
            composeRule
                .onNode(hasSetTextAction() and hasText(text(R.string.profile_first_name_label)))
                .fetchSemanticsNode()
        assertTrue("label and field are one element", node.config.contains(SemanticsProperties.EditableText))
        assertTrue(node.config[SemanticsProperties.Text].any { it.text == text(R.string.profile_first_name_label) })
        assertTrue("the error is attached to the field", node.config.contains(SemanticsProperties.Error))
        composeRule.onNodeWithText("50 characters maximum.").assertExists()
    }

    @Test
    fun profile_theScreenIsNamed_andTheTitleIsAHeading() {
        profileStep(profileState())
        val panes =
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
                .fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.PaneTitle] }
        assertTrue(panes.toString(), panes.contains(text(R.string.profile_title)))
        composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasText(text(R.string.profile_title)),
            ).assertCountEquals(1)
    }

    @Test
    fun profile_touchTargetsAreAtLeast48dp() {
        profileStep(profileState(first = "A", last = "B"))
        composeRule
            .onNodeWithContentDescription(
                text(R.string.profile_next_a11y),
            ).assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.profile_photo_add)).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        composeRule
            .onNode(hasSetTextAction() and hasText(text(R.string.profile_first_name_label)))
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun profile_photoMessagesAreCalm() {
        profileStep(profileState(message = ProfileMessage.PHOTO_UNUSABLE))
        composeRule.onNodeWithText("We can't use this image. Try another photo.").assertExists()
        shoot("profile-photo-unusable")
    }

    @Test
    fun profile_tappingThePhotoOffersCameraAndGallery_andRemovalOnlyWithAPhoto() {
        profileStep(profileState())
        composeRule.onNodeWithText(text(R.string.profile_photo_add)).performClick()
        composeRule.onNodeWithText(text(R.string.profile_photo_take)).assertExists()
        composeRule.onNodeWithText(text(R.string.profile_photo_choose)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.profile_photo_remove)).assertCountEquals(0)
    }

    @Test
    fun profile_withAPhoto_theSheetOffersToRemoveIt() {
        profileStep(profileState(avatar = avatarBytes()))
        composeRule.onNodeWithText(text(R.string.profile_photo_change)).performClick()
        composeRule.onNodeWithText(text(R.string.profile_photo_remove)).assertExists()
    }

    @Test
    fun profile_largestFont_everythingStaysReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                ProfileStepScreen(profileState(first = "A", last = "B"), {}, {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText(text(R.string.profile_last_name_label)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun profile_french_usesTheTutoiement() {
        profileStep(profileState())
        shoot("profile-fr")
        composeRule.onNodeWithText("Permets à tes proches de t'identifier").assertExists()
        composeRule.onNodeWithText("Ajouter une photo (facultatif)").assertExists()
        composeRule.onNodeWithText("Visible par les membres de tes Miffs").assertExists()
        composeRule.onNodeWithText("Prénom").assertExists()
        composeRule.onNodeWithText("Nom").assertExists()
    }

    // --- Phone step (mockups -2 and -6) ----------------------------------------------------------------------------------------------

    @Test
    fun phone_followsTheMockup_countryThenNumber_withTheExampleAndTheHelp() {
        phoneStep(phoneState())
        shoot("phone-empty")
        composeRule.onNodeWithText(text(R.string.profile_phone_title)).assertExists()
        composeRule.onNodeWithText("France +33").assertExists()
        // The "+33" prefix is visual only (hidden from the screen reader, the country field says the code).
        // The grey example ("6 12 34 56 78") is visual only: it is hidden from the screen reader (see the screenshot).
        composeRule.onNodeWithText("Enter your number without the leading zero.").assertExists()
        val country = composeRule.onNodeWithText("France +33").getUnclippedBoundsInRoot()
        val number = composeRule.onNodeWithText(text(R.string.profile_phone_number_label)).getUnclippedBoundsInRoot()
        assertTrue(country.top < number.top)
    }

    @Test
    fun phone_theMentionOfWhoSeesTheNumberIsAlwaysThere_TEL6() {
        phoneStep(phoneState())
        val mention = "Only the members of your Miffs will see this number, so they can call or text you. No one else."
        composeRule.onNodeWithText(mention).assertExists().assertIsDisplayed()
        val field = composeRule.onNodeWithText(text(R.string.profile_phone_number_label)).getUnclippedBoundsInRoot()
        val shown = composeRule.onNodeWithText(mention).getUnclippedBoundsInRoot()
        assertTrue("under the number field", shown.top >= field.bottom)
        // Still there when the number is wrong, and when there is a network message.
    }

    @Test
    fun phone_theMentionIsStillThereWithAnError() {
        phoneStep(phoneState(phone = "06 12", invalid = true))
        composeRule
            .onNodeWithText(
                "Only the members of your Miffs will see this number, so they can call or text you. No one else.",
            ).assertExists()
        composeRule.onNodeWithText("This number doesn't look right.").assertExists()
    }

    @Test
    fun phone_noPassOption_andTheArrowIsGreyUntilADigit_TEL1() {
        phoneStep(phoneState())
        composeRule.onAllNodesWithText("Skip").assertCountEquals(0)
        composeRule.onAllNodesWithText("Later").assertCountEquals(0)
        composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).assertIsNotEnabled()
    }

    @Test
    fun phone_theArrowWorksOnceANumberIsTyped() {
        var submitted = 0
        phoneStep(phoneState(phone = "0612345678"), onSubmit = { submitted++ })
        composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).assertIsEnabled().performClick()
        assertEquals(1, submitted)
    }

    @Test
    fun phone_anInvalidNumber_isCalm_inWords_andAnnounced_TEL3() {
        phoneStep(phoneState(phone = "07 83 30", invalid = true))
        shoot("phone-error")
        composeRule.onNodeWithText("This number doesn't look right.").assertExists()
        val node =
            composeRule
                .onNode(
                    hasSetTextAction() and hasText(text(R.string.profile_phone_number_label)),
                ).fetchSemanticsNode()
        assertTrue(node.config.contains(SemanticsProperties.Error))
        val message = composeRule.onNodeWithText("This number doesn't look right.").fetchSemanticsNode()
        assertTrue("live region", message.config.contains(SemanticsProperties.LiveRegion))
        val error = composeRule.onNodeWithText("This number doesn't look right.").getUnclippedBoundsInRoot()
        val help = composeRule.onNodeWithText("Enter your number without the leading zero.").getUnclippedBoundsInRoot()
        assertTrue("the error comes before the help, like the mockup", error.top < help.top)
    }

    @Test
    fun phone_theBackArrowIsThere_exceptWhenTheStepStandsAlone() {
        var back = 0
        phoneStep(phoneState(), onBack = { back++ })
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).assertHasClickAction().performClick()
        assertEquals(1, back)
    }

    @Test
    fun phone_alone_hasNoBackArrow_TEL8() {
        phoneStep(phoneState(mode = OnboardingMode.PHONE_ONLY), onBack = null)
        composeRule.onAllNodesWithContentDescription(text(R.string.auth_back_a11y)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.profile_phone_title)).assertExists()
    }

    @Test
    fun phone_noNetwork_isAKindMessage_andTheFieldsStay() {
        phoneStep(phoneState(phone = "0612345678", message = ProfileMessage.NETWORK))
        shoot("phone-offline")
        composeRule.onNodeWithText(text(R.string.auth_error_network)).assertExists()
        composeRule.onNodeWithText("0612345678").assertExists()
    }

    @Test
    fun phone_writing_showsAnAnnouncedSpinner_andLocksTheFields() {
        phoneStep(phoneState(phone = "0612345678", writing = true))
        composeRule.onNodeWithContentDescription(text(R.string.auth_loading_a11y)).assertExists()
        composeRule.onAllNodesWithContentDescription(text(R.string.profile_next_a11y)).assertCountEquals(0)
    }

    @Test
    fun phone_theCountryOfTheDeviceIsShown() {
        phoneStep(phoneState(region = "CG"))
        val congo =
            Locale
                .Builder()
                .setRegion("CG")
                .build()
                .getDisplayCountry(Locale.ENGLISH)
        composeRule.onNodeWithText("$congo +242").assertExists()
    }

    @Test
    fun phone_theCountrySelectorIsOneDropdownElement_withTheCodeSaidAloud() {
        phoneStep(phoneState())
        val node =
            composeRule
                .onNodeWithContentDescription(
                    "Country: France, calling code +33",
                    substring = true,
                ).fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.Role.DropdownList, node.config[SemanticsProperties.Role])
        assertTrue(node.config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun phone_theSelectorListsAllCountries_andSearchFindsOneIgnoringAccents() {
        var chosen: String? = null
        phoneStep(phoneState(), onCountrySelected = { chosen = it })
        composeRule.onNodeWithContentDescription("Country: France", substring = true).performClick()
        composeRule.onNodeWithText(text(R.string.profile_phone_picker_title)).assertExists()
        composeRule
            .onNode(
                hasSetTextAction() and hasText(text(R.string.profile_phone_picker_search)),
            ).performTextInput("belg")
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Belgium").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Belgium").performClick()
        composeRule.waitForIdle()
        assertEquals("BE", chosen)
    }

    @Test
    fun phone_theSearchSaysWhenNothingMatches() {
        phoneStep(phoneState())
        composeRule.onNodeWithContentDescription("Country: France", substring = true).performClick()
        composeRule
            .onNode(
                hasSetTextAction() and hasText(text(R.string.profile_phone_picker_search)),
            ).performTextInput("zzzzzz")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("No country matches.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun phone_largestFont_theArrowAndTheMentionStayReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                PhoneStepScreen(phoneState(phone = "06"), {}, {}, {}, {})
            }
        }
        composeRule
            .onNodeWithText(
                "Only the members of your Miffs will see this number, so they can call or text you. No one else.",
            ).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).assertIsDisplayed()
    }

    @Test
    fun phone_rightToLeft_theArrowStaysAtTheEnd_andTheBackArrowAtTheStart() {
        show {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PhoneStepScreen(phoneState(phone = "06"), {}, {}, {}, {})
            }
        }
        shoot("phone-rtl")
        val next = composeRule.onNodeWithContentDescription(text(R.string.profile_next_a11y)).getUnclippedBoundsInRoot()
        val back = composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).getUnclippedBoundsInRoot()
        assertTrue("in right-to-left the next arrow is at the left (the end)", next.left < 100.dp)
        assertTrue("and the back arrow at the right (the start)", back.left > 200.dp)
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun phone_french_usesTheTutoiement() {
        phoneStep(phoneState())
        shoot("phone-fr")
        composeRule.onNodeWithText("Permets à tes proches de te contacter").assertExists()
        composeRule.onNodeWithContentDescription("Pays : France, indicatif +33", substring = true).assertExists()
        composeRule.onNodeWithText("France +33").assertExists()
        composeRule.onNodeWithText("Numéro de téléphone").assertExists()
        composeRule.onNodeWithText("Saisis ton numéro sans le premier zéro.").assertExists()
        composeRule
            .onNodeWithText(
                "Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre.",
            ).assertExists()
    }

    // --- Crop ----------------------------------------------------------------------------------------------------------------------------

    private fun cropState(zoom: Float = 1f) =
        CropState(
            photo =
                BitmapPhoto(
                    Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).also {
                        val canvas = Canvas(it)
                        canvas.drawColor(Color.rgb(200, 230, 255))
                        canvas.drawCircle(400f, 260f, 120f, Paint().apply { color = Color.rgb(120, 80, 50) })
                        canvas.drawRect(200f, 400f, 600f, 600f, Paint().apply { color = Color.rgb(40, 90, 200) })
                    },
                ),
            zoom = zoom,
            centerX = 400f,
            centerY = 300f,
        )

    @Test
    fun crop_showsTheSquare_theHint_aZoomSlider_andTwoButtons() {
        var confirmed = 0
        var cancelled = 0
        show { PhotoCropScreen(cropState(), { _, _, _ -> }, { confirmed++ }, { cancelled++ }) }
        shoot("crop")
        composeRule.onNodeWithText(text(R.string.profile_crop_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.profile_crop_hint)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.profile_crop_zoom_a11y)).assertExists()
        composeRule.onNodeWithText(text(R.string.profile_crop_confirm)).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText(text(R.string.profile_crop_cancel)).performClick()
        assertEquals(1, confirmed)
        assertEquals(1, cancelled)
    }

    @Test
    fun crop_theSquareCanBeMovedWithoutTouchGestures_forTheScreenReader() {
        val changes = mutableListOf<Triple<Float, Float, Float>>()
        show { PhotoCropScreen(cropState(zoom = 2f), { z, x, y -> changes += Triple(z, x, y) }, {}, {}) }
        val area = composeRule.onNodeWithContentDescription(text(R.string.profile_crop_area_a11y))
        val labels = area.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(
            listOf("Move photo left", "Move photo right", "Move photo up", "Move photo down"),
            labels,
        )

        fun invoke(label: String) {
            val action = area.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
            composeRule.runOnUiThread { action.action() }
            composeRule.waitForIdle()
        }
        invoke("Move photo left")
        invoke("Move photo down")
        assertTrue("left lowers x: ${changes[0]}", changes[0].second < 400f && changes[0].third == 300f)
        assertTrue("down raises y: ${changes[1]}", changes[1].third > 300f && changes[1].second == 400f)
        assertEquals("zoom unchanged by a move", 2f, changes[0].first, 0f)
    }

    @Test
    fun crop_whileEncoding_theButtonsAreLocked_andASpinnerIsAnnounced() {
        show { PhotoCropScreen(cropState().copy(isEncoding = true), { _, _, _ -> }, {}, {}) }
        composeRule.onNodeWithText(text(R.string.profile_crop_confirm)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.profile_crop_cancel)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(text(R.string.auth_loading_a11y)).assertExists()
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun crop_french() {
        show { PhotoCropScreen(cropState(), { _, _, _ -> }, {}, {}) }
        composeRule.onNodeWithText("Cadre ta photo").assertExists()
        composeRule.onNodeWithText("Utiliser cette photo").assertExists()
        composeRule.onNodeWithText("Annuler").assertExists()
    }

    @Test
    fun theFrenchCountryNamesAreFrench() {
        // Sanity check of the language used by the selector on a French phone.
        assertEquals(
            "Allemagne",
            Locale
                .Builder()
                .setRegion("DE")
                .build()
                .getDisplayCountry(Locale.FRENCH),
        )
    }
}
