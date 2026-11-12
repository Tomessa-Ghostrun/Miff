package com.tomessa.miff.ui.notification

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.notification.NotificationTexts
import com.tomessa.miff.domain.notification.ItemSection
import com.tomessa.miff.domain.notification.NotificationSections
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.miff.MiffConfirmDialog
import com.tomessa.miff.ui.settings.SettingsScaffold
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextSecondary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular14
import java.time.ZoneId

/** What the gestures of the centre do. */
class NotificationCenterActions(
    val onBack: () -> Unit = {},
    /** A tap on a line: it is marked read and the screen it leads to opens (Update for N1 and N2, the Feed for the others; never an error). */
    val onOpen: (CenterRow) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onClearAll: () -> Unit = {},
    val onBannerAction: (CenterBanner) -> Unit = {},
)

/**
 * The centre of notifications (NOTIF-6A, ticket 7.2 and 20.4): a top bar with the menu "⋯" (tout marquer comme lu, tout effacer), a calm band when the
 * notifications are off, then the lines by day ("Aujourd'hui", "Hier", "Plus ancien"), newest first, or the empty state.
 *
 * A line is ONE element for the screen reader ("{titre}, {heure}, non lu"); deleting goes through a long press (a menu) AND a custom action of the
 * screen reader (no gesture to learn). Everything is local: nothing here touches the server.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationCenterScreen(
    state: NotificationCenterUiState,
    actions: NotificationCenterActions,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // Notifications refused (or never asked): the centre is shown BLURRED, with generic bars where the lines would be (never real data), and ONE way to turn them on.
    val locked = state.banner == CenterBanner.OFF || state.banner == CenterBanner.NOT_ASKED
    SettingsScaffold(
        title = stringResource(R.string.notif_center_title),
        onBack = actions.onBack,
        endAction = {
            CenterMenu(
                open = menuOpen,
                onOpenChange = { menuOpen = it },
                hasRows = state.rows.isNotEmpty() && !locked,
                onMarkAllRead = actions.onMarkAllRead,
                onClearAll = { confirmClear = true },
            )
        },
    ) {
        if (locked) {
            LockedPreview(state.banner) { actions.onBannerAction(state.banner) }
        } else if (state.banner != CenterBanner.NONE) {
            Banner(state.banner) { actions.onBannerAction(state.banner) }
            Spacer(Modifier.size(Spacing.m))
        }
        if (locked) {
            // Nothing else: the real lines stay out of sight while the notifications are off.
        } else if (state.rows.isEmpty()) {
            EmptyState()
        } else {
            NotificationSections.group(state.rows, { it.createdAtMillis }, nowMillis, zone).forEach { (section, rows) ->
                Text(
                    text = stringResource(sectionTitle(section)),
                    style = textBodyBold16,
                    color = colorTextForm,
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xs).semantics { heading() },
                )
                rows.forEach { row ->
                    CenterRowView(
                        row = row,
                        nowMillis = nowMillis,
                        older = section == ItemSection.OLDER,
                        onOpen = { actions.onOpen(row) },
                        onDelete = { actions.onDelete(row.id) },
                    )
                }
            }
        }
    }
    if (confirmClear) {
        MiffConfirmDialog(
            title = stringResource(R.string.notif_center_clear_confirm_title),
            body = stringResource(R.string.notif_center_clear_confirm_body),
            confirmLabel = stringResource(R.string.notif_center_clear_confirm_action),
            cancelLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                confirmClear = false
                actions.onClearAll()
            },
            onCancel = { confirmClear = false },
        )
    }
}

private fun sectionTitle(section: ItemSection): Int =
    when (section) {
        ItemSection.TODAY -> R.string.notif_center_section_today
        ItemSection.YESTERDAY -> R.string.notif_center_section_yesterday
        ItemSection.OLDER -> R.string.notif_center_section_older
    }

/** The "⋯" button at the end of the bar and its two actions. */
@Composable
private fun BoxScope.CenterMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    hasRows: Boolean,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit,
) {
    Box(Modifier.align(Alignment.CenterEnd)) {
        IconButton(onClick = { onOpenChange(true) }, modifier = Modifier.size(Spacing.minTouchTarget)) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.notif_center_menu_a11y),
                tint = colorActionPrimary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { onOpenChange(false) }, containerColor = colorBgSurface) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.notif_center_mark_all_read),
                        style = textBodyRegular14,
                        color = colorTextForm,
                    )
                },
                enabled = hasRows,
                onClick = {
                    onOpenChange(false)
                    onMarkAllRead()
                },
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.notif_center_clear_all),
                        style = textBodyRegular14,
                        color = colorTextForm,
                    )
                },
                enabled = hasRows,
                onClick = {
                    onOpenChange(false)
                    onClearAll()
                },
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            )
        }
    }
}

