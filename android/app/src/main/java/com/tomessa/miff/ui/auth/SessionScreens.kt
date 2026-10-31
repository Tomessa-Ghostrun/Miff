package com.tomessa.miff.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textDisplayHeadline

@Composable
private fun CenteredFrame(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .miffScreenInsets()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        content()
    }
}

/** While the session or the account is being checked (launch, sign-out in progress). */
@Composable
fun SessionLoadingScreen() {
    CenteredFrame { LoadingIndicator() }
}

/** Signed in, but the account could not be read: retry or sign out. No other screen is reachable. */
@Composable
fun AccountLookupFailedScreen(
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    CenteredFrame {
        MessageText(text = stringResource(R.string.auth_lookup_failed), isError = false)
        MiffButton(
            text = stringResource(R.string.auth_retry_action),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onSignOut) {
            Text(
                text = stringResource(R.string.auth_signout_action),
                style = textBodyBold16,
                color = colorActionPrimary,
            )
        }
    }
}

/**
 * PROVISIONAL stand-in for the "create or join a Miff" screens (Miff tickets, not built yet): a person without a Miff never
 * reaches the tabs (Onboarding ticket 6.7), so this screen is where they stay. It only says where the app opened and offers
 * a sign-out so that the routing and the sign-out can be tried on a phone; it disappears with the Miff ticket.
 *
 * [onPreviewTabs] exists in DEBUG builds only (internal testing): it opens the tabs, the Profile and the Settings without
 * having to create a Miff by hand in the Firebase console. It is null in the release builds, where the code is removed.
 */
@Composable
fun NextStepPlaceholderScreen(
    onSignOut: () -> Unit,
    onPreviewTabs: (() -> Unit)? = null,
) {
    CenteredFrame {
        Text(
            text = stringResource(R.string.home_title),
            style = textDisplayHeadline,
            color = colorTextForm,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.android_next_miff_choice),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
        if (onPreviewTabs != null) {
            MiffButton(
                text = stringResource(R.string.android_debug_preview_tabs),
                onClick = onPreviewTabs,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiffButton(
            text = stringResource(R.string.auth_signout_action),
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
