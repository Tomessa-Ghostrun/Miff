#if DEBUG || INTERNAL
import SwiftUI

// BUILDS DE TEST SEULEMENT (Debug et Internal). Cette carte choisit ce que répondent le FAUX magasin, le FAUX serveur et le FAUX droit (docs/dev/premium-achat-ios-test.md), pour
// essayer toute l'interface de Miff+ sans produit dans App Store Connect : ce que le magasin propose, ce que fera le prochain achat, et l'état de l'abonnement. Tout est en
// mémoire (perdu à la fin du processus). Elle n'accorde RIEN sur le vrai serveur (le faux n'écrit nulle part) : les limites de lieux et de Miffs ne changent pas. Texte de
// diagnostic, en français seulement, jamais traduit (l'écran de diagnostic est pour l'équipe). Miroir de `BillingDebugCard.kt` d'Android.

/// La carte « Achat Miff+ (faux magasin) » du diagnostic.
struct BillingDebugCard: View {
    var body: some View {
        SectionCard("ios_diag_billing_title", section: .billing) {
            #if BILLING_FAKE
            FakeBillingControls()
            #else
            DiagLine(
                verbatim: BuildFlavor.includesPremiumPurchase
                    ? "Le faux magasin est éteint dans ce build : l'achat parle au vrai App Store (bac à sable)."
                    : "L'achat est éteint dans ce build (entrée premium_purchase du workflow ios-testflight).",
                .description
            )
            #endif
        }
    }
}

#if BILLING_FAKE
private struct FakeBillingControls: View {
    @State private var catalog = FakeBillingControl.catalog
    @State private var purchase = FakeBillingControl.purchase
    @State private var doc = FakeBillingControl.doc
    @State private var owns = FakeBillingControl.storeOwns

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            DiagLine(
                verbatim: "Rien n'est accordé sur le vrai serveur : on voit les écrans et les messages, "
                    + "les limites de lieux et de Miffs ne changent pas.",
                .description
            )
            DiagLine(verbatim: "Ce que le magasin propose", .strong)
            ForEach(FakeBillingControl.CatalogMode.allCases, id: \.label) { mode in
                BillingChoice(label: mode.label, selected: mode == catalog) {
                    FakeBillingControl.catalog = mode
                    catalog = mode
                }
            }
            DiagLine(verbatim: "Ce que fera le prochain achat", .strong)
            ForEach(FakeBillingControl.PurchaseMode.allCases, id: \.label) { mode in
                BillingChoice(label: mode.label, selected: mode == purchase) {
                    FakeBillingControl.purchase = mode
                    purchase = mode
                }
            }
            DiagLine(verbatim: "État de l'abonnement (entitlements/{uid} simulé)", .strong)
            ForEach(FakeBillingControl.Doc.allCases, id: \.label) { value in
                BillingChoice(label: value.label, selected: value == doc) {
                    FakeBillingControl.doc = value
                    doc = value
                }
            }
            BillingChoice(label: "Le store possède un achat pour ce compte (essayer la restauration)", selected: owns) {
                FakeBillingControl.storeOwns.toggle()
                owns = FakeBillingControl.storeOwns
            }
            Button {
                FakeBillingControl.reset()
                catalog = FakeBillingControl.catalog
                purchase = FakeBillingControl.purchase
                doc = FakeBillingControl.doc
                owns = FakeBillingControl.storeOwns
            } label: {
                Text(verbatim: "Tout remettre à zéro")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        }
    }
}

/// Une ligne de choix : une coche écrite devant le choix courant (le gras seul ne suffirait pas), toute la ligne est un bouton.
private struct BillingChoice: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(verbatim: (selected ? "\u{2713} " : "") + label)
                .miffTextStyle(selected ? textBodyBold14 : textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
#endif
#endif
