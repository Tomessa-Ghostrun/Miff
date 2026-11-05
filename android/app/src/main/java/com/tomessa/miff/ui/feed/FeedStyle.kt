package com.tomessa.miff.ui.feed

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.theme.StrokeAvatarRing
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusDangerText
import com.tomessa.miff.ui.theme.colorStatusDangerTint
import com.tomessa.miff.ui.theme.colorStatusNeutral
import com.tomessa.miff.ui.theme.colorStatusNeutralTint
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusSafeText
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorStatusWarning
import com.tomessa.miff.ui.theme.colorStatusWarningText
import com.tomessa.miff.ui.theme.colorStatusWarningTint
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold14

/*
 * What a LEVEL looks like. The colour is never alone: every level has a glyph (tick, clock, "!") and a text, and the dark text shades
 * (colorStatus*Text) are the ones that reach the AA contrast on the 30 % tints. A person whose status is not known is a neutral GREY:
 * never green when nobody knows.
 */

/** The ring of an avatar, the strip of a card. */
internal fun StatusLevel.ring(): Color =
    when (this) {
        StatusLevel.GREEN -> colorStatusSafe
        StatusLevel.ORANGE -> colorStatusWarning
        StatusLevel.RED -> colorStatusDanger
        StatusLevel.NEUTRAL -> colorStatusNeutral
    }

/** The 30 % tint behind a label or a bubble. */
internal fun StatusLevel.tint(): Color =
    when (this) {
        StatusLevel.GREEN -> colorStatusSafeTint
        StatusLevel.ORANGE -> colorStatusWarningTint
        StatusLevel.RED -> colorStatusDangerTint
        StatusLevel.NEUTRAL -> colorStatusNeutralTint
    }

/** The text and pictogram colour that sits on [tint]. */
internal fun StatusLevel.onTint(): Color =
    when (this) {
        StatusLevel.GREEN -> colorStatusSafeText
        StatusLevel.ORANGE -> colorStatusWarningText
        StatusLevel.RED -> colorStatusDangerText
        StatusLevel.NEUTRAL -> colorTextForm
    }

/** The glyph on the ring of an avatar: none for a person whose status is not known. */
internal fun StatusLevel.glyph(): Int? =
    when (this) {
        StatusLevel.GREEN -> R.drawable.ic_loc_check
        StatusLevel.ORANGE -> R.drawable.ic_feed_clock
        StatusLevel.RED -> R.drawable.ic_feed_alert
        StatusLevel.NEUTRAL -> null
    }

/**
 * The round photo (or the initials) of a person with the ring of their level and, bottom end, its glyph. Decorative for the screen reader
 * (the parent says who it is and in what state). [jpeg] is the stored photo of the profile (20 000 bytes at most), decoded once.
 */
@Composable
internal fun FeedAvatar(
    jpeg: ByteArray?,
    initials: String,
    level: StatusLevel,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val bitmap = remember(jpeg) { jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(modifier = modifier.size(size).clearAndSetSemantics {}) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colorStatusNeutral)
                    .border(StrokeAvatarRing, level.ring(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(text = initials, style = textBodyBold14, color = colorTextForm)
            }
        }
        level.glyph()?.let { glyph ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(level.ring()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(glyph),
                    contentDescription = null,
                    tint = level.onTint(),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}
