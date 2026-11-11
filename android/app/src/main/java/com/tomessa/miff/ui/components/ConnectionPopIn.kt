package com.tomessa.miff.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.ui.miff.MiffConfirmDialog
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.textBodyCaption

/**
 * The pop-in "Pas de connexion" (bug of 2026-11-10): calm, one sentence, one button, a discreet countdown before the next automatic attempt. It is the
 * dialog model of the app ([MiffConfirmDialog], without "Annuler"): the screen reader announces it as a window and puts the focus on its title; "Retour"
 * closes it (the page then says it itself: [ConnectionInline]); a tap outside does nothing. The countdown is NOT a live region (a screen reader would
 * repeat it every second). Only one is ever drawn per screen: [ConnectionGate] has one state, and [suppressed] holds it back while another message of
 * the app is over the screen.
 */
@Composable
fun ConnectionPopIn(
    host: ConnectionHost,
    modifier: Modifier = Modifier,
    suppressed: Boolean = false,
) {
    if (!host.state.popInVisible || suppressed) return
    val seconds = host.state.secondsLeft
    MiffConfirmDialog(
        title = stringResource(R.string.feed_offline_popin_title),
        body = stringResource(R.string.feed_offline_popin_body),
        confirmLabel = stringResource(R.string.feed_offline_popin_retry),
        cancelLabel = "",
        onConfirm = host.onRetry,
        onCancel = host.onDismiss,
        modifier = modifier,
        showCancel = false,
        content =
            seconds?.let {
                {
                    Text(
                        text = stringResource(R.string.feed_offline_popin_countdown, it),
                        style = textBodyCaption,
                        color = colorTextDescription,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
    )
}

/** What the page says once the pop-in was closed: the same calm sentence and the same button, in place of the skeleton. */
@Composable
fun ConnectionInline(
    host: ConnectionHost,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffNotice(stringResource(R.string.feed_offline_note))
        MiffSecondaryButton(
            text = stringResource(R.string.feed_offline_popin_retry),
            onClick = host.onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
