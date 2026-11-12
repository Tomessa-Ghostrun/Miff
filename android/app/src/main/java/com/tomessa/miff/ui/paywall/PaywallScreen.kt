package com.tomessa.miff.ui.paywall

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.domain.paywall.PaywallNotice
import com.tomessa.miff.domain.paywall.PaywallOffer
import com.tomessa.miff.domain.paywall.PaywallPrice
import com.tomessa.miff.domain.paywall.PaywallProgress
import com.tomessa.miff.domain.paywall.PaywallUiState
import com.tomessa.miff.domain.place.PlaceLimits
import com.tomessa.miff.ui.auth.LegalLinks
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.splash.ReduceMotion
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeFull
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.ShapeSm
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgGradientEnd
import com.tomessa.miff.ui.theme.colorBgPlaceholder
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorStatusDangerTint
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.colorTextSecondary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyLarge
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textCaptionBold12
import com.tomessa.miff.ui.theme.textTitleBold20
import com.tomessa.miff.ui.theme.textTitleBold24Gradient

/** What a person can do on the offer. Every purchase action is a no-op until SRV-8A plugs the store in: in production only [onClose] is reachable. */
data class PaywallActions(
    /** Closes this display (the cross, the system "back", Escape): only ever called once the cross is available (see [PaywallViewModel]). */
    val onClose: () -> Unit,
    val onStartTrial: () -> Unit = {},
    val onBuyAnnual: () -> Unit = {},
    val onBuyMonthly: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRestore: () -> Unit = {},
    /** "Continuer" of the confirmation (a person who has Miff+). Closes the screen, like the cross. */
    val onContinue: () -> Unit = onClose,
)

/** What production shows today: no store, no purchase (the premium is granted by hand): "Miff+ arrive bientôt". SRV-8A replaces this by the real state. */
internal fun currentPaywallState(): PaywallUiState = PaywallUiState.PRODUCTION

/**
 * THE offer screen (ticket docs/tickets/paywall-v2.md, PAY-UI-A): the same composable for every entry (a 3rd place, a danger zone, a 2nd Miff from
 * the selector of the Feed or from the carousel of the Profile), faithful to design/exports/paywall/ (list of the differences in the pull request).
 * ONE content, with no variant by entry (decision of the PM, 2026-11-12: no sentence saying which limit was hit): the title, the three cards, the offer. Nothing is
 * written by this screen: leaving it changes nothing.
 *
 * THE RULE OF THE PM (2026-11-12), at EVERY display: the close cross is ABSENT for [com.tomessa.miff.domain.paywall.PaywallCloseDelay.MILLIS], then
 * appears with a fade (at once if animations are off). Absent = not drawn, so not in the accessibility tree, not focusable, not touchable; its
 * place is reserved (no jump). The system "back" and Escape are ignored until then. See [PaywallViewModel] for what survives a rotation.
 *
 * "Notifications intelligentes" is NOT listed: no notification is sent yet (never promise what does not exist).
 *
 * @param onShown called ONCE per display, when the screen is really on view (the `paywall_shown` event).
 * @param announce says a sentence to a screen reader without moving its focus; null = the system announcement.
 * @param reduceMotion animations turned off by the person: the cross appears at once.
 */
