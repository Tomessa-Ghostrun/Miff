package com.tomessa.miff.ui.feed

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tomessa.miff.R
import com.tomessa.miff.data.feed.PhotoLookup
import com.tomessa.miff.data.feed.PhotoReader
import com.tomessa.miff.domain.feed.MemberCard
import com.tomessa.miff.domain.feed.Moment
import com.tomessa.miff.domain.feed.PhotoSlot
import com.tomessa.miff.domain.feed.PositionFix
import com.tomessa.miff.domain.update.SharedPhoto
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.ShapeSm
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint5
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.colorUpdateScrim
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20
import java.time.ZoneId

/*
 * The photo of the last update on a card, and in full screen when touched (FEED-4; decision D24 of the PM, design/exports/feed/claude/09-photo-plein-ecran
 * and design/screens/update.md 6.1). PRIVACY, COST and SECURITY, for this file:
 * - the photo is read ONLY when the card comes on screen (the list is lazy) and only when the status says there is one ([PhotoSlot.Candidate]); a photo
 *   already read is not read again (PhotoCache); nothing is written or deleted from here, EVER: a refusal of the rules is shown as a calm sentence
 *   ("Partage ta position pour voir cette photo."), never taken for an expired photo and never a reason to delete anything;
 * - every photo has a position now (decision of the PM, 2026-11-06), so a member who has not published their own position meets that sentence on the
 *   photos of the others (reciprocity of the rules, the same as "Localiser");
 * - an unreadable image, a missing, expired or refused photo draws NOTHING or the calm sentence, never an error;
 * - the position of a photo is handed, coordinates alone, to the maps of the system, and only on the touch of "Voir où c'était": no name, no label.
 */

/** The reader of the photos, provided by the Feed tab. Null (tests, previews) draws no photo at all. */
val LocalPhotoReader = compositionLocalOf<PhotoReader?> { null }

/** The photo opened in full screen. Keeps nothing but what is on screen; `toString()` shows nothing. */
class OpenedPhoto(
    val firstName: String,
    val photo: SharedPhoto,
) {
    override fun toString(): String = "OpenedPhoto(redacted)"
}

/** The tile "Partage ta position…" keeps a modest height; the photo itself is a SQUARE (PM, 2026-11-06). */
private val NeedsPositionTileHeight = 108.dp
private const val MAX_PHOTO_PIXELS = 4_000_000L

/** The picture of a photo read from the network, or null when it is not an image or is far too big (somebody else wrote it). */
internal fun decodeSharedPhoto(jpeg: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    if (bounds.outWidth.toLong() * bounds.outHeight > MAX_PHOTO_PIXELS) return null
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
}

/** What the slot of a card shows: the window on the photo, the calm sentence of reciprocity, or nothing. */
@Composable
internal fun MemberPhotoSlot(
    card: MemberCard,
    miffId: String,
    texts: FeedTexts,
    nowMillis: Long,
    onOpen: (OpenedPhoto) -> Unit,
) {
    val slot = card.photo as? PhotoSlot.Candidate ?: return
    val reader = LocalPhotoReader.current ?: return
    val uid = card.member.uid
    var lookup by remember(miffId, uid, slot.lastUpdateAtMillis) { mutableStateOf<PhotoLookup?>(null) }
    // Runs when the card is composed, which a lazy list only does for the cards on screen.
    LaunchedEffect(miffId, uid, slot.lastUpdateAtMillis) {
        lookup = reader.read(miffId, uid, slot.lastUpdateAtMillis)
    }
    when (val found = lookup) {
        is PhotoLookup.Found -> {
            PhotoWindow(card, found.photo, texts, nowMillis, onOpen)
        }

        PhotoLookup.NeedsOwnPosition -> {
            NeedsPositionTile()
        }

        PhotoLookup.Missing, PhotoLookup.Offline, null -> {}
    }
}

@Composable
private fun PhotoWindow(
    card: MemberCard,
    photo: SharedPhoto,
    texts: FeedTexts,
    nowMillis: Long,
    onOpen: (OpenedPhoto) -> Unit,
) {
    val bitmap = remember(photo) { decodeSharedPhoto(photo.jpeg) } ?: return
    val name = card.member.callName
    val label =
        stringResource(
            R.string.update_card_photo_label,
            texts.from(Moment.of(photo.createdAtMillis, nowMillis, ZoneId.systemDefault())),
        )
    val openName = stringResource(R.string.update_card_photo_open_a11y, name)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                // A SQUARE as wide as the card, centred on the middle of the photo (a centred crop, never stretched); the whole photo is in full screen.
                .aspectRatio(1f)
                .clip(ShapeSm)
                .clickable(role = Role.Button) { onOpen(OpenedPhoto(name, photo)) }
                .semantics { contentDescription = "$openName. $label" },
    ) {
        // A square window centred on the middle of the photo; the whole photo is in full screen.
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = label,
            style = textBodyCaption,
            color = colorTextPrimary,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.s)
                    .clip(ShapeSm)
                    .background(colorBgSurface)
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
    }
}

/** "Partage ta position pour voir cette photo." : a soft pale-blue tile with a lock, never "erreur" (design 09, C). */
@Composable
private fun NeedsPositionTile() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(NeedsPositionTileHeight)
                .clip(ShapeSm)
                .background(colorActionPrimaryTint5)
                .border(StrokeHairline, colorBorderAction.copy(alpha = 0.4f), ShapeSm)
                .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text =
                stringResource(R.string.update_card_photo_needs_position),
            style = textBodyRegular14,
            color = colorTextPrimary,
        )
    }
}

/**
 * The photo in full screen (D24): black background, the WHOLE photo (never cropped), "Photo de {Prénom} · {heure}", the close button, the zoom by pinching, and
 * "Voir où c'était" (the position of the photo, handed to the maps of the system) with the time of the measure. The back gesture closes it.
 */
@Composable
internal fun PhotoFullScreen(
    opened: OpenedPhoto,
    texts: FeedTexts,
    nowMillis: Long,
    onOpenMap: (PositionFix) -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val photo = opened.photo
    val bitmap = remember(photo) { decodeSharedPhoto(photo.jpeg) }
    val zone = ZoneId.systemDefault()
    val title =
        stringResource(
            R.string.update_photo_title,
            opened.firstName,
            texts.since(Moment.of(photo.createdAtMillis, nowMillis, zone)),
        )
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform =
        rememberTransformableState { _, zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
    val unavailable = stringResource(R.string.feed_action_unavailable)
    val closeName = stringResource(R.string.update_photo_close_a11y)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).miffScreenInsets()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .transformable(transform)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                            ),
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.s)
                        .size(Spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .semantics { contentDescription = closeName },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(40.dp).background(colorUpdateScrim, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_loc_close),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(text = title, style = textTitleBold20, color = Color.White)
                photo.location?.let { place ->
                    Text(
                        text =
                            stringResource(
                                R.string.update_photo_position,
                                texts.at(Moment.of(place.measuredAtMillis, nowMillis, zone)),
                            ),
                        style = textBodyRegular14,
                        color = Color.White,
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(Spacing.minTouchTarget + Spacing.xs)
                                .clip(ShapeMd)
                                .border(StrokeHairline * 2, Color.White, ShapeMd)
                                .clickable(role = Role.Button) {
                                    if (!onOpenMap(PositionFix(place.lat, place.lng))) {
                                        Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                                    }
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_update_pin),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.update_photo_see_where),
                                style = textBodyBold14,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_ZOOM = 4f
