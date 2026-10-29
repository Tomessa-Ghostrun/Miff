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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.GradientBrandTitle
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.ShapeSm
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBorderSubtle
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.colorTextSecondary
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayHeadline
import com.tomessa.miff.ui.theme.textDisplayLabel

/** Number of slides of the welcome carousel. */
const val CAROUSEL_PAGE_COUNT = 3

/**
 * Welcome carousel (mockups "Welcoming screen", "-16", "-17"; ticket Onboarding section 6.1), shown before the sign-up
 * on the very FIRST opening only.
 *
 * - Manual scrolling only (swipe, or the scroll actions of the screen reader): no automatic scrolling, no autoplay.
 * - No analytics: this screen has no access to the tracker at all (consent is only asked at the sign-up).
 * - "Page 1 sur 3" is announced by the screen reader when the page changes; "Commencer" is on every slide.
 * - The illustrations are PROVISIONAL drawings made of Compose shapes (no photo): they will be replaced by the final
 *   exports. They are described by one sentence for the screen reader and their inner texts are hidden from it.
 */
@Composable
fun WelcomeCarouselScreen(
    onStart: () -> Unit,
    onHaveAccount: () -> Unit,
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { CAROUSEL_PAGE_COUNT })
    val animations = rememberAnimationsEnabled()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.miff_wordmark_color),
            contentDescription = stringResource(R.string.home_logo_a11y),
            modifier = Modifier.padding(top = Spacing.s).width(Spacing.logoWidth / 2).semantics { heading() },
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(IllustrationHeight),
        ) { page ->
            CarouselIllustration(page)
        }
        PageDots(current = pagerState.currentPage, animations = animations)
        // Outside the pager: the sentence may grow freely with the system font size.
        Text(
            text = stringResource(captionOf(pagerState.currentPage)),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        )
        Spacer(Modifier.height(Spacing.m))
        Text(
            text = stringResource(R.string.onboarding_headline),
            style = textDisplayHeadline.copy(brush = GradientBrandTitle),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.l))
        MiffButton(
            text = stringResource(R.string.onboarding_start),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
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
) {
    val description = stringResource(R.string.onboarding_page_a11y, current + 1, CAROUSEL_PAGE_COUNT)
    Row(
        modifier =
            Modifier
                .padding(vertical = Spacing.s)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        repeat(CAROUSEL_PAGE_COUNT) { index ->
            val color by animateColorAsState(
                targetValue = if (index == current) colorActionPrimary else colorBorderSubtle,
                animationSpec = if (animations) tween(durationMillis = 200) else snap(),
                label = "dot",
            )
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        }
    }
}

private fun captionOf(page: Int): Int =
    when (page) {
        0 -> R.string.onboarding_slide1_caption
        1 -> R.string.onboarding_slide2_caption
        else -> R.string.onboarding_slide3_caption
    }

@Composable
private fun CarouselIllustration(page: Int) {
    val description =
        when (page) {
            0 -> R.string.onboarding_slide1_illustration_a11y
            1 -> R.string.onboarding_slide2_illustration_a11y
            else -> R.string.onboarding_slide3_illustration_a11y
        }
    val illustrationDescription = stringResource(description)
    // One sentence for the screen reader; the texts drawn inside are decoration.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { contentDescription = illustrationDescription },
        contentAlignment = Alignment.Center,
    ) {
        DecorativeBlobs()
        when (page) {
            0 -> StatusIllustration()
            1 -> PlaceIllustration()
            else -> MessagesIllustration()
        }
    }
}

private val IllustrationHeight = 320.dp

/** Pale green organic shapes of the mockups, as vector shapes. */
@Composable
private fun DecorativeBlobs() {
    Canvas(Modifier.fillMaxSize()) {
        val blob = colorStatusSafe.copy(alpha = 0.28f)
        drawOval(
            blob,
            topLeft = Offset(size.width * 0.02f, size.height * 0.10f),
            size =
                Size(
                    size.width * 0.80f,
                    size.height * 0.62f,
                ),
        )
        drawOval(
            blob,
            topLeft = Offset(size.width * 0.28f, size.height * 0.40f),
            size =
                Size(
                    size.width * 0.72f,
                    size.height * 0.55f,
                ),
        )
    }
}

