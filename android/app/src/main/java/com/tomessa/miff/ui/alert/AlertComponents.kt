package com.tomessa.miff.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomessa.miff.R
import com.tomessa.miff.data.alert.EndStep
import com.tomessa.miff.data.alert.MyAlert
import com.tomessa.miff.data.alert.SendStep
import com.tomessa.miff.domain.alert.AlertRules
import com.tomessa.miff.ui.miff.MiffConfirmDialog
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorAlertBanner
import com.tomessa.miff.ui.theme.colorAlertBannerProgress
import com.tomessa.miff.ui.theme.colorAlertFill
import com.tomessa.miff.ui.theme.colorOnAlert
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold24

/*
 * The pieces of the alert that sit on other screens (docs/tickets/alerte-danger.md 7.1, 7.2, 7.4, 13): the blue bubble of the onboarding, its "En savoir plus" sheet, the red mode
 * of the Update while the finger holds, the confirmation of the accessible way, and the banner that follows the person on every tab.
 *
 * TONE: factual and calm. No "!" and no "urgent". The state is never carried by the colour alone: the words are always there.
 */

const val ALERT_HINT_TAG = "alert_hint"
const val ALERT_ARMED_TAG = "alert_armed"
const val ALERT_BANNER_TAG = "alert_banner"
const val ALERT_BANNER_CALL_TAG = "alert_banner_call"
const val ALERT_BANNER_SEE_TAG = "alert_banner_see_miff"
const val ALERT_BANNER_RETRY_TAG = "alert_banner_retry"
const val ALERT_BANNER_CLOSE_TAG = "alert_banner_close"
const val ALERT_BANNER_TITLE_TAG = "alert_banner_title"

/**
 * The TITLE of the banner of an alert in progress: twice the size of before (textBodyBold16: 16 sp), 32 / 40 (decision of the PM, 2026-11-20). A token OWN to the alert, like iOS's
 * `textAlertBannerTitle` (DangerAlertTokens.swift): it is NOT in the shared set of `Type.kt`, which iOS compares to its own (ColorTokensTests.testTextStylesAreIdenticalToAndroid).
 */
private val textAlertBannerTitle = textTitleBold24.copy(fontSize = 32.sp, lineHeight = 40.sp)

/** The most of the screen the banner of an alert in progress, and the blue bubble of the onboarding, may take (the texts scroll inside beyond that; the Update's bubble stops at 40 %). */
private const val BANNER_MAX_SCREEN_SHARE = 0.45f

// --- Blue bubble of the onboarding (maquette 01) ---------------------------------------------------------------------------------------------

/**
 * "Touche pour donner de tes nouvelles / Maintiens pour déclarer une alerte" (maquette 01 said "publier une update": reworded by the decision I18N-4C; tutoiement instead of the infinitive: E1). Not blocking, no button but the discreet "En savoir
 * plus". NO TIMER (decision of the PM, 2026-11-20, recette): it stays until the person's FIRST interaction with the capture button (a press, a hold or a click on it; the caller puts it away and the
 * phone remembers it, see AlertViewModel.onHintDone), so that a screen reader always has the time to read it. Announced politely to a screen reader, once.
 */
