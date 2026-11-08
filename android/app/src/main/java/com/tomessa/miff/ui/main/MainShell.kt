package com.tomessa.miff.ui.main

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tomessa.miff.AppContainer
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.auth.SignInProvider
import com.tomessa.miff.domain.feed.StateLabel
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.profile.AvatarCodec
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.update.PromptLevel
import com.tomessa.miff.ui.components.LocalBottomOverlayPadding
import com.tomessa.miff.ui.components.miffBottomInsets
import com.tomessa.miff.ui.components.miffKeyboardVisible
import com.tomessa.miff.ui.feed.FeedActions
import com.tomessa.miff.ui.feed.FeedHost
import com.tomessa.miff.ui.feed.FeedMe
import com.tomessa.miff.ui.feed.FeedScreen
import com.tomessa.miff.ui.feed.FeedUiState
import com.tomessa.miff.ui.feed.FeedViewModel
import com.tomessa.miff.ui.feed.LocalPhotoReader
import com.tomessa.miff.ui.feed.rememberSystemFeedActions
import com.tomessa.miff.ui.miff.AccessLostScreen
import com.tomessa.miff.ui.miff.AgreementUi
import com.tomessa.miff.ui.miff.CreateMiffRoute
import com.tomessa.miff.ui.miff.JoinMiffRoute
import com.tomessa.miff.ui.miff.MiffRoute
import com.tomessa.miff.ui.miff.MiffSharing
import com.tomessa.miff.ui.miff.miffEntryRoutes
import com.tomessa.miff.ui.miff.miffScreenRoute
import com.tomessa.miff.ui.navigation.PasswordResetRoute
import com.tomessa.miff.ui.navigation.googleSourceFor
import com.tomessa.miff.ui.navigation.passwordResetRoute
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import com.tomessa.miff.ui.place.PlaceDetailRoute
import com.tomessa.miff.ui.place.PlaceFormRoute
import com.tomessa.miff.ui.place.PlacePaywallRoute
import com.tomessa.miff.ui.place.ProfilePlacesSection
import com.tomessa.miff.ui.place.placeRoutes
import com.tomessa.miff.ui.profile.ProfileTabScreen
import com.tomessa.miff.ui.profile.ProfileTabUiState
import com.tomessa.miff.ui.profile.ProfileTabViewModel
import com.tomessa.miff.ui.settings.AccountScreen
import com.tomessa.miff.ui.settings.AccountViewModel
import com.tomessa.miff.ui.settings.DeleteAccountInfoScreen
import com.tomessa.miff.ui.settings.DeleteAccountInfoViewModel
import com.tomessa.miff.ui.settings.DeleteAccountReauthScreen
import com.tomessa.miff.ui.settings.DeleteReauthViewModel
import com.tomessa.miff.ui.settings.DetectionHelpScreen
import com.tomessa.miff.ui.settings.ExternalLinks
import com.tomessa.miff.ui.settings.LanguageScreen
import com.tomessa.miff.ui.settings.LanguageViewModel
import com.tomessa.miff.ui.settings.MyInfoScreen
import com.tomessa.miff.ui.settings.MyInfoViewModel
import com.tomessa.miff.ui.settings.PrivacyScreen
import com.tomessa.miff.ui.settings.PrivacyViewModel
import com.tomessa.miff.ui.settings.SettingsScreen
import com.tomessa.miff.ui.settings.rememberLocationPrivacy
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.update.UpdateActions
import com.tomessa.miff.ui.update.UpdateBottomLayer
import com.tomessa.miff.ui.update.UpdateEvent
import com.tomessa.miff.ui.update.UpdateStage
import com.tomessa.miff.ui.update.UpdateTab
import com.tomessa.miff.ui.update.UpdateViewModel
import com.tomessa.miff.ui.update.glassVariantFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data object TabsRoute

@Serializable
data object SettingsRoute

@Serializable
data object MyInfoRoute

