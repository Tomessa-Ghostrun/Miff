package com.tomessa.miff.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.Battery
import com.tomessa.miff.domain.feed.MemberCard
import com.tomessa.miff.domain.feed.PhotoSlot
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.feed.PlaceRef
import com.tomessa.miff.domain.feed.StateLabel
import com.tomessa.miff.domain.profile.Initials
import com.tomessa.miff.ui.theme.ShadowSubtle
import com.tomessa.miff.ui.theme.ShapeFull
import com.tomessa.miff.ui.theme.ShapeSm
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textCaptionBold12
import com.tomessa.miff.ui.theme.textDisplayLabel

/*
 * The card of a member (maquettes 06 and the PM's "Welcoming screen"): a strip of the colour of the level on the left, the avatar with its
 * ring, the first name, the battery, the label of the state, the line of time, and the three gestures. MY card is the first one and has no
 * gesture. The colour is the only signal of the level (strip, ring, label: no glyph, decision of the PM, 2026-11-06); the words of the state stay and are read. One reading for the screen reader: the
 * information is one block, the gestures are buttons of their own after it.
 */

@Composable
fun MemberCardView(
    card: MemberCard,
    texts: FeedTexts,
    locating: Boolean,
    onLocate: (uid: String) -> Unit,
    onMessage: (phone: String) -> Unit,
    onCall: (phone: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .miffShadow(ShadowSubtle, ShapeSm)
                .clip(ShapeSm)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderDefault, ShapeSm),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(card.level.ring()))
        Column(
            modifier =
                Modifier
                    .weight(
                        1f,
                    ).padding(start = Spacing.m - Spacing.xs, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // ONE block for the screen reader, read in the order of the ticket (T2): the person, the state, the time, then the battery.
            Box(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FeedAvatar(
                            jpeg = card.member.avatarJpeg,
                            initials = Initials.of(card.member.firstName, card.member.lastName),
                            level = card.level,
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = card.member.callName,
                                style = textDisplayLabel,
                                color = colorTextPrimary,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (card.isMe) YouPill()
                        }
                        card.battery?.let { BatteryChip(it, texts) }
                    }
                    StateChip(card, texts)
                    if (card.limitedLocation) LimitedLocationBadge()
                    texts
                        .timeLine(
                            card.time,
                        )?.let { Text(text = it, style = textBodyRegular14, color = colorTextDescription) }
                    // FEED-4 will draw the photo of the last update here; "no photo" draws nothing (no hole, no message).
                    PhotoSlotView(card.photo)
                    if (card.isMe) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_visibility),
                                contentDescription = null,
                                tint = colorTextDescription,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.feed_card_you_sees),
                                style = textBodyCaption,
                                color = colorTextDescription,
                            )
                        }
                    }
                }
                // The battery is READ last (state, time, then battery: ticket T2) although it is DRAWN top right: the chip is hidden from the
                // screen reader and this empty node, last of the block, carries its sentence ("Batterie à 85 % à 16 h 02").
                card.battery?.let { battery ->
                    val spoken = texts.batteryA11y(battery)
                    Box(Modifier.size(0.dp).semantics { text = AnnotatedString(spoken) })
                }
            }
            CardActions(card, locating, onLocate, onMessage, onCall)
        }
    }
}

@Composable
private fun PhotoSlotView(slot: PhotoSlot) {
    when (slot) {
        PhotoSlot.None -> {}
    }
}

@Composable
private fun YouPill() {
    Text(
        text = stringResource(R.string.feed_card_you),
        style = textCaptionBold12,
        color = colorTextPrimary,
        modifier =
            Modifier
                .clip(ShapeFull)
                .background(colorBorderDefault)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs / 2),
    )
}

@Composable
private fun BatteryChip(
    battery: Battery,
    texts: FeedTexts,
) {
    Row(
        modifier =
            Modifier
                .clip(ShapeSm)
                .background(colorBorderDefault)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs / 2)
                .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_feed_battery),
            contentDescription = null,
            tint = colorTextDescription,
            modifier = Modifier.size(14.dp),
        )
        Text(text = texts.battery(battery), style = textCaptionBold12, color = colorTextDescription)
    }
}

/** The label of the state: its words on the tint of the level. The colour is the only signal, no pictogram (decision of the PM, 2026-11-06). */
@Composable
private fun StateChip(
    card: MemberCard,
    texts: FeedTexts,
) {
    Text(
        text = texts.label(card.label),
        style = textBodyRegular14,
        color = card.level.onTint(),
        modifier =
            Modifier
                .clip(ShapeSm)
                .background(card.level.tint())
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

@Composable
private fun LimitedLocationBadge() {
    Row(
        modifier =
            Modifier
                .clip(ShapeSm)
                .background(colorActionPrimaryTint10)
                .border(StrokeHairline, colorBorderAction, ShapeSm)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_feed_pin_off),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.feed_label_limited_location),
            style = textBodyRegular14,
            color = colorActionPrimary,
        )
    }
}

/**
 * Localiser, Message, Appeler: three buttons of 48 dp, in pictograms (as the PM's mockup), each named for the screen reader. None on MY card.
 * "Écrire à" and "Appeler" exist only with a number; "Localiser" only when the card says it can be located.
 */
@Composable
private fun CardActions(
    card: MemberCard,
    locating: Boolean,
    onLocate: (String) -> Unit,
    onMessage: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    if (card.isMe) return
    val name = card.member.callName
    val phone = card.phone
    if (!card.canLocate && phone == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (card.canLocate) {
            if (locating) {
                val busy = stringResource(R.string.feed_card_locating_a11y, name)
                Box(
                    modifier = Modifier.size(Spacing.minTouchTarget).semantics { contentDescription = busy },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Spacing.m + Spacing.s),
                        strokeWidth = 2.dp,
                        color = colorActionPrimary,
                    )
                }
            } else {
                IconButton(onClick = { onLocate(card.member.uid) }, modifier = Modifier.size(Spacing.minTouchTarget)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_loc_pin),
                        contentDescription = stringResource(R.string.feed_card_locate_a11y, name),
                        tint = colorActionPrimary,
                    )
                }
            }
        }
        if (phone != null) {
            IconButton(onClick = { onMessage(phone) }, modifier = Modifier.size(Spacing.minTouchTarget)) {
                Icon(
                    painter = painterResource(R.drawable.ic_feed_chat),
                    contentDescription = stringResource(R.string.feed_card_message_a11y, name),
                    tint = colorActionPrimary,
                )
            }
            IconButton(onClick = { onCall(phone) }, modifier = Modifier.size(Spacing.minTouchTarget)) {
                Icon(
                    painter = painterResource(R.drawable.ic_feed_call),
                    contentDescription = stringResource(R.string.feed_card_call_a11y, name),
                    tint = colorActionPrimary,
                )
            }
        }
    }
}
