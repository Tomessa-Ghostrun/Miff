package com.tomessa.miff.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.miffBottomInsets
import com.tomessa.miff.ui.components.miffTopInset
import com.tomessa.miff.ui.theme.GradientBrandTitle
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBorderSubtle
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textTitleBold20

/** Number of slides of the welcome carousel. */
const val CAROUSEL_PAGE_COUNT = 3

/**
 * Welcome carousel (mockups "Welcoming screen", "-16", "-17" of design/exports/onboarding-original; ticket Onboarding
 * section 6.1), shown before the sign-up on the very FIRST opening only.
 *
 * Faithful to the mockups: the wordmark on top, the pale green shapes and the illustration of each slide, three dots,
 * the headline "Savoir qu'ils vont bien, sans les déranger" (the same on the three slides, there is no other sentence)
 * and the "Commencer" button. The layout follows the proportions of the mockups: everything is positioned in the
 * units of the mockups (320 wide) times the scale of the screen (one unit = screen width / 320).
 *
 * - Slides 1 and 2 are CROPS of the mockups (the screenshots of the Feed and "Nouveau lieu" screens, in French, with
 *   their real photos): resolution limited to the exports (155 x 290 px), to be replaced by better exports.
 * - Slide 3 is rebuilt in Compose: three cards with sharp text, and the three real profile photos cropped from the
 *   export (34 px each).
 * - The shapes are vectors traced from the mockups ([WelcomeBlobs]).
 * - Manual scrolling only (swipe, or the scroll actions of the screen reader): no automatic scrolling, no autoplay.
 * - No analytics: this screen has no access to the tracker at all (consent is only asked at the sign-up).
 * - "Page 1 sur 3" is announced by the screen reader when the page changes; "Commencer" is on every slide.
 * - Each illustration is described by one sentence for the screen reader and its inner texts are hidden from it.
 */
@Composable
fun WelcomeCarouselScreen(
    onStart: () -> Unit,
    onHaveAccount: () -> Unit,
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { CAROUSEL_PAGE_COUNT })
    val animations = rememberAnimationsEnabled()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colorBgCanvas)) {
        val unit: Dp = maxWidth / WelcomeBlobs.UNITS_WIDTH
        val topInset = miffTopInset()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .miffBottomInsets()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                    CarouselPage(page = page, unit = unit)
                }
                Image(
                    painter = painterResource(R.drawable.miff_wordmark_color),
                    contentDescription = stringResource(R.string.home_logo_a11y),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = maxOf(unit * WORDMARK_TOP_UNITS, topInset + Spacing.s))
                            .width(unit * WORDMARK_WIDTH_UNITS)
                            .semantics { heading() },
                )
                PageDots(
                    current = pagerState.currentPage,
                    animations = animations,
                    unit = unit,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = unit * DOTS_BOTTOM_UNITS),
                )
            }
            // Outside the pager: the sentence may grow freely with the system font size.
            Text(
                text = stringResource(R.string.onboarding_headline),
                style = textTitleBold20.copy(brush = GradientBrandTitle),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l).padding(top = Spacing.s),
            )
            Spacer(Modifier.height(Spacing.m))
            MiffButton(
                text = stringResource(R.string.onboarding_start),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
            )
            TextButton(onClick = onHaveAccount) {
                Text(
                    text = stringResource(R.string.onboarding_have_account),
                    style = textBodyBold16.copy(textDecoration = TextDecoration.Underline),
                    color = colorActionPrimary,
                )
            }
        }
    }
}

/** The wordmark of the mockups: 90 units wide, top at 48 units (or just below the status bar if that is lower). */
private const val WORDMARK_TOP_UNITS = 48f
private const val WORDMARK_WIDTH_UNITS = 90f

/** Distance between the dots and the bottom of the illustration area, in units (the dots sit at 440 of 480). */
private const val DOTS_BOTTOM_UNITS = 36f

/** The animations setting of the phone ("Remove animations" / animator duration scale 0): then nothing moves by itself. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** Pagination dots, announced as "Page 1 sur 3" (the dots themselves are decoration). */
@Composable
private fun PageDots(
    current: Int,
    animations: Boolean,
    unit: Dp,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.onboarding_page_a11y, current + 1, CAROUSEL_PAGE_COUNT)
    Row(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(unit * 7f),
    ) {
        repeat(CAROUSEL_PAGE_COUNT) { index ->
            val color by animateColorAsState(
                targetValue = if (index == current) colorActionPrimary else colorBorderSubtle,
                animationSpec = if (animations) tween(durationMillis = 200) else snap(),
                label = "dot",
            )
            Box(Modifier.size(unit * 8f).clip(CircleShape).background(color))
        }
    }
}

/** One slide: the traced shapes, then the illustration at the position of the mockup. */
@Composable
private fun CarouselPage(
    page: Int,
    unit: Dp,
) {
    val description =
        when (page) {
            0 -> R.string.onboarding_slide1_illustration_a11y
            1 -> R.string.onboarding_slide2_illustration_a11y
            else -> R.string.onboarding_slide3_illustration_a11y
        }
    val illustrationDescription = stringResource(description)
    // One sentence for the screen reader; the texts and photos drawn inside are decoration.
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = unit * WelcomeBlobs.UNITS_HEIGHT)
                .clearAndSetSemantics { contentDescription = illustrationDescription },
    ) {
        BlobShapes(page = page, modifier = Modifier.matchParentSize())
        when (page) {
            0 -> FeedIllustration(unit)
            1 -> NewPlaceIllustration(unit)
            else -> MessagesIllustration(unit)
        }
    }
}

/** The pale green shapes of the mockups, as vectors (see [WelcomeBlobs]). */
@Composable
private fun BlobShapes(
    page: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val scale = size.width / WelcomeBlobs.UNITS_WIDTH
        drawPath(WelcomeBlobs.path(page, scale), color = colorStatusSafeTint)
    }
}
