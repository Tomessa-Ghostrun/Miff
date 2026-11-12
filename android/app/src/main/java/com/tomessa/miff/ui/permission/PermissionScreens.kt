package com.tomessa.miff.ui.permission

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.permission.LocationScreenModel
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.miff.ConsentLine
import com.tomessa.miff.ui.miff.DialogTone
import com.tomessa.miff.ui.miff.MiffConfirmDialog
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.miff.RenewalDetailsCard
import com.tomessa.miff.ui.miff.actionMessageText
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeFull
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.colorTextSecondary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayHeadline
import com.tomessa.miff.ui.theme.textTitleBold20

/*
 * The screens of consent before the Feed (LOC-2a). Pure drawing: state in, events out; the view model decides and the host (PermissionGate)
 * calls the system. NOTHING here reads a position, sends an event or logs a word (PermissionPrivacyTest reads these sources).
 *
 * Design: pop-ins of the PM (design/exports/pop-ins 01 and 02), screens 11 to 13 (design/exports/lieux/claude) and 21 (renewal). The pop-in
 * is drawn on the app's own background rather than on a blurred Feed: nothing of the Feed is loaded behind a blocking screen (listed
 * as a deviation in the pull request).
 */

/** A pop-in of the PM: white card, blue border, centred on the background; the content scrolls, the button stays visible below it. */
@Composable
private fun PopIn(
    paneTitle: String,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .miffScreenInsets()
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
                .semantics { this.paneTitle = paneTitle },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, ShapeMd)
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .border(StrokeAccent, colorBorderAction, ShapeMd),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = Spacing.m, end = Spacing.m, top = Spacing.l, bottom = Spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                content()
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                footer()
            }
        }
    }
}

@Composable
private fun PopInTitle(text: String) {
    Text(
        text = text,
        style = textTitleBold20,
        color = colorActionPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

// --- Notifications (pop-in 01, decision (b) of the PM) -----------------------------------------------------------------

/**
 * "Un rappel, seulement quand c'est utile": the pop-in of the PM (maquette 01) with the text of NOTIF-P0 (ticket section 8.3, in the tutoiement): ONE
 * example, the real reminder N1 that the person would get, an honest body (the alerts about each loved one come later), "Autoriser" and "Plus tard".
 * "Plus tard" does NOT use up the window of the system. NEVER blocking: whatever the answer, the person goes on.
 */
@Composable
fun NotificationsPopIn(
    onAllow: () -> Unit,
    onLater: () -> Unit,
) {
    val title = stringResource(R.string.notif_prime_title)
    PopIn(
        paneTitle = title,
        footer = {
            MiffButton(
                text = stringResource(R.string.notif_prime_allow),
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onLater, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                Text(stringResource(R.string.notif_prime_later), style = textBodyBold16, color = colorActionPrimary)
            }
        },
    ) {
        PopInTitle(title)
        NotificationExample(
            title = stringResource(R.string.notif_n1_title),
            body = stringResource(R.string.notif_n1_body),
            whenText = stringResource(R.string.notif_prime_example_when),
        )
        Text(
            text = stringResource(R.string.notif_prime_body),
            style = textBodyRegular14,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
    }
}

/** An example of notification: the heart of Miff in a blue disc, the two lines of the reminder and the time. */
@Composable
private fun NotificationExample(
    title: String,
    body: String,
    whenText: String,
) {
    // Decorative sample of what a notification looks like: the screen reader gets the sentences, not the picture.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, colorActionPrimaryTint10, ShapeMd)
                .padding(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Spacing.minTouchTarget).clip(CircleShape).background(colorActionPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stat_miff),
                contentDescription = null,
                tint = colorBrandPrimary,
                modifier = Modifier.size(Spacing.l),
            )
        }
        Column {
            Text(text = title, style = textBodyBold14, color = colorTextPrimary)
            Text(text = body, style = textBodyRegular14, color = colorTextPrimary)
            Text(text = whenText, style = textBodyCaption, color = colorTextSecondary)
        }
    }
}

// --- Location (screens 11, 12, 13) ----------------------------------------------------------------------------------------

