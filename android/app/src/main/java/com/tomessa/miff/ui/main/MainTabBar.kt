package com.tomessa.miff.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAvatarRing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusWarning
import com.tomessa.miff.ui.theme.colorTextSecondary
import com.tomessa.miff.ui.theme.colorUpdateNeutral
import com.tomessa.miff.ui.theme.miffShadow

private val BarShape = RoundedCornerShape(20.dp)
private val ItemHeight = 52.dp
private val PlateSize = 44.dp
private val DotSize = 30.dp

/**
 * The floating tab bar of the mockups (Onboarding ticket 6.7): a white rounded card with the Feed (house with a heart), the
 * round central button and the Profile (silhouette). The tab in use has a pale blue pad behind a blue icon; the others are
 * grey. There are no visible labels (mockups): every tab has a spoken name, and the state ("selected", "disabled") is
 * given to the screen reader, never by colour alone for it.
 *
 * The bar is laid out with start and end: in a right-to-left language the order is reversed by itself, and the icons
 * (no direction) are not mirrored. Each tab is at least 48 dp tall and wide.
 */
@Composable
fun MainTabBar(
    config: TabBarConfig,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    /** The colour of MY status in the Miff shown by the Feed: the dot of the central button (D19, D25). */
    updateLevel: StatusLevel = StatusLevel.NEUTRAL,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, BarShape)
                    .clip(BarShape)
                    .background(colorBgSurface)
                    .padding(Spacing.xs)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            config.tabs.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selected,
                    enabled = config.isEnabled(tab),
                    updateLevel = updateLevel,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: MainTab,
    selected: Boolean,
    enabled: Boolean,
    updateLevel: StatusLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name =
        stringResource(
            when (tab) {
                MainTab.FEED -> {
                    R.string.tabbar_feed_a11y
                }

                MainTab.PROFILE -> {
                    R.string.tabbar_profile_a11y
                }

                // The colour is the ONLY visual signal of the central button (D25): the spoken name says the state for the screen reader.
                MainTab.UPDATE -> {
                    when (updateLevel) {
                        StatusLevel.GREEN -> R.string.tabbar_update_green_a11y
                        StatusLevel.ORANGE -> R.string.tabbar_update_orange_a11y
                        StatusLevel.RED -> R.string.tabbar_update_red_a11y
                        StatusLevel.NEUTRAL -> R.string.tabbar_update_a11y
                    }
                }
            },
        )
    val tint = if (selected) colorActionPrimary else colorTextSecondary
    Box(
        modifier =
            modifier
                .heightIn(min = ItemHeight)
                .clip(ShapeMd)
                .background(if (selected) colorActionPrimaryTint20 else Color.Transparent)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onClick,
                ).semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        when (tab) {
            MainTab.FEED -> {
                Icon(painter = painterResource(R.drawable.ic_tab_feed), contentDescription = null, tint = tint)
            }

            MainTab.PROFILE -> {
                Icon(painter = painterResource(R.drawable.ic_person), contentDescription = null, tint = tint)
            }

            MainTab.UPDATE -> {
                StatusDot(updateLevel)
            }
        }
    }
}

/**
 * The central button (decision D25 of the PM, 2026-11-06: "la couleur est le seul signal"): an opaque white plate, the blue ring of the mockups, and a
 * plain DOT of the colour of my status inside: green, orange, red, or grey when nothing is known (sharing stopped, no status yet). NO pictogram: no tick, clock
 * or exclamation mark. The plate keeps the dot readable on any background.
 */
@Composable
private fun StatusDot(level: StatusLevel) {
    val neutral = level == StatusLevel.NEUTRAL
    val dot =
        when (level) {
            StatusLevel.GREEN -> colorStatusSafe
            StatusLevel.ORANGE -> colorStatusWarning
            StatusLevel.RED -> colorStatusDanger
            StatusLevel.NEUTRAL -> colorUpdateNeutral
        }
    Box(
        modifier =
            Modifier
                .size(PlateSize)
                .background(colorBgSurface, CircleShape)
                .border(StrokeAvatarRing, if (neutral) colorUpdateNeutral else colorActionPrimary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(DotSize).background(dot, CircleShape))
    }
}
