package com.tomessa.miff.ui.feed

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.feed.PhotoReader
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.feed.FeedBanner
import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.PositionFix
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.components.ConnectionHost
import com.tomessa.miff.ui.components.ConnectionInline
import com.tomessa.miff.ui.components.ConnectionPopIn
import com.tomessa.miff.ui.components.LocalBottomOverlayPadding
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.components.miffTopInsets
import com.tomessa.miff.ui.main.LocalTabBarInset
import com.tomessa.miff.ui.miff.AgreementUi
import com.tomessa.miff.ui.miff.AwaitingAgreementCard
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.miff.MiffSharing
import com.tomessa.miff.ui.splash.ReduceMotion
import com.tomessa.miff.ui.theme.ShapeFull
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorBgPlaceholder
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorStatusSafeText
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the buttons of the Feed do. The Feed never writes anything: the gestures are the hand-over to the system applications (messages,
 * dialler, maps, share sheet), the opening of another screen of the app, and the one reading of a position.
 */
class FeedActions(
    val onOpenProfile: () -> Unit = {},
    val onLocate: (uid: String) -> Unit = {},
    val onLocateDismissed: () -> Unit = {},
    val onMessage: (phone: String) -> Unit = {},
    val onCall: (phone: String) -> Unit = {},
    val onAddLovedOne: (miffName: String, code: String) -> Unit = { _, _ -> },
    /** True when an application took the coordinates. */
    val onOpenMap: (PositionFix) -> Boolean = { false },
    /** "Reprendre le partage": the Miff screen holds the dialog and the consent (nothing is written from the Feed). */
    val onResumeSharing: (miffId: String) -> Unit = {},
    val onRetryFeed: () -> Unit = {},
    /** Pull to refresh and the TalkBack action "Actualiser": ONE fresh reading from the server (bounded: see FeedViewModel.onRefresh). */
    val onRefresh: () -> Unit = {},
)

/**
 * What the Feed tab needs from the outside, in one piece (so that [com.tomessa.miff.ui.main.MainTabs] does not grow a parameter per button):
 * the state computed by the view model, the sheet, who I am, the callbacks, and [observe], the suspending reading that lives while the tab is on screen.
 */
class FeedHost(
    val state: FeedUiState = FeedUiState.Loading,
    val locate: LocateUiState = LocateUiState.Idle,
    val me: FeedMe = FeedMe(),
    val onLocate: (uid: String) -> Unit = {},
    val onLocateDismissed: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** True while a forced reading of "Actualiser" is under way. */
    val refreshing: Boolean = false,
    val onRefresh: () -> Unit = {},
    val observe: suspend () -> Unit = {},
    /** FEED-4: where the photos are read (null: no photo is drawn). */
    val photos: PhotoReader? = null,
    /** The pop-in "Pas de connexion" (state and buttons): shown only while the Feed has nothing to show. */
    val connection: ConnectionHost = ConnectionHost(),
)

/** The hand-overs to the applications of the system, with the calm message of the ticket when none answers. */
@Composable
fun rememberSystemFeedActions(base: FeedActions): FeedActions {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.feed_action_unavailable)
    return remember(base, context, unavailable) {
        fun tell(ok: Boolean) {
            if (!ok) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
        }
        FeedActions(
            onOpenProfile = base.onOpenProfile,
            onLocate = base.onLocate,
            onLocateDismissed = base.onLocateDismissed,
            onMessage = { tell(FeedIntents.message(context, it)) },
            onCall = { tell(FeedIntents.call(context, it)) },
            onAddLovedOne = { name, code -> MiffSharing.share(context, name, code) },
            onOpenMap = { FeedIntents.openMap(context, it) },
            onResumeSharing = base.onResumeSharing,
            onRetryFeed = base.onRetryFeed,
            onRefresh = base.onRefresh,
        )
    }
}