@Composable
fun PaywallScreen(
    model: PaywallViewModel,
    state: PaywallUiState,
    actions: PaywallActions,
    onShown: () -> Unit = {},
    announce: ((String) -> Unit)? = null,
    reduceMotion: Boolean = ReduceMotion.isRequested(LocalContext.current),
) {
    val offer = state.offer
    val closeAvailable by model.closeAvailable.collectAsStateWithLifecycle()
    // A confirmation has no offer left to read: nothing to wait for (question Q-PAY1, default applied).
    val closeVisible = closeAvailable || offer is PaywallOffer.Subscribed
    val shown by rememberUpdatedState(onShown)

    // The countdown starts when the screen is really on view (RESUMED: foreground, and for a navigation entry, the end of its entry animation),
    // whatever the prices are doing; it stops in the background and is computed again from the clock when the screen comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (model.onDisplayed()) shown()
            model.runCountdown()
        }
    }

    // The cross "appears" for a screen reader: said once, without moving the focus (never again after a rotation).
    val view = LocalView.current
    val announcement = stringResource(R.string.paywall_close_available_a11y)
    LaunchedEffect(closeAvailable) {
        if (closeAvailable && model.takeAnnouncement()) {
            (announce ?: { text -> announceToScreenReader(view, text) })(announcement)
        }
    }

    // "Back" (button or gesture) does NOTHING during the delay (no message, no preview that would look like a closing), then closes like the cross.
    BackHandler { if (closeVisible) actions.onClose() }

    val title =
        stringResource(
            when (offer) {
                PaywallOffer.ComingSoon -> R.string.place_paywall_soon
                PaywallOffer.Subscribed -> R.string.paywall_subscribed
                else -> R.string.place_paywall_headline
            },
        )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colorBgCanvas, colorBgGradientEnd)))
                .miffScreenInsets()
                // Escape of a keyboard follows the same rule as "back".
                .onPreviewKeyEvent { event ->
                    when (escapeOutcome(event.key == Key.Escape, event.type == KeyEventType.KeyUp, closeVisible)) {
                        EscapeOutcome.NOT_ESCAPE -> {
                            false
                        }

                        EscapeOutcome.IGNORED -> {
                            true
                        }

                        EscapeOutcome.CLOSE -> {
                            actions.onClose()
                            true
                        }
                    }
                }.semantics { paneTitle = title },
    ) {
        TopBar(closeVisible = closeVisible, reduceMotion = reduceMotion, onClose = actions.onClose)
        var showAll by rememberSaveable { mutableStateOf(false) }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (offer !is PaywallOffer.Subscribed) {
                Text(
                    text = stringResource(R.string.place_paywall_headline),
                    style = textTitleBold24Gradient,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Feature(
                    R.drawable.ic_loc_pin,
                    R.string.place_paywall_feature_places,
                    R.string.place_paywall_feature_places_desc,
                )
                Feature(
                    R.drawable.ic_group,
                    R.string.place_paywall_feature_miffs,
                    R.string.place_paywall_feature_miffs_desc,
                )
                Feature(
                    R.drawable.ic_loc_warning,
                    R.string.place_paywall_feature_danger,
                    R.string.place_paywall_feature_danger_desc,
                )
            }
            ScrollingOffer(offer = offer, showAll = showAll, onRestore = actions.onRestore)
        }
        PurchaseZone(state = state, showAll = showAll, onShowAll = { showAll = true }, actions = actions)
    }
}

/** What the Escape key does (same rule as "back"): nothing for another key, swallowed during the delay, closes on release once the cross is there. */
internal enum class EscapeOutcome { NOT_ESCAPE, IGNORED, CLOSE }

internal fun escapeOutcome(
    isEscape: Boolean,
    isKeyUp: Boolean,
    closeVisible: Boolean,
): EscapeOutcome =
    when {
        !isEscape -> EscapeOutcome.NOT_ESCAPE
        isKeyUp && closeVisible -> EscapeOutcome.CLOSE
        else -> EscapeOutcome.IGNORED
    }

@Suppress("DEPRECATION") // announceForAccessibility is the one call that says a sentence once without moving the focus.
private fun announceToScreenReader(
    view: View,
    text: String,
) {
    view.announceForAccessibility(text)
}

// --- Top bar: the reserved place of the cross ---------------------------------------------------------------------------------

@Composable
private fun TopBar(
    closeVisible: Boolean,
    reduceMotion: Boolean,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
        // The place is ALWAYS there (a square of the touch size): the cross comes into it without moving anything.
        Box(
            modifier = Modifier.align(Alignment.CenterStart).size(Spacing.minTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = closeVisible,
                enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(FADE_MILLIS)),
                exit = ExitTransition.None,
            ) {
                // Not drawn before: absent for the screen reader, the keyboard and the finger. 48 dp to touch, a clearly visible disc to see.
                IconButton(onClick = onClose, modifier = Modifier.size(Spacing.minTouchTarget)) {
                    Box(
                        modifier = Modifier.size(Spacing.xl).clip(CircleShape).background(colorTextSecondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_loc_close),
                            contentDescription = stringResource(R.string.place_paywall_close_a11y),
                            tint = colorBgSurface,
                            modifier = Modifier.size(Spacing.m),
                        )
                    }
                }
            }
        }
        Image(
            painter = painterResource(R.drawable.miff_wordmark_color),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).width(Spacing.logoWidth / 2),
        )
    }
}

