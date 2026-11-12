import SwiftUI

/// Sous l'offre : le bloc d'information OBLIGATOIRE des stores (renouvellement automatique, annulation facile), la phrase qui dit que
/// la formule gratuite continue (accès sans essai, exigé par Google, retenu pour les deux : ticket §5.4), « Restaurer mes achats »
/// et les liens vers les conditions et la politique de confidentialité. Atteignable par défilement à toutes les tailles de texte.
///
/// Rien de cela n'est montré en variante « sans achat » : il n'y a ni abonnement à décrire ni achat à restaurer.
struct PaywallInformationView: View {
    @ObservedObject var model: PaywallViewModel
    let actions: PaywallActions

    @Environment(\.openURL) private var openURL

    var body: some View {
        if model.state.showsInformation {
            VStack(spacing: Spacing.sm) {
                ForEach(paragraphs, id: \.self) { paragraph in
                    Text(verbatim: paragraph)
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextPrimary.color)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Text(verbatim: PaywallInformation.freePlanSentence())
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                links
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var paragraphs: [String] {
        guard let offers = model.state.offers, let layout = model.layout else {
            return []
        }
        return PaywallInformation.paragraphs(offers: offers, layout: layout)
    }

    /// Une colonne de liens de 44 pt de haut (grandes tailles de texte : elle ne se tronque pas, elle s'allonge). « Restaurer » n'existe
    /// que quand il y a une offre à restaurer, et sur iOS elle ne part JAMAIS toute seule : seulement sur ce toucher (critère PW12).
    private var links: some View {
        VStack(spacing: 0) {
            if model.state.offers != nil {
                link("paywall_restore", enabled: !model.state.isBusy) {
                    actions.onRestore()
                }
            }
            link("paywall_terms_link", enabled: true) {
                open(AppLinks.termsURL())
            }
            link("paywall_privacy_link", enabled: true) {
                open(AppLinks.privacyURL())
            }
        }
    }

    private func link(_ key: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(verbatim: L10n.string(key))
                .miffTextStyle(textBodyBold14)
                .underline()
                .foregroundColor(colorActionPrimary.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
        }
        .disabled(!enabled)
    }

    private func open(_ url: URL?) {
        if let url {
            openURL(url)
        }
    }
}
