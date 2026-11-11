package com.tomessa.miff.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16

/**
 * What the Feed shows of the notifications (NOTIF-1A and NOTIF-6A): the dot of the bell, the way to the centre, and the card of the "second chance".
 * [unreadCount] feeds the dot AND the words of the screen reader; the dot never shows a number (calm). [secondChanceVisible] is decided by
 * [com.tomessa.miff.domain.notification.SecondChanceRules.visible].
 */
class FeedNotifications(
    val unreadCount: Int = 0,
    val secondChanceVisible: Boolean = false,
    val onOpenCenter: () -> Unit = {},
    val onSecondChanceEnable: () -> Unit = {},
    val onSecondChanceLater: () -> Unit = {},
)

/**
 * The bell of the header (maquette 01): a blue outline with a small yellow-green dot when something is unread. 48 dp to touch; named "Notifications"
 * or "Notifications, 2 non lues" for the screen reader (the dot alone would be a colour with no words).
 */
@Composable
fun NotificationBell(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        if (unreadCount > 0) {
            pluralStringResource(R.plurals.notif_bell_unread_a11y, unreadCount, unreadCount)
        } else {
            stringResource(R.string.notif_bell_a11y)
        }
    Box(
        modifier =
            modifier
                .size(Spacing.minTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bell),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(28.dp),
        )
        if (unreadCount > 0) {
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
}

/**
 * The card of the "second chance" (ticket 8.1, step 3): non modal, in the list of the Feed, offered ONCE after the first update of someone who put the
 * pop-in off. Two quiet answers; either one makes it go for good. No window, no sound.
 */
@Composable
fun NotificationSecondChanceCard(
    onEnable: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint10)
                .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.notif_second_chance_title),
            style = textBodyBold16,
            color = colorTextForm,
        )
        MiffButton(
            text = stringResource(R.string.notif_second_chance_action),
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onLater, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                Text(
                    text = stringResource(R.string.notif_second_chance_later),
                    style = textBodyBold16,
                    color = colorActionPrimary,
                )
            }
        }
    }
}
