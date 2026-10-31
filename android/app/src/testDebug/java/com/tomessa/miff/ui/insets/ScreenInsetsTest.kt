package com.tomessa.miff.ui.insets

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.onboarding.DraftStep
import com.tomessa.miff.data.profile.BitmapPhoto
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.domain.settings.AppLanguage
import com.tomessa.miff.ui.auth.ConsentPanelScreen
import com.tomessa.miff.ui.auth.ConsentPanelUiState
import com.tomessa.miff.ui.auth.PasswordResetScreen
import com.tomessa.miff.ui.auth.PasswordResetUiState
import com.tomessa.miff.ui.auth.SignInScreen
import com.tomessa.miff.ui.auth.SignInUiState
import com.tomessa.miff.ui.auth.SignUpScreen
import com.tomessa.miff.ui.auth.SignUpUiState
import com.tomessa.miff.ui.components.MiffScreen
import com.tomessa.miff.ui.home.HomeScreen
import com.tomessa.miff.ui.main.MainTabs
import com.tomessa.miff.ui.onboarding.WelcomeCarouselScreen
import com.tomessa.miff.ui.profile.CropState
import com.tomessa.miff.ui.profile.OnboardingMode
import com.tomessa.miff.ui.profile.PhoneStepScreen
import com.tomessa.miff.ui.profile.PhotoCropScreen
import com.tomessa.miff.ui.profile.ProfileStepScreen
import com.tomessa.miff.ui.profile.ProfileTabStatus
import com.tomessa.miff.ui.profile.ProfileTabUiState
import com.tomessa.miff.ui.profile.ProfileUiState
import com.tomessa.miff.ui.settings.AccountScreen
import com.tomessa.miff.ui.settings.AccountUiState
import com.tomessa.miff.ui.settings.LanguageScreen
import com.tomessa.miff.ui.settings.MyInfoLoad
import com.tomessa.miff.ui.settings.MyInfoScreen
import com.tomessa.miff.ui.settings.MyInfoUiState
import com.tomessa.miff.ui.settings.PrivacyScreen
import com.tomessa.miff.ui.settings.PrivacyUiState
import com.tomessa.miff.ui.settings.SettingsScreen
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/**
 * Safe areas, screen by screen (PM recipe on the OPPO, 2026-10-31: "the logo is drawn over the clock"). A phone is simulated
 * by SENDING real window insets to the screen (status bar, navigation bar, cut-out, keyboard) like Android does; then no
 * text, no icon and no button may be drawn under the status bar or the cut-out, and the tab bar must stay above the
 * navigation bar. These tests FAIL if a screen goes under the status bar again.
 *
 * Without insets (the default of any test) everything would pass whatever the code does: that is why [applyInsets] exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class ScreenInsetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var host: View? = null
    private var density: Density = Density(1f)

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun show(
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            val view = LocalView.current
            val base = LocalDensity.current
            SideEffect {
                host = view
                density = base
            }
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MiffTheme { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    /** What a phone sends to the app: heights/widths in dp of each system area. */
    private data class Phone(
        val statusBar: Dp = 48.dp,
        val navigationBottom: Dp = 48.dp,
        val navigationEnd: Dp = 0.dp,
        val cutoutStart: Dp = 0.dp,
        val keyboard: Dp = 0.dp,
    )

    private fun px(value: Dp): Float = value.value * density.density

    private fun applyInsets(phone: Phone) {
        fun inset(
            l: Dp,
            t: Dp,
            r: Dp,
            b: Dp,
        ) = Insets.of(px(l).roundToInt(), px(t).roundToInt(), px(r).roundToInt(), px(b).roundToInt())
        val builder =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), inset(0.dp, phone.statusBar, 0.dp, 0.dp))
                .setInsets(
                    WindowInsetsCompat.Type.navigationBars(),
                    inset(0.dp, 0.dp, phone.navigationEnd, phone.navigationBottom),
                ).setInsets(WindowInsetsCompat.Type.displayCutout(), inset(phone.cutoutStart, 0.dp, 0.dp, 0.dp))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
        if (phone.keyboard > 0.dp) {
            builder
                .setInsets(WindowInsetsCompat.Type.ime(), inset(0.dp, 0.dp, 0.dp, phone.keyboard))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
        }
        val view = checkNotNull(host) { "show() first" }
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, builder.build()) }
        composeRule.waitForIdle()
    }

    private val showsSomething =
        SemanticsMatcher("shows text, an icon with a description, or can be clicked") { node ->
            val config = node.config
            config.contains(SemanticsProperties.Text) ||
                config.contains(SemanticsProperties.EditableText) ||
                config.contains(SemanticsProperties.ContentDescription) ||
                config.contains(SemanticsActions.OnClick)
        }

    /** Every text, icon (with a description) and button visible on screen is below the status bar and inside the cut-out. */
    private fun assertAllContentInsideSafeArea(phone: Phone) {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val offenders =
            composeRule
                .onAllNodes(showsSomething, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { it.boundsInRoot.height > 0f && it.boundsInRoot.bottom > 0f }
                .filter {
                    val b = it.boundsInRoot
                    b.top < px(phone.statusBar) - TOLERANCE ||
                        b.left < px(phone.cutoutStart) - TOLERANCE ||
                        b.right > rootBounds.right - px(phone.navigationEnd) + TOLERANCE
                }.map { it.describe() }
        assertTrue("drawn under the status bar / cut-out / side bar: $offenders", offenders.isEmpty())
    }

    private fun androidx.compose.ui.semantics.SemanticsNode.describe(): String {
        val label =
            config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: "(button)"
        return "\"$label\" $boundsInRoot"
    }

    private fun assertBelowStatusBar(
        description: String,
        phone: Phone,
    ) {
        val top =
            composeRule
                .onNodeWithContentDescription(description)
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue(
            "\"$description\" top=$top is under the status bar (${px(phone.statusBar)} px)",
            top >= px(phone.statusBar) - TOLERANCE,
        )
    }

    private fun assertAboveNavigationBar(
        description: String,
        phone: Phone,
    ) {
        val rootBottom =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val bottom =
            composeRule
                .onNodeWithContentDescription(description)
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        assertTrue(
            "\"$description\" bottom=$bottom is under the navigation bar (starts at ${rootBottom -
                px(
                    phone.navigationBottom,
                )
            } px)",
            bottom <= rootBottom - px(phone.navigationBottom) + TOLERANCE,
        )
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val phone = Phone()
    private val threeButtonsLandscape =
        Phone(statusBar = 24.dp, navigationBottom = 0.dp, navigationEnd = 48.dp, cutoutStart = 48.dp)

    // --- The container itself ----------------------------------------------------------------------------------------------------

    @Test
    fun miffScreen_keepsItsContentInsideTheSafeArea_andOnlyItsBackgroundGoesUnderTheBars() {
        show { MiffScreen { androidx.compose.material3.Text("Bonjour", Modifier.fillMaxSize()) } }
        applyInsets(phone)
        val b = composeRule.onNodeWithText("Bonjour").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(b.top >= px(phone.statusBar) - TOLERANCE)
        assertTrue(b.bottom <= root.bottom - px(phone.navigationBottom) + TOLERANCE)
    }

    // --- Tabs (the screen of the PM's capture) --------------------------------------------------------------------------------------

    private fun tabs(selectedProfile: Boolean = false) {
        show {
            MainTabs(
                tabBar = TabBarConfig.forBuild(isInternalBuild = true),
                profileState =
                    ProfileTabUiState(
                        ProfileTabStatus.READY,
                        MyProfile("Emmanuelle", "Martin", "+33612345678", null),
                    ),
                onOpenMyInfo = {},
                onOpenSettings = {},
                onRetryProfile = {},
            )
        }
        applyInsets(phone)
        if (selectedProfile) composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).performClick()
    }

    @Test
    fun feedTab_logoAndTexts_areBelowTheStatusBar_andTheTabBarAboveTheNavigationBar() {
        tabs()
        shoot("insets-feed")
        assertBelowStatusBar(text(R.string.home_logo_a11y), phone)
        assertAllContentInsideSafeArea(phone)
        assertAboveNavigationBar(text(R.string.tabbar_feed_a11y), phone)
        assertAboveNavigationBar(text(R.string.tabbar_profile_a11y), phone)
    }

    @Test
    fun profileTab_identityAndGear_areBelowTheStatusBar() {
        tabs(selectedProfile = true)
        shoot("insets-profile-tab")
        assertAllContentInsideSafeArea(phone)
        assertBelowStatusBar(text(R.string.profile_tab_settings_a11y), phone)
        assertAboveNavigationBar(text(R.string.tabbar_profile_a11y), phone)
    }

    @Test
    fun tabs_inLandscapeWithThreeButtons_stayInsideTheCutoutAndTheSideBar() {
        tabs()
        applyInsets(threeButtonsLandscape)
        assertAllContentInsideSafeArea(threeButtonsLandscape)
    }

    @Test
    fun tabs_withTheLargestFont_stayBelowTheStatusBar() {
        show(fontScale = 2f) {
            MainTabs(
                tabBar = TabBarConfig.forBuild(isInternalBuild = true),
                profileState = ProfileTabUiState(ProfileTabStatus.LOADING, null),
                onOpenMyInfo = {},
                onOpenSettings = {},
                onRetryProfile = {},
            )
        }
        applyInsets(phone)
        assertAllContentInsideSafeArea(phone)
    }

    @Test
    fun tabs_theBarHidesWhileTheKeyboardIsOpen_andComesBack() {
        tabs()
        composeRule.onAllNodesWithContentDescription(text(R.string.tabbar_feed_a11y)).assertCountEquals(1)
        applyInsets(phone.copy(keyboard = 300.dp))
        composeRule.onAllNodesWithContentDescription(text(R.string.tabbar_feed_a11y)).assertCountEquals(0)
        applyInsets(phone)
        composeRule.onAllNodesWithContentDescription(text(R.string.tabbar_feed_a11y)).assertCountEquals(1)
    }

    // --- One test per screen: a phone with a status bar and a navigation bar is simulated -------------------------------------

    private fun assertScreenIsSafe(
        name: String,
        withBackArrow: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        show { content() }
        applyInsets(phone)
        shoot("insets-$name")
        if (withBackArrow) assertBelowStatusBar(text(R.string.auth_back_a11y), phone)
        assertAllContentInsideSafeArea(phone)
    }

    private fun assertScreenIsSafeInLandscape(content: @Composable () -> Unit) {
        show { content() }
        applyInsets(threeButtonsLandscape)
        assertAllContentInsideSafeArea(threeButtonsLandscape)
    }

    @Test
    fun settings_isInsideTheSafeArea() =
        assertScreenIsSafe("settings", withBackArrow = true) {
            SettingsScreen({}, {}, {}, {}, {})
        }

    @Test
    fun language_isInsideTheSafeArea() =
        assertScreenIsSafe("language", withBackArrow = true) { LanguageScreen(AppLanguage.SYSTEM, {}, {}) }

    @Test
    fun account_isInsideTheSafeArea() =
        assertScreenIsSafe("account", withBackArrow = true) {
            AccountScreen(AccountUiState(email = "emmanuelle@example.org"), {}, {}, {}, {}, {})
        }

    @Test
    fun privacy_isInsideTheSafeArea() =
        assertScreenIsSafe("privacy", withBackArrow = true) {
            PrivacyScreen(PrivacyUiState(), {}, {}, {})
        }

    @Test
    fun myInfo_isInsideTheSafeArea() =
        assertScreenIsSafe("my-info", withBackArrow = true) {
            MyInfoScreen(myInfoState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {}, {})
        }

    @Test
    fun settings_inLandscapeWithACutout_staysInsideIt() =
        assertScreenIsSafeInLandscape {
            SettingsScreen({}, {}, {}, {}, {})
        }

    @Test
    fun signUp_isInsideTheSafeArea() =
        assertScreenIsSafe("signup") {
            SignUpScreen(SignUpUiState(), null, { _, _ -> }, {}, {})
        }

    @Test
    fun signIn_isInsideTheSafeArea() =
        assertScreenIsSafe(
            "signin",
            withBackArrow = true,
        ) { SignInScreen(SignInUiState(), {}, { _, _ -> }, {}, {}, {}) }

    @Test
    fun passwordReset_isInsideTheSafeArea() =
        assertScreenIsSafe(
            "reset",
            withBackArrow = true,
        ) { PasswordResetScreen(PasswordResetUiState(), "", {}, {}, {}, {}) }

    @Test
    fun consentPanel_isInsideTheSafeArea() =
        assertScreenIsSafe("consent-panel") {
            ConsentPanelScreen(ConsentPanelUiState(), {}, {})
        }

    @Test
    fun home_isInsideTheSafeArea() = assertScreenIsSafe("home") { HomeScreen() }

    @Test
    fun signIn_inLandscapeWithACutout_staysInsideIt() =
        assertScreenIsSafeInLandscape { SignInScreen(SignInUiState(), {}, { _, _ -> }, {}, {}, {}) }

    @Test
    fun photoCrop_isInsideTheSafeArea() =
        assertScreenIsSafe("photo-crop") {
            PhotoCropScreen(cropState(), { _, _, _ -> }, {}, {})
        }

    @Test
    fun profileStep_arrowAndFields_areInsideTheSafeArea() {
        assertScreenIsSafe(
            "profile-step",
        ) { ProfileStepScreen(profileState(DraftStep.PROFILE), {}, {}, {}, {}, {}, {}) }
        assertAboveNavigationBar(text(R.string.profile_next_a11y), phone)
    }

    @Test
    fun phoneStep_arrowAndFields_areInsideTheSafeArea() {
        assertScreenIsSafe("phone-step", withBackArrow = true) {
            PhoneStepScreen(profileState(DraftStep.PHONE), {}, {}, {}, {})
        }
        assertAboveNavigationBar(text(R.string.profile_next_a11y), phone)
    }

    @Test
    fun carousel_wordmarkAndButton_areInsideTheSafeArea_theIllustrationMayGoUnderTheStatusBar() {
        show { WelcomeCarouselScreen(onStart = {}, onHaveAccount = {}) }
        applyInsets(phone)
        shoot("insets-carousel")
        assertBelowStatusBar(text(R.string.home_logo_a11y), phone)
        val button = composeRule.onNodeWithText("Get started", ignoreCase = true).fetchSemanticsNode().boundsInRoot
        assertTrue(button.left >= px(phone.cutoutStart) - TOLERANCE)
    }

    private fun profileState(step: DraftStep) = ProfileUiState(mode = OnboardingMode.FULL, step = step)

    // --- Helpers ---------------------------------------------------------------------------------------------------------------------

    private fun myInfoState() =
        MyInfoUiState(
            load = MyInfoLoad.READY,
            firstName = "Emmanuelle",
            lastName = "Martin",
            regionCode = "FR",
            phoneText = "06 12 34 56 78",
        )

    private fun cropState() =
        CropState(
            photo = BitmapPhoto(Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)),
            zoom = 1f,
            centerX = 400f,
            centerY = 300f,
        )

    private companion object {
        /** Rounding of dp to pixels and of text boxes. */
        const val TOLERANCE = 2f
    }
}