@Composable
fun AlertHintBubble(
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // It stays until the first interaction, so with a big system font (x2.0) it must never take the screen: at most [BANNER_MAX_SCREEN_SHARE] of the height, the text scrolls inside.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * BANNER_MAX_SCREEN_SHARE).dp
    Column(
        modifier =
            modifier
                .testTag(ALERT_HINT_TAG)
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(colorActionPrimary.copy(alpha = 0.92f))
                .border(2.dp, colorActionPrimary, RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(text = "• " + stringResource(R.string.alert_hint_tap), style = textBodyRegular14, color = colorOnAlert)
            Text(
                text = "• " + stringResource(R.string.alert_hint_hold),
                style = textBodyRegular14,
                color = colorOnAlert,
            )
        }
        Box(
            modifier =
                Modifier
                    .heightIn(min = Spacing.minTouchTarget)
                    .clickable(role = Role.Button, onClick = onMore),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.alert_hint_more),
                style = textBodyBold14,
                color = colorOnAlert,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

/** What the alert does, what it does not do, and where the position stands (ticket 7.1, 14.2). One button: "Compris". */
@Composable
fun AlertInfoDialog(onClose: () -> Unit) {
    MiffConfirmDialog(
        title = stringResource(R.string.alert_info_title),
        body =
            stringResource(R.string.alert_info_body) + "\n\n" +
                stringResource(R.string.alert_info_position) + "\n\n" +
                stringResource(R.string.alert_not_emergency_note),
        confirmLabel = stringResource(R.string.alert_info_close),
        cancelLabel = stringResource(R.string.alert_info_close),
        onConfirm = onClose,
        onCancel = onClose,
        showCancel = false,
    )
}

// --- Red mode of the Update while the finger holds (maquette 02) ----------------------------------------------------------------------------

/**
 * The Update in alert mode: a red veil that fills from the bottom like a progress bar, and the card "Je suis en danger" with its sentence (maquette 02). No figures (a count down is
 * heavier), no sound. [miffName]: the Miff the alert will go to, shown BEFORE it leaves, only when the person has several Miffs (ticket 7.2, E13). A polite live region: a screen
 * reader says it once when the screen arms.
 */
@Composable
fun AlertArmedOverlay(
    progress: Float,
    miffName: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().testTag(ALERT_ARMED_TAG)) {
        Box(Modifier.fillMaxSize().background(colorAlertFill.copy(alpha = 0.18f)))
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(progress.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .background(colorAlertFill.copy(alpha = 0.50f)),
        )
        Column(
            modifier =
                Modifier.align(Alignment.Center).padding(horizontal = Spacing.l).semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.alert_hold_title),
                style = textTitleBold24,
                color = colorOnAlert,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorAlertBanner.copy(alpha = 0.85f))
                        .border(2.dp, colorAlertFill, RoundedCornerShape(10.dp))
                        .padding(vertical = Spacing.m, horizontal = Spacing.m),
            )
            // White on a dark veil (maquette 02 writes this sentence in red on the photo, which is not readable on a light one: deviation listed in the PR).
            Text(
                text = stringResource(R.string.alert_hold_hint),
                style = textBodyRegular14,
                color = colorOnAlert,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(8.dp),
                        ).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
            )
            miffName?.let {
                Text(
                    text = stringResource(R.string.alert_hold_miff, it),
                    style = textBodyBold14,
                    color = colorOnAlert,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(8.dp),
                            ).background(
                                Color.Black.copy(alpha = 0.55f),
                            ).padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }
        }
    }
}

// --- The two-step confirmation of the accessible way (ticket 13) ----------------------------------------------------------------------------