@Serializable
data object AccountRoute

/** Deletion of the account (AUTH-5): A (information, with the dialog B) and C (re-authentication). */
@Serializable
data object DeleteAccountRoute

@Serializable
data object DeleteAccountVerifyRoute

@Serializable
data object LanguageRoute

@Serializable
data object PrivacyRoute

/** LOC-2b: the help for the phones that stop the apps in the background (opened from Settings > Confidentialité). */
@Serializable
data object DetectionHelpRoute

/**
 * The app once the onboarding is done (a Miff exists): the three tabs, and, over them, the Settings screens (which hide the
 * tab bar, like the mockups). Reached from [com.tomessa.miff.ui.navigation.AppRoot] only for the destination FEED: a person
 * without a Miff never sees the bar (ticket Onboarding 6.7). The tab in use and the scroll of each tab survive a rotation
 * of the phone and a trip to the Settings.
 */
@Composable
fun MainShell(
    container: AppContainer,
    tabBar: TabBarConfig,
) {
    val navController = rememberNavController()
    Box(Modifier.fillMaxSize()) {
        MainNavigation(container, tabBar, navController)
        LostAccessOverlay(container, navController)
        DeletionNotices(container, navController)
    }
}

/**
 * The deletion of a Miff goes on in the app, whether its screen is open or not. This picks it up again at launch (ticket S4) and
 * says so calmly with a short system message; and tells, the same way, when a deletion ends while the person is elsewhere. While the
 * screen of that Miff is open it says it itself (dialog), so nothing is said twice.
 */
