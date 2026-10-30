package com.tomessa.miff.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
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
import com.tomessa.miff.ui.theme.GradientBrandTitle
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderSubtle
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextSecondary
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
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
        val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ).verticalScroll(rememberScrollState()),
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
            0 -> ScreenshotIllustration(R.drawable.onboarding_slide1_status, unit)
            1 -> ScreenshotIllustration(R.drawable.onboarding_slide2_place, unit)
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

/** Slides 1 and 2: the screenshot of the mockup (155 x 290 px), at 82 / 125 units like in the mockup. */
@Composable
private fun ScreenshotIllustration(
    drawable: Int,
    unit: Dp,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier =
            Modifier
                .offset(x = unit * 82f, y = unit * 125f)
                .size(width = unit * 155f, height = unit * 290f)
                .shadow(4.dp, RoundedCornerShape(2.dp), clip = false),
    )
}

/** Slide 3: the three cards of the mockup (left 31 / 50 / 30, top 169 / 246 / 323, 259 units wide, 62 high at least). */
@Composable
private fun MessagesIllustration(unit: Dp) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(top = unit * 169f, bottom = unit * 44f),
            verticalArrangement = Arrangement.spacedBy(unit * 15f),
        ) {
            MessageCard(
                photo = painterResource(R.drawable.onboarding_avatar_thomas),
                message = R.string.onboarding_sample_msg_1,
                whenText = R.string.onboarding_sample_when_1,
                unit = unit,
                left = 31f,
            )
            MessageCard(
                photo = painterResource(R.drawable.onboarding_avatar_amandine),
                message = R.string.onboarding_sample_msg_2,
                whenText = R.string.onboarding_sample_when_2,
                unit = unit,
                left = 50f,
            )
            MessageCard(
                photo = painterResource(R.drawable.onboarding_avatar_maman),
                message = R.string.onboarding_sample_msg_3,
                whenText = R.string.onboarding_sample_when_3,
                unit = unit,
                left = 30f,
            )
        }
        // The small yellow-green dot on the corner of the first card.
        Box(
            Modifier
                .offset(x = unit * 264.5f, y = unit * 161.5f)
                .size(unit * 14f)
                .clip(CircleShape)
                .background(colorBrandPrimary),
        )
    }
}

@Composable
private fun MessageCard(
    photo: Painter,
    message: Int,
    whenText: Int,
    unit: Dp,
    left: Float,
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = colorBgSurface),
        border = BorderStroke(1.dp, colorActionPrimaryTint20),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.padding(start = unit * left).width(unit * 259f),
    ) {
        Row(
            modifier = Modifier.heightIn(min = unit * 62f).padding(horizontal = unit * 5f, vertical = unit * 6f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(unit * 4f),
        ) {
            // The photo of the mockup (a 34 px crop) inside the blue ring.
            Image(
                painter = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(unit * 38f)
                        .border(2.dp, colorActionPrimary, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(message), style = textBodyBold14, color = colorTextForm)
                Text(stringResource(whenText), style = textBodyCaption, color = colorTextSecondary)
            }
        }
    }
}