/** "Déclarer une alerte à tes proches ?" -> "Envoyer l'alerte" / "Annuler": the only way when a screen reader or a switch is on (the hold is not practicable), AD5. */
@Composable
fun AlertStartConfirmDialog(
    miffName: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    MiffConfirmDialog(
        title = stringResource(R.string.alert_confirm_start_title),
        body =
            miffName?.let { stringResource(R.string.alert_confirm_start_body_miff, it) }
                ?: stringResource(R.string.alert_confirm_start_body),
        confirmLabel = stringResource(R.string.alert_confirm_start_action),
        cancelLabel = stringResource(R.string.alert_confirm_cancel),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

@Composable
fun AlertEndConfirmDialog(
    miffName: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    MiffConfirmDialog(
        title = stringResource(R.string.alert_confirm_end_title),
        body =
            miffName?.let { stringResource(R.string.alert_confirm_end_body, it) }
                ?: stringResource(R.string.alert_confirm_end_body_no_name),
        confirmLabel = stringResource(R.string.alert_confirm_end_action),
        cancelLabel = stringResource(R.string.alert_confirm_end_keep),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

// --- The banner (maquette 05) ----------------------------------------------------------------------------------------------------------------

/**
 * The banner of an alert, above the tab bar, on every tab (ticket 7.4). It names the Miff the alert went to (or says "ta Miff" when the name is not known yet, after a relaunch).
 * ACTIVE: holding the body for 5 seconds ends the alert (a veil fills from the start, the same haptics); "Appeler" reopens the screen of the call; "Voir la Miff {nom}" shows the
 * Miff of origin when the Feed shows another one. It has no cross: it goes when the alert ends or expires.
 * NOT ACTIVE on the server (waiting for the network, refused, the daily limit, waiting for a confirmation): it says so calmly, offers "Réessayer" when retrying makes sense and
 * "Numéros d'urgence", and has a CROSS ("Fermer", 48 dp, named for a screen reader) that gives the sending up: nothing is shown any more, nothing is spent or given back, the person
 * may start again (the pointer is only erased once the server confirmed that no alert of mine is alive, see AlertController.cancelPending).
 *
 * Screen reader: a polite region announced once, and the action "Terminer l'alerte" (confirmation in two steps) replaces the hold.
 */
@Composable
fun AlertBanner(
    alert: MyAlert,
    miffName: String?,
    showSeeMiff: Boolean,
    onCall: () -> Unit,
    onSeeMiff: () -> Unit,
    onEndRequested: () -> Unit,
    onEndAccessible: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    if (alert is MyAlert.None) return
    val state = remember { AlertHoldState() }
    val sending = alert as? MyAlert.Sending
    val endable = alert is MyAlert.Active && alert.end != EndStep.ENDING
    // The cross: only where the alert is not active on the server and nothing is in flight (a write under way could still land).
    val closable = sending != null && sending.step != SendStep.SENDING
    val name = miffName?.takeIf { it.isNotBlank() }
    // HIERARCHY (decision of the PM, 2026-11-20, recette): while the alert is ACTIVE (whatever the state of its end) the title is THE QUESTION of the gesture, twice as big as before
    // (32 sp, the body was 16), without the name of the Miff (same as iOS); the sentence under it says how. Every phase of the SENDING keeps its title that names the Miff, at the size of before.
    val endQuestion = alert is MyAlert.Active
    val title =
        when {
            endQuestion -> stringResource(R.string.alert_banner_title)
            sending != null && name != null -> stringResource(R.string.alert_banner_sending, name)
            else -> stringResource(R.string.alert_banner_sending_no_name)
        }
    val note =
        when {
            sending?.step == SendStep.WAITING_NETWORK -> {
                stringResource(R.string.alert_banner_waiting)
            }

            sending?.step == SendStep.NEEDS_CONFIRMATION -> {
                stringResource(R.string.alert_banner_needs_confirmation)
            }

            sending?.step == SendStep.REFUSED -> {
                stringResource(R.string.alert_banner_refused)
            }

            sending?.step == SendStep.QUOTA -> {
                pluralStringResource(
                    R.plurals.alert_banner_quota,
                    AlertRules.MAX_STARTS_PER_DAY,
                    AlertRules.MAX_STARTS_PER_DAY,
                )
            }

            sending != null -> {
                null
            }

            alert is MyAlert.Active && alert.end == EndStep.WAITING_NETWORK -> {
                stringResource(
                    R.string.alert_banner_end_pending,
                )
            }

            else -> {
                stringResource(R.string.alert_banner_body)
            }
        }
    val endAction = stringResource(R.string.alert_banner_end_action)
    // The banner follows the person on every tab: with a big system font (x1.3, x2.0) it must never take the screen. It stops at [BANNER_MAX_SCREEN_SHARE] of the height; beyond that the
    // texts scroll INSIDE it and the buttons stay pinned at its bottom, so that "Appeler" never leaves the screen.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * BANNER_MAX_SCREEN_SHARE).dp
    Box(
        modifier =
            modifier
                .testTag(ALERT_BANNER_TAG)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(colorAlertBanner)
                .alertHoldGesture(
                    state = state,
                    enabled = endable,
                    // The buttons of the banner keep their own touch: a touch they took never starts the hold.
                    ignoreConsumed = true,
                    onDone = onEndRequested,
                ).semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (endable) {
                        customActions =
                            listOf(CustomAccessibilityAction(endAction) { onEndAccessible().let { true } })
                    }
                },
    ) {
        // The veil of the end (a straight fill from the start; nothing when not holding).
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxWidth(
                        state.progress.coerceIn(0f, 1f),
                    ).fillMaxHeight()
                    .background(colorAlertBannerProgress),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.m, top = Spacing.s + Spacing.xs, bottom = Spacing.s + Spacing.xs)
                    // Room for the cross (48 dp) when there is one.
                    .padding(end = if (closable) Spacing.minTouchTarget else Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = title,
                    style = if (endQuestion) textAlertBannerTitle else textBodyBold16,
                    color = colorOnAlert,
                    modifier = Modifier.semantics { heading() }.testTag(ALERT_BANNER_TITLE_TAG),
                )
                note?.let { Text(text = it, style = textBodyRegular14, color = colorOnAlert) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sending?.step == SendStep.REFUSED) {
                    BannerAction(
                        stringResource(R.string.alert_banner_retry),
                        onRetry,
                        Modifier.testTag(ALERT_BANNER_RETRY_TAG),
                    )
                }
                // "Appeler" is for the people of the Miff once the alert is active; before, what the screen offers is the numbers of emergency (and, for a waiting confirmation, the question).
                BannerAction(
                    stringResource(
                        when (sending?.step) {
                            null -> R.string.alert_banner_call
                            SendStep.NEEDS_CONFIRMATION -> R.string.alert_banner_decide
                            else -> R.string.alert_banner_emergency
                        },
                    ),
                    onCall,
                    Modifier.testTag(ALERT_BANNER_CALL_TAG),
                )
                if (showSeeMiff && name != null) {
                    BannerAction(
                        stringResource(R.string.alert_banner_see_miff, name),
                        onSeeMiff,
                        Modifier.testTag(ALERT_BANNER_SEE_TAG),
                    )
                }
            }
        }
        if (closable) {
            val close = stringResource(R.string.alert_banner_close_a11y)
            IconButton(
                onClick = onClose,
                modifier =
                    Modifier
                        .align(
                            Alignment.TopEnd,
                        ).size(Spacing.minTouchTarget)
                        .testTag(ALERT_BANNER_CLOSE_TAG),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_close),
                    contentDescription = close,
                    tint = colorOnAlert,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BannerAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.heightIn(min = Spacing.minTouchTarget).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = textBodyBold14,
            color = colorOnAlert,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Bold,
        )
    }
}