/** The general switch of the app is off (the phone allows): a calm band with one button that turns it back on. */
@Composable
private fun Banner(
    banner: CenterBanner,
    onAction: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint10)
                .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = stringResource(R.string.notif_center_off_title), style = textBodyBold16, color = colorTextForm)
        Text(
            text = stringResource(R.string.notif_center_off_body),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
        MiffButton(
            text = stringResource(R.string.notif_center_off_action),
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The centre when the notifications are refused or never asked: the screen as it would look with lines behind it, BLURRED (bars only, nothing real, nothing readable,
 * hidden from the screen reader), and over it a calm card: what is off, and ONE button "Activer les notifications". On Android before 12 the blur does nothing: the
 * bars are pale enough to stay a decoration. The button asks the system window when Android can still show it, otherwise leads to the calm help that opens the phone settings.
 */
@Composable
private fun LockedPreview(
    banner: CenterBanner,
    onEnable: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .blur(10.dp)
                    .alpha(0.7f)
                    .clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            repeat(FAKE_ROWS) { index ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(
                                top = Spacing.xs,
                            ).size(Spacing.s + Spacing.xs)
                            .clip(CircleShape)
                            .background(colorBrandPrimary),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    if (index % 2 ==
                                        0
                                    ) {
                                        0.7f
                                    } else {
                                        0.55f
                                    },
                                ).height(Spacing.m)
                                .clip(ShapeMd)
                                .background(colorActionPrimaryTint30),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    0.95f,
                                ).height(Spacing.s + Spacing.xs)
                                .clip(ShapeMd)
                                .background(colorActionPrimaryTint20),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    0.25f,
                                ).height(Spacing.s)
                                .clip(ShapeMd)
                                .background(colorActionPrimaryTint10),
                        )
                    }
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, ShapeMd)
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text =
                    stringResource(
                        if (banner ==
                            CenterBanner.NOT_ASKED
                        ) {
                            R.string.notif_state_notasked
                        } else {
                            R.string.notif_center_off_title
                        },
                    ),
                style = textBodyBold16,
                color = colorTextForm,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text =
                    stringResource(
                        if (banner ==
                            CenterBanner.NOT_ASKED
                        ) {
                            R.string.notif_center_notasked_body
                        } else {
                            R.string.notif_center_off_body
                        },
                    ),
                style = textBodyRegular14,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
            )
            MiffButton(
                text = stringResource(R.string.notif_center_enable_action),
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val FAKE_ROWS = 5

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bell),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.notif_center_empty_title),
            style = textBodyBold16,
            color = colorTextForm,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.notif_center_empty_body),
            style = textBodyRegular14,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CenterRowView(
    row: CenterRow,
    nowMillis: Long,
    older: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val title =
        if (row.type == NotificationType.N9 && row.who != null) {
            stringResource(R.string.notif_circle_update_title, row.who)
        } else {
            stringResource(NotificationTexts.title(row.type))
        }
    val body = stringResource(NotificationTexts.body(row.type))
    val time =
        remember(row.createdAtMillis, older) {
            val flags =
                if (older) {
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
                } else {
                    DateUtils.FORMAT_SHOW_TIME
                }
            DateUtils.formatDateTime(context, row.createdAtMillis, flags)
        }
    val miff = row.miffName?.let { stringResource(R.string.notif_center_in_miff, it) }
    val meta = if (miff != null) "$time · $miff" else time
    val unreadState = stringResource(R.string.notif_center_unread_a11y)
    val deleteLabel = stringResource(R.string.notif_center_delete_a11y)
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(ShapeMd)
                    .heightIn(min = Spacing.minTouchTarget + Spacing.l)
                    .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = { menuOpen = true })
                    .padding(vertical = Spacing.s)
                    // ONE element for the screen reader: the title, the sentence and the time are read together, then "non lu" when it is.
                    .semantics(mergeDescendants = true) {
                        if (row.unread) stateDescription = unreadState
                        customActions =
                            listOf(
                                CustomAccessibilityAction(deleteLabel) {
                                    onDelete()
                                    true
                                },
                            )
                    },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            // The unread mark is a dot AND a bold title: never the colour alone.
            Box(Modifier.padding(top = Spacing.xs).size(Spacing.s + Spacing.xs), contentAlignment = Alignment.Center) {
                if (row.unread) {
                    Box(Modifier.size(Spacing.s + Spacing.xs).clip(CircleShape).background(colorBrandPrimary))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (row.unread) textBodyBold16 else textBodyBold14,
                    color = colorTextForm,
                )
                Text(text = body, style = textBodyRegular14, color = colorTextDescription)
                Text(text = meta, style = textBodyCaption, color = colorTextSecondary)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colorBgSurface) {
            DropdownMenuItem(
                text = { Text(deleteLabel, style = textBodyRegular14, color = colorTextForm) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            )
        }
    }
}
