package com.tomessa.miff.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.miff.MiffLoading
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyItalic
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textDisplayHeadline

/**
 * The Feed tab, still PROVISIONAL (the real Feed is the FEED tickets). What this ticket gives it (ticket Miff 7, decisions Q2
 * and Q12 of the feed ticket), following the mockup "feed -2" and "feed -3":
 * - the pastille of the Miff under the wordmark: the NAME of the Miff, not clickable with a free account (a free account has
 *   one Miff; the Profile card opens it); a selector with "+ Rejoindre une Miff" and "+ Créer une Miff" for a Premium account;
 * - the state "Pas encore de Miff" with the two buttons "Créer une Miff" and "Rejoindre une Miff". The green "Tout le monde est
 *   en sécurité" of the mockup is NOT drawn: no Miff means nothing is known about anybody, and the app never says "all is well"
 *   when it does not know (decision Q12).
 * [scrollToTopSignal] goes up each time the person taps the tab they are already on.
 */
@Composable
fun FeedScreen(
    state: DirectoryState,
    scrollToTopSignal: Int,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) scroll.animateScrollTo(0)
    }
    val paneName = stringResource(R.string.feed_pane_a11y)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
                .semantics { paneTitle = paneName },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Image(
            painter = painterResource(R.drawable.miff_wordmark_color),
            contentDescription = stringResource(R.string.home_logo_a11y),
            modifier = Modifier.width(Spacing.logoWidth / 2),
        )
        when (state) {
            DirectoryState.Loading -> {
                MiffLoading()
            }

            DirectoryState.Unavailable -> {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    MiffNotice(stringResource(R.string.feed_unavailable))
                    MiffSecondaryButton(
                        text = stringResource(R.string.auth_retry_action),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is DirectoryState.Ready -> {
                if (state.miffs.isEmpty()) {
                    NoMiff(onCreate, onJoin)
                } else {
                    MiffPill(state, onSelect, onCreate, onJoin)
                    if (state.fromCache) MiffNotice(stringResource(R.string.feed_offline_note))
                    Text(
                        text = stringResource(R.string.feed_empty_title),
                        style = textDisplayHeadline,
                        color = colorTextForm,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.l).semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.feed_empty_body),
                        style = textBodyRegular,
                        color = colorTextDescription,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** "Pas encore de Miff": a pastille that does nothing, a calm sentence, and the two ways to begin. */
@Composable
private fun NoMiff(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Pill(text = stringResource(R.string.feed_selector_none), selectable = false, contentLabel = null, onClick = {})
    Text(
        text = stringResource(R.string.feed_nomiff_body),
        style = textBodyRegular,
        color = colorTextDescription,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = Spacing.l),
    )
    MiffButton(
        text = stringResource(R.string.feed_create_action),
        onClick = onCreate,
        modifier = Modifier.fillMaxWidth(),
    )
    MiffButton(
        text = stringResource(R.string.feed_join_action),
        onClick = onJoin,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The name of the active Miff; a selector only for a Premium account (a free account has one Miff, decision Q2). */
@Composable
private fun MiffPill(
    state: DirectoryState.Ready,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    val name = state.active?.name ?: return
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(
            text = name,
            selectable = state.premium,
            contentLabel = if (state.premium) stringResource(R.string.feed_selector_a11y, name) else null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colorBgSurface) {
            state.miffs.forEach { miff ->
                DropdownMenuItem(
                    text = { Text(miff.name, style = textBodyItalic, color = colorTextForm) },
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
                    onJoin()
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
                    onCreate()
                },
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            )
        }
    }
}

/** The grey rounded pastille of the mockups (name in italics, small triangle when it opens a list). */
@Composable
private fun Pill(
    text: String,
    selectable: Boolean,
    contentLabel: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .then(
                    if (selectable) Modifier.clickable(role = Role.DropdownList, onClick = onClick) else Modifier,
                ).heightIn(min = Spacing.minTouchTarget)
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
