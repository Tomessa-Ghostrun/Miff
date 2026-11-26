import SwiftUI

/// Ce que l'écran de l'abonnement peut faire (l'hôte les branche : l'offre, la retour).
struct SubscriptionActions {
    let onBack: () -> Void
    /// « Découvrir Miff+ » / « Reprendre Miff+ » : l'offre.
    let onOffer: () -> Void
}

/// Réglages > Abonnement (ticket `docs/tickets/premium-achat-reel.md` §8). AUCUNE maquette n'existe pour cet écran : il est construit dans le design system, le plus simplement
/// possible (la barre de titre des Réglages, le nom « Miff+ », UNE phrase calme qui dit ce qui se passe, ce qui reste vrai et quoi faire, puis trois actions au plus). Écart avec
/// les maquettes listé dans la PR.
///
/// Le ton (décision du ticket) : jamais de rouge, de point d'exclamation, de « attention », de compte à rebours ni de culpabilisation. Les phrases donnent l'état, jamais un prix :
/// voir `SubscriptionViewModel`. Pas de bouton « Arrêter l'abonnement » dans l'app : résilier, reprendre et changer se font dans la gestion d'Apple (« Gérer mon abonnement »).
struct SubscriptionView: View {
    @ObservedObject var model: SubscriptionViewModel
    let actions: SubscriptionActions

    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        MiffFrame(title: L10n.string("settings_subscription_title"), onBack: actions.onBack, usesCanvas: true) {
            Text("settings_subscription_plan_name")
                .miffTextStyle(textTitleBold20)
                .foregroundColor(colorTextForm.color)
                .accessibilityAddTraits(.isHeader)
            content
        }
        .onAppear {
            model.load()
        }
        // Retour de la page d'Apple ou de l'offre : ce que le serveur sait a peut-être changé.
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                model.onReturned()
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.loading && model.status == nil {
            HStack(spacing: Spacing.md) {
                ProgressView()
                Text("paywall_working")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
            }
            .padding(.top, Spacing.sm)
        } else if let status = model.status {
            SubscriptionStatusBlock(status: status, notice: model.notice)
            actionsBlock(status)
        } else {
            VStack(spacing: Spacing.sm) {
                MiffNotice(text: L10n.string("settings_subscription_unreadable"))
                Button {
                    model.load()
                } label: {
                    Text("settings_delete_retry")
                }
                .buttonStyle(.miffSecondaryFullWidth)
            }
            .padding(.top, Spacing.sm)
        }
    }

    // MARK: - Les actions

    /// Du plus utile au moins utile : l'offre (quand il y a quelque chose à acheter : un compte gratuit, un abonnement expiré ou remboursé), « Gérer mon abonnement » (un
    /// abonnement qui existe chez un store), « Restaurer mes achats » (un lien discret : retrouvé après une réinstallation ou sur un nouveau téléphone ; demandé seul, jamais
    /// en boucle).
    @ViewBuilder
    private func actionsBlock(_ status: SubscriptionStatus) -> some View {
        VStack(spacing: Spacing.sm) {
            if SubscriptionActionsRules.canBuy(status) {
                Button(action: actions.onOffer) {
                    Text(verbatim: L10n.string(Self.offerKey(status)))
                }
                .buttonStyle(.miffPrimaryFullWidth)
                .disabled(model.refreshing)
            }
            if let source = SubscriptionActionsRules.manageableSource(status) {
                Button {
                    model.manage(source)
                } label: {
                    Text("settings_subscription_manage")
                }
                .buttonStyle(.miffSecondaryFullWidth)
            }
            if SubscriptionActionsRules.canRestore(status) {
                restoreLink
            }
        }
        .padding(.top, Spacing.md)
    }

    /// « Découvrir Miff+ » pour un compte qui n'a jamais eu Miff+, « Reprendre Miff+ » pour un abonnement terminé ou remboursé.
    private static func offerKey(_ status: SubscriptionStatus) -> String {
        status == .free ? "settings_subscription_discover" : "settings_subscription_resume"
    }

    @ViewBuilder
    private var restoreLink: some View {
        if model.refreshing {
            HStack(spacing: Spacing.md) {
                ProgressView()
                Text("paywall_working")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
            }
            .frame(minHeight: Spacing.minTouchTarget)
        } else {
            Button {
                model.restore()
            } label: {
                Text("paywall_restore")
                    .miffTextStyle(textBodyBold16)
                    .underline()
                    .foregroundColor(colorActionPrimary.color)
                    .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            }
        }
    }
}

/// Qui peut faire quoi sur l'écran, sans dessin (testé seul).
enum SubscriptionActionsRules {
    /// Il y a quelque chose à acheter : un compte gratuit, un abonnement expiré ou remboursé.
    static func canBuy(_ status: SubscriptionStatus) -> Bool {
        switch status {
        case .free:
            return true
        case .store(let subscription):
            return subscription.state == .expired || subscription.state == .revoked
        case .manual:
            return false
        }
    }

    /// Le store où la personne gère son abonnement, seulement tant qu'il existe chez lui (un abonnement expiré ou remboursé n'a rien à gérer : l'offre est là à la place).
    static func manageableSource(_ status: SubscriptionStatus) -> SubscriptionSource? {
        guard case .store(let subscription) = status, !canBuy(status),
              subscription.source == .appStore || subscription.source == .googlePlay else {
            return nil
        }
        return subscription.source
    }

    /// « Restaurer mes achats » : pour tout sauf un accès accordé à la main (rien à restaurer chez un store).
    static func canRestore(_ status: SubscriptionStatus) -> Bool {
        if case .manual = status {
            return false
        }
        return true
    }
}

/// Les phrases de l'état : une phrase par état (ticket §8), une date au format long de la langue, jamais un prix.
struct SubscriptionStatusBlock: View {
    let status: SubscriptionStatus
    let notice: SubscriptionNotice?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            ForEach(SubscriptionTexts.lines(for: status), id: \.text) { line in
                Text(verbatim: line.text)
                    .miffTextStyle(line.small ? textBodyRegular14 : textBodyRegular)
                    .foregroundColor((line.small ? colorTextDescription : colorTextForm).color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, line.gap ? Spacing.sm : 0)
            }
            if let notice {
                MiffNotice(text: SubscriptionTexts.text(for: notice))
                    .padding(.top, Spacing.sm)
            }
        }
        .padding(.top, Spacing.xs)
    }
}
