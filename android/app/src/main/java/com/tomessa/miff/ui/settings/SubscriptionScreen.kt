package com.tomessa.miff.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.tomessa.miff.R
import com.tomessa.miff.domain.subscription.SubscriptionInfo
import com.tomessa.miff.domain.subscription.formatLongDate
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20

/**
 * Settings > Abonnement (ticket ABO-1, mockup "reglages -6" reduced to what is true today): the name of the offer, until when it
 * is active, and, for an access granted by hand, who granted it. READ ONLY: no next payment, no change of plan, no "Arrêter
 * l'abonnement", no price, no button (nothing is bought in the app yet). The offer is always called "Miff+".
 */
@Composable
fun SubscriptionScreen(
    subscription: SubscriptionInfo,
    onBack: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val untilDate =
        remember(subscription.expiresAtMillis, locale) { formatLongDate(subscription.expiresAtMillis, locale) }
    SettingsScaffold(title = stringResource(R.string.settings_subscription_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.settings_subscription_plan_name),
            style = textTitleBold20,
            color = colorTextForm,
            modifier = Modifier.semantics { heading() },
        )
        Column(modifier = Modifier.padding(top = Spacing.xs)) {
            Text(
                text = stringResource(R.string.settings_subscription_active_until, untilDate),
                style = textBodyRegular,
                color = colorTextForm,
            )
            Text(
                text =
                    stringResource(
                        if (subscription.grantedByTeam) {
                            R.string.settings_subscription_source_manual
                        } else {
                            R.string.settings_subscription_source_other
                        },
                    ),
                style = textBodyRegular14,
                color = colorTextDescription,
            )
        }
        Text(
            text = stringResource(R.string.settings_subscription_store_soon),
            style = textBodyRegular14,
            color = colorTextDescription,
            modifier = Modifier.padding(top = Spacing.l),
        )
    }
}