/**
 * "Toujours autoriser" (BLOCKING by default). Screens 11 and 12: the disclosure that Google Play requires, the three lines, the button of the
 * system window, and the red message of the ticket under the button after a try. Screen 13 (Android 11+, or a system that no longer shows
 * its window): the three steps to the settings of the app. [onPutOff] ("Plus tard") exists only when the build is relaxed; [onSignOut] is
 * the discreet way out of a decision of the PM that is still pending (GatePolicy.EXIT_LINKS_ON_BLOCKING_SCREENS).
 */
@Composable
fun LocationPopIn(
    model: LocationScreenModel,
    onAction: () -> Unit,
    onPutOff: (() -> Unit)?,
    onSignOut: (() -> Unit)?,
) {
    val title = stringResource(R.string.permission_location_title)
    PopIn(
        paneTitle = title,
        footer = {
            MiffButton(
                text =
                    stringResource(
                        if (model.steps) {
                            R.string.permission_location_action_settings
                        } else {
                            R.string.permission_location_action
                        },
                    ),
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
            if (model.error) {
                // Read aloud as soon as it appears (polite live region), in the readable red of the design system.
                Column(
                    modifier = Modifier.padding(top = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.permission_location_error),
                        style = textBodyRegular14,
                        color = colorTextError,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text =
                            stringResource(
                                if (model.steps) {
                                    R.string.permission_location_steps_error_calm
                                } else {
                                    R.string.permission_location_error_calm
                                },
                            ),
                        style = textBodyCaption,
                        color = colorTextDescription,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (onPutOff != null) {
                TextButton(onClick = onPutOff, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                    Text(
                        stringResource(R.string.permission_location_later),
                        style = textBodyBold16,
                        color = colorActionPrimary,
                    )
                }
            }
            if (onSignOut != null) {
                TextButton(onClick = onSignOut, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                    Text(
                        stringResource(R.string.auth_signout_action),
                        style = textBodyRegular14,
                        color = colorActionPrimary,
                    )
                }
            }
        },
    ) {
        PopInTitle(title)
        Image(
            painter = painterResource(R.drawable.ic_perm_pin_art),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text =
                stringResource(
                    if (model.steps) R.string.permission_location_steps_body else R.string.permission_location_body,
                ),
            style = textBodyRegular14,
            color = colorTextPrimary,
            textAlign = TextAlign.Center,
        )
        // The disclosure that Google Play requires (location, background, app closed, the features concerned) and the three lines: on BOTH
        // variants, visible BEFORE the button. On the settings variant (screen 13) it is repeated on purpose: when the precise location was
        // already granted, that is the first screen the person sees (review of the PR).
        Text(
            text = stringResource(R.string.permission_location_disclosure),
            style = textBodyRegular14,
            color = colorTextPrimary,
            textAlign = TextAlign.Center,
        )
        if (model.steps) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Step(1, stringResource(R.string.permission_location_step_1))
                Step(2, stringResource(R.string.permission_location_step_2))
                Step(3, stringResource(R.string.permission_location_step_3))
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(ShapeMd)
                    .background(colorBgCanvas)
                    .padding(Spacing.s + Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ConsentLine(
                R.drawable.ic_perm_phone,
                R.string.permission_location_closed_title,
                R.string.permission_location_closed_body,
            )
            ConsentLine(
                R.drawable.ic_visibility,
                R.string.permission_location_seen_title,
                R.string.permission_location_seen_body,
            )
            ConsentLine(
                R.drawable.ic_perm_swap,
                R.string.permission_location_control_title,
                R.string.permission_location_control_body,
            )
        }
    }
}

@Composable
private fun Step(
    number: Int,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Spacing.xl).clip(ShapeFull).background(colorActionPrimaryTint10),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = textBodyBold14, color = colorActionPrimary)
        }
        Text(text = text, style = textBodyRegular14, color = colorTextPrimary)
    }
}

// --- Renewal of the consent (screens 21 and 22) ---------------------------------------------------------------------------

