package com.tomessa.miff.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tomessa.miff.R

/*
 * Typography tokens of design/design-system.md section 2. All sizes are in sp so that text follows the system
 * font size. The fonts are variable fonts (variation settings work from API 26, which is our minSdk).
 * Open point: the design system gives no weight for the "display" styles; the Figma state file says "Regular" (400).
 */

@OptIn(ExperimentalTextApi::class)
private fun hanken(
    weight: Int,
    italic: Boolean = false,
): Font =
    Font(
        resId = if (italic) R.font.hanken_grotesk_italic else R.font.hanken_grotesk,
        weight = FontWeight(weight),
        style = if (italic) FontStyle.Italic else FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

@OptIn(ExperimentalTextApi::class)
val FontFamilyBaloo =
    FontFamily(
        Font(
            resId = R.font.baloo_thambi_2,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
    )

val FontFamilyHanken =
    FontFamily(
        hanken(400),
        hanken(400, italic = true),
        hanken(700),
    )

private fun style(
    family: FontFamily,
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
): TextStyle =
    TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        fontStyle = fontStyle,
    )

val textDisplayHero = style(FontFamilyBaloo, 64, 72)
val textDisplayHeadline = style(FontFamilyBaloo, 24, 30)
val textDisplayTitle = style(FontFamilyBaloo, 20, 26)
val textDisplayLabel = style(FontFamilyBaloo, 16, 22)

val textTitleBold24 = style(FontFamilyHanken, 24, 30, FontWeight.Bold)
val textTitleBold20 = style(FontFamilyHanken, 20, 26, FontWeight.Bold)
val textBodyLarge = style(FontFamilyHanken, 20, 26)
val textBodyRegular = style(FontFamilyHanken, 16, 22)
val textBodyItalic = style(FontFamilyHanken, 16, 22, fontStyle = FontStyle.Italic)
val textBodyBold16 = style(FontFamilyHanken, 16, 22, FontWeight.Bold)
val textBodyRegular14 = style(FontFamilyHanken, 14, 20)
val textBodyBold14 = style(FontFamilyHanken, 14, 20, FontWeight.Bold)
val textBodyCaption = style(FontFamilyHanken, 12, 16)
val textCaptionBold12 = style(FontFamilyHanken, 12, 16, FontWeight.Bold)
val textCaptionMicro10 = style(FontFamilyHanken, 10, 14)
val textCaptionTag8 = style(FontFamilyHanken, 8, 10)

/** Brand headline: title/bold-24 filled with [GradientBrandTitle] (used for "Bienvenue", Paywall). */
val textTitleBold24Gradient: TextStyle = textTitleBold24.copy(brush = GradientBrandTitle)