/**
 * The Feed tab (tickets FEED-1 to FEED-3): the fixed header, then the bubble of the Miff, the cards of the members (me last), and
 * "Ajouter un proche". [feed] is what the view model computed; [locate] the sheet "Localiser". The photo of the last update is FEED-4.
 * [scrollToTopSignal] goes up each time the person taps the tab they are already on.
 *
 * Nothing here says "tout va bien" without knowing: the skeleton, the empty Miff, the offline mention and the neutral bubbles are
 * calm and never a phrase of safety.
 */
@Composable
fun FeedScreen(
    state: DirectoryState,
    scrollToTopSignal: Int,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    /** LOC-2b, design 23A: the Miffs that wait for the agreement of the person (nothing is shared or detected for them). */
    agreement: AgreementUi = AgreementUi(),
    feed: FeedUiState = FeedUiState.Loading,
    locate: LocateUiState = LocateUiState.Idle,
    /** The indicator of the pull to refresh: a forced reading of the server is under way. */
    refreshing: Boolean = false,
    me: FeedMe = FeedMe(),
    actions: FeedActions = FeedActions(),
    /** The pop-in "Pas de connexion" (bug of 2026-11-10): shown only while there is nothing to show; once closed, the page says it itself. */
    popIn: ConnectionHost = ConnectionHost(),
    popInSuppressed: Boolean = false,
) {
    RecentsPrivacy()
    val texts = rememberFeedTexts()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduceMotion = ReduceMotion.isRequested(LocalContext.current)

    suspend fun toTop() = if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
    // FEED-4: the photo opened in full screen (nothing of it is kept once it is closed or the screen is left).
    var opened by remember { mutableStateOf<OpenedPhoto?>(null) }
    val paneName = stringResource(R.string.feed_pane_a11y)
    val content = (feed as? FeedUiState.Ready)?.content
    val density = LocalDensity.current

    // The header hides while the person scrolls down and is back AT ONCE when they scroll up (decision of the PM, 2026-11-06).
    // The last measured height is kept (rotation, font size): the first image already has the right room, no jump while it is measured again.
    val header = rememberSaveable(saver = CollapsingHeaderState.Saver) { CollapsingHeaderState() }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            header.show()
            toTop()
        }
    }
    var selectorOpen by remember { mutableStateOf(false) }
    val accessibilityOn = rememberAccessibilityActive()
    val isList = state is DirectoryState.Ready && state.miffs.isNotEmpty() && feed is FeedUiState.Ready
    val offline =
        content?.banners?.any { it is FeedBanner.Offline } == true ||
            (state as? DirectoryState.Ready)?.fromCache == true
    // Always visible: while it loads, on an error, offline, with an accessibility service on (it would take the focus with it), and while the
    // selector of the Miffs is open. (A list too short to scroll never moves it: no scroll reaches it.)
    rememberHeaderLock(header, locked = !isList || offline || accessibilityOn || selectorOpen)
    val connection = remember(header, listState) { CollapsingHeaderConnection(header, listState) }
    // The room of the header at the top of the content: measured, so that nothing is hidden under it and no band is left blank (font size, language).
    val headerRoom = with(density) { header.heightPx.toDp() }
    // TABBAR-GLASS: the glass bar floats over the tab (it is measured, its height is given by LocalTabBarInset): every state keeps that room at the bottom,
    // so that the last element can always scroll above it.
    val tabBarRoom = LocalTabBarInset.current

    // Back to the top (logo, tap on the tab already in use): the header is shown again.
    suspend fun toTopAndShow() {
        header.show()
        toTop()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .miffTopInsets()
                .clipToBounds()
                .nestedScroll(connection)
                .semantics {
                    paneTitle = paneName
                    // The header is drawn after the list (it floats above it): a screen reader reads it FIRST all the same (see FeedHeader below).
                    isTraversalGroup = true
                },
    ) {
        when (state) {
            DirectoryState.Loading -> {
                Box(Modifier.padding(top = headerRoom, bottom = tabBarRoom)) {
                    if (popIn.state.inlineVisible) {
                        ConnectionInline(
                            popIn,
                            Modifier.padding(Spacing.m),
                        )
                    } else {
                        FeedSkeleton()
                    }
                }
            }

            DirectoryState.Unavailable -> {
                Column(
                    modifier = Modifier.padding(top = headerRoom, bottom = tabBarRoom).padding(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    MiffNotice(stringResource(R.string.feed_unavailable))
                    MiffSecondaryButton(
                        text = stringResource(R.string.auth_retry_action),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is DirectoryState.Ready -> {
                if (state.miffs.isEmpty()) {
                    Box(Modifier.padding(top = headerRoom, bottom = tabBarRoom)) { NoMiff(onCreate, onJoin) }
                } else {
                    FeedBody(
                        state,
                        feed,
                        locate,
                        agreement,
                        texts,
                        listState,
                        actions,
                        onRetry,
                        onOpenPhoto = { opened = it },
                        topRoom = headerRoom,
                        refreshing = refreshing,
                        popIn = popIn,
                    )
                }
            }
        }
        FeedHeader(
            me = me,
            myLevel = content?.myLevel ?: StatusLevel.NEUTRAL,
            myStatusDescription = content?.let { texts.label(it.myLabel) },
            onOpenProfile = actions.onOpenProfile,
            onLogo = { scope.launch { toTopAndShow() } },
            // Moved by a translation read in the draw phase only: no recomposition, no layout, no margin that moves.
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { header.onMeasured(it.height) }
                    .graphicsLayer { translationY = header.offsetPx }
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = HEADER_TRAVERSAL_INDEX
                    },
            pill = {
                val ready = state as? DirectoryState.Ready
                when {
                    ready == null -> {}

                    ready.miffs.isEmpty() -> {
                        Pill(
                            text = stringResource(R.string.feed_selector_none),
                            selectable = false,
                            contentLabel = null,
                            onClick = {},
                        )
                    }

                    else -> {
                        MiffPill(ready, onSelect, onCreate, onJoin, onOpenChanged = { selectorOpen = it })
                    }
                }
            },
        )
        // Polite announcement of "Actualiser" (a node of one dp INSIDE the safe area, drawn nowhere).
        RefreshAnnouncement(refreshing, offline)
    }
    // Nothing to show and no network (or no answer for a while): the calm pop-in, with "Réessayer" and the countdown of the next automatic attempt.
    ConnectionPopIn(popIn, suppressed = popInSuppressed)
    opened?.let { photo ->
        PhotoFullScreen(
            opened = photo,
            texts = texts,
            nowMillis = System.currentTimeMillis(),
            onOpenMap = actions.onOpenMap,
            onDismiss = { opened = null },
        )
    }
    val shown = locate as? LocateUiState.Shown
    if (shown != null) {
        LocateSheet(
            firstName = shown.firstName,
            result = shown.result,
            texts = texts,
            onOpenMap = actions.onOpenMap,
            onDismiss = actions.onLocateDismissed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedBody(
    state: DirectoryState.Ready,
    feed: FeedUiState,
    locate: LocateUiState,
    agreement: AgreementUi,
    texts: FeedTexts,
    listState: androidx.compose.foundation.lazy.LazyListState,
    actions: FeedActions,
    onRetry: () -> Unit,
    onOpenPhoto: (OpenedPhoto) -> Unit,
    /** The header is drawn over the list: its height is the top padding of the content, so that the first card starts under it. */
    topRoom: Dp,
    refreshing: Boolean,
    popIn: ConnectionHost,
) {
    val content = (feed as? FeedUiState.Ready)?.content
    val pullState = rememberPullToRefreshState()
    val refreshLabel = stringResource(R.string.feed_refresh_a11y)
    // Pull to refresh: the gesture at the top of the list, and, for TalkBack, the same thing as an action of the list ("Actualiser").
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = actions.onRefresh,
        state = pullState,
        modifier =
            Modifier.fillMaxSize().semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(refreshLabel) {
                            actions.onRefresh()
                            true
                        },
                    )
            },
        // Discreet, in the colours of the app, and under the header (which stays where it is: the gesture is at the top of the list only).
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topRoom),
                containerColor = colorBgSurface,
                color = colorActionPrimary,
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.m,
                    end = Spacing.m,
                    top = topRoom + Spacing.m,
                    // TABBAR-GLASS: the last element scrolls above the glass bar, never under it; and above the bubble of encouragement when it is shown (UPDATE-2).
                    bottom = Spacing.m + LocalTabBarInset.current + LocalBottomOverlayPadding.current,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.fromCache && content == null) {
                item { MiffNotice(stringResource(R.string.feed_offline_note)) }
            }
            if (agreement.isWaiting(state.activeId)) {
                // "Pas maintenant" was chosen: a permanent card tells the truth (nothing is shared), calmly, with the one way back.
                item {
                    AwaitingAgreementCard(
                        body = stringResource(R.string.miff_waiting_feed_body, state.active?.name.orEmpty()),
                        onReview = agreement.onReview,
                    )
                }
            }
            when (feed) {
                FeedUiState.Loading -> {
                    if (!agreement.isWaiting(state.activeId)) {
                        item { if (popIn.state.inlineVisible) ConnectionInline(popIn) else SkeletonBlocks() }
                    }
                }

                FeedUiState.Unavailable -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            MiffNotice(stringResource(R.string.feed_unavailable))
                            MiffSecondaryButton(
                                text = stringResource(R.string.auth_retry_action),
                                onClick = {
                                    actions.onRetryFeed()
                                    onRetry()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                is FeedUiState.Ready -> {
                    readyItems(feed.content, locate, texts, actions, onOpenPhoto)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.readyItems(
    content: FeedContent,
    locate: LocateUiState,
    texts: FeedTexts,
    actions: FeedActions,
    onOpenPhoto: (OpenedPhoto) -> Unit,
) {
    items(content.banners, key = { it::class.simpleName.orEmpty() }) { banner -> BannerView(banner, texts) }
    item(key = "bubble") {
        BubbleView(
            bubble = content.bubble,
            announceKey = content.announceKey,
            texts = texts,
            onMessage = actions.onMessage,
            onResumeSharing = { actions.onResumeSharing(content.miffId) },
        )
    }
    items(content.cards, key = { it.member.uid }) { card ->
        MemberCardView(
            card = card,
            texts = texts,
            locating = (locate as? LocateUiState.Loading)?.uid == card.member.uid,
            onLocate = actions.onLocate,
            onMessage = actions.onMessage,
            onCall = actions.onCall,
            miffId = content.miffId,
            nowMillis = System.currentTimeMillis(),
            onOpenPhoto = onOpenPhoto,
        )
    }
    item(key = "add") {
        if (content.isFull) {
            FullNote()
        } else {
            MiffButton(
                text = stringResource(R.string.feed_add_loved_one),
                onClick = { actions.onAddLovedOne(content.miffName, content.joinCode) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Polite announcement of "Actualiser" for the screen reader: "Actualisation en cours" at the start and "Informations actualisées" at the end (nothing is
 * claimed up to date when the Feed is telling "Dernières informations de…"). A node apart, one dp: the sentence is not drawn.
 */
@Composable
private fun RefreshAnnouncement(
    refreshing: Boolean,
    stale: Boolean,
) {
    // 0 = nothing to say, 1 = a reading is under way, 2 = it has just ended. "Informations actualisées" is said ONCE, when a reading ends, never
    // again later (for example when the network comes back).
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            phase = 1
        } else if (phase == 1) {
            phase = 2
            delay(REFRESH_DONE_ANNOUNCEMENT_MILLIS)
            phase = 0
        }
    }
    val text =
        when {
            refreshing -> stringResource(R.string.feed_refresh_started_a11y)
            phase == 2 && !stale -> stringResource(R.string.feed_refresh_done_a11y)
            else -> ""
        }
    Box(
        Modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        },
    )
}

/** A mention above the bubble: calm, grey for "no connection" (no error tint), pale blue for "Partage de ton statut en cours…". */
@Composable
private fun BannerView(
    banner: FeedBanner,
    texts: FeedTexts,
) {
    when (banner) {
        is FeedBanner.Offline -> {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(ShapeMd)
                        .background(colorBgSurface)
                        .border(StrokeHairline, colorBorderStrong, ShapeMd)
                        .padding(Spacing.m)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_cloud_off),
                    contentDescription = null,
                    tint = colorTextPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Text(text = texts.offlineBanner(banner.latest), style = textBodyRegular14, color = colorTextPrimary)
            }
        }

        FeedBanner.Publishing -> {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(ShapeMd)
                        .background(colorActionPrimaryTint10)
                        .padding(Spacing.m)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Decorative: the text carries the meaning, and it is never an error.
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).semantics { contentDescription = "" },
                    strokeWidth = 2.dp,
                    color = colorActionPrimary,
                )
                Text(
                    text = stringResource(R.string.feed_banner_publishing),
                    style = textBodyRegular14,
                    color = colorTextPrimary,
                )
            }
        }
    }
}

/** "Cette Miff est bien remplie !": a green note, not interactive, that does not look like a grey button. */
@Composable
private fun FullNote() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorStatusSafeTint)
                .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_group),
            contentDescription = null,
            tint = colorStatusSafeText,
            modifier = Modifier.size(20.dp),
        )
        Text(text = stringResource(R.string.feed_miff_full), style = textBodyBold14, color = colorStatusSafeText)
    }
}

