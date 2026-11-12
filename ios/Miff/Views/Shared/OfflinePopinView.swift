import SwiftUI

// La pop-in « Pas de connexion » partagée (Feed, onglet Profil) : une carte blanche à contour bleu, comme les pop-ins du PM, posée
// sur un voile léger. Calme : aucune couleur d'erreur, aucun point d'exclamation, aucune faute à la personne. Elle disparaît d'elle-même
// quand la connexion revient (`OfflineRetryModel`).
//
// Elle se FERME : toucher le voile, la croix, ou le geste « échappe » de VoiceOver (deux doigts en Z). Fermée, les essais continuent et la
// page de l'écran dit la même chose à sa place (`OfflineInlineNote` : la phrase et « Réessayer »). C'est indispensable : sans Miff lue,
// la barre d'onglets est masquée (`MainShellRules.barVisible`), et la pop-in ne doit jamais enfermer la personne dans l'écran.
//
// VoiceOver : une seule annonce. La carte est « modale » (le reste de l'écran est ignoré) et le focus est posé sur son texte, qui est lu
// d'un bloc ; aucune annonce supplémentaire n'est envoyée. Le compte à rebours n'est pas une zone « live » : il ne se répète pas chaque
// seconde. Aucune donnée de la personne ici, aucun événement, aucun journal.

/// Le voile et la carte, montrés tant que `model.isPopinVisible`. À poser par-dessus le contenu de l'écran.
struct OfflinePopinOverlay: View {
    @ObservedObject var model: OfflineRetryModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if model.isPopinVisible {
                // Le voile arrête aussi les touchers vers l'écran dessous ; le toucher le ferme (la croix et « échappe » font de même).
                colorTextPrimary.color
                    .opacity(0.3)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture { model.dismiss() }
                    .accessibilityHidden(true)
                    .transition(.opacity)
                OfflinePopinCard(
                    isRetrying: model.isRetrying,
                    secondsLeft: model.secondsLeft,
                    onRetry: { model.retryNow() },
                    onClose: { model.dismiss() }
                )
                .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        // « Réduire les animations » : un simple fondu, sans mouvement.
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: model.isPopinVisible)
    }
}

/// La carte seule (sans le voile) : le titre, la phrase, le compte à rebours, « Réessayer » et la croix. Le contenu défile quand le
/// texte agrandi ne tient pas.
struct OfflinePopinCard: View {
    let isRetrying: Bool
    /// Le compte à rebours avant le prochain essai automatique (nil : aucun n'est prévu).
    let secondsLeft: Int?
    let onRetry: () -> Void
    let onClose: () -> Void

    @AccessibilityFocusState private var textFocused: Bool

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
        // Le geste « échappe » de VoiceOver ferme la pop-in.
        .accessibilityAction(.escape) { onClose() }
        .onAppear {
            // Le focus VoiceOver va dans la carte (le texte), pas sur l'écran dessous.
            DispatchQueue.main.async {
                textFocused = true
            }
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
            Text("feed_offline_popin_body")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        // Le titre et la phrase sont lus d'un bloc, une seule fois.
        .accessibilityElement(children: .combine)
        .accessibilityFocused($textFocused)
        .padding(.horizontal, Spacing.md)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.sm)
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var countdown: some View {
        if let secondsLeft {
            Text(verbatim: L10n.format("feed_offline_popin_countdown", secondsLeft))
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, Spacing.md)
                .padding(.bottom, Spacing.sm)
        }
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
        .accessibilityValue(isRetrying ? Text("ios_feed_offline_popin_retrying_a11y") : Text(verbatim: ""))
        .padding(.horizontal, Spacing.md)
        .padding(.bottom, Spacing.md)
    }

    private var closeButton: some View {
        Button(action: onClose) {
            Image(systemName: "xmark")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(colorTextDescription.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("ios_feed_offline_popin_close_a11y"))
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
            countdown
            retryButton
        }
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
        .overlay(alignment: .topTrailing) {
            closeButton
        }
        .miffShadow(shadowDefault)
    }
}

/// Montre le contenu normal de l'écran, ou `OfflineInlineNote` à sa place quand la pop-in a été fermée. Observe le modèle lui-même : l'écran
/// qui l'utilise n'a pas à le faire.
struct OfflineInlineSwitch<Normal: View>: View {
    @ObservedObject var model: OfflineRetryModel
    /// La marge sous l'en-tête de l'écran qui le porte (0 quand l'écran n'a pas d'en-tête superposé).
    var topInset: CGFloat = 0
    @ViewBuilder var normal: () -> Normal

    var body: some View {
        if model.isInlineVisible {
            OfflineInlineNote(model: model)
                .padding(Spacing.md)
                .padding(.top, topInset)
                .frame(maxHeight: .infinity, alignment: .top)
        } else {
            normal()
        }
    }
}

/// Ce que la page de l'écran dit une fois la pop-in fermée : la même phrase calme et le même bouton, à la place du squelette ou de
/// l'indicateur. Les essais automatiques continuent derrière.
struct OfflineInlineNote: View {
    @ObservedObject var model: OfflineRetryModel

    var body: some View {
        VStack(spacing: Spacing.sm) {
            MiffNotice(text: L10n.string("feed_offline_note"))
            Button {
                model.retryNow()
            } label: {
                HStack(spacing: Spacing.sm) {
                    Text("feed_offline_popin_retry")
                    if model.isRetrying {
                        ProgressView()
                            .tint(colorActionPrimary.color)
                            .accessibilityHidden(true)
                    }
                }
            }
            .buttonStyle(.miffSecondaryFullWidth)
            .accessibilityValue(model.isRetrying ? Text("ios_feed_offline_popin_retrying_a11y") : Text(verbatim: ""))
        }
    }
}
