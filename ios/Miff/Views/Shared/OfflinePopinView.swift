import SwiftUI

// La pop-in « Pas de connexion » partagée (Feed, onglet Profil) : une carte blanche à contour bleu, comme les pop-ins du PM, posée
// sur un voile léger. Calme : aucune couleur d'erreur, aucun point d'exclamation, aucune faute à la personne. Elle disparaît d'elle-même
// quand la connexion revient (`OfflineRetryModel`). Elle ne couvre que l'écran qui la porte : la barre d'onglets reste utilisable.
// Aucune donnée de la personne ici, aucun événement, aucun journal.

/// Le voile et la carte, montrés tant que `model.isShown`. À poser par-dessus le contenu de l'écran.
struct OfflinePopinOverlay: View {
    @ObservedObject var model: OfflineRetryModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if model.isShown {
                // Le voile arrête aussi les touchers vers l'écran dessous : la pop-in est la seule chose à faire ici.
                colorTextPrimary.color
                    .opacity(0.3)
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
                    .transition(.opacity)
                OfflinePopinCard(isRetrying: model.isRetrying, onRetry: { model.retryNow() })
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        // « Réduire les animations » : un simple fondu, sans mouvement.
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: model.isShown)
    }
}

/// La carte seule (sans le voile) : le titre, la phrase, « Réessayer ». Le contenu défile quand le texte agrandi ne tient pas.
struct OfflinePopinCard: View {
    let isRetrying: Bool
    let onRetry: () -> Void

    var body: some View {
        ViewThatFits(in: .vertical) {
            card(scrolls: false)
            card(scrolls: true)
        }
        .frame(maxWidth: 420)
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.md)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .onAppear {
            Announcer.announce(L10n.string("feed_offline_popin_a11y"))
        }
    }

    private var texts: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 28))
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            Text("feed_offline_popin_title")
                .miffTextStyle(textTitleBold20)
                .foregroundColor(colorActionPrimary.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text("feed_offline_popin_message")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, Spacing.md)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.sm)
        .frame(maxWidth: .infinity)
    }

    private var retryButton: some View {
        Button(action: onRetry) {
            HStack(spacing: Spacing.sm) {
                Text("feed_offline_popin_retry")
                if isRetrying {
                    // Décoratif : le texte et la valeur d'accessibilité disent la même chose.
                    ProgressView()
                        .tint(colorTextOnAction.color)
                        .accessibilityHidden(true)
                }
            }
        }
        .buttonStyle(.miffPrimaryFullWidth)
        .accessibilityValue(isRetrying ? Text("feed_offline_popin_retrying_a11y") : Text(verbatim: ""))
        .padding(.horizontal, Spacing.md)
        .padding(.bottom, Spacing.md)
    }

    private func card(scrolls: Bool) -> some View {
        VStack(spacing: 0) {
            if scrolls {
                ScrollView {
                    texts
                }
            } else {
                texts
            }
            retryButton
        }
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
        .miffShadow(shadowDefault)
    }
}