/** The skeleton: grey shapes where the bubble and two cards will be. No colour of status, no text of safety, no animation. */
@Composable
private fun FeedSkeleton() {
    Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonBlocks()
    }
}

@Composable
private fun SkeletonBlocks() {
    val loading = stringResource(R.string.feed_loading_a11y)
    Column(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // A calm word under the shapes: the wait is told, and nothing is said about the news before it is known.
        Text(
            text = stringResource(R.string.feed_loading_news),
            style = textBodyRegular14,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(ShapeFull)
                .background(colorBgPlaceholder.copy(alpha = 0.45f)),
        )
        repeat(2) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(ShapeMd)
                        .background(colorBgSurface)
                        .border(StrokeHairline, colorBgPlaceholder, ShapeMd)
                        .padding(Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(colorBgPlaceholder.copy(alpha = 0.65f)))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Box(
                        Modifier
                            .size(
                                width = 120.dp,
                                height = 16.dp,
                            ).clip(ShapeMd)
                            .background(colorBgPlaceholder.copy(alpha = 0.65f)),
                    )
                    Box(
                        Modifier
                            .size(
                                width = 180.dp,
                                height = 24.dp,
                            ).clip(ShapeMd)
                            .background(colorBgPlaceholder.copy(alpha = 0.65f)),
                    )
                }
            }
        }
    }
}

/** "Pas encore de Miff": a calm sentence, and the two ways to begin (the safety net: the person is normally never here). */
@Composable
private fun NoMiff(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(
            text = stringResource(R.string.feed_nomiff_body),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Spacing.l),
        )
        MiffButton(
            text = stringResource(R.string.feed_create_action),
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
        )
        MiffButton(
            text = stringResource(R.string.feed_join_action),
            onClick = onJoin,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A screen reader reads the header (avatar, logo, Miff) before the list although it is drawn after it: the lowest index of the screen. */
internal const val HEADER_TRAVERSAL_INDEX = -1f

/** The sentence "Informations actualisées" stays this long in its node, then the node is empty again. */
private const val REFRESH_DONE_ANNOUNCEMENT_MILLIS = 3_000L
