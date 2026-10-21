package com.tomessa.miff.ui.diagnostic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.GradientBrandTitle
import com.tomessa.miff.ui.theme.RadiusFull
import com.tomessa.miff.ui.theme.RadiusMd
import com.tomessa.miff.ui.theme.RadiusSm
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShadowSubtle
import com.tomessa.miff.ui.theme.ShadowToken
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorActionPrimaryTint5
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgGradientEnd
import com.tomessa.miff.ui.theme.colorBgOverlayHeader
import com.tomessa.miff.ui.theme.colorBgPlaceholder
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorBorderSubtle
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusDangerTint
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorStatusWarning
import com.tomessa.miff.ui.theme.colorStatusWarningTint
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextMuted
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.colorTextPlaceholder
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.colorTextSecondary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyItalic
import com.tomessa.miff.ui.theme.textBodyLarge
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textCaptionBold12
import com.tomessa.miff.ui.theme.textCaptionMicro10
import com.tomessa.miff.ui.theme.textCaptionTag8
import com.tomessa.miff.ui.theme.textDisplayHeadline
import com.tomessa.miff.ui.theme.textDisplayHero
import com.tomessa.miff.ui.theme.textDisplayLabel
import com.tomessa.miff.ui.theme.textDisplayTitle
import com.tomessa.miff.ui.theme.textTitleBold20
import com.tomessa.miff.ui.theme.textTitleBold24
import com.tomessa.miff.ui.theme.textTitleBold24Gradient

// Token names are code identifiers (same on iOS), not user-facing text.
private val colorTokens: List<Pair<String, Color>> =
    listOf(
        "colorBrandPrimary" to colorBrandPrimary,
        "colorActionPrimary" to colorActionPrimary,
        "colorActionPrimaryTint5" to colorActionPrimaryTint5,
        "colorActionPrimaryTint10" to colorActionPrimaryTint10,
        "colorActionPrimaryTint20" to colorActionPrimaryTint20,
        "colorActionPrimaryTint30" to colorActionPrimaryTint30,
        "colorTextOnAction" to colorTextOnAction,
        "colorStatusSafe" to colorStatusSafe,
        "colorStatusSafeTint" to colorStatusSafeTint,
        "colorStatusWarning" to colorStatusWarning,
        "colorStatusWarningTint" to colorStatusWarningTint,
        "colorStatusDanger" to colorStatusDanger,
        "colorStatusDangerTint" to colorStatusDangerTint,
        "colorBgCanvas" to colorBgCanvas,
        "colorBgSurface" to colorBgSurface,
        "colorBgPlaceholder" to colorBgPlaceholder,
        "colorBgOverlayHeader" to colorBgOverlayHeader,
        "colorBgGradientEnd" to colorBgGradientEnd,
        "colorTextPrimary" to colorTextPrimary,
        "colorTextForm" to colorTextForm,
        "colorTextPlaceholder" to colorTextPlaceholder,
        "colorTextDescription" to colorTextDescription,
        "colorTextMuted" to colorTextMuted,
        "colorTextSecondary" to colorTextSecondary,
        "colorTextError" to colorTextError,
        "colorBorderDefault" to colorBorderDefault,
        "colorBorderStrong" to colorBorderStrong,
        "colorBorderSubtle" to colorBorderSubtle,
        "colorBorderAction" to colorBorderAction,
    )

private val textTokens: List<Pair<String, TextStyle>> =
    listOf(
        "textDisplayHero" to textDisplayHero,
        "textDisplayHeadline" to textDisplayHeadline,
        "textDisplayTitle" to textDisplayTitle,
        "textDisplayLabel" to textDisplayLabel,
        "textTitleBold24" to textTitleBold24,
        "textTitleBold20" to textTitleBold20,
        "textBodyLarge" to textBodyLarge,
        "textBodyRegular" to textBodyRegular,
        "textBodyItalic" to textBodyItalic,
        "textBodyBold16" to textBodyBold16,
        "textBodyRegular14" to textBodyRegular14,
        "textBodyBold14" to textBodyBold14,
        "textBodyCaption" to textBodyCaption,
        "textCaptionBold12" to textCaptionBold12,
        "textCaptionMicro10" to textCaptionMicro10,
        "textCaptionTag8" to textCaptionTag8,
    )

