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
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAvatarRing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorTextSecondary
import com.tomessa.miff.ui.theme.miffShadow

private val BarShape = RoundedCornerShape(20.dp)
private val ItemHeight = 52.dp
private val UpdateRingSize = 28.dp

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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name =
        stringResource(
            when (tab) {
                MainTab.FEED -> R.string.tabbar_feed_a11y
                MainTab.PROFILE -> R.string.tabbar_profile_a11y
                MainTab.UPDATE -> if (enabled) R.string.tabbar_update_a11y else R.string.tabbar_update_soon_a11y
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
                // The round button of the mockups: a yellow-green ring around a pale centre. Dimmed while it does nothing.
                Box(
                    modifier =
                        Modifier
                            .size(UpdateRingSize)
                            .background(colorStatusSafeTint, CircleShape)
                            .border(
                                StrokeAvatarRing,
                                colorBrandPrimary.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
                                CircleShape,
                            ),
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f
