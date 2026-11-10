package com.tomessa.miff.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.AlertPage
import com.tomessa.miff.domain.feed.Bubble
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorStatusNeutral
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayHeadline
import kotlinx.coroutines.launch

/*
 * The bubble of the Miff (maquettes 02 to 05): ONE blob, in the colour of the Miff, under the header. It INFORMS, it never alarms: absolute
 * times for orange and red, no animation, no sound, no automatic scrolling, no point of exclamation, and never "en sécurité". A pictogram
 * is always there (the colour is never alone). Several alerts are a carousel (reds first), with dots only when there are several pages and
 * "Alerte précédente / suivante" for the screen reader, so that every page is reachable without a swipe.
 */

/** A soft organic blob (a smooth closed curve through ten points), like the stain of the PM's mockups. Symmetric in right-to-left. */
private object BubbleShape : Shape {
    private val points =
        listOf(
            Offset(0.50f, 0.02f),
            Offset(0.80f, 0.07f),
            Offset(0.98f, 0.30f),
            Offset(0.97f, 0.62f),
            Offset(0.80f, 0.94f),
            Offset(0.50f, 0.99f),
            Offset(0.20f, 0.93f),
            Offset(0.03f, 0.64f),
            Offset(0.03f, 0.30f),
            Offset(0.20f, 0.07f),
        )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val scaled = points.map { Offset(it.x * size.width, it.y * size.height) }

        fun mid(
            a: Offset,
            b: Offset,
        ) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        val start = mid(scaled.last(), scaled.first())
        path.moveTo(start.x, start.y)
        scaled.forEachIndexed { index, point ->
            val end = mid(point, scaled[(index + 1) % scaled.size])
            path.quadraticTo(point.x, point.y, end.x, end.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun BubbleView(
    bubble: Bubble,
    announceKey: String,
    texts: FeedTexts,
    onMessage: (phone: String) -> Unit,
    onResumeSharing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // What the screen reader says is fixed when the LEVEL (or the set of alerts) changes, not each minute when "il y a 2 min" changes.
    val announcement = remember(announceKey) { texts.announcement(bubble) }
    // The slow movement of the blob (null = still: animations removed by the person, or the screen not started). One for all the pages.
    val phases = rememberBubblePhases()
    Box(modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalBubblePhases provides phases) {
            when (bubble) {
                is Bubble.Alerts -> AlertCarousel(bubble.pages, texts, onMessage)
                else -> CalmBubble(bubble, texts, onResumeSharing)
            }
        }
        // Apart from the movement: nothing the screen reader says depends on it.
        Box(
            Modifier
                .size(1.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announcement
                },
        )
    }
}

@Composable
private fun CalmBubble(
    bubble: Bubble,
    texts: FeedTexts,
    onResumeSharing: () -> Unit,
) {
    BubbleBlob(level = bubble.level, pictogram = pictogram(bubble.level)) {
        when (bubble) {
            is Bubble.AllGood -> {
                Headline(stringResource(R.string.feed_bubble_all_good))
                bubble.latestNews?.let { Line(texts.latestNews(it)) }
                if (bubble.answered != null && bubble.total != null) {
                    Caption(stringResource(R.string.feed_bubble_answered, bubble.answered, bubble.total))
                }
            }

            Bubble.NoNews -> {
                Headline(stringResource(R.string.feed_bubble_no_news_title))
                Line(stringResource(R.string.feed_bubble_no_news_body))
            }

            Bubble.Alone -> {
                Headline(stringResource(R.string.feed_bubble_alone))
            }

            Bubble.AwaitingAgreement -> {
                Headline(stringResource(R.string.feed_bubble_no_news_title))
                Line(stringResource(R.string.miff_waiting_feed_note))
            }

            Bubble.SharingStopped -> {
                Headline(stringResource(R.string.feed_bubble_sharing_stopped_title))
                Line(stringResource(R.string.feed_bubble_sharing_stopped_body))
                BubbleButton(
                    text = stringResource(R.string.feed_bubble_resume_action),
                    icon = R.drawable.ic_loc_pin,
                    onClick = onResumeSharing,
                )
            }

            is Bubble.Alerts -> {}
        }
    }
}

@Composable
private fun AlertCarousel(
    pages: List<AlertPage>,
    texts: FeedTexts,
    onMessage: (String) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    // An alert that goes away takes its page with it: the carousel stays on a page that exists.
    LaunchedEffect(pages.size) {
        if (pager.currentPage > pages.lastIndex) pager.scrollToPage(pages.lastIndex)
    }
    val current = pager.currentPage.coerceIn(0, pages.lastIndex)
    val several = pages.size > 1
    val previous = stringResource(R.string.feed_bubble_previous_a11y)
    val next = stringResource(R.string.feed_bubble_next_a11y)
    val position = stringResource(R.string.feed_bubble_page_a11y, current + 1, pages.size)
    Column(
        modifier =
            Modifier.semantics {
                if (several) {
                    stateDescription = position
                    // Every page is reachable without a swipe, for the screen reader.
                    customActions =
                        listOf(
                            CustomAccessibilityAction(previous) {
                                if (pager.currentPage > 0) scope.launch { pager.scrollToPage(pager.currentPage - 1) }
                                true
                            },
                            CustomAccessibilityAction(next) {
                                if (pager.currentPage <
                                    pages.lastIndex
                                ) {
                                    scope.launch { pager.scrollToPage(pager.currentPage + 1) }
                                }
                                true
                            },
                        )
                }
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        // Keyed by the member: when an orange becomes red the ORDER changes, and each page must keep its own state.
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = several,
            key = { index -> pages.getOrNull(index)?.member?.uid ?: index },
        ) { index ->
            val page = pages[index]
            AlertPageBubble(page, texts, onMessage)
        }
        if (several) PagerDots(count = pages.size, current = current, level = pages[current].level)
    }
}

@Composable
private fun AlertPageBubble(
    page: AlertPage,
    texts: FeedTexts,
    onMessage: (String) -> Unit,
) {
    BubbleBlob(level = page.level, pictogram = pictogram(page.level)) {
        Headline(texts.alertTitle(page))
        texts.alertDetail(page)?.let { Line(it) }
        texts.alertNote(page)?.let { Caption(it) }
        texts.offlineAlert(page)?.let { Caption(it) }
        val phone = page.member.phone
        if (page.canMessage && phone != null) {
            BubbleButton(
                text = stringResource(R.string.feed_bubble_message_action),
                icon = R.drawable.ic_feed_chat,
                onClick = { onMessage(phone) },
            )
        }
    }
}

/** The movement of the bubble, given to every blob below [BubbleView] (null = still). */
private val LocalBubblePhases = compositionLocalOf<BubblePhases?> { null }

@Composable
private fun BubbleBlob(
    level: StatusLevel,
    pictogram: Pictogram?,
    content: @Composable () -> Unit,
) {
    val phases = LocalBubblePhases.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 176.dp)
                // Drawn, never laid out: the slow vertical drift of the whole bubble, read in the draw phase only (no recomposition, no
                // margin moves, no touch area moved by more than the drift itself).
                .graphicsLayer {
                    if (phases != null) translationY = BubbleMotion.driftDp(phases.blob.value) * density
                },
    ) {
        // The SHAPE only is deformed (squashed and stretched out of phase, a fraction of a degree of tilt, decision D45): the text and the button
        // in front of it are not scaled nor rotated. A layer of the GPU, read in the draw phase: no recomposition, no layout.
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        if (phases != null) {
                            val phase = phases.blob.value
                            scaleX = BubbleMotion.scaleX(phase)
                            scaleY = BubbleMotion.scaleY(phase)
                            rotationZ = BubbleMotion.tiltDegrees(phase)
                        }
                    }.clip(BubbleShape)
                    .background(level.tint()),
        )
        // CENTRED in the blob (decision of the PM, 2026-11-08, TestFlight build 22): the same margin on both sides, text and button centred, the
        // block centred vertically. The pictogram badge keeps its corner, so the margins are wide enough never to touch it; without a badge (the
        // green bubble) the margins are the plain ones.
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = if (pictogram != null) 56.dp else 40.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            content()
        }
        if (pictogram == null) return@Box
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = 40.dp)
                    .size(40.dp)
                    // The star breathes on its own, slightly different cycle: out of phase with the blob.
                    .graphicsLayer {
                        if (phases != null) {
                            val phase = phases.star.value
                            val breath = BubbleMotion.starScale(phase)
                            scaleX = breath
                            scaleY = breath
                            translationY = BubbleMotion.starDriftDp(phase) * density
                        }
                    }.clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(pictogram.drawable),
                contentDescription = null,
                tint = pictogram.tint(level),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private class Pictogram(
    val drawable: Int,
    val tint: (StatusLevel) -> Color,
)

