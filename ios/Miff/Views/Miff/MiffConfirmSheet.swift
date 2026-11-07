import SwiftUI

/// Le poids d'une question : `sensitive` a un bouton blanc au trait rouge (jamais un fond rouge : le ton reste calme).
enum MiffDialogTone: Equatable {
    case standard
    case sensitive
}

/// Une seconde action, plus discrète, au-dessus de l'action principale, qui ouvre la feuille de partage du système avec un
/// texte prêt (« Prévenir mes proches », « Partager le code »), et une courte ligne qui dit ce qu'elle fait vraiment.
struct MiffShareExtra {
    let label: String
    let hint: String?
    let item: String
}

/// Ce que dit une fenêtre de confirmation.
struct MiffConfirmContent {
    let title: String
    let message: String?
    let confirmLabel: String
    let isBusy: Bool
    let failure: ActionMessage?
    var tone = MiffDialogTone.standard
    /// Faux pour une fenêtre qui informe seulement (« Terminer » après un retrait) : alors il n'y a pas d'« Annuler ».
    var showsCancel = true
    /// Le libellé de la sortie (« Annuler » par défaut ; « Fermer » pour une suppression arrêtée).
    var cancelLabel: String?
    var share: MiffShareExtra?
}

struct MiffConfirmActions {
    let onConfirm: () -> Void
    let onCancel: () -> Void
}

/// LA fenêtre de confirmation du flux (un seul modèle pour toutes les questions, ticket §3.3) : un titre, une ou deux phrases
/// calmes, le bouton principal, et « Annuler », qui est toujours la sortie sans risque. Pendant l'opération, le bouton
/// principal est grisé, un indicateur tourne et rien d'autre n'est possible (un appui = une opération). Présentée en feuille :
/// le lecteur d'écran y reste enfermé et la taille de texte maximale la fait défiler. Les actions sensibles (retirer, quitter,
/// supprimer) ont un bouton blanc au trait rouge, le libellé dit l'action (la couleur n'est pas le seul signal).
struct MiffConfirmSheet<Extra: View>: View {
    let content: MiffConfirmContent
    let actions: MiffConfirmActions
    let extra: Extra

    init(content: MiffConfirmContent, actions: MiffConfirmActions, @ViewBuilder extra: () -> Extra) {
        self.content = content
        self.actions = actions
        self.extra = extra()
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Text(verbatim: content.title)
                    .miffTextStyle(textTitleBold20)
                    .foregroundColor(colorTextPrimary.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if let message = content.message {
                    Text(verbatim: message)
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextDescription.color)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                extra
                if let share = content.share {
                    MiffShareButton(share: share, isEnabled: !content.isBusy)
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.lg)
            .padding(.bottom, Spacing.sm)
        }
        // Les boutons (et la phrase d'erreur) restent ÉPINGLÉS en bas : quand `extra` est long (le consentement de la version 3
        // de « Reprendre le partage »), le texte défile et le choix reste toujours à portée.
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: Spacing.sm) {
                if let failure = content.failure {
                    MiffNotice(text: actionMessageText(failure))
                }
                confirmButton
                if content.isBusy {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(colorActionPrimary.color)
                        .accessibilityLabel("miff_dialog_working_a11y")
                }
                if content.showsCancel {
                    Button(action: actions.onCancel) {
                        Text(verbatim: content.cancelLabel ?? L10n.string("common_cancel"))
                            .miffTextStyle(textBodyBold16)
                            .foregroundColor(colorActionPrimary.color)
                            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
                    }
                    .disabled(content.isBusy)
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.sm)
            .padding(.bottom, Spacing.md)
            .frame(maxWidth: .infinity)
            .background(colorBgSurface.color.ignoresSafeArea(edges: .bottom))
        }
        .background(colorBgSurface.color.ignoresSafeArea())
        .interactiveDismissDisabled(content.isBusy)
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private var confirmButton: some View {
        switch content.tone {
        case .standard:
            Button(action: actions.onConfirm) {
                Text(verbatim: content.confirmLabel)
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(content.isBusy)
        case .sensitive:
            Button(action: actions.onConfirm) {
                Text(verbatim: content.confirmLabel)
            }
            .buttonStyle(.miffSensitiveFullWidth)
            .disabled(content.isBusy)
        }
    }
}

extension MiffConfirmSheet where Extra == EmptyView {
    init(content: MiffConfirmContent, actions: MiffConfirmActions) {
        self.init(content: content, actions: actions) {
            EmptyView()
        }
    }
}

/// Le bouton qui ouvre la feuille de partage du système (jamais un envoi fait par l'app) : même aspect que le bouton
/// secondaire. La ligne dessous dit ce qu'il fait vraiment.
private struct MiffShareButton: View {
    let share: MiffShareExtra
    let isEnabled: Bool

    var body: some View {
        VStack(spacing: Spacing.xs) {
            ShareLink(item: share.item) {
                MiffSecondaryLabel(text: share.label)
            }
            .buttonStyle(.plain)
            .disabled(!isEnabled)
            if let hint = share.hint {
                Text(verbatim: hint)
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Le bouton secondaire dessiné « à la main » pour les endroits où ce n'est pas un `Button` (le lien de partage du système) :
/// même fond, même trait, même texte que `MiffSecondaryButtonStyle`.
struct MiffSecondaryLabel: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyLarge)
            .foregroundColor(colorActionPrimary.color)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorActionPrimary.color, lineWidth: strokeAccent)
            )
            .padding(.vertical, Spacing.xs)
    }
}
