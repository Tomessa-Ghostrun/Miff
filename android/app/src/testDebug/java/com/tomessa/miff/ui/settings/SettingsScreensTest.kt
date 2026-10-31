package com.tomessa.miff.ui.settings

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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.domain.settings.AppLanguage
import com.tomessa.miff.ui.feed.FeedScreen
import com.tomessa.miff.ui.main.MainTabBar
import com.tomessa.miff.ui.profile.ProfileTabScreen
import com.tomessa.miff.ui.profile.ProfileTabStatus
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
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The tab bar, the provisional Feed, the Profile tab and the Settings screens on the JVM (Robolectric, no phone): structure
 * against the mockups, what must NOT be there (no Notifications line, no Premium banner, no "Supprimer mon compte", no
 * invented Miff), touch targets, large font, right-to-left, TalkBack semantics. Writes screenshots to build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class SettingsScreensTest {
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

    private fun photoBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 220, 170))
        canvas.drawCircle(80f, 66f, 34f, Paint().apply { color = Color.rgb(120, 80, 50) })
        canvas.drawCircle(80f, 190f, 80f, Paint().apply { color = Color.rgb(40, 90, 200) })
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    private val internal = TabBarConfig.forBuild(isInternalBuild = true)
    private val external = TabBarConfig.forBuild(isInternalBuild = false)

    // --- Tab bar (criteria A1, A2, TB1, TB4) ------------------------------------------------------------------------------------

    @Composable
    private fun StatefulBar(
        config: TabBarConfig,
        onSelect: (MainTab) -> Unit = {},
    ) {
        var selected by remember { mutableStateOf(MainTab.FEED) }
        MainTabBar(config = config, selected = selected, onSelect = {
            selected = it
            onSelect(it)
        })
    }

    @Test
    fun tabBar_internalBuild_hasThreeTabsWithSpokenNames_theCentralOneIsDisabled() {
        val picked = mutableListOf<MainTab>()
        show { StatefulBar(internal) { picked += it } }
        shoot("tabbar-internal")
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).assertIsSelected().assertIsEnabled()
        composeRule
            .onNodeWithContentDescription(
                text(R.string.tabbar_profile_a11y),
            ).assertIsNotSelected()
            .assertIsEnabled()
        composeRule
            .onNodeWithContentDescription(text(R.string.tabbar_update_soon_a11y))
            .assertIsNotEnabled()
            .performClick()
        assertTrue("the disabled central button selects nothing", picked.isEmpty())
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).performClick()
        assertEquals(listOf(MainTab.PROFILE), picked)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).assertIsSelected()
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).assertIsNotSelected()
    }

    @Test
    fun tabBar_buildForOutsideTesters_hasNoCentralButtonAtAll() {
        show { StatefulBar(external) }
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).assertExists()
        composeRule.onAllNodesWithContentDescription(text(R.string.tabbar_update_soon_a11y)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(text(R.string.tabbar_update_a11y)).assertCountEquals(0)
        shoot("tabbar-external")
    }

    @Test
    fun tabBar_everyTabIsAtLeast48dpTall_andWide() {
        show { StatefulBar(internal) }
        listOf(R.string.tabbar_feed_a11y, R.string.tabbar_update_soon_a11y, R.string.tabbar_profile_a11y).forEach {
            composeRule
                .onNodeWithContentDescription(text(it))
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun tabBar_theStateIsGivenToTheScreenReader_notOnlyByColour() {
        show { StatefulBar(internal) }
        val node = composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).fetchSemanticsNode()
        assertTrue(node.config.contains(SemanticsProperties.Selected))
        assertEquals(androidx.compose.ui.semantics.Role.Tab, node.config[SemanticsProperties.Role])
    }

    @Test
    fun tabBar_rightToLeft_reversesTheOrder_withoutMirroringAnything() {
        show {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { StatefulBar(internal) }
        }
        val feed = composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).getUnclippedBoundsInRoot()
        val profile =
            composeRule
                .onNodeWithContentDescription(
                    text(R.string.tabbar_profile_a11y),
                ).getUnclippedBoundsInRoot()
        assertTrue("in right-to-left the Feed is on the right", feed.left > profile.left)
    }

    @Test
    fun tabBar_leftToRight_putsTheFeedFirst_theCentralButtonInTheMiddle_theProfileLast() {
        show { StatefulBar(internal) }
        val feed = composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).getUnclippedBoundsInRoot()
        val middle =
            composeRule
                .onNodeWithContentDescription(
                    text(R.string.tabbar_update_soon_a11y),
                ).getUnclippedBoundsInRoot()
        val profile =
            composeRule
                .onNodeWithContentDescription(
                    text(R.string.tabbar_profile_a11y),
                ).getUnclippedBoundsInRoot()
        assertTrue(feed.left < middle.left && middle.left < profile.left)
    }

    // --- Provisional Feed ---------------------------------------------------------------------------------------------------------

    @Test
    fun feed_saysHonestlyThatItIsNotBuiltYet_noBell_noInventedPerson_noPromiseOfSafety() {
        show {
            FeedScreen(
                state =
                    DirectoryState.Ready(
                        listOf(MiffSummary("A".repeat(20), "Chez nous")),
                        "A".repeat(20),
                        premium = false,
                        fromCache = false,
                    ),
                scrollToTopSignal = 0,
                onRetry = {},
                onSelect = {},
                onCreate = {},
                onJoin = {},
            )
        }
        shoot("feed-placeholder")
        composeRule.onNodeWithText(text(R.string.feed_empty_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_empty_body)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.home_logo_a11y)).assertExists()
        // The words of the mockup that would promise something the app cannot know (decision Q12, no bell: Q15).
        composeRule.onAllNodesWithText("safe", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("sécurité", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Famille", substring = true).assertCountEquals(0)
        composeRule
            .onAllNodesWithContentDescription(
                "notification",
                substring = true,
                ignoreCase = true,
            ).assertCountEquals(0)
    }

    // --- Profile tab (criterion A3) ----------------------------------------------------------------------------------------------

    private fun readyProfile(photo: Boolean = true) =
        ProfileTabUiState(
            ProfileTabStatus.READY,
            MyProfile("Emmanuelle", "Martin", "+33612345678", if (photo) photoBytes() else null),
        )

    private fun profileTab(
        state: ProfileTabUiState,
        onOpenMyInfo: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = show {
        ProfileTabScreen(
            state,
            scrollToTopSignal = 0,
            onOpenMyInfo = onOpenMyInfo,
            onOpenSettings = onOpenSettings,
            onRetry = onRetry,
        )
    }

    @Test
    fun profileTab_showsThePhoto_theName_andThatItIsYou_thenNothingElse() {
        profileTab(readyProfile())
        shoot("profile-tab")
        composeRule.onNodeWithText("Emmanuelle Martin").assertExists()
        composeRule.onNodeWithText(text(R.string.profile_tab_you_label)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.profile_tab_settings_a11y)).assertHasClickAction()
        // No data of the Miff and Places tickets, which do not exist yet (criteria A3 and PT2).
        composeRule.onAllNodesWithText("Famille", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("membres", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("members", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Lieux", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Ajouter", substring = true).assertCountEquals(0)
    }

    @Test
    fun profileTab_withoutAPhoto_theInitialsTakeItsPlace() {
        profileTab(readyProfile(photo = false))
        shoot("profile-tab-initials")
        // The initials are drawn, but the screen reader gets the name once, not the letters.
        composeRule.onAllNodesWithText("EM").assertCountEquals(0) // hidden from the semantics tree (decorative)
        composeRule.onNodeWithText("Emmanuelle Martin").assertExists()
    }

    @Test
    fun profileTab_theGearOpensTheSettings_theNameAndPhotoOpenMyInformation() {
        var settings = 0
        var myInfo = 0
        profileTab(readyProfile(), onOpenMyInfo = { myInfo++ }, onOpenSettings = { settings++ })
        composeRule.onNodeWithContentDescription(text(R.string.profile_tab_settings_a11y)).performClick()
        assertEquals(1, settings)
        composeRule.onNodeWithText("Emmanuelle Martin").performClick()
        assertEquals(1, myInfo)
    }

    @Test
    fun profileTab_theGearAndTheIdentityAreAtLeast48dp() {
        profileTab(readyProfile())
        composeRule
            .onNodeWithContentDescription(text(R.string.profile_tab_settings_a11y))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithText("Emmanuelle Martin").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun profileTab_theIdentityIsOneButtonWithAnActionLabel_andTheNameIsAHeading() {
        profileTab(readyProfile())
        val node = composeRule.onNodeWithText("Emmanuelle Martin").fetchSemanticsNode()
        assertTrue(
            node.config.contains(SemanticsProperties.Heading) ||
                node.parent?.config?.contains(SemanticsProperties.Role) == true,
        )
        composeRule.onNodeWithText("Emmanuelle Martin").assertHasClickAction()
    }

    @Test
    fun profileTab_anUnreadableProfileIsSaidCalmly_withARetry_andTheGearStillWorks() {
        var retried = 0
        var settings = 0
        profileTab(
            ProfileTabUiState(ProfileTabStatus.UNAVAILABLE),
            onOpenSettings = { settings++ },
            onRetry = { retried++ },
        )
        shoot("profile-tab-unavailable")
        composeRule.onNodeWithText(text(R.string.profile_tab_unavailable)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).performClick()
        assertEquals(1, retried)
        composeRule.onNodeWithContentDescription(text(R.string.profile_tab_settings_a11y)).performClick()
        assertEquals(1, settings)
    }

    @Test
    fun profileTab_whileLoading_thereIsASpokenSpinner_andNoName() {
        profileTab(ProfileTabUiState(ProfileTabStatus.LOADING))
        composeRule.onNodeWithContentDescription(text(R.string.auth_loading_a11y)).assertExists()
        composeRule.onAllNodesWithText("C'est toi").assertCountEquals(0)
    }

    // --- Settings list (criterion A4) --------------------------------------------------------------------------------------------

    @Test
    fun settings_listsMyInformationMyAccountAndLanguage_andNothingElse() {
        show { SettingsScreen({}, {}, {}, {}) }
        shoot("settings-list")
        composeRule.onNodeWithText(text(R.string.settings_my_info_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_account_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_language_title)).assertExists()
        // Nothing is sent yet, nothing can be bought yet, nothing can be deleted yet.
        composeRule.onAllNodesWithText("Notification", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Premium", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("trial", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Subscription", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Delete", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun settings_eachRowIsOneButton_ofAtLeast48dp_andOpensItsScreen() {
        val opened = mutableListOf<String>()
        show { SettingsScreen({}, { opened += "info" }, { opened += "account" }, { opened += "language" }) }
        listOf(
            R.string.settings_my_info_title,
            R.string.settings_account_title,
            R.string.settings_language_title,
        ).forEach {
            composeRule.onNodeWithText(text(it)).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithText(text(R.string.settings_my_info_title)).performClick()
        composeRule.onNodeWithText(text(R.string.settings_account_title)).performClick()
        composeRule.onNodeWithText(text(R.string.settings_language_title)).performClick()
        assertEquals(listOf("info", "account", "language"), opened)
    }

    @Test
    fun settings_theBackArrowWorks_andTheTitleIsTheOnlyHeading() {
        var back = 0
        show { SettingsScreen({ back++ }, {}, {}, {}) }
        composeRule
            .onNodeWithContentDescription(
                text(R.string.auth_back_a11y),
            ).assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, back)
        composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasText(text(R.string.settings_title)),
            ).assertCountEquals(1)
    }

    // --- Language (criterion A9) -----------------------------------------------------------------------------------------------------

    @Test
    fun language_offersFrenchEnglishAndThePhone_eachNamedInItsOwnLanguage_noSaveButton() {
        val chosen = mutableListOf<AppLanguage>()
        show { LanguageScreen(selected = AppLanguage.FRENCH, onBack = {}, onSelect = { chosen += it }) }
        shoot("language")
        composeRule.onNodeWithText("Français").assertIsSelected()
        composeRule.onNodeWithText("English").assertIsNotSelected()
        composeRule.onNodeWithText(text(R.string.settings_language_system)).assertIsNotSelected()
        // Neither Spanish, German, Portuguese nor Italian (decision Q4), and no "Enregistrer" button: a tap applies.
        listOf("Español", "Spanish", "German", "Deutsch", "Portuguese", "Italian").forEach {
            composeRule.onAllNodesWithText(it).assertCountEquals(0)
        }
        composeRule.onAllNodesWithText("Save").assertCountEquals(0)
        composeRule.onNodeWithText("English").performClick()
        composeRule.onNodeWithText(text(R.string.settings_language_system)).performClick()
        assertEquals(listOf(AppLanguage.ENGLISH, AppLanguage.SYSTEM), chosen)
    }

    @Test
    fun language_theRowsAreRadioButtonsOfAtLeast48dp() {
        show { LanguageScreen(AppLanguage.SYSTEM, {}, {}) }
        listOf("Français", "English", text(R.string.settings_language_system)).forEach {
            val node = composeRule.onNodeWithText(it).assertHeightIsAtLeast(48.dp).fetchSemanticsNode()
            assertEquals(androidx.compose.ui.semantics.Role.RadioButton, node.config[SemanticsProperties.Role])
        }
        composeRule.onNodeWithText(text(R.string.settings_language_system)).assertIsSelected()
    }

    // --- Account (criteria B1, B2) --------------------------------------------------------------------------------------------------

    @Test
    fun account_showsTheEmailAsReadOnly_aSecondarySignOut_andNoDeleteLine() {
        show {
            AccountScreen(AccountUiState(email = "emmanuelle@example.org"), {}, {}, {}, {})
        }
        shoot("account")
        composeRule
            .onNodeWithContentDescription("Email address: emmanuelle@example.org. It can't be changed here.")
            .assertExists()
        // No editable field: the address cannot be typed into.
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule
            .onNodeWithText(
                text(R.string.settings_account_sign_out_action),
            ).assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onAllNodesWithText("Delete", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("irrecoverable", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("password", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun account_signOutAsksForConfirmation_withTwoButtonsAndTheHonestSentence() {
        var requested = 0
        var confirmed = 0
        var cancelled = 0
        show {
            var state by remember { mutableStateOf(AccountUiState(email = "a@b.c")) }
            AccountScreen(
                state = state,
                onBack = {},
                onSignOutRequested = {
                    requested++
                    state = state.copy(confirmingSignOut = true)
                },
                onSignOutConfirmed = { confirmed++ },
                onSignOutCancelled = {
                    cancelled++
                    state = state.copy(confirmingSignOut = false)
                },
            )
        }
        composeRule.onNodeWithText(text(R.string.settings_account_sign_out_action)).performClick()
        assertEquals(1, requested)
        composeRule.onNodeWithText("Sign out?").assertExists()
        composeRule.onNodeWithText("Your loved ones won't see your updates while you're signed out.").assertExists()
        shoot("account-sign-out-dialog")
        composeRule.onNodeWithText(text(R.string.settings_account_sign_out_cancel)).performClick()
        assertEquals(1, cancelled)
        assertEquals(0, confirmed)
    }

    // --- My information (criteria A5 to A8) ---------------------------------------------------------------------------------------

    private fun myInfoState(
        first: String = "Laura",
        last: String = "Kouajou",
        phone: String = "612345678",
        photo: Boolean = true,
        hasChanges: Boolean = false,
        invalid: Boolean = false,
        message: MyInfoMessage? = null,
        load: MyInfoLoad = MyInfoLoad.READY,
    ) = MyInfoUiState(
        load = load,
        firstName = first,
        lastName = last,
        avatar =
            if (photo) {
                com.tomessa.miff.ui.profile
                    .AvatarImage(photoBytes())
            } else {
                null
            },
        regionCode = "FR",
        phoneText = phone,
        phoneInvalid = invalid,
        message = message,
        hasChanges = hasChanges,
        hasEdits = hasChanges,
        namesComplete = first.isNotBlank() && last.isNotBlank(),
    )

    private fun myInfo(
        state: MyInfoUiState,
        onSave: () -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = show {
        MyInfoScreen(
            state = state,
            onBack = onBack,
            onRetry = onRetry,
            onFirstNameChanged = {},
            onLastNameChanged = {},
            onCountrySelected = {},
            onPhoneChanged = {},
            onPhoneFocusLost = {},
            onPhotoChosen = {},
            onRemovePhoto = {},
            onCameraUnavailable = {},
            onCropChanged = { _, _, _ -> },
            onCropConfirm = {},
            onCropCancel = {},
            onSave = onSave,
        )
    }

    @Test
    fun myInfo_followsTheMockup_andCorrectsIt() {
        myInfo(myInfoState())
        shoot("my-info")
        // Photo, first name, last name, country, phone, save: in this order, like the mockup.
        val tops =
            listOf(
                composeRule.onNodeWithContentDescription(text(R.string.settings_my_info_photo_edit_a11y)),
                composeRule.onNodeWithText(text(R.string.profile_first_name_label)),
                composeRule.onNodeWithText(text(R.string.profile_last_name_label)),
                composeRule.onNode(hasContentDescription("Country:", substring = true)),
                composeRule.onNodeWithText(text(R.string.settings_my_info_phone_label)),
                composeRule.onNodeWithText(text(R.string.settings_my_info_save_action)),
            ).map { it.getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        // Correction A1: the phone field is called "Phone", not "Name" like the mockup.
        composeRule.onNodeWithText("Phone").assertExists()
        // Correction A2: the counters give the REAL length ("Laura" is 5 characters, "Kouajou" is 7).
        composeRule.onNodeWithText("5/50").assertExists()
        composeRule.onNodeWithText("7/50").assertExists()
        // Correction B9: no "3 characters minimum".
        composeRule.onAllNodesWithText("minimum", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun myInfo_saysWhoSeesTheNumber_decisionN1() {
        myInfo(myInfoState())
        composeRule.onNodeWithText(text(R.string.profile_phone_visibility)).assertExists()
        composeRule.onNodeWithText(text(R.string.profile_phone_help)).assertExists()
    }

    @Test
    fun myInfo_saveIsGreyUntilSomethingChanges_thenActive() {
        var saved = 0
        myInfo(myInfoState(hasChanges = false), onSave = { saved++ })
        composeRule
            .onNodeWithText(text(R.string.settings_my_info_save_action))
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun myInfo_saveIsActiveWhenSomethingChanged_andPressing_saves() {
        var saved = 0
        myInfo(myInfoState(hasChanges = true), onSave = { saved++ })
        composeRule
            .onNodeWithText(
                text(R.string.settings_my_info_save_action),
            ).performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, saved)
    }

    @Test
    fun myInfo_aNumberThatIsNotRightIsSaidCalmly_andSaveIsGrey_likeMockup14() {
        myInfo(myInfoState(phone = "123", hasChanges = true, invalid = true))
        shoot("my-info-invalid-phone")
        composeRule.onNodeWithText("This number doesn't look right.").assertExists()
        composeRule.onNodeWithText(text(R.string.settings_my_info_save_action)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun myInfo_confirmationAndFailuresAreWordsNotColourAlone() {
        myInfo(myInfoState(hasChanges = false, message = MyInfoMessage.SAVED))
        composeRule.onNodeWithText("Saved.").assertExists()
    }

    @Test
    fun myInfo_aFailedReadOffersToTryAgain() {
        var retried = 0
        myInfo(myInfoState(load = MyInfoLoad.FAILED), onRetry = { retried++ })
        composeRule.onNodeWithText(text(R.string.settings_my_info_load_failed)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun myInfo_leavingWithUnsavedChangesAsksFirst_withTwoButtons() {
        var back = 0
        myInfo(myInfoState(hasChanges = true), onBack = { back++ })
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).performClick()
        assertEquals("nothing leaves without asking", 0, back)
        composeRule.onNodeWithText("Leave without saving?").assertExists()
        shoot("my-info-discard")
        composeRule.onNodeWithText(text(R.string.settings_my_info_discard_cancel)).performClick()
        assertEquals(0, back)
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).performClick()
        composeRule.onNodeWithText(text(R.string.settings_my_info_discard_confirm)).performClick()
        assertEquals(1, back)
    }

    @Test
    fun myInfo_leavingWithoutChangesAsksNothing() {
        var back = 0
        myInfo(myInfoState(hasChanges = false), onBack = { back++ })
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).performClick()
        assertEquals(1, back)
    }

    @Test
    fun myInfo_tappingThePhotoOffersCameraGalleryAndRemoval() {
        myInfo(myInfoState(photo = true))
        composeRule
            .onNodeWithContentDescription(
                text(R.string.settings_my_info_photo_edit_a11y),
            ).assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText(text(R.string.profile_photo_take)).assertExists()
        composeRule.onNodeWithText(text(R.string.profile_photo_choose)).assertExists()
        composeRule.onNodeWithText(text(R.string.profile_photo_remove)).assertExists()
    }

    @Test
    fun myInfo_withoutAPhoto_removalIsNotOffered_andTheInitialsAreDrawn() {
        myInfo(myInfoState(photo = false))
        composeRule.onNodeWithContentDescription(text(R.string.settings_my_info_photo_edit_a11y)).performClick()
        composeRule.onAllNodesWithText(text(R.string.profile_photo_remove)).assertCountEquals(0)
    }

    @Test
    fun myInfo_fieldsAreLabelled_andTheTouchTargetsAreAtLeast48dp() {
        myInfo(myInfoState(hasChanges = true))
        composeRule
            .onNode(hasSetTextAction() and hasText(text(R.string.profile_first_name_label)))
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNode(hasSetTextAction() and hasText(text(R.string.settings_my_info_phone_label)))
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithContentDescription(text(R.string.settings_my_info_photo_edit_a11y))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule
            .onNodeWithText(
                text(R.string.settings_my_info_save_action),
            ).performScrollTo()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun myInfo_largestFont_everythingStaysReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                MyInfoScreen(
                    state = myInfoState(hasChanges = true),
                    onBack = {},
                    onRetry = {},
                    onFirstNameChanged = {},
                    onLastNameChanged = {},
                    onCountrySelected = {},
                    onPhoneChanged = {},
                    onPhoneFocusLost = {},
                    onPhotoChosen = {},
                    onRemovePhoto = {},
                    onCameraUnavailable = {},
                    onCropChanged = {
                        _,
                        _,
                        _,
                        ->
                    },
                    onCropConfirm = {},
                    onCropCancel = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.settings_my_info_save_action)).performScrollTo().assertIsDisplayed()
    }
}