private val radiusTokens: List<Pair<String, Dp>> =
    listOf("RadiusMd" to RadiusMd, "RadiusSm" to RadiusSm, "RadiusFull" to RadiusFull)

private val shadowTokens: List<Pair<String, ShadowToken>> =
    listOf("ShadowDefault" to ShadowDefault, "ShadowSubtle" to ShadowSubtle)

private val gradientTokens: List<Pair<String, Brush>> =
    listOf("GradientBrandTitle" to GradientBrandTitle, "GradientAuthBackground" to GradientAuthBackground)

private val swatchSize = 40.dp
private val sampleBoxHeight = 56.dp
private val sampleBoxWidth = 96.dp

@Composable
fun TokenGallery() {
    SectionCard(stringResource(R.string.diag_gallery_title)) {
        GalleryHeading(stringResource(R.string.diag_gallery_colors))
        colorTokens.forEach { (name, color) -> ColorRow(name, color) }

        GalleryHeading(stringResource(R.string.diag_gallery_text))
        val sample = stringResource(R.string.diag_gallery_sample)
        textTokens.forEach { (name, style) ->
            Column {
                Text(
                    text =
                        stringResource(
                            R.string.diag_gallery_style_meta,
                            style.fontSize.value.toInt(),
                            style.lineHeight.value.toInt(),
                        ),
                    style = textCaptionBold12,
                    color = colorTextSecondary,
                )
                Text(text = name, style = textBodyCaption, color = colorTextSecondary)
                Text(text = sample, style = style, color = colorTextPrimary)
            }
        }
        Text(text = sample, style = textTitleBold24Gradient)

        GalleryHeading(stringResource(R.string.diag_gallery_radii))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            radiusTokens.forEach { (name, radius) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier =
                            Modifier
                                .size(swatchSize + Spacing.s)
                                .clip(RoundedCornerShape(radius))
                                .background(colorActionPrimaryTint20)
                                .border(StrokeHairline, colorBorderAction, RoundedCornerShape(radius)),
                    )
                    Text(text = name, style = textBodyCaption, color = colorTextSecondary)
                    val dp = if (radius == RadiusFull) Int.MAX_VALUE else radius.value.toInt()
                    if (dp != Int.MAX_VALUE) {
                        Text(
                            text = stringResource(R.string.diag_gallery_radius_meta, dp),
                            style = textBodyCaption,
                            color = colorTextSecondary,
                        )
                    }
                }
            }
        }

        GalleryHeading(stringResource(R.string.diag_gallery_shadows))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l), modifier = Modifier.padding(Spacing.s)) {
            shadowTokens.forEach { (name, token) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = sampleBoxWidth, height = sampleBoxHeight)
                                .miffShadow(token, RoundedCornerShape(RadiusMd))
                                .background(colorBgSurface, RoundedCornerShape(RadiusMd)),
                    )
                    Text(text = name, style = textBodyCaption, color = colorTextSecondary)
                }
            }
        }

        GalleryHeading(stringResource(R.string.diag_gallery_gradients))
        gradientTokens.forEach { (name, brush) ->
            Text(text = name, style = textBodyCaption, color = colorTextSecondary)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(sampleBoxHeight)
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(brush)
                        .border(BorderStroke(StrokeHairline, colorBorderDefault), RoundedCornerShape(RadiusMd)),
            )
        }
    }
}

@Composable
private fun GalleryHeading(text: String) {
    Text(text = text, style = textTitleBold20, color = colorTextPrimary, modifier = Modifier.padding(top = Spacing.s))
}

@Composable
private fun ColorRow(
    name: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .size(swatchSize)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(colorBgPlaceholder)
                    .background(color)
                    .border(StrokeHairline, colorBorderDefault, RoundedCornerShape(RadiusSm)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = textBodyBold14, color = colorTextPrimary)
            Text(text = colorLabel(color), style = textBodyCaption, color = colorTextSecondary)
        }
    }
}

/** "#RRGGBB" for opaque colors, "#RRGGBB · NN %" otherwise (alpha as a percentage, as in the design system). */
@Composable
private fun colorLabel(color: Color): String {
    val argb = color.toArgb()
    val hex = String.format(java.util.Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    val alphaPercent = Math.round(color.alpha * 100)
    return if (alphaPercent == 100) hex else stringResource(R.string.diag_gallery_color_alpha, hex, alphaPercent)
}
