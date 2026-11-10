package com.tomessa.miff.ui.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.miff.SelectorTap
import com.tomessa.miff.domain.profile.AvatarDecodePlan
import com.tomessa.miff.domain.profile.CardFaces
import com.tomessa.miff.domain.profile.CarouselItem
import com.tomessa.miff.domain.profile.FacesLayout
import com.tomessa.miff.domain.profile.MemberFace
import com.tomessa.miff.domain.profile.MiffCarousel
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderSubtle
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textCaptionBold12
import com.tomessa.miff.ui.theme.textDisplayTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the carousel of Miffs needs from the outside: the faces already known, and what to do when a card appears, when the list of Miffs changes,
 * and when the last card is touched (the creation, or the offer for a free account that already has a Miff: decision of the PM, 2026-11-08 and 2026-11-10).
 */
class MiffCarouselHost(
    val faces: Map<String, CardFaces> = emptyMap(),
    /** The card of this Miff is the one the person looks at: the view model reads its members if, and only if, it has to. */
    val onCardShown: (String) -> Unit = {},
    /** The ids of the Miffs of the person now (the faces of any other are forgotten). */
    val onMiffsKnown: (Set<String>) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onOffer: () -> Unit = {},
)

/**
 * "Mes Miffs" (mockups profil-0 and profil-1): a carousel of cards, one per Miff, then the card "Créer une Miff" as the LAST element. A card shows the
 * name, the number of members and their faces in miniature (photo, else initials), and opens the screen of the Miff. Dots under the carousel say
 * where the person is (decoration: the position is said by the cards themselves, "Carte 2 sur 3", and each card offers "Carte précédente / suivante"
 * to a screen reader, so that no swipe is needed).
 *
 * COST: nothing is read here. The page the person SETTLES on tells the host ([MiffCarouselHost.onCardShown]); a neighbour that is only composed
 * (so that a screen reader can reach it) reads nothing.
 */
@Composable
internal fun MiffCarouselSection(
    miffs: DirectoryState,
    host: MiffCarouselHost,
    onOpenMiff: (String) -> Unit,
) {
    val ready = miffs as? DirectoryState.Ready ?: return
    val items = remember(ready.miffs) { MiffCarousel.items(ready.miffs) }
    val ids = remember(ready.miffs) { ready.miffs.map { it.id } }
    val pager =
        rememberPagerState(
            initialPage = MiffCarousel.initialPage(ready.miffs, ready.activeId),
            pageCount = { items.size },
        )
    val onMiffsKnown by rememberUpdatedState(host.onMiffsKnown)
    val onCardShown by rememberUpdatedState(host.onCardShown)
    LaunchedEffect(ids) {
        onMiffsKnown(ids.toSet())
        // A Miff that went away takes its page with it: the carousel stays on a page that exists.
        if (pager.currentPage > items.lastIndex) pager.scrollToPage(items.lastIndex)
    }
    // The page the person settles on (and the one it opens on): only then is a card "shown".
    LaunchedEffect(pager, items) {
        snapshotFlow { pager.settledPage }.collect { page ->
            (items.getOrNull(page) as? CarouselItem.Card)?.let { onCardShown(it.miff.id) }
        }
    }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.profile_tab_miffs_title),
            style = textDisplayTitle,
            color = colorTextForm,
            modifier = Modifier.semantics { heading() },
        )
        HorizontalPager(
            state = pager,
            // The shadow of a card is drawn outside it: the carousel reaches a little beyond the margins of the screen (and its padding gives it back).
            modifier = Modifier.breakOutHorizontally(SHADOW_ROOM),
            contentPadding = PaddingValues(horizontal = SHADOW_ROOM),
            // Wider than the padding AND the shadow: the next card never shows at the edge while the person is on this one.
            pageSpacing = Spacing.l,
            // One neighbour is composed so that a screen reader can move to it; it reads nothing (see above).
            beyondViewportPageCount = 1,
            userScrollEnabled = items.size > 1,
            key = { index -> (items[index] as? CarouselItem.Card)?.miff?.id ?: CREATE_KEY },
        ) { page ->
            val position = stringResource(R.string.profile_tab_carousel_page_a11y, page + 1, items.size)
            val move = pageActions(pager, items.size, scope)
            Box(Modifier.padding(vertical = Spacing.s)) {
                when (val item = items[page]) {
                    is CarouselItem.Card -> {
                        MiffCard(
                            miff = item.miff,
                            faces = host.faces[item.miff.id] ?: CardFaces.Loading,
                            position = position,
                            actions = move,
                            onOpen = { onOpenMiff(item.miff.id) },
                        )
                    }

                    CarouselItem.Create -> {
                        CreateCard(
                            position = position,
                            actions = move,
                            onClick = {
                                // The same rule as the selector of the Feed (MiffCarousel.onCreateTap).
                                when (MiffCarousel.onCreateTap(ready.premiumOrNull, ready.miffs.size)) {
                                    SelectorTap.OpenOffer -> host.onOffer()
                                    SelectorTap.OpenCreate, SelectorTap.OpenJoin -> host.onCreate()
                                }
                            },
                        )
                    }
                }
            }
        }
        if (items.size > 1) CarouselDots(count = items.size, current = pager.currentPage)
    }
}

