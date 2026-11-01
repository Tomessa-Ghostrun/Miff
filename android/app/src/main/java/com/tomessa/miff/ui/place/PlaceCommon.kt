package com.tomessa.miff.ui.place

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.ui.theme.RadiusMd
import com.tomessa.miff.ui.theme.ShapeFull
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorStatusDangerTint
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textCaptionBold12

/** The icon of a category (provisional drawings ported from design/screens/lieux/icones-categories.md, to be replaced by the Figma export). */
@get:DrawableRes
val PlaceCategory.iconRes: Int
    get() =
        when (this) {
            PlaceCategory.HOME -> R.drawable.ic_loc_home
            PlaceCategory.WORK -> R.drawable.ic_loc_work
            PlaceCategory.SCHOOL -> R.drawable.ic_loc_school
            PlaceCategory.SPORT -> R.drawable.ic_loc_sport
            PlaceCategory.RELATIVE -> R.drawable.ic_loc_relative
            PlaceCategory.LEISURE -> R.drawable.ic_loc_leisure
            PlaceCategory.OTHER -> R.drawable.ic_loc_other
        }

@get:StringRes
val PlaceCategory.labelRes: Int
    get() =
        when (this) {
            PlaceCategory.HOME -> R.string.place_category_home
            PlaceCategory.WORK -> R.string.place_category_work
            PlaceCategory.SCHOOL -> R.string.place_category_school
            PlaceCategory.SPORT -> R.string.place_category_sport
            PlaceCategory.RELATIVE -> R.string.place_category_relative
            PlaceCategory.LEISURE -> R.string.place_category_leisure
            PlaceCategory.OTHER -> R.string.place_category_other
        }

@get:StringRes
val PlaceType.labelRes: Int
    get() = if (this == PlaceType.TRUSTED) R.string.place_type_trusted else R.string.place_type_danger

@get:DrawableRes
val PlaceType.iconRes: Int
    get() = if (this == PlaceType.TRUSTED) R.drawable.ic_loc_shield else R.drawable.ic_loc_warning

/** A pale blue square with the icon of a category: 28 dp in the form, 44 dp in the lists (fiche icones-categories). */
@Composable
fun CategoryIcon(
    category: PlaceCategory?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(ShapeMd).background(colorActionPrimaryTint20),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource((category ?: PlaceCategory.OTHER).iconRes),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(size * ICON_RATIO),
        )
    }
}

private const val ICON_RATIO = 0.64f

/** A dashed rounded outline (the "Ajouter un lieu" line of the mockups). */
fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 1.dp,
    radius: Dp = RadiusMd,
): Modifier =
    drawBehind {
        val width = strokeWidth.toPx()
        drawRoundRect(
            color = color,
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(width / 2, width / 2),
            size =
                androidx.compose.ui.geometry
                    .Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(radius.toPx()),
            style =
                Stroke(
                    width = width,
                    pathEffect =
                        PathEffect.dashPathEffect(
                            floatArrayOf(
                                DASH * density,
                                DASH * density,
                            ),
                        ),
                ),
        )
    }

private const val DASH = 5f

/** The pill "Miff+" with a lock: what is Miff+ only is visible as such BEFORE it is touched (design decision 11). */
@Composable
fun PremiumPill(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .clip(ShapeFull)
                .background(colorActionPrimaryTint10)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(PILL_ICON),
        )
        Text(text = stringResource(R.string.place_premium_pill), style = textCaptionBold12, color = colorActionPrimary)
    }
}

private val PILL_ICON = 12.dp

/** The label "Zone de confiance" / "Zone de danger": colour AND icon AND words, never the colour alone. */
@Composable
fun PlaceTypeTag(
    type: PlaceType,
    modifier: Modifier = Modifier,
) {
    val danger = type == PlaceType.RISK
    Row(
        modifier =
            modifier
                .clip(ShapeMd)
                .background(if (danger) colorStatusDangerTint else colorStatusSafeTint)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            painter = painterResource(type.iconRes),
            contentDescription = null,
            tint = if (danger) colorTextError else colorTextForm,
            modifier = Modifier.size(PILL_ICON + Spacing.xs),
        )
        Text(
            text = stringResource(type.labelRes),
            style = textCaptionBold12,
            color = if (danger) colorTextError else colorTextForm,
        )
    }
}
