package com.tomessa.miff.ui.diagnostic

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.MiffApplication
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * DEBUG BUILD ONLY. Lets the PM try the periodic offer (docs/tickets/paywall-periodique.md, docs/qa/checklists/paywall-periodique-android-recette-pm.md) without opening the app 10
 * times: it shows the counter, the threshold and the state of the session, and puts the counter at "threshold - 1" (the next opening of the app, then a first tap, brings the offer) or at
 * zero. The threshold itself can be lowered at build time with -Pmiff.periodicPaywallOpenings=N (debug build only). Nothing here exists in a release build (debug source set).
 */
@Composable
fun PeriodicPaywallDebugCard() {
    val rules = (LocalContext.current.applicationContext as MiffApplication).container.periodicPaywall
    var counter by remember { mutableIntStateOf(rules.counterForDiagnostic()) }
    var session by remember { mutableStateOf(rules.sessionForDiagnostic()) }
    val threshold = rules.thresholdForDiagnostic
    SectionCard("Offre périodique (essai)") {
        Text(
            "compteur : $counter / $threshold ; ${if (BuildConfig.PERIODIC_PAYWALL_OPENINGS_DEBUG > 0) "seuil abaissé par le build" else "seuil normal"} ; $session",
            style = textBodyRegular14,
            color = colorTextPrimary,
        )
        MiffButton(
            text = "Compteur = ${threshold - 1} (seuil moins un)",
            onClick = {
                rules.setCounterForDiagnostic(threshold - 1)
                counter = rules.counterForDiagnostic()
                session = rules.sessionForDiagnostic()
            },
        )
        MiffButton(
            text = "Compteur = $threshold (offre due, dès le prochain appui)",
            onClick = {
                rules.setCounterForDiagnostic(threshold)
                counter = rules.counterForDiagnostic()
                session = rules.sessionForDiagnostic()
            },
        )
        MiffButton(
            text = "Compteur = 0",
            onClick = {
                rules.setCounterForDiagnostic(0)
                counter = rules.counterForDiagnostic()
                session = rules.sessionForDiagnostic()
            },
        )
        Text(
            "Seuil moins un : ferme complètement l'app (ou quitte-la 30 minutes), rouvre-la : cette ouverture est comptée ; touche un bouton : l'offre vient après environ 1,5 s de calme. Seuil : l'offre est due tout de suite, il suffit de toucher un bouton.",
            style = textBodyRegular14,
            color = colorTextPrimary,
        )
    }
}
