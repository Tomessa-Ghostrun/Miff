package com.tomessa.miff.ui.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.theme.GradientProfileHeader
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textDisplayHeadline
import com.tomessa.miff.ui.theme.textDisplayTitle

private val AvatarSize = 80.dp
private val GearSize = Spacing.minTouchTarget

/**
 * The Profile tab (mockup "profil", ticket ONB-3a, criterion A3): the photo (or the initials), "Prénom Nom", "C'est toi" and
 * the gear that opens Settings. A tap on the photo and the name opens "Mes informations". Nothing is drawn under the header
 * until the Miff and Places tickets exist: no "Famille", no invented place. [scrollToTopSignal] goes up when the person taps
 * the tab they are already on.
 */
@Composable
fun ProfileTabScreen(
    state: ProfileTabUiState,
    scrollToTopSignal: Int,
    onOpenMyInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    miffs: DirectoryState = DirectoryState.Loading,
    onOpenMiff: (String) -> Unit = {},
) {
    RecentsPrivacy()
    val scroll = rememberScrollState()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) scroll.animateScrollTo(0)
    }
    val paneName = stringResource(R.string.profile_tab_pane_a11y)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientProfileHeader)
                .statusBarsPadding()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
                .semantics { paneTitle = paneName },
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (state.status) {
                    ProfileTabStatus.READY -> Identity(state, onOpenMyInfo)
                    ProfileTabStatus.LOADING -> LoadingIdentity()
                    ProfileTabStatus.UNAVAILABLE -> Unit
                }
            }
            GearButton(onOpenSettings)
        }
        if (state.status == ProfileTabStatus.UNAVAILABLE) {
            Unavailable(onRetry)
        }
        MiffCards(miffs, onOpenMiff)
    }
}

@Composable
private fun Identity(
    state: ProfileTabUiState,
    onOpenMyInfo: () -> Unit,
) {
    val openLabel = stringResource(R.string.profile_tab_open_my_info_a11y)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpenMyInfo)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ProfileAvatar(jpeg = state.profile?.avatarJpeg, initials = state.initials, size = AvatarSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.displayName,
                style = textDisplayHeadline,
                color = colorTextForm,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.profile_tab_you_label),
                style = textBodyRegular,
                color = colorTextDescription,
            )
        }
    }
}

@Composable
private fun LoadingIdentity() {
    val loading = stringResource(R.string.auth_loading_a11y)
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = AvatarSize),
        contentAlignment = Alignment.CenterStart,
    ) {
        CircularProgressIndicator(
            color = colorActionPrimary,
            modifier =
                Modifier
                    .size(Spacing.xl)
                    .semantics {
                        contentDescription = loading
                        liveRegion = LiveRegionMode.Polite
                    },
        )
    }
}

@Composable
private fun Unavailable(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            text = stringResource(R.string.profile_tab_unavailable),
            style = textBodyRegular,
            color = colorTextDescription,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        MiffSecondaryButton(text = stringResource(R.string.auth_retry_action), onClick = onRetry)
    }
}

/** The gear of the mockup: a white disc with a blue gear, 48 dp so that it is easy to reach. */
@Composable
private fun GearButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(GearSize)
                .miffShadow(ShadowDefault, CircleShape)
                .background(colorBgSurface, CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.profile_tab_settings_a11y),
            tint = colorActionPrimary,
        )
    }
}

/**
 * "Mes Miffs": one card per Miff, which opens the screen of the Miff (decision Q2 of the feed ticket: the card of the Profile is
 * the way in for every account; the "Créer une nouvelle Miff" card is for Premium accounts and arrives with the Premium ticket).
 * Only the NAME is shown: no count, no photo, no status (the full Profile is the PRO-1 ticket).
 */
@Composable
private fun MiffCards(
    miffs: DirectoryState,
    onOpenMiff: (String) -> Unit,
) {
    val ready = miffs as? DirectoryState.Ready ?: return
    if (ready.miffs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            text = stringResource(R.string.profile_tab_miffs_title),
            style = textDisplayTitle,
            color = colorTextForm,
            modifier = Modifier.semantics { heading() },
        )
        ready.miffs.forEach { miff ->
            val openLabel = stringResource(R.string.profile_tab_miff_open_a11y, miff.name)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .miffShadow(ShadowDefault, ShapeMd)
                        .clip(ShapeMd)
                        .background(colorBgSurface)
                        .clickable(onClickLabel = openLabel, role = Role.Button) { onOpenMiff(miff.id) }
                        .heightIn(min = Spacing.minTouchTarget)
                        .padding(horizontal = Spacing.m, vertical = Spacing.s)
                        .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = miff.name, style = textBodyBold16, color = colorTextForm)
                    Text(
                        text = stringResource(R.string.profile_tab_miff_hint),
                        style = textBodyRegular,
                        color = colorTextDescription,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_end),
                    contentDescription = null,
                    tint = colorActionPrimary,
                )
            }
        }
    }
}
