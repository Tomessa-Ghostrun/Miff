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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.onboarding.DraftStep
import com.tomessa.miff.data.profile.BitmapPhoto
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffPlace
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSummary
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
import com.tomessa.miff.ui.components.ProvideSystemBarsFallback
import com.tomessa.miff.ui.components.SystemBarSizes
import com.tomessa.miff.ui.home.HomeScreen
import com.tomessa.miff.ui.main.MainTabs
import com.tomessa.miff.ui.miff.AccessLostScreen
import com.tomessa.miff.ui.miff.CreateMiffScreen
import com.tomessa.miff.ui.miff.CreateStep
import com.tomessa.miff.ui.miff.CreateUiState
import com.tomessa.miff.ui.miff.CreatedMiff
import com.tomessa.miff.ui.miff.JoinMiffScreen
import com.tomessa.miff.ui.miff.JoinStep
import com.tomessa.miff.ui.miff.JoinUiState
import com.tomessa.miff.ui.miff.MiffChoiceScreen
import com.tomessa.miff.ui.miff.MiffScreenState
import com.tomessa.miff.ui.miff.MiffScreenStatus
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
import com.tomessa.miff.ui.theme.Spacing
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt
import com.tomessa.miff.ui.miff.MiffScreen as MiffDetailScreen

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
        systemBarsFallback: Boolean = false,
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
                MiffTheme {
                    ProvideSystemBarsFallback(
                        systemBarsFallback,
                    ) { Box(Modifier.fillMaxSize()) { content() } }
                }
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

    @Composable
    private fun TabsUnderTest(
        profile: ProfileTabUiState,
        miffs: DirectoryState,
    ) {
        MainTabs(
            tabBar = TabBarConfig.forBuild(isInternalBuild = true),
            profileState = profile,
            onOpenMyInfo = {},
            onOpenSettings = {},
            onRetryProfile = {},
            miffs = miffs,
            onRetryMiffs = {},
            onSelectMiff = {},
            onCreateMiff = {},
            onJoinMiff = {},
            onOpenMiff = {},
        )
    }

    // --- Tabs (the screen of the PM's capture) --------------------------------------------------------------------------------------

    private fun tabs(
        selectedProfile: Boolean = false,
        miffs: DirectoryState = DirectoryState.Loading,
    ) {
        show {
            TabsUnderTest(
                profile =
                    ProfileTabUiState(
                        ProfileTabStatus.READY,
                        MyProfile("Emmanuelle", "Martin", "+33612345678", null),
                    ),
                miffs = miffs,
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
            TabsUnderTest(
                profile =
                    ProfileTabUiState(ProfileTabStatus.LOADING, null),
                miffs = DirectoryState.Loading,
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

    // --- A phone that reports NOTHING for the status bar (the OPPO of the recipe of 2026-10-31) ---------------------------------

    private fun feedWithFallback() {
        show(systemBarsFallback = true) {
            TabsUnderTest(
                profile =
                    ProfileTabUiState(ProfileTabStatus.LOADING, null),
                miffs = DirectoryState.Loading,
            )
        }
    }

    private fun logoTop(): Float =
        composeRule
            .onNodeWithContentDescription(text(R.string.home_logo_a11y))
            .fetchSemanticsNode()
            .boundsInRoot.top

    @Test
    fun feed_whenThePhoneReportsNoStatusBarAtAll_theRealHeightOfTheBarIsUsed() {
        feedWithFallback()
        applyInsets(Phone(statusBar = 0.dp, navigationBottom = 0.dp))
        val bar = SystemBarSizes.statusBarHeight(landscape = false)
        assertTrue("the framework declares a status bar height", bar > 0)
        assertTrue("logo top=${logoTop()} must be below the real status bar ($bar px)", logoTop() >= bar - TOLERANCE)
    }

    @Test
    fun feed_whenThePhoneReportsARealValue_thatValueIsKept_noExtraMargin() {
        feedWithFallback()
        val reported = Phone(statusBar = 40.dp, navigationBottom = 0.dp)
        applyInsets(reported)
        val expected = px(reported.statusBar) + px(Spacing.m)
        assertTrue("logo top=${logoTop()} expected about $expected", kotlin.math.abs(logoTop() - expected) <= TOLERANCE)
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

    // --- Ticket MIFF-1: create, join, the screen of a Miff, the Feed and the Profile cards, the lost-access message --------------

    private val oneMiff =
        DirectoryState.Ready(listOf(MiffSummary("miff-id-1", "Famille Alberta")), "miff-id-1", false, false)
    private val noMiff = DirectoryState.Ready(emptyList(), null, false, false)

    private fun createdState() =
        CreateUiState(step = CreateStep.READY, created = CreatedMiff("miff-id-1", "Famille Alberta", "ABCDEFGHJKMN"))

    private fun miffScreenState(owner: Boolean = true) =
        MiffScreenState(
            status = MiffScreenStatus.READY,
            miffId = "miff-id-1",
            name = "Famille Alberta",
            joinCode = "ABCDEFGHJKMN",
            isOwner = owner,
            members =
                listOf(
                    MiffMember(
                        "me",
                        "Emmanuelle",
                        "Martin",
                        null,
                        if (owner) MiffRole.OWNER else MiffRole.MEMBER,
                        true,
                    ),
                    MiffMember("other", "Thomas", "Alberta", null, MiffRole.MEMBER, true),
                ),
            places = listOf(MiffPlace("p1", "Maison")),
            myUid = "me",
            sharing = true,
            nameInput = "Famille Alberta",
        )

    @Composable
    private fun MiffDetail(state: MiffScreenState = miffScreenState()) =
        MiffDetailScreen(state, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun feed_withAMiff_isInsideTheSafeArea() {
        tabs(miffs = oneMiff)
        shoot("insets-feed-with-miff")
        assertBelowStatusBar(text(R.string.home_logo_a11y), phone)
        assertAllContentInsideSafeArea(phone)
        assertAboveNavigationBar(text(R.string.tabbar_feed_a11y), phone)
    }

    @Test
    fun feed_withoutAMiff_isInsideTheSafeArea() {
        tabs(miffs = noMiff)
        shoot("insets-feed-no-miff")
        assertBelowStatusBar(text(R.string.home_logo_a11y), phone)
        assertAllContentInsideSafeArea(phone)
    }

    @Test
    fun feed_whenTheMiffsCannotBeRead_isInsideTheSafeArea() {
        tabs(miffs = DirectoryState.Unavailable)
        assertBelowStatusBar(text(R.string.home_logo_a11y), phone)
        assertAllContentInsideSafeArea(phone)
    }

    @Test
    fun profileTab_withTheMiffCards_isInsideTheSafeArea() {
        tabs(selectedProfile = true, miffs = oneMiff)
        shoot("insets-profile-tab-miffs")
        assertAllContentInsideSafeArea(phone)
        assertAboveNavigationBar(text(R.string.tabbar_profile_a11y), phone)
    }

    @Test
    fun miffChoice_isInsideTheSafeArea() = assertScreenIsSafe("miff-choice") { MiffChoiceScreen({}, {}, {}) }

    @Test
    fun createMiff_nameStep_isInsideTheSafeArea() =
        assertScreenIsSafe("miff-create", withBackArrow = true) { CreateMiffScreen(CreateUiState(), {}, {}, {}, {}) }

    @Test
    fun createMiff_readyStep_isInsideTheSafeArea() =
        assertScreenIsSafe("miff-created") { CreateMiffScreen(createdState(), {}, {}, {}, {}) }

    @Test
    fun joinMiff_codeStep_isInsideTheSafeArea() =
        assertScreenIsSafe("miff-join", withBackArrow = true) {
            JoinMiffScreen(JoinUiState(), {}, {}, {}, {}, {}, {})
        }

    @Test
    fun joinMiff_confirmStep_isInsideTheSafeArea() =
        assertScreenIsSafe("miff-join-confirm") {
            val state = JoinUiState(step = JoinStep.CONFIRM, code = "ABCDEFGHJKMN", miffName = "Famille Alberta")
            JoinMiffScreen(state, {}, {}, {}, {}, {}, {})
        }

    @Test
    fun miffScreen_asOwner_isInsideTheSafeArea() =
        assertScreenIsSafe("miff-screen-owner", withBackArrow = true) { MiffDetail() }

    @Test
    fun miffScreen_asMember_isInsideTheSafeArea() =
        assertScreenIsSafe("miff-screen-member", withBackArrow = true) { MiffDetail(miffScreenState(owner = false)) }

    @Test
    fun miffScreen_whileLoading_isInsideTheSafeArea() =
        assertScreenIsSafe("miff-screen-loading", withBackArrow = true) { MiffDetail(MiffScreenState()) }

    @Test
    fun accessLost_isInsideTheSafeArea() =
        assertScreenIsSafe("miff-access-lost") {
            AccessLostScreen("Famille Alberta", {})
        }

    @Test
    fun miffChoice_inLandscapeWithACutout_staysInsideIt() =
        assertScreenIsSafeInLandscape { MiffChoiceScreen({}, {}, {}) }

    @Test
    fun createMiff_inLandscapeWithACutout_staysInsideIt() =
        assertScreenIsSafeInLandscape { CreateMiffScreen(CreateUiState(), {}, {}, {}, {}) }

    @Test
    fun miffScreen_inLandscapeWithACutout_staysInsideIt() = assertScreenIsSafeInLandscape { MiffDetail() }

    @Test
    fun feed_inLandscapeWithACutout_staysInsideIt() {
        tabs(miffs = oneMiff)
        applyInsets(threeButtonsLandscape)
        assertAllContentInsideSafeArea(threeButtonsLandscape)
    }

    @Test
    fun createMiff_withTheLargestFont_staysBelowTheStatusBar() {
        show(fontScale = 2f) { CreateMiffScreen(CreateUiState(), {}, {}, {}, {}) }
        applyInsets(phone)
        assertAllContentInsideSafeArea(phone)
    }

    @Test
    fun miffScreen_withTheLargestFont_staysBelowTheStatusBar() {
        show(fontScale = 2f) { MiffDetail() }
        applyInsets(phone)
        assertAllContentInsideSafeArea(phone)
    }

    /** The last item of a long, scrolling screen, once scrolled to, must stay ABOVE the keyboard (the viewport shrinks). */
    private fun assertLastItemAboveKeyboard(lastItemText: String) {
        val keyboardPhone = phone.copy(keyboard = 300.dp)
        applyInsets(keyboardPhone)
        composeRule.onAllNodesWithText(lastItemText).onFirst().performScrollTo()
        composeRule.waitForIdle()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val bottom =
            composeRule
                .onAllNodesWithText(lastItemText)
                .onFirst()
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        assertTrue(
            "\"$lastItemText\" bottom=$bottom is under the keyboard (starts at ${root.bottom -
                px(
                    keyboardPhone.keyboard,
                )
            } px)",
            bottom <= root.bottom - px(keyboardPhone.keyboard) + TOLERANCE,
        )
    }

    @Test
    fun createMiff_withTheKeyboardOpen_theButtonCanBeReachedAboveIt() {
        show { CreateMiffScreen(CreateUiState(name = "Famille"), {}, {}, {}, {}) }
        applyInsets(phone)
        assertLastItemAboveKeyboard(text(R.string.miff_create_action))
    }

    @Test
    fun joinMiff_withTheKeyboardOpen_theButtonCanBeReachedAboveIt() {
        show { JoinMiffScreen(JoinUiState(code = "ABCD"), {}, {}, {}, {}, {}, {}) }
        applyInsets(phone)
        assertLastItemAboveKeyboard(text(R.string.miff_join_continue_action))
    }

    @Test
    fun miffScreen_withTheKeyboardOpen_theLastSectionCanBeReachedAboveIt() {
        show { MiffDetail() }
        applyInsets(phone)
        assertLastItemAboveKeyboard("Maison")
    }

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
