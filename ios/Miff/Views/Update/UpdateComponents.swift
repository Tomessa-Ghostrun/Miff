import SwiftUI
import UIKit

// Les éléments d'interface de l'Update : obturateur, boutons ronds sur la photo, « Recommencer », « Publier », pop-in. Aucune valeur de
// couleur n'est écrite ici : tout vient du design system (`UpdateTokens.swift`).

/// Annonce calme au lecteur d'écran (sans son, sans vibration).
enum UpdateAnnouncer {
    static func post(_ text: String) {
        UIAccessibility.post(notification: .announcement, argument: text)
    }
}

/// L'obturateur rond : 80 pt, un anneau blanc et un anneau bleu à l'intérieur (le bleu seul disparaît sur un fond sombre), centre
/// transparent. Nom parlé « Prendre la photo ».
struct ShutterButton: View {
    let onTap: () -> Void
    let onSwitchCamera: (() -> Void)?

    var body: some View {
        Button {
            onTap()
        } label: {
            ZStack {
                Circle()
                    .strokeBorder(colorUpdateOnPhoto.color, lineWidth: UpdateLayout.shutterRingWidth)
                Circle()
                    .strokeBorder(colorActionPrimary.color, lineWidth: UpdateLayout.shutterInnerRingWidth)
                    .padding(UpdateLayout.shutterInnerInset)
            }
            .frame(width: UpdateLayout.shutterSize, height: UpdateLayout.shutterSize)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("update_shutter_a11y"))
        .accessibilityAddTraits(.isButton)
        .modifier(SwitchCameraAction(action: onSwitchCamera))
    }
}

/// L'action d'accessibilité « Changer de caméra » (équivalent du double toucher), seulement quand il y a deux caméras.
private struct SwitchCameraAction: ViewModifier {
    let action: (() -> Void)?

    @ViewBuilder
    func body(content: Content) -> some View {
        if let action {
            content.accessibilityAction(named: Text("update_flip_a11y")) {
                action()
            }
        } else {
            content
        }
    }
}

/// Un bouton rond posé sur une photo : 48 pt de zone, un disque sombre translucide de 40 pt (un pictogramme blanc seul disparaît sur une
/// photo claire), le pictogramme en blanc.
struct RoundOverlayButton: View {
    let systemName: String
    let labelKey: String
    let onTap: () -> Void

    var body: some View {
        Button {
            onTap()
        } label: {
            ZStack {
                Circle()
                    .fill(colorUpdateScrim.color)
                    .frame(width: UpdateLayout.roundButtonDisc, height: UpdateLayout.roundButtonDisc)
                Image(systemName: systemName)
                    .font(.system(size: UpdateLayout.roundButtonIcon, weight: .regular))
                    .foregroundColor(colorUpdateOnPhoto.color)
            }
            .frame(width: UpdateLayout.roundButtonSize, height: UpdateLayout.roundButtonSize)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: L10n.string(labelKey)))
    }
}

/// « Recommencer » : un bouton blanc aux coins arrondis, pictogramme bleu et contour bleu (maquette : pictogramme seul, donc nom parlé).
struct RetakeButton: View {
    let enabled: Bool
    let onTap: () -> Void

    var body: some View {
        Button {
            onTap()
        } label: {
            Image(systemName: "arrow.counterclockwise")
                .font(.system(size: 20, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: UpdateLayout.retakeWidth, height: UpdateLayout.actionHeight)
                .background(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .fill(colorBgCanvas.withAlpha(enabled ? 1 : 0.6).color)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .stroke(colorBorderAction.color, lineWidth: 1.5)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(Text("update_retake_a11y"))
    }
}

/// « Publier » : le bouton bleu (48 pt de haut), le mot et l'avion en papier de la maquette ; à 30 % quand il ne peut pas servir.
struct PublishButton: View {
    let enabled: Bool
    let onTap: () -> Void

    var body: some View {
        Button {
            onTap()
        } label: {
            HStack(spacing: Spacing.sm) {
                Text("update_publish_action")
                    .miffTextStyle(textBodyLarge)
                Image(systemName: "paperplane")
                    .font(.system(size: 18, weight: .regular))
            }
            .foregroundColor(colorTextOnAction.color)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: UpdateLayout.actionHeight)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .fill((enabled ? colorActionPrimary : colorActionPrimaryTint30).color)
            )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(Text("update_publish_action"))
    }
}

/// Un pop-in du PM (design/exports/pop-ins) : carte blanche au contour bleu, titre bleu, pictogramme jaune-vert, texte centré, un bouton
/// bleu, un secondaire éventuel et un lien. Le fond calme est celui de l'app.
struct UpdatePopIn: View {
    struct Action {
        let titleKey: String
        let run: () -> Void
    }

    let systemImage: String
    let titleKey: String
    let bodyKey: String
    var primary: Action?
    let dismiss: Action

    var body: some View {
        ZStack {
            colorBgCanvas.color.ignoresSafeArea()
            GeometryReader { proxy in
                ScrollView {
                    card
                        .frame(maxWidth: 420)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, Spacing.md)
                        .frame(maxWidth: .infinity, minHeight: proxy.size.height)
                }
            }
        }
    }

    private var card: some View {
        VStack(spacing: Spacing.md) {
            Text(verbatim: L10n.string(titleKey))
                .miffTextStyle(textTitleBold20)
                .foregroundColor(colorActionPrimary.color)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Image(systemName: systemImage)
                .font(.system(size: 56, weight: .regular))
                .foregroundColor(colorBrandPrimary.color)
                .accessibilityHidden(true)
            Text(verbatim: L10n.string(bodyKey))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextPrimary.color)
                .multilineTextAlignment(.center)
            if let primary {
                Button {
                    primary.run()
                } label: {
                    Text(verbatim: L10n.string(primary.titleKey))
                }
                .buttonStyle(MiffPrimaryButtonStyle(fillsWidth: true))
            }
            Button {
                dismiss.run()
            } label: {
                Text(verbatim: L10n.string(dismiss.titleKey))
                    .miffTextStyle(textBodyBold14)
                    .foregroundColor(colorActionPrimary.color)
                    .frame(minHeight: Spacing.minTouchTarget)
            }
            .buttonStyle(.plain)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorBgSurface.color)
                .miffShadow(shadowDefault)
        )
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
    }
}