@Composable
private fun MockCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        shape = ShapeSm,
        colors = CardDefaults.cardColors(containerColor = colorBgSurface),
        border = BorderStroke(1.dp, colorBorderDefault),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier,
    ) {
        content()
    }
}

/** A round avatar with an initial and the status ring (no photo of a real person). */
@Composable
private fun InitialAvatar(
    name: String,
    ring: Color = colorActionPrimary,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .border(3.dp, ring, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(colorActionPrimaryTint20),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name.take(1), style = textDisplayLabel, color = colorActionPrimary)
    }
}

@Composable
private fun StatusIllustration() {
    MockCard(Modifier.fillMaxWidth(0.82f)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.onboarding_sample_all_safe), style = textDisplayLabel, color = colorTextForm)
            Text(
                stringResource(R.string.onboarding_sample_updated),
                style = textBodyCaption,
                color = colorTextSecondary,
            )
            MemberRow(R.string.onboarding_sample_name_1, R.string.onboarding_sample_status_school)
            MemberRow(R.string.onboarding_sample_name_2, R.string.onboarding_sample_status_home)
        }
    }
}

@Composable
private fun MemberRow(
    nameRes: Int,
    statusRes: Int,
) {
    val name = stringResource(nameRes)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        InitialAvatar(name, ring = colorStatusSafe)
        Column {
            Text(name, style = textDisplayLabel, color = colorTextForm)
            Text(
                text = stringResource(statusRes),
                style = textBodyCaption,
                color = colorTextPrimary,
                modifier =
                    Modifier
                        .clip(
                            ShapeSm,
                        ).background(colorStatusSafeTint)
                        .padding(horizontal = Spacing.s, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PlaceIllustration() {
    MockCard(Modifier.fillMaxWidth(0.82f)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                stringResource(R.string.onboarding_sample_place_title),
                style = textDisplayLabel,
                color = colorTextForm,
            )
            Text(stringResource(R.string.onboarding_sample_place_name), style = textBodyBold14, color = colorTextForm)
            FieldBox(stringResource(R.string.onboarding_sample_place_name_value))
            Text(
                stringResource(R.string.onboarding_sample_place_address),
                style = textBodyBold14,
                color = colorTextForm,
            )
            FieldBox(stringResource(R.string.onboarding_sample_place_address_value))
            Text(stringResource(R.string.onboarding_sample_place_radius), style = textBodyBold14, color = colorTextForm)
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(colorActionPrimary.copy(alpha = 0.18f), radius = size.height / 2f, center = center)
                drawCircle(colorActionPrimary, radius = 5.dp.toPx(), center = center)
            }
        }
    }
}

@Composable
private fun FieldBox(text: String) {
    Text(
        text = text,
        style = textBodyRegular14,
        color = colorTextDescription,
        modifier = Modifier.fillMaxWidth().border(1.dp, colorBorderSubtle, ShapeSm).padding(Spacing.s),
    )
}

@Composable
private fun MessagesIllustration() {
    Column(Modifier.fillMaxWidth(0.92f), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        MessageBubble(
            R.string.onboarding_sample_name_1,
            R.string.onboarding_sample_msg_1,
            R.string.onboarding_sample_when_1,
            0.dp,
        )
        MessageBubble(
            R.string.onboarding_sample_name_2,
            R.string.onboarding_sample_msg_2,
            R.string.onboarding_sample_when_2,
            24.dp,
        )
        MessageBubble(
            R.string.onboarding_sample_name_3,
            R.string.onboarding_sample_msg_3,
            R.string.onboarding_sample_when_3,
            0.dp,
        )
    }
}

@Composable
private fun MessageBubble(
    nameRes: Int,
    messageRes: Int,
    whenRes: Int,
    shift: androidx.compose.ui.unit.Dp,
) {
    MockCard(Modifier.fillMaxWidth().offset(x = shift)) {
        Row(
            modifier = Modifier.padding(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            InitialAvatar(stringResource(nameRes))
            Column(Modifier.weight(1f)) {
                Text(stringResource(messageRes), style = textBodyBold14, color = colorTextForm)
                Text(stringResource(whenRes), style = textBodyCaption, color = colorTextSecondary)
            }
        }
    }
}
