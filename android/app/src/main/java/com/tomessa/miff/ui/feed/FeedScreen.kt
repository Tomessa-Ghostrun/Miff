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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.feed.FeedBanner
import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.PositionFix
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.components.miffTopInsets
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
    val observe: suspend () -> Unit = {},
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
        )
    }
}

/**
 * The Feed tab (tickets FEED-1 to FEED-3): the fixed header, then the bubble of the Miff, the cards of the members (me first), and
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
    me: FeedMe = FeedMe(),
    actions: FeedActions = FeedActions(),
) {
    RecentsPrivacy()
    val texts = rememberFeedTexts()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduceMotion = ReduceMotion.isRequested(LocalContext.current)

    suspend fun toTop() = if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) toTop()
    }
    val paneName = stringResource(R.string.feed_pane_a11y)
    val content = (feed as? FeedUiState.Ready)?.content
    Column(modifier = Modifier.fillMaxSize().miffTopInsets().semantics { paneTitle = paneName }) {
        FeedHeader(
            me = me,
            myLevel = content?.myLevel ?: StatusLevel.NEUTRAL,
            myStatusDescription = content?.let { texts.label(it.myLabel) },
            onOpenProfile = actions.onOpenProfile,
            onLogo = { scope.launch { toTop() } },
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
                        MiffPill(ready, onSelect, onCreate, onJoin)
                    }
                }
            },
        )
        when (state) {
            DirectoryState.Loading -> {
                FeedSkeleton()
            }

            DirectoryState.Unavailable -> {
                Column(
                    modifier = Modifier.padding(Spacing.m),
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
                    NoMiff(onCreate, onJoin)
                } else {
                    FeedBody(state, feed, locate, agreement, texts, listState, actions, onRetry)
                }
            }
        }
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
) {
    val content = (feed as? FeedUiState.Ready)?.content
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.m),
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
                if (!agreement.isWaiting(state.activeId)) item { SkeletonBlocks() }
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
                readyItems(feed.content, locate, texts, actions)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.readyItems(
    content: FeedContent,
    locate: LocateUiState,
    texts: FeedTexts,
    actions: FeedActions,
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
