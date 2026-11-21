package com.tomessa.miff.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.domain.subscription.StoreState
import com.tomessa.miff.domain.subscription.SubscriptionSource
import com.tomessa.miff.domain.subscription.SubscriptionStatus
import com.tomessa.miff.domain.subscription.formatLongDate
import com.tomessa.miff.ui.auth.LoadingIndicator
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20
import java.util.Locale

/** What the screen can do (the host wires them: the page of the store, the offer, the refresh). */
data class SubscriptionActions(
    val onBack: () -> Unit,
    /** "Gérer mon abonnement": the page of the store where the person cancels, resumes or changes it (nothing is cancelled in the app). */
    val onManage: (SubscriptionSource) -> Unit,
    /** "Découvrir Miff+" / "Reprendre Miff+": the offer. */
    val onOffer: () -> Unit,
    /** "Actualiser mon abonnement"; null while the purchase is switched off (nothing to refresh). */
    val onRefresh: (() -> Unit)?,
    val onRetry: () -> Unit,
)

/**
 * Settings > Abonnement (ticket docs/tickets/premium-achat-reel.md section 8; REPLACES the read-only shell of ABO-1). No mockup exists for it: it is built in the design system, as
 * simply as possible (the title bar of the Settings, the name "Miff+", one calm sentence that says what is going on, what stays true and what to do, then at most three actions).
 *
 * Tone (decision of the ticket): never red, never an exclamation mark, never "attention", no countdown, no blame. The sentences give the state, never a price from the document:
 * a price is the store's, and the sentence is written without one when the store is out of reach.
 *
 * No "Arrêter l'abonnement" in the app: cancelling, resuming and changing the plan happen on the page of the store ("Gérer mon abonnement", criterion AN10).
 */
