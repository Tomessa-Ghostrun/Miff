#if REVIEW_PROMPT
import SwiftUI

// La fenêtre « Que penses-tu de Miff ? » (maquette `design/exports/pop-ins/03-avis-store.png`, construite par décision du PM du 2026-11-07,
// risque assumé : `docs/tickets/pop-ins-avis.md`). Sans état : `ReviewPromptHostModifier` la nourrit. Miroir de `ReviewPromptWindow.kt`.
//
// Écarts avec la maquette (tous listés dans la PR) : PAS d'étoiles et deux boutons « J'adore ! » et « Peux faire mieux... » (décision du PM,
// 2026-11-08) ; tutoiement (2026-11-01) ; une petite croix en haut à droite (la maquette n'a aucune sortie visible) ; un cadre de 2 pt aux
// couleurs et aux proportions des autres pop-ins de l'app ; un fond flouté et légèrement assombri par le système.
//
// CONFIDENTIALITÉ : aucune donnée de la personne ici, aucun événement, aucun journal.
//
// VoiceOver : la carte est « modale » (le reste de l'écran est ignoré), le focus va sur son texte à l'apparition, l'ordre de lecture est
// le titre, « J'adore ! », « Peux faire mieux... », puis « Fermer » ; le geste « échappe » (deux doigts en Z) ferme, sauf pendant
// « Merci ! ». Grande taille de texte : la carte défile au lieu de se couper.

/// Le texte qui marque cette fenêtre dans le code de l'app : un build de publication ne doit pas le contenir (tout le fichier est derrière
/// `REVIEW_PROMPT`, absent en Release). Contrôlé par `ci/check-binaries.sh`. Ne pas renommer sans changer ce script.
let reviewPromptProbe = "REVIEWPROMPTPROBE"

/// Ce que font les boutons de la fenêtre.
struct ReviewPromptActions {
    let onLove: () -> Void
    let onBetter: () -> Void
    let onClose: () -> Void
    let onWrite: () -> Void
    let onLater: () -> Void

    static let inert = ReviewPromptActions(onLove: {}, onBetter: {}, onClose: {}, onWrite: {}, onLater: {})
}

/// Le fond et la carte, montrés tant que `stage` n'est pas `.hidden`. À poser par-dessus l'écran.
struct ReviewPromptOverlay: View {
    let stage: ReviewStage
    let actions: ReviewPromptActions
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if stage != .hidden {
                // Le fond arrête aussi les touchers vers l'écran dessous. Un toucher à côté de la carte ne ferme JAMAIS la fenêtre (il
                // compterait comme une fermeture).
                Rectangle()
                    .fill(.ultraThinMaterial)
                    .overlay(colorTextPrimary.color.opacity(0.15))
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {}
                    .accessibilityHidden(true)
                    .transition(.opacity)
                ReviewPromptCard(stage: stage, actions: actions)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        // « Réduire les animations » : un simple fondu, sans mouvement.
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: stage)
    }
}

/// La carte seule (sans le fond) : la question, le « Merci ! » ou l'écran calme « Merci de nous le dire ! ».
struct ReviewPromptCard: View {
    let stage: ReviewStage
    let actions: ReviewPromptActions

    @AccessibilityFocusState private var textFocused: Bool

    /// 280 pt au plus : la largeur des autres pop-ins de l'app (ticket §8, écart 4).
    private static let maxWidth: CGFloat = 280

    var body: some View {
        ViewThatFits(in: .vertical) {
            card(scrolls: false)
            card(scrolls: true)
        }
        .frame(maxWidth: Self.maxWidth)
        .padding(Spacing.md)
        .accessibilityIdentifier(reviewPromptProbe)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        // Le geste « échappe » ferme (comme le geste retour d'Android) ; sans effet pendant « Merci ! » (le modèle l'ignore).
        .accessibilityAction(.escape) { actions.onClose() }
        .onAppear {
            // Le focus VoiceOver va dans la carte (le texte), pas sur l'écran dessous.
            DispatchQueue.main.async {
                textFocused = true
            }
        }
    }

    private func card(scrolls: Bool) -> some View {
        VStack(spacing: 0) {
            if scrolls {
                ScrollView {
                    content
                }
            } else {
                content
            }
        }
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
        .miffShadow(shadowDefault)
    }

    @ViewBuilder
    private var content: some View {
        switch stage {
        case .question:
            questionContent
        case .thanksForLove:
            thanksContent(title: "review_thanks_title", message: "review_thanks_body")
        case .thanksForFeedback:
            feedbackContent
        case .hidden:
            EmptyView()
        }
    }

    // MARK: - La question

    private var questionContent: some View {
        VStack(spacing: 0) {
            // Une ligne à elle pour la croix : elle ne recouvre jamais le titre (grande taille de texte, autres langues).
            HStack {
                Spacer(minLength: 0)
                Button(action: actions.onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(colorTextDescription.color)
                        .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("review_close_a11y"))
                .accessibilitySortPriority(1)
            }
            .padding(.top, Spacing.xs)
            .padding(.trailing, Spacing.xs)
            VStack(spacing: Spacing.sm) {
                Text("review_title")
                    .miffTextStyle(textTitleBold20)
                    .foregroundColor(colorActionPrimary.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityFocused($textFocused)
                    .accessibilitySortPriority(4)
                    .padding(.bottom, Spacing.sm)
                Button(action: actions.onLove) {
                    Text("review_love_action")
                }
                .buttonStyle(.miffPrimaryFullWidth)
                .accessibilitySortPriority(3)
                Button(action: actions.onBetter) {
                    Text("review_better_action")
                }
                .buttonStyle(.miffPrimaryFullWidth)
                .accessibilitySortPriority(2)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.bottom, Spacing.md)
        }
    }

    // MARK: - « Merci ! » et « Merci de nous le dire ! »

    private func thanksContent(title: LocalizedStringKey, message: LocalizedStringKey) -> some View {
        texts(title: title, message: message)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.lg)
    }

    private var feedbackContent: some View {
        VStack(spacing: Spacing.sm) {
            texts(title: "review_feedback_title", message: "review_feedback_body")
            Button(action: actions.onWrite) {
                Text("review_feedback_write")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            Button(action: actions.onLater) {
                Text("review_feedback_later")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorActionPrimary.color)
                    .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.lg)
    }

    /// Le titre et la phrase, lus d'un bloc une seule fois ; le focus VoiceOver y est posé.
    private func texts(title: LocalizedStringKey, message: LocalizedStringKey) -> some View {
        VStack(spacing: Spacing.sm) {
            Text(title)
                .miffTextStyle(textTitleBold20)
                .foregroundColor(colorActionPrimary.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text(message)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityFocused($textFocused)
    }
}

/// Le message calme quand rien n'a pu s'ouvrir (jamais un geste muet) : un petit encadré qui disparaît seul, jamais une alerte.
struct ReviewOpenFailedNotice: View {
    var body: some View {
        Text("review_open_failed")
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorTextPrimary.color)
            .multilineTextAlignment(.center)
            .padding(Spacing.md)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderStrong.color, lineWidth: strokeHairline)
            )
            .miffShadow(shadowDefault)
            .padding(.horizontal, Spacing.lg)
            .onAppear {
                MiffNoticeAnnouncements.announceOnce(L10n.string("review_open_failed"))
            }
    }
}
#endif
