package com.tomessa.miff.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.miff.MiffSelector
import com.tomessa.miff.domain.miff.SelectorEntry
import com.tomessa.miff.domain.miff.SelectorTap
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShadowSubtle
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyItalic

/*
 * The FIXED header of the Feed (maquette 01): my avatar with the ring of MY status (it opens the Profile tab), the logo (it brings the feed
 * back to the top), and, under them, the pastille of the Miff. No bell: nothing is notified (plan gratuit), the right side stays empty.
 */

/** Me, for the header: the initials and the photo of my profile. */
class FeedMe(
    val initials: String = "?",
    val avatarJpeg: ByteArray? = null,
) {
    override fun toString(): String = "FeedMe(redacted)"
}

@Composable
internal fun FeedHeader(
    me: FeedMe,
    myLevel: StatusLevel,
    myStatusDescription: String?,
    onOpenProfile: () -> Unit,
    onLogo: () -> Unit,
    modifier: Modifier = Modifier,
    pill: @Composable () -> Unit,
) {
    val profileLabel =
        if (myStatusDescription != null) {
            stringResource(R.string.feed_header_avatar_a11y, myStatusDescription)
        } else {
            stringResource(R.string.feed_header_avatar_plain_a11y)
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .miffShadow(ShadowSubtle, RectangleShape)
                .background(colorBgCanvas)
                .padding(horizontal = Spacing.m)
                .padding(bottom = Spacing.s + Spacing.xs / 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
            // The zone of touch is 48 dp (the disc is 46). The right end of the row is RESERVED (48 dp) for the future button of the notifications:
            // nothing is drawn there today (no bell), and the logo stays centred.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(Spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(onClickLabel = null, role = Role.Button, onClick = onOpenProfile)
                        .semantics { contentDescription = profileLabel },
                contentAlignment = Alignment.Center,
            ) {
                FeedAvatar(jpeg = me.avatarJpeg, initials = me.initials, level = myLevel)
            }
            Spacer(Modifier.align(Alignment.CenterEnd).size(Spacing.minTouchTarget))
            val scrollLabel = stringResource(R.string.feed_header_logo_a11y)
            Image(
                painter = painterResource(R.drawable.miff_wordmark_color),
                contentDescription = stringResource(R.string.home_logo_a11y),
                modifier =
                    Modifier
                        .width(Spacing.logoWidth / 2)
                        .clickable(onClickLabel = scrollLabel, role = Role.Button, onClick = onLogo)
                        // The logo is 38 dp high: its zone of touch is at least 48 dp (the layout grows to it, the picture stays centred).
                        .minimumInteractiveComponentSize(),
            )
        }
        pill()
    }
}

/**
 * The name of the active Miff, and the selector it opens: for EVERY account, even with a single Miff (decision of the PM, 2026-11-08, which replaces
 * Q2 / D12). The list shows the Miff(s) and the two entries "Rejoindre une Miff" and "Créer une Miff"; what a touch on an entry does is decided by
 * [MiffSelector.onTap]: a free account that already has a Miff gets the offer ([onOffer]), the others go on to the screen of the entry.
 */
@Composable
internal fun MiffPill(
    state: DirectoryState.Ready,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    /** Opens the offer (paywall) for a 2nd Miff: only a free account that already has a Miff gets here. */
    onOffer: () -> Unit = {},
    /** Told each time the list of Miffs opens or closes: while it is open the header cannot hide. */
    onOpenChanged: (Boolean) -> Unit = {},
) {
    val name = state.active?.name ?: return

    fun onEntry(entry: SelectorEntry) {
        when (MiffSelector.onTap(entry, state.premiumOrNull, state.miffs.size)) {
            SelectorTap.OpenOffer -> onOffer()
            SelectorTap.OpenJoin -> onJoin()
            SelectorTap.OpenCreate -> onCreate()
        }
    }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { onOpenChanged(open) }
    // A header that leaves the composition while the list is open must not keep the lock.
    DisposableEffect(Unit) { onDispose { onOpenChanged(false) } }
    Box {
        Pill(
            text = name,
            selectable = true,
            contentLabel = stringResource(R.string.feed_selector_a11y, name),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colorBgSurface) {
            state.miffs.forEach { miff ->
                val active = miff.id == state.activeId
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // A tick on the Miff shown: bold alone is not enough to tell it.
                            if (active) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_loc_check),
                                    contentDescription = null,
                                    tint = colorActionPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(miff.name, style = textBodyItalic, color = colorTextForm)
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(miff.id)
                    },
                    modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.feed_selector_join),
                        style = textBodyBold16,
                        color = colorActionPrimary,
                    )
                },
                onClick = {
                    open = false
                    onEntry(SelectorEntry.JOIN)
                },
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.feed_selector_create),
                        style = textBodyBold16,
                        color = colorActionPrimary,
                    )
                },
                onClick = {
                    open = false
                    onEntry(SelectorEntry.CREATE)
                },
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            )
        }
    }
}

/**
 * The pastille of the Miff. The selector of the Feed has the shadow and the blue arrow of the mockup and opens the list, for everyone. The flat
 * form (no shadow, no arrow, announced as text, not a button) is only for "Pas encore de Miff", which has nothing to open.
 */
@Composable
internal fun Pill(
    text: String,
    selectable: Boolean,
    contentLabel: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .then(if (selectable) Modifier.miffShadow(ShadowDefault, ShapeMd) else Modifier)
                .clip(ShapeMd)
                .background(if (selectable) colorBgSurface else Color.Black.copy(alpha = 0.05f))
                .then(
                    if (selectable) Modifier.clickable(role = Role.DropdownList, onClick = onClick) else Modifier,
                ).heightIn(min = if (selectable) Spacing.minTouchTarget else 40.dp)
                .padding(horizontal = Spacing.m)
                .semantics(mergeDescendants = true) {
                    if (contentLabel != null) contentDescription = contentLabel
                    liveRegion = LiveRegionMode.Polite
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = textBodyItalic, color = colorTextForm)
        if (selectable) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = colorActionPrimary,
            )
        }
    }
}