@Composable
fun SubscriptionScreen(
    state: SubscriptionUiState,
    actions: SubscriptionActions,
) {
    SettingsScaffold(title = stringResource(R.string.settings_subscription_title), onBack = actions.onBack) {
        Text(
            text = stringResource(R.string.settings_subscription_plan_name),
            style = textTitleBold20,
            color = colorTextForm,
            modifier = Modifier.semantics { heading() },
        )
        val status = state.status
        when {
            state.loading && status == null -> {
                Row(
                    modifier = Modifier.padding(top = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    LoadingIndicator(Modifier.size(Spacing.l))
                    Text(
                        text = stringResource(R.string.paywall_working),
                        style = textBodyRegular14,
                        color = colorTextDescription,
                    )
                }
            }

            status == null -> {
                Unreadable(onRetry = actions.onRetry)
            }

            else -> {
                StatusBlock(state = state, status = status, actions = actions)
            }
        }
    }
}

@Composable
private fun Unreadable(onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffNotice(text = stringResource(R.string.settings_subscription_unreadable))
        MiffSecondaryButton(
            text = stringResource(R.string.settings_delete_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusBlock(
    state: SubscriptionUiState,
    status: SubscriptionStatus,
    actions: SubscriptionActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier.padding(top = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        when (status) {
            SubscriptionStatus.Free -> {
                Sentence(stringResource(R.string.settings_subscription_free))
            }

            is SubscriptionStatus.Manual -> {
                val until = remember(status.expiresAtMillis, locale) { formatLongDate(status.expiresAtMillis, locale) }
                Sentence(stringResource(R.string.settings_subscription_active_until, until))
                Small(stringResource(R.string.settings_subscription_source_manual))
                Small(stringResource(R.string.settings_subscription_no_store_note), topGap = true)
            }

            is SubscriptionStatus.Store -> {
                StoreSentences(state = state, status = status, locale = locale)
            }
        }
        state.notice?.let { NoticeLine(it) }
    }
    Actions(state = state, status = status, actions = actions)
}

@Composable
private fun StoreSentences(
    state: SubscriptionUiState,
    status: SubscriptionStatus.Store,
    locale: Locale,
) {
    val end = status.expiresAtMillis
    val date = remember(end, locale) { end?.let { formatLongDate(it, locale) } }
    val price = state.renewalPrice()
    when (status.state) {
        StoreState.TRIAL -> {
            if (date != null) {
                Sentence(
                    if (price != null) {
                        stringResource(R.string.settings_subscription_trial, date, price)
                    } else {
                        stringResource(R.string.settings_subscription_trial_noprice, date)
                    },
                )
            }
        }

        StoreState.ACTIVE -> {
            if (date != null) {
                Sentence(
                    when {
                        status.source == SubscriptionSource.OTHER -> {
                            stringResource(
                                R.string.settings_subscription_active_until,
                                date,
                            )
                        }

                        price != null -> {
                            stringResource(R.string.settings_subscription_renews, date, price)
                        }

                        else -> {
                            stringResource(R.string.settings_subscription_renews_noprice, date)
                        }
                    },
                )
            }
            if (status.source ==
                SubscriptionSource.OTHER
            ) {
                Small(stringResource(R.string.settings_subscription_source_other))
            }
        }

        StoreState.CANCELED -> {
            if (date != null) Sentence(stringResource(R.string.settings_subscription_ends, date))
        }

        StoreState.GRACE -> {
            if (date != null) Sentence(stringResource(R.string.settings_subscription_grace, date))
        }

        StoreState.ON_HOLD -> {
            Sentence(stringResource(R.string.settings_subscription_on_hold))
        }

        StoreState.EXPIRED -> {
            Sentence(stringResource(R.string.settings_subscription_expired))
        }

        StoreState.REVOKED -> {
            Sentence(stringResource(R.string.settings_subscription_revoked))
        }
    }
    // A subscription bought in another store (on an iPhone) is managed there, not in Google Play.
    if (status.source ==
        SubscriptionSource.APP_STORE
    ) {
        Small(stringResource(R.string.settings_subscription_other_store), topGap = true)
    }
    // A test purchase (licence tester, sandbox): said plainly so that nobody takes it for a real payment.
    if (status.test) Small(stringResource(R.string.settings_subscription_test_note), topGap = true)
}

/** A store the person can manage the subscription in (Google Play on this phone, the App Store when it was bought on an iPhone). */
private fun manageUrlSource(source: SubscriptionSource): Boolean =
    source == SubscriptionSource.GOOGLE_PLAY || source == SubscriptionSource.APP_STORE

@Composable
private fun Sentence(text: String) {
    Text(text = text, style = textBodyRegular, color = colorTextForm)
}

@Composable
private fun Small(
    text: String,
    topGap: Boolean = false,
) {
    Text(
        text = text,
        style = textBodyRegular14,
        color = colorTextDescription,
        modifier = if (topGap) Modifier.padding(top = Spacing.s) else Modifier,
    )
}

/** A calm sentence about the last refresh, read by a screen reader when it appears. */
@Composable
private fun NoticeLine(notice: SubscriptionNotice) {
    // The sentences of the offer are reused (same words everywhere): the store is named in "nothing found", the contact is given in "already used by another account".
    Text(
        text =
            when (notice) {
                SubscriptionNotice.NOTHING_FOUND -> {
                    stringResource(
                        R.string.paywall_restore_none,
                        stringResource(R.string.android_paywall_store_name),
                    )
                }

                SubscriptionNotice.OTHER_ACCOUNT -> {
                    stringResource(
                        R.string.paywall_other_account,
                        BuildConfig.SUPPORT_EMAIL,
                    )
                }

                SubscriptionNotice.PENDING -> {
                    stringResource(R.string.paywall_pending)
                }

                SubscriptionNotice.REFRESH_FAILED -> {
                    stringResource(R.string.settings_subscription_refresh_failed)
                }
            },
        style = textBodyRegular14,
        color = colorTextForm,
        modifier = Modifier.padding(top = Spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The actions, from the most useful to the least: the offer (when there is something to buy: a free account, an expired or refunded subscription), "Gérer mon abonnement" (a
 * subscription that exists in a store), "Actualiser mon abonnement" (a quiet link: found again after a reinstall or on a new phone; asked on its own, never in a loop).
 */
@Composable
private fun Actions(
    state: SubscriptionUiState,
    status: SubscriptionStatus,
    actions: SubscriptionActions,
) {
    val canBuy =
        status == SubscriptionStatus.Free ||
            (
                status is SubscriptionStatus.Store &&
                    (status.state == StoreState.EXPIRED || status.state == StoreState.REVOKED)
            )
    Column(
        modifier = Modifier.padding(top = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        // The offer is only reachable while the purchase is switched on (a refresh exists only then: same switch).
        if (canBuy && actions.onRefresh != null) {
            MiffButton(
                text =
                    stringResource(
                        if (status ==
                            SubscriptionStatus.Free
                        ) {
                            R.string.settings_subscription_discover
                        } else {
                            R.string.settings_subscription_resume
                        },
                    ),
                onClick = actions.onOffer,
                enabled = !state.refreshing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Only while the subscription exists in the store (an expired or refunded one has nothing to manage: the offer is there instead).
        val manageable = status is SubscriptionStatus.Store && !canBuy && manageUrlSource(status.source)
        if (status is SubscriptionStatus.Store && manageable) {
            MiffSecondaryButton(
                text = stringResource(R.string.settings_subscription_manage),
                onClick = { actions.onManage(status.source) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val refresh = actions.onRefresh
        if (refresh != null && status !is SubscriptionStatus.Manual) {
            if (state.refreshing) {
                Row(
                    modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    LoadingIndicator(Modifier.size(Spacing.l))
                    Text(
                        text = stringResource(R.string.paywall_working),
                        style = textBodyRegular14,
                        color = colorTextDescription,
                    )
                }
            } else {
                TextButton(
                    onClick = refresh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
                ) {
                    Text(
                        text = stringResource(R.string.settings_subscription_refresh),
                        style = textBodyBold16,
                        color = colorActionPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