/**
 * "Une précision sur ce que tu partages": a full screen (not a pop-in: a consent is read, not skimmed), the text of the version 3 with the
 * name of the Miff, the lines the introduction does not say, and two buttons that stay pinned. "Pas maintenant" is as visible as "J'accepte"
 * and writes NOTHING; the person is never reproached.
 */
@Composable
fun RenewalScreen(
    state: RenewalUiState,
    onAccept: () -> Unit,
    onLater: () -> Unit,
    onLeave: () -> Unit = {},
    onLeaveConfirmed: () -> Unit = {},
    onLeaveCancelled: () -> Unit = {},
) {
    val title = stringResource(R.string.miff_renewal_title)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .miffScreenInsets()
                .semantics { paneTitle = title },
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Image(
                painter = painterResource(R.drawable.miff_wordmark_color),
                contentDescription = stringResource(R.string.home_logo_a11y),
                modifier =
                    Modifier
                        .size(width = Spacing.logoWidth / 2, height = Spacing.logoWidth / 5)
                        .align(Alignment.CenterHorizontally),
            )
            Text(
                text = title,
                style = textDisplayHeadline,
                color = colorTextForm,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            if (state.miffs.size > 1) {
                // Read-only chips with the names (design screen 21C).
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                    state.miffs.filter { it.name.isNotEmpty() }.forEach { miff ->
                        Text(
                            text = miff.name,
                            style = textBodyBold14,
                            color = colorActionPrimary,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clip(ShapeFull)
                                    .background(colorActionPrimaryTint10)
                                    .border(StrokeHairline, colorBorderAction, ShapeFull)
                                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        )
                    }
                }
            }
            Text(text = introText(state), style = textBodyRegular14, color = colorTextPrimary)
            RenewalDetailsCard()
            // The way out of design screen 21B, at the END of the text (not pinned: two more choices all the time would weigh on the screen).
            // Only for a member whose single Miff waits for the agreement; the creator deletes his Miff elsewhere, and "Supprimer mon
            // compte" does not exist yet (AUTH-5), so the sentence does not promise it.
            state.leavable?.let {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(ShapeMd)
                            .background(colorBgSurface.copy(alpha = 0.7f))
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) {
                    Text(
                        text = stringResource(R.string.miff_renewal_exit_note),
                        style = textBodyRegular14,
                        color = colorTextDescription,
                    )
                    TextButton(
                        onClick = onLeave,
                        enabled = !state.busy,
                        modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                    ) {
                        Text(
                            stringResource(R.string.miff_leave_link),
                            style = textBodyBold16,
                            color = colorActionPrimary,
                        )
                    }
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colorBgCanvas)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.message?.takeIf { !state.leaveAsked }?.let {
                MiffNotice(text = actionMessageText(it), isError = true)
                Text(
                    text = stringResource(R.string.miff_renewal_error_extra),
                    style = textBodyCaption,
                    color = colorTextDescription,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
            }
            MiffButton(
                text = stringResource(if (state.busy) R.string.miff_renewal_saving else R.string.miff_renewal_accept),
                onClick = onAccept,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onLater,
                enabled = !state.busy,
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            ) {
                Text(stringResource(R.string.miff_renewal_later), style = textBodyBold16, color = colorActionPrimary)
            }
        }
    }
    if (state.leaveAsked) {
        state.leavable?.let { miff ->
            MiffConfirmDialog(
                title = stringResource(R.string.miff_leave_title, miff.name),
                body = stringResource(R.string.miff_leave_body),
                confirmLabel = stringResource(R.string.miff_leave_action),
                cancelLabel = stringResource(R.string.common_cancel),
                tone = DialogTone.SENSITIVE,
                busy = state.busy,
                message = state.message,
                onConfirm = onLeaveConfirmed,
                onCancel = onLeaveCancelled,
            )
        }
    }
}

@Composable
private fun introText(state: RenewalUiState): String {
    val single = state.miffs.singleOrNull()
    return when {
        single == null -> stringResource(R.string.miff_renewal_intro_many)
        single.name.isEmpty() -> stringResource(R.string.miff_renewal_intro_unnamed)
        else -> stringResource(R.string.miff_renewal_intro_one, single.name)
    }
}
