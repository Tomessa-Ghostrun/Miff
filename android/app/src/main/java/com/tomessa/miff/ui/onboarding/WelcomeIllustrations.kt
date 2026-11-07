package com.tomessa.miff.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.FontFamilyHanken
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.colorTextSecondary

/*
 * The three illustrations of the welcome carousel, REBUILT in vectors and real text from the PM's mockups
 * (design/exports/onboarding-original: "Welcoming screen", "-16", "-17") so that they stay perfectly sharp on any
 * screen: shapes, icons, shadows and texts are drawn by Compose, only the PHOTOS (profiles, living room, map
 * background) are bitmaps (res/drawable-nodpi/onboarding_photo_*.jpg, cropped from the 320 x 640 exports and upsampled).
 *
 * Everything is placed in "units" of the mockups (the carousel is 320 units wide, one unit = screen width / 320),
 * sizes of texts included: the illustrations are DECORATIVE previews, described by one sentence for the screen reader,
 * so they follow the width of the screen, not the system font size.
 *
 * To use sharper photos: replace the files res/drawable-nodpi/onboarding_photo_{thomas,amandine,maman,salon,map}.jpg
 * (any resolution, same framing; they are scaled with "crop"). No code change is needed.
 */

/** Baloo Thambi 2 (display font of the design system) with a bold variation, for the titles of the mockups. */
@OptIn(ExperimentalTextApi::class)
private val MockBaloo =
    FontFamily(
        Font(
            resId = R.font.baloo_thambi_2,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            resId = R.font.baloo_thambi_2,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )

private val MockBorder = colorActionPrimaryTint20
private val MockShadowColor = Color.Black.copy(alpha = 0.25f)

/** A box placed at ([x], [y]) with a size of [w] x [h] units. */
@Composable
private fun Placed(
    unit: Dp,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(Modifier.offset(unit * x, unit * y).size(unit * w, unit * h).then(modifier), content = content)
}

/**
 * A text whose first line is centered on [yCenter], left edge at [x] (or centered on [x] when [centered]); [size] is
 * in units. [lines] lines of [width] units at most.
 */
@Composable
private fun MockText(
    text: String,
    unit: Dp,
    x: Float,
    yCenter: Float,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    width: Float = 140f,
    lines: Int = 1,
    family: FontFamily = FontFamilyHanken,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    centered: Boolean = false,
    align: TextAlign = TextAlign.Start,
) {
    val lineHeight = size * 1.3f
    val density = LocalDensity.current
    val fontSize: TextUnit = with(density) { (unit * size).toSp() }
    val lh: TextUnit = with(density) { (unit * lineHeight).toSp() }
    val left = if (centered) x - width / 2f else x
    Box(
        modifier
            .offset(unit * left, unit * (yCenter - lineHeight * lines / 2f))
            .size(unit * width, unit * lineHeight * lines),
        contentAlignment = if (centered) Alignment.TopCenter else Alignment.TopStart,
    ) {
        Text(
            text = text,
            color = color,
            maxLines = lines,
            softWrap = lines > 1,
            overflow = TextOverflow.Visible,
            textAlign = if (centered) TextAlign.Center else align,
            style =
                TextStyle(
                    fontFamily = family,
                    fontWeight = weight,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    fontSize = fontSize,
                    lineHeight = lh,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                ),
        )
    }
}

/** The frame of a mocked phone screen: canvas background, thin blue border, soft shadow, clipped content. */
@Composable
private fun MockScreen(
    unit: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Placed(
        unit = unit,
        x = 82f,
        y = 125f,
        w = 155f,
        h = 290f,
        modifier =
            Modifier
                .shadow(unit * 3f, RoundedCornerShape(unit * 0.5f), clip = false, ambientColor = MockShadowColor)
                .clip(RoundedCornerShape(unit * 0.5f))
                .background(colorBgCanvas)
                .border(unit.coerceAtLeast(1.dp) * 0.8f, MockBorder, RoundedCornerShape(unit * 0.5f)),
        content = content,
    )
}

/** A photo from res/drawable-nodpi, cropped to its frame (replace the file to change the picture). */
@Composable
private fun Photo(
    drawable: Int,
    modifier: Modifier,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------------------------------
// Slide 1: the Feed screen ("Welcoming screen")
// ---------------------------------------------------------------------------------------------------------------

@Composable
internal fun FeedIllustration(unit: Dp) {
    MockScreen(unit) {
        // Family selector.
        Placed(
            unit,
            34f,
            4f,
            88f,
            15f,
            Modifier
                .shadow(unit * 1.5f, RoundedCornerShape(unit * 4f), clip = false)
                .clip(RoundedCornerShape(unit * 4f))
                .background(colorBgSurface),
        )
        MockText(
            stringResource(R.string.android_onboarding_family_name),
            unit,
            78f - 6f,
            11.5f,
            7.4f,
            colorTextForm,
            centered = true,
            width = 70f,
            italic = true,
        )
        MockIcon(unit, 111f, 8f, 6f, MockIconKind.TriangleDown, colorActionPrimary)

        // "Everyone is safe" bubble (green organic shape), its star and its two texts.
        Canvas(Modifier.offset(unit * 8f, unit * 33f).size(unit * 144f, unit * 95f)) {
            val s = size.width / 144f
            val path =
                Path().apply {
                    moveTo(8f * s, 50f * s)
                    cubicTo(8f * s, 18f * s, 52f * s, 2f * s, 90f * s, 6f * s)
                    cubicTo(125f * s, 9f * s, 144f * s, 32f * s, 143f * s, 58f * s)
                    cubicTo(142f * s, 82f * s, 105f * s, 94f * s, 66f * s, 91f * s)
                    cubicTo(28f * s, 88f * s, 8f * s, 76f * s, 8f * s, 50f * s)
                    close()
                }
            drawPath(path, colorStatusSafeTint)
        }
        MockText(
            stringResource(R.string.onboarding_sample_all_safe),
            unit,
            24f,
            71f,
            10.2f,
            colorTextPrimary,
            width = 100f,
            lines = 2,
            family = MockBaloo,
            weight = FontWeight.Bold,
        )
        MockText(
            stringResource(R.string.onboarding_sample_updated),
            unit,
            24f,
            97f,
            6.2f,
            colorTextForm,
            width = 110f,
        )
        MockIcon(unit, 105f, 75f, 16f, MockIconKind.Sparkle, colorStatusSafe)

        MemberCard(
            unit = unit,
            top = 136f,
            height = 118f,
            photo = R.drawable.onboarding_photo_thomas,
            name = stringResource(R.string.onboarding_sample_name_1),
            battery = stringResource(R.string.android_onboarding_battery_1),
            place = stringResource(R.string.onboarding_sample_status_home),
            placeIcon = MockIconKind.Home,
            arrived = stringResource(R.string.android_onboarding_arrived_ago),
            withDetails = true,
        )
        MemberCard(
            unit = unit,
            top = 261f,
            height = 60f,
            photo = R.drawable.onboarding_photo_amandine,
            name = stringResource(R.string.onboarding_sample_name_2),
            battery = stringResource(R.string.android_onboarding_battery_2),
            place = stringResource(R.string.onboarding_sample_status_school),
            placeIcon = MockIconKind.Mortarboard,
            arrived = null,
            withDetails = false,
        )
    }
}

@Composable
private fun BoxScope.MemberCard(
    unit: Dp,
    top: Float,
    height: Float,
    photo: Int,
    name: String,
    battery: String,
    place: String,
    placeIcon: MockIconKind,
    arrived: String?,
    withDetails: Boolean,
) {
    // The card with its green bar on the left.
    Placed(
        unit,
        5f,
        top,
        144f,
        height,
        Modifier
            .shadow(unit * 0.8f, RoundedCornerShape(unit * 2.5f), clip = false)
            .clip(RoundedCornerShape(unit * 2.5f))
            .background(colorBgSurface),
    ) {
        Box(Modifier.size(unit * 1.8f, unit * height).background(colorBrandPrimary))
    }
    // Photo with its green ring.
    Placed(
        unit,
        15f,
        top + 5f,
        22f,
        22f,
        Modifier.clip(CircleShape).background(colorBrandPrimary),
    ) {
        Photo(photo, Modifier.offset(unit * 1.6f, unit * 1.6f).size(unit * 18.8f).clip(CircleShape))
    }
    MockText(
        name,
        unit,
        43f,
        top + 10f,
        8.6f,
        colorTextPrimary,
        family = MockBaloo,
        weight = FontWeight.Bold,
        width = 70f,
    )
    // Battery chip.
    Placed(
        unit,
        121f,
        top + 4.5f,
        23f,
        8f,
        Modifier.clip(RoundedCornerShape(unit * 1.5f)).background(colorBorderDefault.copy(alpha = 0.8f)),
    )
    MockIcon(unit, 123f, top + 5.8f, 5.2f, MockIconKind.Battery, colorTextSecondary)
    MockText(battery, unit, 130f, top + 8.5f, 5.8f, colorTextSecondary, width = 14f)
    // Place pill.
    Placed(
        unit,
        43f,
        top + 18f,
        45f,
        8.6f,
        Modifier.clip(RoundedCornerShape(unit * 1.8f)).background(colorStatusSafeTint),
    )
    MockIcon(unit, 46f, top + 19.6f, 5.4f, placeIcon, colorStatusSafe)
    MockText(place, unit, 54f, top + 22.3f, 6f, colorStatusSafe, width = 34f)
    if (withDetails) {
        MockText(arrived.orEmpty(), unit, 43f, top + 35f, 5.8f, colorTextSecondary, width = 80f)
        Placed(unit, 15f, top + 45f, 130f, 50f, Modifier.clip(RoundedCornerShape(unit * 2f))) {
            Photo(R.drawable.onboarding_photo_salon, Modifier.fillMaxWidth().size(unit * 130f, unit * 50f))
        }
        MockIcon(unit, 87.5f, top + 100f, 9f, MockIconKind.Pin, colorActionPrimary)
        MockIcon(unit, 107.5f, top + 101f, 9f, MockIconKind.Message, colorActionPrimary)
        MockIcon(unit, 131.5f, top + 100f, 9f, MockIconKind.Phone, colorActionPrimary)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Slide 2: the "Nouveau lieu" screen ("-16")
// ---------------------------------------------------------------------------------------------------------------

@Composable
internal fun NewPlaceIllustration(unit: Dp) {
    MockScreen(unit) {
        // Header: back arrow, title, validate button; the screenshot of the mockup is cut at the top, so is this one.
        MockIcon(unit, 7f, -1f, 8f, MockIconKind.Back, colorActionPrimary)
        MockText(
            stringResource(R.string.onboarding_sample_place_title),
            unit,
            77f,
            1.5f,
            9.5f,
            colorTextPrimary,
            centered = true,
            width = 80f,
            family = MockBaloo,
            weight = FontWeight.Bold,
        )
        Placed(unit, 132f, -9f, 21f, 21f, Modifier.clip(CircleShape).background(colorActionPrimary))
        MockIcon(unit, 137f, 0.5f, 9f, MockIconKind.Check, Color.White)
        Placed(unit, 0f, 18.5f, 155f, 1.2f, Modifier.background(colorBorderDefault.copy(alpha = 1f)))

        FormSection(unit, 30f, MockIconKind.Pencil, stringResource(R.string.onboarding_sample_place_name))
        MockText(
            stringResource(R.string.android_onboarding_name_hint),
            unit,
            29f,
            52.5f,
            5.2f,
            colorTextSecondary,
            width = 120f,
            lines = 2,
        )
        TextField(
            unit,
            64f,
            stringResource(R.string.onboarding_sample_place_name_value),
            stringResource(R.string.android_onboarding_name_counter),
        )

        FormSection(unit, 88.5f, MockIconKind.PersonPin, stringResource(R.string.onboarding_sample_place_address))
        MockText(
            stringResource(R.string.android_onboarding_address_hint),
            unit,
            29f,
            107.5f,
            5.2f,
            colorTextSecondary,
            width = 120f,
        )
        TextField(unit, 115f, stringResource(R.string.onboarding_sample_place_address_value), null)

        FormSection(unit, 138f, MockIconKind.Target, stringResource(R.string.onboarding_sample_place_radius))
        MockText(
            stringResource(R.string.android_onboarding_radius_hint),
            unit,
            29f,
            157f,
            5.2f,
            colorTextSecondary,
            width = 120f,
        )
        // Map: the photo (map background with the circle of the mockup), a crisp circle and dot drawn on top.
        Placed(
            unit,
            15f,
            164f,
            124f,
            48f,
            Modifier
                .clip(RoundedCornerShape(unit * 3.5f))
                .border(unit.coerceAtLeast(1.dp) * 0.7f, colorBorderDefault, RoundedCornerShape(unit * 3.5f)),
        ) {
            Photo(R.drawable.onboarding_photo_map, Modifier.size(unit * 124f, unit * 48f))
            Canvas(Modifier.size(unit * 124f, unit * 48f)) {
                val s = size.width / 124f
                val center = Offset(62f * s, 23.5f * s)
                drawCircle(
                    colorActionPrimary.copy(alpha = 0.55f),
                    radius = 21.3f * s,
                    center = center,
                    style =
                        Stroke(0.6f * s),
                )
                drawCircle(colorActionPrimary, radius = 2.3f * s, center = center)
            }
        }
        // Slider and its value.
        Placed(unit, 28f, 227.6f, 94f, 2.8f, Modifier.clip(CircleShape).background(colorBorderDefault))
        Placed(unit, 28f, 227.6f, 21f, 2.8f, Modifier.clip(CircleShape).background(colorActionPrimary))
        Placed(
            unit,
            45f,
            224.8f,
            8.4f,
            8.4f,
            Modifier.clip(CircleShape).background(colorBgSurface).border(unit * 1.2f, colorActionPrimary, CircleShape),
        )
        Placed(
            unit,
            128f,
            222f,
            24f,
            14f,
            Modifier.clip(RoundedCornerShape(unit * 2.5f)).background(colorBorderDefault),
        )
        MockText(
            stringResource(R.string.android_onboarding_radius_value),
            unit,
            140f,
            229f,
            5.8f,
            colorActionPrimary,
            centered = true,
            width = 22f,
            weight = FontWeight.Bold,
        )

        FormSection(unit, 245f, MockIconKind.Eye, stringResource(R.string.android_onboarding_type_title))
        MockText(
            stringResource(R.string.android_onboarding_type_hint),
            unit,
            29f,
            264.2f,
            5.2f,
            colorTextSecondary,
            width = 120f,
        )
        ZoneCard(
            unit,
            28f,
            72f,
            stringResource(R.string.android_onboarding_zone_trust),
            stringResource(R.string.android_onboarding_zone_trust_hint),
            trust = true,
        )
        ZoneCard(
            unit,
            103f,
            60f,
            stringResource(R.string.android_onboarding_zone_risk),
            stringResource(R.string.android_onboarding_zone_risk_hint),
            trust = false,
        )
    }
}

@Composable
private fun BoxScope.FormSection(
    unit: Dp,
    top: Float,
    icon: MockIconKind,
    title: String,
) {
    Placed(unit, 6f, top, 16f, 16f, Modifier.clip(RoundedCornerShape(unit * 4f)).background(colorActionPrimaryTint20))
    MockIcon(unit, 9f, top + 3f, 10f, icon, colorActionPrimary)
    MockText(
        title,
        unit,
        29f,
        top + 7f,
        9.2f,
        colorTextPrimary,
        family = MockBaloo,
        weight = FontWeight.Bold,
        width = 100f,
    )
}

@Composable
private fun BoxScope.TextField(
    unit: Dp,
    top: Float,
    value: String,
    counter: String?,
) {
    Placed(
        unit,
        27f,
        top,
        123f,
        12.5f,
        Modifier
            .clip(RoundedCornerShape(unit * 2.5f))
            .background(colorBgSurface)
            .border(unit.coerceAtLeast(1.dp) * 0.6f, colorBorderDefault, RoundedCornerShape(unit * 2.5f)),
    )
    MockText(value, unit, 33f, top + 6.3f, 5.6f, colorTextForm, width = 100f)
    if (counter !=
        null
    ) {
        MockText(counter, unit, 138f, top + 6.3f, 4.6f, colorTextSecondary.copy(alpha = 0.6f), width = 12f)
    }
}

@Composable
private fun BoxScope.ZoneCard(
    unit: Dp,
    left: Float,
    width: Float,
    title: String,
    hint: String,
    trust: Boolean,
) {
    val tint = if (trust) colorStatusSafeTint else colorBgSurface
    val line = if (trust) colorStatusSafe.copy(alpha = 0.6f) else colorStatusDanger.copy(alpha = 0.6f)
    Placed(
        unit,
        left,
        271f,
        width,
        40f,
        Modifier
            .clip(RoundedCornerShape(unit * 2.5f))
            .background(tint)
            .border(unit.coerceAtLeast(1.dp) * 0.7f, line, RoundedCornerShape(unit * 2.5f)),
    )
    if (trust) {
        MockIcon(unit, left + 3f, 277f, 9f, MockIconKind.CheckCircle, colorStatusSafe)
    } else {
        MockIcon(unit, left + 3f, 277f, 9f, MockIconKind.Warning, colorStatusDanger)
    }
    MockText(
        title,
        unit,
        left + 14f,
        279f,
        5.4f,
        colorTextPrimary,
        family = MockBaloo,
        weight = FontWeight.Bold,
        width =
            width - 15f,
    )
    MockText(hint, unit, left + 14f, 288.5f, 4.4f, colorTextSecondary, width = width - 15f)
}

// ---------------------------------------------------------------------------------------------------------------
// Slide 3: the notification cards ("-17")
// ---------------------------------------------------------------------------------------------------------------

@Composable
internal fun MessagesIllustration(unit: Dp) {
    NotificationCard(
        unit,
        31f,
        169f,
        R.drawable.onboarding_photo_thomas,
        stringResource(R.string.onboarding_sample_msg_1),
        1,
        stringResource(R.string.onboarding_sample_when_1),
    )
    NotificationCard(
        unit,
        50f,
        246f,
        R.drawable.onboarding_photo_amandine,
        stringResource(R.string.onboarding_sample_msg_2),
        2,
        stringResource(R.string.onboarding_sample_when_2),
    )
    NotificationCard(
        unit,
        30f,
        323f,
        R.drawable.onboarding_photo_maman,
        stringResource(R.string.onboarding_sample_msg_3),
        2,
        stringResource(R.string.onboarding_sample_when_3),
    )
    // The small yellow-green dot on the corner of the first card.
    Placed(unit, 264.5f, 161.5f, 14f, 14f, Modifier.clip(CircleShape).background(colorBrandPrimary))
}

@Composable
private fun NotificationCard(
    unit: Dp,
    left: Float,
    top: Float,
    photo: Int,
    message: String,
    messageLines: Int,
    whenText: String,
) {
    Placed(
        unit,
        left,
        top,
        259f,
        62f,
        Modifier
            .shadow(unit * 1.6f, RoundedCornerShape(unit * 4f), clip = false)
            .clip(RoundedCornerShape(unit * 4f))
            .background(colorBgSurface)
            .border(unit.coerceAtLeast(1.dp) * 0.8f, MockBorder, RoundedCornerShape(unit * 4f)),
    )
    Placed(unit, left + 5f, top + 12.5f, 38f, 38f, Modifier.clip(CircleShape).background(colorActionPrimary)) {
        Photo(photo, Modifier.offset(unit * 1.8f, unit * 1.8f).size(unit * 34.4f).clip(CircleShape))
    }
    val textLeft = left + 47f
    if (messageLines == 1) {
        MockText(
            message,
            unit,
            textLeft,
            top + 22f,
            12.2f,
            colorTextPrimary,
            width = 205f,
            family = MockBaloo,
            weight = FontWeight.Bold,
        )
        MockText(whenText, unit, textLeft + 4f, top + 44f, 9.6f, colorTextSecondary, width = 120f)
    } else {
        MockText(
            message,
            unit,
            textLeft,
            top + 22f,
            12.2f,
            colorTextPrimary,
            width = 205f,
            lines = 2,
            family = MockBaloo,
            weight = FontWeight.Bold,
        )
        MockText(whenText, unit, textLeft + 4f, top + 51f, 9.6f, colorTextSecondary, width = 120f)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Icons of the mockups, drawn as vectors in a square of [size] units
// ---------------------------------------------------------------------------------------------------------------

internal enum class MockIconKind {
    TriangleDown,
    Sparkle,
    Home,
    Mortarboard,
    Battery,
    Pin,
    Message,
    Phone,
    Back,
    Check,
    Pencil,
    PersonPin,
    Target,
    Eye,
    CheckCircle,
    Warning,
}

@Composable
private fun BoxScope.MockIcon(
    unit: Dp,
    x: Float,
    y: Float,
    size: Float,
    kind: MockIconKind,
    color: Color,
) {
    Canvas(Modifier.offset(unit * x, unit * y).size(unit * size)) { drawMockIcon(kind, color, this.size.width) }
}

private fun DrawScope.drawMockIcon(
    kind: MockIconKind,
    color: Color,
    s: Float,
) {
    val u = s / 10f // the icons are designed in a 10 x 10 square

    fun p(
        x: Float,
        y: Float,
    ) = Offset(x * u, y * u)
    val line = Stroke(width = 1.1f * u, cap = StrokeCap.Round)
    when (kind) {
        MockIconKind.TriangleDown -> {
            drawPath(
                Path().apply {
                    moveTo(p(0.5f, 2.5f).x, p(0.5f, 2.5f).y)
                    lineTo(9.5f * u, 2.5f * u)
                    lineTo(
                        5f * u,
                        8f * u,
                    )
                    close()
                },
                color,
            )
        }

        MockIconKind.Sparkle -> {
            drawPath(
                Path().apply {
                    moveTo(5f * u, 0f)
                    quadraticTo(5.6f * u, 4.4f * u, 10f * u, 5f * u)
                    quadraticTo(5.6f * u, 5.6f * u, 5f * u, 10f * u)
                    quadraticTo(4.4f * u, 5.6f * u, 0f, 5f * u)
                    quadraticTo(4.4f * u, 4.4f * u, 5f * u, 0f)
                    close()
                },
                color,
            )
        }

        MockIconKind.Home -> {
            drawPath(
                Path().apply {
                    moveTo(5f * u, 0.8f * u)
                    lineTo(9.6f * u, 5f * u)
                    lineTo(8.2f * u, 5f * u)
                    lineTo(
                        8.2f * u,
                        9.4f * u,
                    )
                    lineTo(6f * u, 9.4f * u)
                    lineTo(6f * u, 6.4f * u)
                    lineTo(4f * u, 6.4f * u)
                    lineTo(
                        4f * u,
                        9.4f * u,
                    )
                    lineTo(1.8f * u, 9.4f * u)
                    lineTo(1.8f * u, 5f * u)
                    lineTo(0.4f * u, 5f * u)
                    close()
                },
                color,
            )
        }

        MockIconKind.Mortarboard -> {
            drawPath(
                Path().apply {
                    moveTo(5f * u, 1.5f * u)
                    lineTo(10f * u, 4.2f * u)
                    lineTo(5f * u, 6.9f * u)
                    lineTo(
                        0f,
                        4.2f * u,
                    )
                    close()
                },
                color,
            )
            drawPath(
                Path().apply {
                    moveTo(2.2f * u, 6f * u)
                    lineTo(2.2f * u, 8.2f * u)
                    quadraticTo(
                        5f * u,
                        10f * u,
                        7.8f * u,
                        8.2f * u,
                    )
                    lineTo(7.8f * u, 6f * u)
                    lineTo(5f * u, 7.6f * u)
                    close()
                },
                color,
            )
        }

        MockIconKind.Battery -> {
            drawRoundRect(color, p(2.6f, 0.8f), Size(4.8f * u, 8.4f * u), CornerRadius(0.9f * u))
            drawRect(color, p(3.8f, 0f), Size(2.4f * u, 1.2f * u))
        }

        MockIconKind.Pin -> {
            drawPath(
                Path().apply {
                    addOval(Rect(p(1.6f, 0.4f), Size(6.8f * u, 6.8f * u)))
                    moveTo(2.2f * u, 5.2f * u)
                    lineTo(5f * u, 10f * u)
                    lineTo(7.8f * u, 5.2f * u)
                    close()
                },
                color,
            )
            drawCircle(Color.White, radius = 1.4f * u, center = p(5f, 3.8f))
        }

        MockIconKind.Message -> {
            drawRoundRect(color, p(0.2f, 1.2f), Size(9.6f * u, 6.6f * u), CornerRadius(2f * u))
            drawPath(
                Path().apply {
                    moveTo(2f * u, 7f * u)
                    lineTo(2f * u, 9.4f * u)
                    lineTo(4.6f * u, 7f * u)
                    close()
                },
                color,
            )
        }

        MockIconKind.Phone -> {
            drawArc(
                color,
                startAngle = 100f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = p(-4.5f, -1f),
                size = Size(13f * u, 13f * u),
                style = Stroke(width = 2.6f * u, cap = StrokeCap.Round),
            )
        }

        MockIconKind.Back -> {
            drawLine(color, p(9f, 5f), p(1.5f, 5f), strokeWidth = 1.4f * u, cap = StrokeCap.Round)
            drawLine(color, p(1.5f, 5f), p(5f, 1.5f), strokeWidth = 1.4f * u, cap = StrokeCap.Round)
            drawLine(color, p(1.5f, 5f), p(5f, 8.5f), strokeWidth = 1.4f * u, cap = StrokeCap.Round)
        }

        MockIconKind.Check -> {
            drawLine(color, p(1.6f, 5.4f), p(4.2f, 8f), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
            drawLine(color, p(4.2f, 8f), p(8.6f, 2.2f), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
        }

        MockIconKind.Pencil -> {
            drawLine(color, p(2f, 8f), p(8f, 2f), strokeWidth = 1.7f * u, cap = StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(1.2f * u, 8.8f * u)
                    lineTo(2.2f * u, 6.4f * u)
                    lineTo(3.6f * u, 7.8f * u)
                    close()
                },
                color,
            )
        }

        MockIconKind.PersonPin -> {
            drawCircle(color, radius = 1.5f * u, center = p(5f, 3.6f))
            drawArc(color, 200f, 140f, false, p(2.4f, 5.2f), Size(5.2f * u, 5.2f * u), style = line)
            drawCircle(color, radius = 4.6f * u, center = p(5f, 5f), style = Stroke(0.9f * u))
        }

        MockIconKind.Target -> {
            drawCircle(color, radius = 4.4f * u, center = p(5f, 5f), style = Stroke(0.8f * u))
            drawCircle(color, radius = 2.8f * u, center = p(5f, 5f), style = Stroke(0.8f * u))
            drawCircle(color, radius = 1.2f * u, center = p(5f, 5f))
        }

        MockIconKind.Eye -> {
            drawPath(
                Path().apply {
                    moveTo(0.4f * u, 5f * u)
                    quadraticTo(5f * u, -0.8f * u, 9.6f * u, 5f * u)
                    quadraticTo(5f * u, 10.8f * u, 0.4f * u, 5f * u)
                    close()
                },
                color,
                style = Stroke(0.9f * u),
            )
            drawCircle(color, radius = 1.7f * u, center = p(5f, 5f))
        }

        MockIconKind.CheckCircle -> {
            drawCircle(color, radius = 4.6f * u, center = p(5f, 5f))
            drawLine(
                Color.White.copy(alpha = 0.85f),
                p(2.9f, 5.2f),
                p(4.5f, 6.8f),
                strokeWidth = 1.1f * u,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color.White.copy(alpha = 0.85f),
                p(4.5f, 6.8f),
                p(7.2f, 3.6f),
                strokeWidth = 1.1f * u,
                cap = StrokeCap.Round,
            )
        }

        MockIconKind.Warning -> {
            drawPath(
                Path().apply {
                    moveTo(5f * u, 0.8f * u)
                    lineTo(9.8f * u, 9f * u)
                    lineTo(0.2f * u, 9f * u)
                    close()
                },
                color,
            )
            drawLine(Color.White, p(5f, 3.6f), p(5f, 6.3f), strokeWidth = 1.1f * u, cap = StrokeCap.Round)
            drawCircle(Color.White, radius = 0.6f * u, center = p(5f, 7.7f))
        }
    }
}