@Composable
private fun DeletionNotices(
    container: AppContainer,
    navController: NavController,
) {
    val context = LocalContext.current
    val resumedText = stringResource(R.string.miff_delete_resume)
    val doneText = stringResource(R.string.miff_delete_done)
    LaunchedEffect(container) {
        val resumed = container.miffDeletions.resumePending()
        if (resumed.isNotEmpty()) {
            Toast.makeText(context, resumedText, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(container) {
        container.miffDeletions.completed.collect { miffId ->
            val entry = navController.currentBackStackEntry
            val onThatMiff =
                entry?.destination?.hasRoute<MiffRoute>() == true && entry.toRoute<MiffRoute>().miffId == miffId
            if (!onThatMiff) {
                Toast.makeText(context, doneText, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * The single message of a lost access (decision QM8), over everything: nothing of that Miff stays visible behind it. "Compris"
 * closes it, and leaves the screen of that Miff if it was open.
 */
@Composable
private fun LostAccessOverlay(
    container: AppContainer,
    navController: NavController,
) {
    val notices by container.miffDirectory.notice.collectAsStateWithLifecycle()
    notices.firstOrNull()?.let { notice ->
        AccessLostScreen(
            miffName = notice.name,
            onAcknowledge = {
                container.miffDirectory.dismissNotice()
                // Only the screen of THAT Miff is left: another Miff open behind (Premium) stays where it is.
                val entry = navController.currentBackStackEntry
                val onThatMiff =
                    entry?.destination?.hasRoute<MiffRoute>() == true &&
                        entry.toRoute<MiffRoute>().miffId == notice.miffId
                if (onThatMiff) navController.popBackStackIfSafe()
            },
        )
    }
}

@Composable
private fun MainNavigation(
    container: AppContainer,
    tabBar: TabBarConfig,
    navController: NavHostController,
) {
    val session = container.sessionManager
    // One view model per account and per session: the next person never meets what the previous one saw.
    val scopeKey = "${session.currentUid}-${session.generation}"

    NavHost(navController = navController, startDestination = TabsRoute) {
        composable<TabsRoute> {
            val profileViewModel: ProfileTabViewModel =
                viewModel(
                    key = "profile-tab-$scopeKey",
                    factory = viewModelFactory { initializer { ProfileTabViewModel(container.currentProfile) } },
                )
            val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
            val feedViewModel: FeedViewModel =
                viewModel(
                    key = "feed-$scopeKey",
                    factory =
                        viewModelFactory {
                            initializer {
                                FeedViewModel(
                                    directory = container.miffDirectory,
                                    repository = container.miffRepository,
                                    statuses = container.statusObserver,
                                    positions = container.positionReader,
                                    refresher = container.feedRefresher,
                                    network = container.networkMonitor,
                                    currentUid = { container.authGateway.currentUid },
                                    waitingAgreement = container.awaitingAgreement.waiting,
                                )
                            }
                        },
                )
            val updateViewModel: UpdateViewModel =
                viewModel(
                    key = "update-$scopeKey",
                    factory =
                        viewModelFactory {
                            initializer {
                                UpdateViewModel(
                                    container.updatePublisher,
                                    container.photoCleaner,
                                    container.publishLedger,
                                )
                            }
                        },
                )
            val directoryState by feedViewModel.directoryState.collectAsStateWithLifecycle()
            val feedState by feedViewModel.uiState.collectAsStateWithLifecycle()
            val locateState by feedViewModel.locate.collectAsStateWithLifecycle()
            val refreshing by feedViewModel.refreshing.collectAsStateWithLifecycle()
            val waiting by container.awaitingAgreement.waiting.collectAsStateWithLifecycle()
            MainTabs(
                tabBar = tabBar,
                profileState = profileState,
                onOpenMyInfo = { navController.open(MyInfoRoute) },
                onOpenSettings = { navController.open(SettingsRoute) },
                onRetryProfile = profileViewModel::retry,
                miffs = directoryState,
                onRetryMiffs = feedViewModel::refresh,
                onSelectMiff = feedViewModel::onSelect,
                onCreateMiff = { navController.open(CreateMiffRoute) },
                onJoinMiff = { navController.open(JoinMiffRoute) },
                onMiffOffer = { navController.open(PlacePaywallRoute.of(PaywallReason.MIFF)) },
                onOpenMiff = { navController.open(MiffRoute(it)) },
                agreement = AgreementUi(waiting, container.awaitingAgreement::requestReview),
                feed =
                    FeedHost(
                        state = feedState,
                        locate = locateState,
                        me =
                            FeedMe(
                                initials = profileState.initials.ifEmpty { "?" },
                                avatarJpeg = profileState.profile?.avatarJpeg,
                            ),
                        onLocate = feedViewModel::onLocate,
                        onLocateDismissed = feedViewModel::onLocateDismissed,
                        onRetry = feedViewModel::retry,
                        refreshing = refreshing,
                        onRefresh = feedViewModel::onRefresh,
                        observe = feedViewModel::observe,
                        photos = container.photoReader,
                    ),
                update =
                    UpdateHost(
                        viewModel = updateViewModel,
                        avatarCodec = container.avatarCodec,
                        onReviewAgreement = container.awaitingAgreement::requestReview,
                    ),
                placesContent = {
                    (directoryState as? DirectoryState.Ready)?.let { ready ->
                        ProfilePlacesSection(
                            container = container,
                            directory = ready,
                            scopeKey = scopeKey,
                            onOpenPlace = { miffId, placeId -> navController.open(PlaceDetailRoute(miffId, placeId)) },
                            onAddPlace = { navController.open(PlaceFormRoute()) },
                        )
                    }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStackIfSafe() },
                onOpenMyInfo = { navController.open(MyInfoRoute) },
                onOpenAccount = { navController.open(AccountRoute) },
                onOpenLanguage = { navController.open(LanguageRoute) },
                onOpenPrivacy = { navController.open(PrivacyRoute) },
            )
        }
        composable<MyInfoRoute> {
            val viewModel: MyInfoViewModel =
                viewModel(
                    key = "my-info-$scopeKey",
                    factory =
                        viewModelFactory {
                            initializer {
                                MyInfoViewModel(
                                    session = session,
                                    repository = container.myProfiles,
                                    current = container.currentProfile,
                                    avatars = container.avatarCodec,
                                    network = container.networkMonitor,
                                    deviceRegion = { Locale.getDefault().country.takeIf { it.isNotBlank() } },
                                )
                            }
                        },
                )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            MyInfoScreen(
                state = state,
                onBack = { navController.popBackStackIfSafe() },
                onRetry = viewModel::retry,
                onFirstNameChanged = viewModel::onFirstNameChanged,
                onLastNameChanged = viewModel::onLastNameChanged,
                onCountrySelected = viewModel::onCountrySelected,
                onPhoneChanged = viewModel::onPhoneChanged,
                onPhoneFocusLost = viewModel::onPhoneFocusLost,
                onPhotoChosen = viewModel::onPhotoChosen,
                onRemovePhoto = viewModel::onRemovePhoto,
                onCameraUnavailable = viewModel::onCameraUnavailable,
                onCropChanged = viewModel::onCropChanged,
                onCropConfirm = viewModel::onCropConfirmed,
                onCropCancel = viewModel::onCropCancelled,
                onSave = viewModel::onSave,
            )
        }
        sharedSettingsRoutes(container, navController, scopeKey)
        composable<LanguageRoute> {
            val viewModel: LanguageViewModel =
                viewModel(factory = viewModelFactory { initializer { LanguageViewModel(container.languageStore) } })
            val selected by viewModel.selected.collectAsStateWithLifecycle()
            LanguageScreen(
                selected = selected,
                onBack = { navController.popBackStackIfSafe() },
                onSelect = viewModel::onSelect,
            )
        }
        miffEntryRoutes(
            container = container,
            navController = navController,
            onCreated = { navController.popBackStackIfSafe() },
            onJoined = { navController.popBackStackIfSafe() },
            onOpenExisting = { miffId ->
                navController.navigate(MiffRoute(miffId)) {
                    popUpTo<JoinMiffRoute> { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
        miffScreenRoute(container, navController)
        placeRoutes(container, navController)
    }
}

/**
 * A (information, with the confirmation dialog B) then C (re-authentication) of the deletion of the account (AUTH-5, criteria SC1 to SC14).
 * Nothing is written on these screens; the controller only starts after a successful re-authentication, and then replaces the whole
 * Settings navigation with the deletion screens (see [com.tomessa.miff.ui.navigation.AppRoot]).
 */
internal fun NavGraphBuilder.deleteAccountRoutes(
    container: AppContainer,
    navController: NavHostController,
    scopeKey: String,
) {
    composable<DeleteAccountRoute> {
        val context = LocalContext.current
        val viewModel: DeleteAccountInfoViewModel =
            viewModel(
                key = "delete-account-info-$scopeKey",
                factory =
                    viewModelFactory {
                        initializer {
                            DeleteAccountInfoViewModel(
                                procedure = container.accountDeletionProcedure,
                                currentUid = { container.authGateway.currentUid },
                                network = container.networkMonitor,
                                appleLinked = SignInProvider.APPLE in container.authGateway.linkedProviders(),
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        DeleteAccountInfoScreen(
            state = state,
            onBack = { navController.popBackStackIfSafe() },
            onContinue = viewModel::onContinue,
            onRetry = viewModel::load,
            onContact = { ExternalLinks.contactSupport(context) },
            onKeep = viewModel::onKeep,
            onConfirm = { if (viewModel.onConfirm()) navController.open(DeleteAccountVerifyRoute) },
            // AUTH-5b, "Prévenir mes proches" (decision QM9): the share sheet only; nothing is sent by the app, the deletion does not move.
            onWarn = { miffName ->
                if (miffName !=
                    null
                ) {
                    MiffSharing.shareClosingNotice(context, miffName)
                } else {
                    MiffSharing.shareClosingNoticeGeneric(context)
                }
            },
        )
    }
    composable<DeleteAccountVerifyRoute> {
        val context = LocalContext.current
        val googleSource = remember(context) { googleSourceFor(context) }
        val viewModel: DeleteReauthViewModel =
            viewModel(
                key = "delete-account-verify-$scopeKey",
                factory =
                    viewModelFactory {
                        initializer {
                            DeleteReauthViewModel(
                                gateway = container.authGateway,
                                network = container.networkMonitor,
                                last = false,
                                // The ONLY way the deletion starts: after Firebase accepted the proof of identity.
                                onVerified = container.accountDeletion::start,
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        DeleteAccountReauthScreen(
            state = state,
            onBack = { navController.popBackStackIfSafe() },
            onSubmitPassword = viewModel::submitPassword,
            onGoogle = { viewModel.submitGoogle(googleSource) },
            onForgotPassword = {
                // Leaves the deletion without losing anything (it has not started); the reset e-mail goes to the account's address.
                navController.open(PasswordResetRoute(container.authGateway.currentEmail().orEmpty()))
            },
            onCancelOrLater = { navController.popBackStackIfSafe() },
            onContact = { ExternalLinks.contactSupport(context) },
        )
    }
}

/** launchSingleTop: two quick taps open one screen, not two. */
private fun NavController.open(route: Any) = navigate(route) { launchSingleTop = true }

/** What the Update tab needs from the outside (UPDATE-1, UPDATE-2): its view model, the reader of the photo of the phone's camera application, the way to the agreement. */
class UpdateHost(
    val viewModel: UpdateViewModel,
    val avatarCodec: AvatarCodec,
    val onReviewAgreement: () -> Unit = {},
)

/**
 * The tabs and their bar. The tab in use is kept through a rotation; leaving a tab keeps its own state (scroll position, and
 * everything it remembers) for when the person comes back; tapping the tab already in use brings it back to the top. The bar
 * disappears while the keyboard is open.
 *
 * GLASS BAR (ticket TABBAR-GLASS, GlassTabBar.kt): [GlassTabHost] draws the tabs FULL SCREEN and the bar over them, at the bottom, so
 * that the content passes UNDER the frosted glass. The bar therefore no longer reserves its place: every tab that scrolls must keep a
 * bottom margin of the height of the bar, `.padding(bottom = LocalTabBarInset.current)` inside its scrolling column (the host measures
 * the bar and gives that height; it is 0 while the bar is hidden). Without it the last element of the tab stays hidden behind the
 * bar. TabBarContentMarginTest scrolls the Profile to its end and checks it; TabBarInsetRuleTest checks that the line is present.
 *
 * Safe areas (see ui/components/ScreenInsets.kt): the BAR keeps itself above the navigation bar (3 buttons or gestures); each
 * TAB keeps itself below the status bar with miffTopInsets() on its own root, so that it can draw its background (the gradient
 * of the Profile) under the status bar. This function must not consume insets it does not pad: it did once (Scaffold +
 * consumeWindowInsets), the status bar was "consumed" before the tabs could use it, and the Feed logo was drawn over the clock.
 *
 * KEYBOARD, if a text field is added to a tab one day: the tab content has no margin for the keyboard (its bottom margin is only
 * the height of the bar, 0 while the bar is hidden), and while the keyboard is open the bar is removed, so nothing would keep the
 * field above the keyboard. The tab will then have to handle
 * the keyboard itself (through ScreenInsets.kt, e.g. a new modifier there that covers the ime), with a test in
 * ScreenInsetsTest that sends a keyboard to that tab. No tab has a text field today.
 */
@Composable
fun MainTabs(
    tabBar: TabBarConfig,
    profileState: ProfileTabUiState,
    onOpenMyInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryProfile: () -> Unit,
    miffs: DirectoryState,
    onRetryMiffs: () -> Unit,
    onSelectMiff: (String) -> Unit,
    onCreateMiff: () -> Unit,
    onJoinMiff: () -> Unit,
    /** A free account that already has a Miff asked for another one from the selector of the Feed: the offer opens (decision of the PM, 2026-11-08). */
    onMiffOffer: () -> Unit = {},
    onOpenMiff: (String) -> Unit,
    agreement: AgreementUi = AgreementUi(),
    feed: FeedHost = FeedHost(),
    placesContent: @Composable () -> Unit = {},
    update: UpdateHost? = null,
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.FEED) }
    // The tab the person came from: the back gesture leaves the Update for it (design 9: "retour système = onglet précédent").
    var previous by rememberSaveable { mutableStateOf(MainTab.FEED) }
    var scrollToTop by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    val keyboardOpen = miffKeyboardVisible()
    // The dark glass is only for the viewfinder and the preview (dark backgrounds); the pop-ins of the Update are light (see glassVariantFor).
    val stageFlow =
        remember(update?.viewModel) {
            update?.viewModel?.stage
                ?: MutableStateFlow<UpdateStage>(UpdateStage.Idle)
        }
    val updateStage by stageFlow.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val feedContent = (feed.state as? FeedUiState.Ready)?.content
    // D19: the dot of the central button, the ring of my photo and the bubble follow the Miff shown by the Feed (one listener, no reading more).
    val myLevel = feedContent?.myLevel ?: StatusLevel.NEUTRAL
    val prompt = feedContent?.myPrompt ?: PromptLevel.NONE
    val ready = miffs as? DirectoryState.Ready
    // Sharing stopped: the Update shows no viewfinder (and asks no permission). Known for sure with ONE Miff; with several (Premium) the publication says it.
    val sharingStopped = feedContent?.myLabel == StateLabel.SharingStopped && (ready?.miffs?.size ?: 0) == 1

    fun select(tab: MainTab) {
        if (tab != selected) previous = selected
        selected = tab
    }
    BackHandler(enabled = selected == MainTab.UPDATE) {
        selected =
            if (previous == MainTab.UPDATE) MainTab.FEED else previous
    }
    // The photo of the update that expired (24 h) is removed from the database by its author, at the launch and at each return to the foreground.
    val updateModel = update?.viewModel
    val directoryReady = ready != null
    LaunchedEffect(updateModel, directoryReady, feedContent?.myPhoto) {
        if (directoryReady) updateModel?.cleanUpExpiredPhoto(feedContent?.myPhoto)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (directoryReady) updateModel?.cleanUpExpiredPhoto(feedContent?.myPhoto)
    }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val publishedText = stringResource(R.string.update_published)
    LaunchedEffect(updateModel) {
        updateModel?.events?.collect { event ->
            if (event is UpdateEvent.Published) {
                // Back to the Feed, where my card now shows the photo; a calm message without sound or vibration, gone after 3 s.
                selected = MainTab.FEED
                confirmation = publishedText
            }
        }
    }
    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            delay(CONFIRMATION_MILLIS)
            confirmation = null
        }
    }
    var bubbleHeightPx by remember { mutableIntStateOf(0) }
    val bubbleShown = prompt != PromptLevel.NONE && selected != MainTab.UPDATE && !keyboardOpen
    val overlayPadding = if (bubbleShown) with(density) { bubbleHeightPx.toDp() } + Spacing.s else 0.dp
    // TABBAR-GLASS: the tabs fill the screen and pass UNDER the glass bar (GlassTabBar.kt); each tab keeps a bottom margin (LocalTabBarInset).
    GlassTabHost(
        variant = glassVariantFor(selected == MainTab.UPDATE, updateStage),
        barVisible = !keyboardOpen,
        bar = { glass ->
            MainTabBar(
                config = tabBar,
                selected = selected,
                onSelect = { tab ->
                    if (tab == selected) scrollToTop++ else select(tab)
                },
                modifier = Modifier.miffBottomInsets(),
                glass = glass,
                updateStatus = myLevel,
            )
        },
    ) {
        // Measured by the host WITH the safe area under the bar: the bubble sits just above the card of the bar (its own 4 dp of air included).
        val barInset = LocalTabBarInset.current
        Box(modifier = Modifier.fillMaxSize()) {
            holder.SaveableStateProvider(selected) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalBottomOverlayPadding provides overlayPadding,
                        LocalPhotoReader provides feed.photos,
                    ) {
                        when (selected) {
                            MainTab.FEED -> {
                                // The listeners of the Feed live only while this tab is the one in use AND the app is in the foreground.
                                val lifecycleOwner = LocalLifecycleOwner.current
                                val observeFeed by rememberUpdatedState(feed.observe)
                                LaunchedEffect(lifecycleOwner) {
                                    lifecycleOwner.lifecycle.repeatOnLifecycle(
                                        Lifecycle.State.STARTED,
                                    ) { observeFeed() }
                                }
                                val actions =
                                    rememberSystemFeedActions(
                                        FeedActions(
                                            onOpenProfile = { select(MainTab.PROFILE) },
                                            onLocate = feed.onLocate,
                                            onLocateDismissed = feed.onLocateDismissed,
                                            onResumeSharing = onOpenMiff,
                                            onRetryFeed = feed.onRetry,
                                            onRefresh = feed.onRefresh,
                                        ),
                                    )
                                FeedScreen(
                                    state = miffs,
                                    scrollToTopSignal = scrollToTop,
                                    onRetry = onRetryMiffs,
                                    onSelect = onSelectMiff,
                                    onCreate = onCreateMiff,
                                    onJoin = onJoinMiff,
                                    onMiffOffer = onMiffOffer,
                                    agreement = agreement,
                                    feed = feed.state,
                                    locate = feed.locate,
                                    refreshing = feed.refreshing,
                                    me = feed.me,
                                    actions = actions,
                                )
                            }

                            MainTab.PROFILE -> {
                                ProfileTabScreen(
                                    state = profileState,
                                    scrollToTopSignal = scrollToTop,
                                    onOpenMyInfo = onOpenMyInfo,
                                    onOpenSettings = onOpenSettings,
                                    onRetry = onRetryProfile,
                                    miffs = miffs,
                                    onOpenMiff = onOpenMiff,
                                    placesContent = placesContent,
                                )
                            }

                            MainTab.UPDATE -> {
                                if (update != null) {
                                    UpdateTab(
                                        viewModel = update.viewModel,
                                        avatarCodec = update.avatarCodec,
                                        sharingStopped = sharingStopped,
                                        miffCount = ready?.miffs?.size ?: 1,
                                        actions =
                                            UpdateActions(
                                                onClose = { selected = MainTab.FEED },
                                                onResumeSharing = {
                                                    selected = MainTab.FEED
                                                    ready?.activeId?.let(onOpenMiff)
                                                },
                                                onReviewAgreement = {
                                                    selected = MainTab.FEED
                                                    update.onReviewAgreement()
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // The calm message and the bubble share ONE column above the bar: they can never overlap (the message goes above the bubble).
            UpdateBottomLayer(
                barInset = barInset,
                prompt = if (bubbleShown) prompt else PromptLevel.NONE,
                confirmation = confirmation,
                maxBodyHeight = (LocalConfiguration.current.screenHeightDp * BUBBLE_MAX_SCREEN_SHARE).dp,
                onBubbleHeight = { bubbleHeightPx = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private const val CONFIRMATION_MILLIS = 3_000L

/** The bubble takes 40 % of the screen at the most (design 4.3). */
private const val BUBBLE_MAX_SCREEN_SHARE = 0.4f

/**
 * The settings screens that do not depend on the tabs, shared by the full Settings (MainShell) and the RESTRICTED Settings of a person with no
 * Miff yet ([restrictedSettingsRoutes]): Mon compte (and from it the deletion of the account, AUTH-5, and the reset e-mail), Confidentialité
 * (and its help for the phones that stop the apps). One implementation, two hosts: nothing is duplicated.
 */
internal fun NavGraphBuilder.sharedSettingsRoutes(
    container: AppContainer,
    navController: NavHostController,
    scopeKey: String,
) {
    val session = container.sessionManager
    composable<AccountRoute> {
        val viewModel: AccountViewModel =
            viewModel(
                key = "account-$scopeKey",
                factory =
                    viewModelFactory {
                        initializer {
                            AccountViewModel(
                                email = container.authGateway.currentEmail(),
                                signIn = container.authGateway.accountSignIn(),
                                signOut = session::signOut,
                                passwordReset = container.passwordResetGateway,
                                network = container.networkMonitor,
                                cooldown = container.passwordResetCooldown,
                                nowMillis = SystemClock::elapsedRealtime,
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AccountScreen(
            state = state,
            onBack = { navController.popBackStackIfSafe() },
            onChangePassword = viewModel::onChangePasswordRequested,
            onSignOutRequested = viewModel::onSignOutRequested,
            onSignOutConfirmed = viewModel::onSignOutConfirmed,
            onSignOutCancelled = viewModel::onSignOutCancelled,
            onDeleteRequested = { navController.open(DeleteAccountRoute) },
        )
    }
    deleteAccountRoutes(container, navController, scopeKey)
    passwordResetRoute(container, navController)
    composable<PrivacyRoute> {
        val viewModel: PrivacyViewModel =
            viewModel(
                key = "privacy-$scopeKey",
                factory =
                    viewModelFactory {
                        initializer {
                            PrivacyViewModel(
                                privacy = container.privacySettings,
                                withdrawals = container.consentWithdrawals,
                                session = session,
                                network = container.networkMonitor,
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val location = rememberLocationPrivacy(container)
        PrivacyScreen(
            state = state,
            onBack = { navController.popBackStackIfSafe() },
            onAnalyticsToggled = viewModel::onAnalyticsToggled,
            onRetryWithdrawal = viewModel::onRetryWithdrawal,
            location = location,
            onReviewAgreement = container.awaitingAgreement::requestReview,
            onOpenDetectionHelp = { navController.open(DetectionHelpRoute) },
        )
    }
    composable<DetectionHelpRoute> {
        DetectionHelpScreen(onBack = { navController.popBackStackIfSafe() })
    }
}

/**
 * The RESTRICTED Settings of a person with no Miff yet (decision of the PM, 2026-11-07, which revises Q13: still no tab bar without a Miff,
 * but the account must be reachable). The list holds "Mon compte" and "Confidentialité" only (no "Mes informations", no "Langue"); every
 * screen behind it is the existing one ([sharedSettingsRoutes]). The way back is the simple one: the screen it was opened from.
 */
internal fun NavGraphBuilder.restrictedSettingsRoutes(
    container: AppContainer,
    navController: NavHostController,
    scopeKey: String,
) {
    restrictedSettingsListRoute(navController)
    sharedSettingsRoutes(container, navController, scopeKey)
}

/**
 * The LIST of the restricted Settings: "Mon compte" and "Confidentialité" only (no "Mes informations", no "Langue"). Its own function so that
 * the navigation test builds the very same route as the app ([restrictedSettingsRoutes]).
 */
internal fun NavGraphBuilder.restrictedSettingsListRoute(navController: NavHostController) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = { navController.popBackStackIfSafe() },
            onOpenMyInfo = null,
            onOpenAccount = { navController.open(AccountRoute) },
            onOpenLanguage = null,
            onOpenPrivacy = { navController.open(PrivacyRoute) },
        )
    }
}

/** The same launch-once navigation as the other hosts, for the entry of the restricted Settings. */
internal fun NavController.openSettings() = open(SettingsRoute)