private const val CREATE_KEY = "create-a-miff"

/** The room the shadow of a card needs on each side. */
private val SHADOW_ROOM = Spacing.s

/** The "previous / next card" actions of a card, for a screen reader (no swipe needed). Empty with a single card. */
@Composable
private fun pageActions(
    pager: PagerState,
    count: Int,
    scope: CoroutineScope,
): List<CustomAccessibilityAction> {
    if (count < 2) return emptyList()
    val previous = stringResource(R.string.profile_tab_carousel_previous_a11y)
    val next = stringResource(R.string.profile_tab_carousel_next_a11y)
    return listOf(
        CustomAccessibilityAction(previous) {
            if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
            true
        },
        CustomAccessibilityAction(next) {
            if (pager.currentPage < count - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            true
        },
    )
}

/** Lays its content out [extra] wider on each side, without moving the rest of the screen: what the shadow of the cards needs. */
private fun Modifier.breakOutHorizontally(extra: Dp): Modifier =
    layout { measurable, constraints ->
        val grow = extra.roundToPx()
        val widened =
            constraints.copy(
                minWidth = (constraints.minWidth + 2 * grow).coerceAtLeast(0),
                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * grow else constraints.maxWidth,
            )
        val placeable = measurable.measure(widened)
        layout(constraints.constrainWidth(placeable.width - 2 * grow), placeable.height) { placeable.place(-grow, 0) }
    }

private val CardMinHeight = 96.dp
private val TileSize = 40.dp
private val FaceSize = 32.dp
private val FaceOverlap = 4.dp

@Composable
private fun MiffCard(
    miff: MiffSummary,
    faces: CardFaces,
    position: String,
    actions: List<CustomAccessibilityAction>,
    onOpen: () -> Unit,
) {
    val openLabel = stringResource(R.string.profile_tab_miff_open_a11y, miff.name)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                .heightIn(min = CardMinHeight)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics(mergeDescendants = true) {
                    stateDescription = position
                    customActions = actions
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        // As in the mockup the tile sits level with the name, the arrow stays in the middle.
        GroupTile(Modifier.align(Alignment.Top))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = miff.name,
                style = textBodyBold16,
                color = colorTextForm,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Nothing is said while the members are not known (or could not be read): no "0 membre", no error, the card keeps its name.
            (faces as? CardFaces.Ready)?.let { ready ->
                Text(
                    text = pluralStringResource(R.plurals.profile_tab_miff_members, ready.count, ready.count),
                    style = textBodyRegular14,
                    color = colorActionPrimary,
                )
                FacesRow(ready)
            }
            if (faces is CardFaces.Loading) FacesPlaceholder()
        }
        Chevron()
    }
}

