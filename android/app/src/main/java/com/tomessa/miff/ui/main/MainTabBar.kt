package com.tomessa.miff.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.tomessa.miff.ui.theme.GlassPalette
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.glassPalette

private val BarShape = RoundedCornerShape(20.dp)
private val ItemHeight = 48.dp
private val ItemWidth = 64.dp
private val IconSize = 26.dp

// 24 dp each side (24 pt validated for iOS, 24 dp PROPOSED for Android: same drawing) and 22 dp under the bar, plus the navigation bar.
private val BarSideMargin = Spacing.l
private val BarBottomMargin = 22.dp

/**
 * The floating tab bar of the mockups (Onboarding ticket 6.7, glass version of ticket TABBAR-GLASS): a rounded bar of frosted glass
 * (see GlassTabBar.kt) with the Feed (house with a heart), the central button (a plate with the colour of the status, no
 * pictogram) and the Profile (silhouette). The tab in use has a pad behind its icon; the others are in the
 * inactive colour. There are no visible labels (mockups): every tab has a spoken name, and the state ("selected", "disabled") is
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
    /** The glass (variant, level, blur source) decided by [GlassTabHost]; without it, a light bar with no blur. */
    glass: TabBarGlass = TabBarGlass.Default,
    /** Colour of the central button: the status of the person (wired by the UPDATE tickets; neutral grey until then). */
    updateStatus: StatusLevel = StatusLevel.NEUTRAL,
    /** A request to add a place waits for the creator in the Profile (ticket LIEU-REQ-C): a small dot on the Profile tab, and its spoken name says why. */
    profileDot: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = BarSideMargin, end = BarSideMargin, bottom = BarBottomMargin),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(GlassBarHeight)
                    .glassSurface(BarShape, glass)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            config.tabs.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selected,
                    enabled = config.isEnabled(tab),
                    onClick = { onSelect(tab) },
                    palette = glassPalette(glass.variant),
                    updateStatus = updateStatus,
                    dot = profileDot && tab == MainTab.PROFILE,
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
    onClick: () -> Unit,
    palette: GlassPalette,
    updateStatus: StatusLevel,
    modifier: Modifier = Modifier,
    /** A small dot: something waits behind this tab (never a number). */
    dot: Boolean = false,
) {
    val name =
        stringResource(
            when (tab) {
                MainTab.FEED -> {
                    R.string.tabbar_feed_a11y
                }

                MainTab.PROFILE -> {
                    if (dot) R.string.tabbar_profile_requests_a11y else R.string.tabbar_profile_a11y
                }

                // The colour is the ONLY visual signal of the central button (D25): the spoken name says the state for the screen reader.
                MainTab.UPDATE -> {
                    when (updateStatus) {
                        StatusLevel.GREEN -> R.string.tabbar_update_green_a11y
                        StatusLevel.ORANGE -> R.string.tabbar_update_orange_a11y
                        StatusLevel.RED -> R.string.tabbar_update_red_a11y
                        StatusLevel.NEUTRAL -> R.string.tabbar_update_a11y
                    }
                }
            },
        )
    val tint = if (selected) palette.iconActive else palette.iconInactive
    Box(
        modifier =
            modifier
                .size(width = ItemWidth, height = ItemHeight)
                .clip(ShapeMd)
                .background(if (selected) palette.pillActive else Color.Transparent)
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
                Icon(
                    painter = painterResource(if (selected) R.drawable.ic_tab_feed_filled else R.drawable.ic_tab_feed),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(IconSize),
                )
            }

            MainTab.PROFILE -> {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(IconSize),
                )
                if (dot) {
                    // The same dot as the bell of the Feed.
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = Spacing.s, end = Spacing.s)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorBrandPrimary),
                    )
                }
            }

            // The plate, ring and dot of the mockups (GlassTabBar.kt): a dot of colour, no pictogram, grey at the neutral state.
            MainTab.UPDATE -> {
                CentralStatusButton(level = updateStatus)
            }
        }
    }
}