// --- What scrolls under the cards -----------------------------------------------------------------------------------------------

@Composable
private fun ScrollingOffer(
    offer: PaywallOffer,
    showAll: Boolean,
    onRestore: () -> Unit,
) {
    when (offer) {
        PaywallOffer.ComingSoon -> {
            Spacer(Modifier.size(Spacing.s))
            Text(
                text = stringResource(R.string.place_paywall_soon),
                style = textTitleBold20,
                color = colorTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.paywall_soon_body),
                style = textBodyRegular,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PaywallOffer.Subscribed -> {
            Spacer(Modifier.size(Spacing.xl))
            Text(
                text = stringResource(R.string.paywall_subscribed),
                style = textTitleBold24Gradient,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text(
                text = stringResource(R.string.paywall_subscribed_detail),
                style = textBodyRegular,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PaywallOffer.Loading, PaywallOffer.Unavailable -> {
            FreePlanSentence()
            LegalAndRestoreLinks(withRestore = false, onRestore = {})
        }

        is PaywallOffer.TrialEligible, is PaywallOffer.Plans -> {
            InformationBlock(offer = offer, showAll = showAll, onRestore = onRestore)
            FreePlanSentence()
        }
    }
}

/** "La formule gratuite reste disponible : 1 Miff et 3 lieux de confiance." A sentence, never a button: the only way out is the cross. */
@Composable
private fun FreePlanSentence() {
    Text(
        text = stringResource(R.string.paywall_free_plan_stays, PlaceLimits.FREE_PLACES),
        style = textBodyRegular14,
        color = colorTextDescription,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The information the stores require near the purchase (ticket section 5.4): what is billed and when, the automatic renewal, how to cancel. The
 * prices are the store's own. Plain sentences, dark enough to read (never pale grey).
 */
@Composable
private fun InformationBlock(
    offer: PaywallOffer,
    showAll: Boolean,
    onRestore: () -> Unit,
) {
    val (annual, monthly, trial) =
        when (offer) {
            is PaywallOffer.TrialEligible -> Triple(offer.annual, offer.monthly, true)
            is PaywallOffer.Plans -> Triple(offer.annual, offer.monthly, false)
            else -> return
        }
    val showMonthly = showAll || !trial
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        InfoLine(
            if (trial) {
                stringResource(R.string.paywall_info_trial, annual.formatted)
            } else {
                stringResource(R.string.paywall_info_annual, annual.formatted)
            },
        )
        if (showMonthly) InfoLine(stringResource(R.string.paywall_info_monthly, monthly.formatted))
        InfoLine(stringResource(R.string.paywall_info_cancel, stringResource(R.string.android_paywall_store_name)))
    }
    LegalAndRestoreLinks(withRestore = true, onRestore = onRestore)
}

@Composable
private fun InfoLine(text: String) {
    Text(text = text, style = textBodyRegular14, color = colorTextPrimary, modifier = Modifier.fillMaxWidth())
}

/** The two legal pages (same pages as everywhere else in the app) and, when a purchase exists, "Restaurer mes achats". */
@Composable
private fun LegalAndRestoreLinks(
    withRestore: Boolean,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LinkButton(
            stringResource(R.string.paywall_terms_link),
        ) { LegalLinks.open(context, LegalLinks.termsUrl(context)) }
        LinkButton(
            stringResource(R.string.paywall_privacy_link),
        ) { LegalLinks.open(context, LegalLinks.privacyUrl(context)) }
        if (withRestore) LinkButton(stringResource(R.string.paywall_restore), onClick = onRestore)
    }
}

@Composable
private fun LinkButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
        Text(
            text = text,
            style = textBodyRegular14,
            color = colorActionPrimary,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
        )
    }
}

// --- The fixed purchase zone, at the bottom --------------------------------------------------------------------------------------

@Composable
private fun PurchaseZone(
    state: PaywallUiState,
    showAll: Boolean,
    onShowAll: () -> Unit,
    actions: PaywallActions,
) {
    val offer = state.offer
    if (offer == PaywallOffer.ComingSoon) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusLine(state = state, actions = actions)
        when (offer) {
            PaywallOffer.Loading -> {
                // Placeholders of the height of the real offer (no jump when the prices arrive), the purchase button asleep.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            Spacing.l,
                        ).clip(ShapeMd)
                        .background(colorBgPlaceholder.copy(alpha = 0.5f)),
                )
                MiffButton(
                    text = stringResource(R.string.paywall_loading),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PaywallOffer.Unavailable -> {
                Text(
                    text = stringResource(R.string.paywall_unavailable),
                    style = textBodyRegular,
                    color = colorTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s),
                )
                MiffSecondaryButton(
                    text = stringResource(R.string.paywall_retry),
                    onClick = actions.onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is PaywallOffer.TrialEligible -> {
                if (showAll) {
                    Plans(offer.annual, offer.monthly, state, actions)
                } else {
                    TrialButton(offer, state, onShowAll, actions)
                }
            }

            is PaywallOffer.Plans -> {
                Plans(offer.annual, offer.monthly, state, actions)
            }

            PaywallOffer.Subscribed -> {
                MiffButton(
                    text = stringResource(R.string.paywall_continue),
                    onClick = actions.onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PaywallOffer.ComingSoon -> {
                Unit
            }
        }
    }
}

/** A purchase in progress, or a calm sentence about the last attempt. Read by a screen reader when it appears; never red, never an alarm. */
@Composable
private fun StatusLine(
    state: PaywallUiState,
    actions: PaywallActions,
) {
    if (state.progress != PaywallProgress.NONE) {
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(bottom = Spacing.s).semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(Spacing.m),
                strokeWidth = 2.dp,
                color = colorActionPrimary,
            )
            Text(text = stringResource(R.string.paywall_working), style = textBodyRegular14, color = colorTextPrimary)
        }
        return
    }
    val notice = state.notice ?: return
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                stringResource(
                    when (notice) {
                        PaywallNotice.PENDING -> R.string.paywall_pending
                        PaywallNotice.ERROR -> R.string.paywall_error_calm
                        PaywallNotice.RESTORE_NONE -> R.string.paywall_restore_none
                    },
                    // Only "restore: nothing found" names the store; the other two sentences have no placeholder.
                    stringResource(R.string.android_paywall_store_name),
                ),
            style = textBodyRegular14,
            color = colorTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (notice != PaywallNotice.PENDING) {
            LinkButton(
                text = stringResource(R.string.paywall_retry),
                onClick = if (notice == PaywallNotice.RESTORE_NONE) actions.onRestore else actions.onRetry,
            )
        }
    }
}

/** Mockup 1: the trial button, "Annulable à tout moment" on its corner, THEN the billed price (the most visible price line), the monthly equivalent smaller. */
@Composable
private fun TrialButton(
    offer: PaywallOffer.TrialEligible,
    state: PaywallUiState,
    onShowAll: () -> Unit,
    actions: PaywallActions,
) {
    WithBubble(stringResource(R.string.paywall_cancel_anytime)) {
        MiffButton(
            text = stringResource(R.string.paywall_cta_trial),
            onClick = actions.onStartTrial,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // The billed price is the biggest price on the screen: 16 bold, against 14 regular for the monthly equivalent.
    Text(
        text = stringResource(R.string.paywall_trial_then, offer.annual.formatted),
        style = textBodyBold16,
        color = colorTextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
    )
    offer.annual.perMonth()?.let { perMonth ->
        Text(
            text = stringResource(R.string.paywall_monthly_equivalent, perMonth),
            style = textBodyRegular14,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    LinkButton(text = stringResource(R.string.paywall_see_all_offers), onClick = onShowAll)
}

/** Mockup 2: yearly (the billed price big, the monthly equivalent small, "presque 30 % d'économie") and monthly ("Sans engagement"). */
@Composable
private fun Plans(
    annual: PaywallPrice,
    monthly: PaywallPrice,
    state: PaywallUiState,
    actions: PaywallActions,
) {
    WithBubble(stringResource(R.string.paywall_annual_savings)) {
        PlanButton(
            title = stringResource(R.string.paywall_plan_annual_title, annual.formatted),
            note = annual.perMonth()?.let { stringResource(R.string.paywall_monthly_equivalent, it) },
            primary = true,
            enabled = !state.busy,
            onClick = actions.onBuyAnnual,
        )
    }
    PlanButton(
        title = stringResource(R.string.paywall_plan_monthly_title, monthly.formatted),
        note = stringResource(R.string.paywall_no_commitment),
        primary = false,
        enabled = !state.busy,
        onClick = actions.onBuyMonthly,
    )
}

/** A plan: the price on the first line (20, the most visible), a smaller note under it. Same height, radius and wrapping as the other buttons. */
@Composable
private fun PlanButton(
    title: String,
    note: String?,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = textBodyLarge, textAlign = TextAlign.Center)
            if (note != null) Text(text = note, style = textBodyRegular14, textAlign = TextAlign.Center)
        }
    }
    val padding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s)
    val modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).heightIn(min = Spacing.minTouchTarget)
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = ShapeMd,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colorActionPrimary,
                    contentColor = colorTextOnAction,
                    disabledContainerColor = colorActionPrimaryTint30,
                    disabledContentColor = colorTextOnAction,
                ),
            elevation = null,
            contentPadding = padding,
            modifier = modifier.then(if (enabled) Modifier.miffShadow(ShadowDefault, ShapeMd) else Modifier),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = ShapeMd,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = colorBgSurface,
                    contentColor = colorActionPrimary,
                    disabledContainerColor = colorBgSurface,
                    disabledContentColor = colorActionPrimaryTint30,
                ),
            border = BorderStroke(StrokeAccent, if (enabled) colorActionPrimary else colorActionPrimaryTint30),
            contentPadding = padding,
            modifier = modifier,
        ) { content() }
    }
}