@Composable
private fun CreateCard(
    position: String,
    actions: List<CustomAccessibilityAction>,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = CardMinHeight)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics(mergeDescendants = true) {
                    stateDescription = position
                    customActions = actions
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        GroupTile()
        Text(
            text = stringResource(R.string.profile_tab_miff_create),
            style = textBodyBold16,
            color = colorTextForm,
            modifier = Modifier.weight(1f),
        )
        Chevron()
    }
}

/** The pale blue square with the group icon of the mockups. Decoration. */
@Composable
private fun GroupTile(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(TileSize)
                .clip(ShapeMd)
                .background(colorActionPrimaryTint20)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_group),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(Spacing.l),
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_end),
        contentDescription = null,
        tint = colorActionPrimary,
    )
}

/**
 * The faces in miniature: up to 5 circles, and "+N" in the fifth when there are more than 5 members ([FacesLayout]). Decoration for a screen
 * reader (the number of members is written next to it, as text that grows with the font size): the circles keep their size.
 */
@Composable
private fun FacesRow(ready: CardFaces.Ready) {
    val layout = FacesLayout.of(ready.count)
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(-FaceOverlap),
    ) {
        ready.faces.take(layout.shown).forEach { face -> FaceCircle(face) }
        if (layout.more > 0) MoreCircle(layout.more)
    }
}

/** While the members are read: grey circles, never a spinner on a card, never a count. */
@Composable
private fun FacesPlaceholder() {
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(-FaceOverlap),
    ) {
        repeat(PLACEHOLDER_FACES) {
            Box(
                Modifier
                    .size(FaceSize)
                    .clip(CircleShape)
                    .background(colorActionPrimaryTint20)
                    .border(FACE_RING, colorBgSurface, CircleShape),
            )
        }
    }
}

private const val PLACEHOLDER_FACES = 3
private val FACE_RING = 2.dp

@Composable
private fun FaceCircle(face: MemberFace) {
    val targetPx = with(LocalDensity.current) { FaceSize.roundToPx() }
    val bitmap = remember(face.uid, face.avatarJpeg) { face.avatarJpeg?.let { decodeFace(it, targetPx) } }
    Box(
        modifier =
            Modifier
                .size(FaceSize)
                .clip(CircleShape)
                .background(colorActionPrimaryTint20)
                .border(FACE_RING, colorBgSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            FaceText(face.initials)
        }
    }
}

@Composable
private fun MoreCircle(more: Int) {
    Box(
        modifier =
            Modifier
                .size(FaceSize)
                .clip(CircleShape)
                .background(colorActionPrimaryTint20)
                .border(FACE_RING, colorBgSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FaceText("+$more")
    }
}

/** The text of a circle: its size is the circle's, not the font scale's (the circle does not grow, so the text would not fit). */
@Composable
private fun FaceText(text: String) {
    val size = with(LocalDensity.current) { FACE_TEXT.toSp() }
    Text(
        text = text,
        style = textCaptionBold12.merge(TextStyle(fontSize = size, lineHeight = size, textAlign = TextAlign.Center)),
        color = colorActionPrimary,
        maxLines = 1,
        softWrap = false,
    )
}

private val FACE_TEXT = 11.dp

/**
 * The photo of a member, small. It comes from ANOTHER account: it is decoded only if its announced size is one of an avatar of the app, and
 * subsampled to what the circle needs ([AvatarDecodePlan]). Null (initials are drawn) when it is not.
 */
private fun decodeFace(
    jpeg: ByteArray,
    targetPx: Int,
): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    val sample = AvatarDecodePlan.sampleSize(bounds.outWidth, bounds.outHeight, targetPx) ?: return null
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)?.asImageBitmap()
}

/** The dots of the carousel: the current one is a LONGER dot (a shape, not only a colour). Decoration. */
@Composable
private fun CarouselDots(
    count: Int,
    current: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                Modifier
                    .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) colorActionPrimary else colorBorderSubtle),
            )
        }
    }
}
