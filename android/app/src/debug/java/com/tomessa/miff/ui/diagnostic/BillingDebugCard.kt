package com.tomessa.miff.ui.diagnostic

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.data.billing.FakeBillingControl
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * DEBUG BUILD ONLY. Chooses what the FAKE store, the FAKE server and the FAKE entitlement answer (docs/dev/premium-achat-android-test.md, section 2), so that the whole interface of
 * Miff+ can be tried without the app being in the Play Console: what the store offers, what the next purchase does, and the state of the subscription. Everything is in memory (lost
 * when the process ends). It grants NOTHING on the real server (the fake never writes anywhere): the limits of places and Miffs do not change.
 * Debug text, French only, never translated (the diagnostic screen is for the team).
 */
@Composable
fun BillingDebugCard() {
    SectionCard("Achat Miff+ (faux magasin)") {
        if (!BuildConfig.PREMIUM_PURCHASE || !BuildConfig.BILLING_FAKE) {
            Text(
                if (!BuildConfig.PREMIUM_PURCHASE) {
                    "L'achat est éteint dans ce build (-Pmiff.premiumPurchase=false)."
                } else {
                    "Le faux magasin est éteint (-Pmiff.billingFake=false) : l'achat parle au vrai Google Play."
                },
                style = textBodyRegular14,
                color = colorTextPrimary,
            )
            return@SectionCard
        }
        Text(
            "Rien n'est accordé sur le vrai serveur : on voit les écrans et les messages, les limites de lieux et de Miffs ne changent pas.",
            style = textBodyRegular14,
            color = colorTextPrimary,
        )
        var catalog by remember { mutableStateOf(FakeBillingControl.catalog) }
        var purchase by remember { mutableStateOf(FakeBillingControl.purchase) }
        var doc by remember { mutableStateOf(FakeBillingControl.doc) }
        var owns by remember { mutableStateOf(FakeBillingControl.storeOwns) }

        Text("Ce que le magasin propose", style = textBodyBold14, color = colorTextPrimary)
        FakeBillingControl.CatalogMode.entries.forEach { mode ->
            Choice(mode.label, mode == catalog) {
                FakeBillingControl.catalog = mode
                catalog = mode
            }
        }
        Text("Ce que fera le prochain achat", style = textBodyBold14, color = colorTextPrimary)
        FakeBillingControl.PurchaseMode.entries.forEach { mode ->
            Choice(mode.label, mode == purchase) {
                FakeBillingControl.purchase = mode
                purchase = mode
            }
        }
        Text("État de l'abonnement (entitlements/{uid} simulé)", style = textBodyBold14, color = colorTextPrimary)
        FakeBillingControl.Doc.entries.forEach { value ->
            Choice(value.label, value == doc) {
                FakeBillingControl.doc = value
                doc = value
            }
        }
        Choice("Le store possède un achat pour ce compte (pour essayer la restauration)", owns) {
            FakeBillingControl.storeOwns = !owns
            owns = !owns
        }
        MiffSecondaryButton(
            text = "Tout remettre à zéro",
            onClick = {
                FakeBillingControl.reset()
                catalog = FakeBillingControl.catalog
                purchase = FakeBillingControl.purchase
                doc = FakeBillingControl.doc
                owns = FakeBillingControl.storeOwns
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    MiffSecondaryButton(
        text = (if (selected) "[x] " else "[ ] ") + label,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}