/**
 * The yellow label of the mockups, on the top corner of a button. Up to a font of 1.3 it overlaps the button's edge, like the mockup (the button's text
 * starts lower than the label ends); above that it moves ABOVE the button, so that it never covers a word (large fonts).
 */
@Composable
private fun WithBubble(
    text: String,
    content: @Composable () -> Unit,
) {
    if (LocalDensity.current.fontScale > BUBBLE_OVERLAP_MAX_FONT_SCALE) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Bubble(text)
            content()
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(top = BUBBLE_OVERLAP_TOP)) { content() }
            Bubble(text, Modifier.align(Alignment.TopEnd).padding(end = Spacing.m))
        }
    }
}

@Composable
private fun Bubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = textCaptionBold12,
        color = colorTextPrimary,
        modifier =
            modifier
                .clip(ShapeSm)
                .background(colorBrandPrimary)
                .border(StrokeHairline, colorTextPrimary, ShapeSm)
                .padding(horizontal = Spacing.s, vertical = 2.dp),
    )
}

// --- The three cards (the same whatever the entry) ---------------------------------------------------------------------------

@Composable
private fun Feature(
    icon: Int,
    title: Int,
    description: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderDefault, ShapeMd)
                .padding(Spacing.m)
                .heightIn(min = Spacing.minTouchTarget)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Spacing.xl + Spacing.s).clip(ShapeMd).background(colorActionPrimaryTint20),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(Spacing.l),
            )
        }
        Column {
            Text(text = stringResource(title), style = textBodyBold16, color = colorTextPrimary)
            Text(text = stringResource(description), style = textBodyRegular14, color = colorTextDescription)
        }
    }
}

/** Duration of the fade of the cross (between 0.2 and 0.3 s: no blinking, nothing that draws the eye). */
internal const val FADE_MILLIS = 250

/** Above this font scale the label no longer overlaps the button: it sits above it. */
internal const val BUBBLE_OVERLAP_MAX_FONT_SCALE = 1.3f

private val BUBBLE_OVERLAP_TOP = 10.dp
