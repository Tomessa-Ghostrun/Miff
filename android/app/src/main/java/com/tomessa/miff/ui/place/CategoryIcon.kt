package com.tomessa.miff.ui.place

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.textBodyBold16

/*
 * The category of a place is shown as an EMOJI (decision of the PM, 2026-11-10), drawn as text by the font of the phone.
 *
 * - Apple's emoji are proprietary and cannot be shipped: Android draws them with the system font (Noto Color Emoji on a Pixel, the maker's own
 *   set on some phones). The seven chosen ones (PlaceCategory.emoji, the shared table of docs/contracts/place-categories.md) all exist since
 *   Android 8 (minSdk 26); the ZWJ "family" one is checked like the others (see [EmojiSupport]).
 * - The emoji is DECORATIVE for a screen reader: the name of the category is always written next to it (or in the description of the row), and
 *   it is that name that is read. Nothing of it is written to the server.
 * - It keeps its size whatever the font size of the phone (it lives in a box of a fixed size, like the drawings it replaces); the words around it grow.
 */

/** Can the font of this phone draw [emoji] as ONE glyph? A font that cannot would show a square, or the pieces of a sequence. */
fun interface EmojiSupport {
    fun canDraw(emoji: String): Boolean
}

/** The system's answer. A failure of the check never hides an emoji: the person sees it as the font draws it, at worst a square. */
object SystemEmojiSupport : EmojiSupport {
    override fun canDraw(emoji: String): Boolean = runCatching { Paint().hasGlyph(emoji) }.getOrDefault(true)
}

/** Replaced in the tests to see the fallback (the first letter of the name of the category). */
val LocalEmojiSupport = staticCompositionLocalOf<EmojiSupport> { SystemEmojiSupport }

/**
 * A pale blue square with the emoji of a category: 28 dp in the form, 44 dp in the lists. A place written before the categories existed has
 * none: a plain pin (never "Autre" by default). When the phone's font cannot draw the emoji, the first letter of the name of the category
 * takes its place (a text fallback, never an empty square).
 */
@Composable
fun CategoryIcon(
    category: PlaceCategory?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(ShapeMd)
                .background(colorActionPrimaryTint20)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (category == null) {
            Icon(
                painter = painterResource(R.drawable.ic_loc_pin),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(size * PIN_RATIO),
            )
            return@Box
        }
        // dp -> sp with the font scale taken out: the glyph stays the size of its box when the person enlarges the text.
        val glyph = with(LocalDensity.current) { (size * GLYPH_RATIO).toSp() }
        CategoryGlyph(
            category = category,
            fontSize = glyph,
            fallbackStyle = textBodyBold16,
            fallbackColor = colorActionPrimary,
        )
    }
}

/**
 * The emoji of a category as TEXT of the given size, alone (no box, no semantics: the caller makes it decorative). When the phone's font cannot draw
 * it, the first letter of the name of the category takes its place (a text fallback, never an empty square). Shared by [CategoryIcon] and the label
 * of a card of the Feed, so that both show the same table and the same fallback (`docs/contracts/place-categories.md`).
 */
@Composable
fun CategoryGlyph(
    category: PlaceCategory,
    fontSize: TextUnit,
    fallbackStyle: TextStyle,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
) {
    val support = LocalEmojiSupport.current
    val drawable = remember(category, support) { support.canDraw(category.emoji) }
    val style =
        TextStyle(
            fontSize = fontSize,
            lineHeight = fontSize * LINE_RATIO,
            textAlign = TextAlign.Center,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    if (drawable) {
        Text(text = category.emoji, style = style, maxLines = 1, softWrap = false, modifier = modifier)
    } else {
        Text(
            text = stringResource(category.labelRes).take(1).uppercase(),
            style = fallbackStyle.merge(style),
            color = fallbackColor,
            maxLines = 1,
            softWrap = false,
            modifier = modifier,
        )
    }
}

private const val PIN_RATIO = 0.64f
private const val GLYPH_RATIO = 0.6f
private const val LINE_RATIO = 1.25f
