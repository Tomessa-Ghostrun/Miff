package com.tomessa.miff.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.textBodyBold16

/**
 * Consent panel of a new person who signed in with Google from the sign-in screen (ticket 7.8, criteria C10 to C14).
 * Nothing else is reachable while it is open. System back, the back gesture and "Annuler" all mean REFUSAL.
 * The two boxes start unticked; "Continuer" waits for (a); no other data is asked here.
 */
@Composable
fun ConsentPanelScreen(
    state: ConsentPanelUiState,
    onTermsChecked: (Boolean) -> Unit,
    onAnalyticsChecked: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onRefuse: () -> Unit,
) {
    // Always consume "back" (even while busy): leaving the app on this panel would skip the refusal.
    BackHandler { if (!state.isLoading) onRefuse() }

    AuthScaffold(paneTitle = stringResource(R.string.auth_panel_title), onBack = null) {
        ScreenTitle(title = stringResource(R.string.auth_panel_title))
        ConsentCheckbox(
            checked = state.termsChecked,
            onCheckedChange = onTermsChecked,
            text =
                stringResource(
                    R.string.auth_consent_terms,
                    stringResource(R.string.auth_consent_terms_link),
                    stringResource(R.string.auth_consent_privacy_link),
                ),
            enabled = !state.isLoading,
        )
        LegalLinksRow()
        ConsentCheckbox(
            checked = state.analyticsChecked,
            onCheckedChange = onAnalyticsChecked,
            text = stringResource(R.string.auth_consent_analytics),
            hint = stringResource(R.string.auth_consent_analytics_hint),
            enabled = !state.isLoading,
        )
        Spacer(Modifier.size(Spacing.m))
        AuthMessageArea(state.message)
        MiffButton(
            text = stringResource(R.string.auth_panel_continue),
            onClick = onContinue,
            enabled = state.termsChecked && !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onRefuse, enabled = !state.isLoading) {
            Text(text = stringResource(R.string.auth_panel_cancel), style = textBodyBold16, color = colorActionPrimary)
        }
        if (state.isLoading) LoadingIndicator()
    }
}
