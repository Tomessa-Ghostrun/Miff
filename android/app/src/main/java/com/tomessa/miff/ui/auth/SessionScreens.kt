package com.tomessa.miff.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.ui.components.MiffButton
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
                .safeDrawingPadding()
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
 * PROVISIONAL stand-in for the screens that do not exist yet (create / join a Miff, feed). The profile and phone steps
 * are real since ONB-2. It only says where the app opened and offers a sign-out so that the routing and the sign-out can
 * be tried on a phone. The sign-out button moves to the Settings ticket; this whole screen disappears with the Feed ticket.
 */
@Composable
fun NextStepPlaceholderScreen(
    destination: AppDestination,
    onSignOut: () -> Unit,
) {
    val text =
        when (destination) {
            AppDestination.MIFF_CHOICE -> R.string.android_next_miff_choice
            else -> R.string.android_next_feed
        }
    CenteredFrame {
        Text(
            text = stringResource(R.string.home_title),
            style = textDisplayHeadline,
            color = colorTextForm,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(text),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
        MiffButton(
            text = stringResource(R.string.auth_signout_action),
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