/**
 * One pictogram per colour for orange, red and neutral (the colour is never alone): a clock, a "!", an hourglass. NONE for green (decision of the PM,
 * 2026-11-08): the green bubble has no symbol, its words are enough.
 */
private fun pictogram(level: StatusLevel): Pictogram? =
    when {
        level == StatusLevel.GREEN -> null
        level == StatusLevel.ORANGE -> Pictogram(R.drawable.ic_feed_clock) { it.onTint() }
        level == StatusLevel.RED -> Pictogram(R.drawable.ic_feed_alert) { it.onTint() }
        else -> Pictogram(R.drawable.ic_feed_hourglass) { colorTextDescription }
    }

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = textDisplayHeadline,
        color = colorTextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Line(text: String) {
    Text(text = text, style = textBodyRegular14, color = colorTextPrimary, textAlign = TextAlign.Center)
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = textBodyCaption, color = colorTextDescription, textAlign = TextAlign.Center)
}

/** The soft action of a bubble: outlined, white inside, 48 dp high. Never a call, never a red button. */
@Composable
private fun BubbleButton(
    text: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .heightIn(min = Spacing.minTouchTarget)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeAccent, colorBorderAction, ShapeMd)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(20.dp),
        )
        Text(text = text, style = textBodyBold16, color = colorActionPrimary, textAlign = TextAlign.Center)
    }
}

/** Only with several pages. The current page is a LONGER dot (a shape, not only a colour). */
@Composable
private fun PagerDots(
    count: Int,
    current: Int,
    level: StatusLevel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                Modifier
                    .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) level.onTint() else colorStatusNeutral),
            )
        }
    }
}
