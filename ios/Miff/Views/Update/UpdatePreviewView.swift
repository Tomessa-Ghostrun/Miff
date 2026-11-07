import SwiftUI
import UIKit

// L'aperçu de la photo prise (ticket Update §5.1, design/screens/update.md §6). La photo est un peu plus petite que le plein écran (marges,
// coins arrondis, fond sombre autour), ENTIÈRE et à son ratio ; la croix (même effet que « Recommencer », D22), « Recommencer » et « Publier »
// DESSOUS. Ce qui est dessiné est le JPEG même qui sera publié (reflet de la caméra frontale compris, D37) : l'aperçu dit la vérité. Rien n'a
// quitté le téléphone à ce stade, et aucune position n'est mesurée (elle l'est au toucher de « Publier », D33).

/// Ce que l'aperçu demande au reste de l'app.
struct UpdateActions {
    /// Retour au Feed (« Fermer », « Pas maintenant » et la fin d'une publication).
    var onClose: () -> Void = {}
    /// « Reprendre le partage » : l'écran de la Miff tient le dialogue et le consentement (rien n'est écrit d'ici).
    var onResumeSharing: () -> Void = {}
    /// « Revoir et accepter » : l'écran de renouvellement de l'accord.
    var onReviewAgreement: () -> Void = {}
    /// Les Réglages de Miff dans le système (position retirée).
    var onOpenSettings: () -> Void = {}
}

struct UpdatePreviewView: View {
    let state: PreviewState
    let miffCount: Int
    let onRetake: () -> Void
    let onPublish: () -> Void
    let actions: UpdateActions

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            colorUpdateStage.color.ignoresSafeArea()
            VStack(spacing: 0) {
                photoFrame
                    .padding(.horizontal, Spacing.md)
                    .padding(.vertical, Spacing.sm)
                visibilityLines
                    .padding(.horizontal, Spacing.md)
                buttons
                    .padding(.horizontal, Spacing.md)
                    .padding(.vertical, Spacing.sm)
            }
        }
        .onAppear {
            if image == nil {
                image = UIImage(data: state.photo.jpeg)
            }
            // Une annonce, une fois, quand l'aperçu apparaît (calme, sans son ni vibration).
            UpdateAnnouncer.post(L10n.string("update_preview_taken_a11y"))
        }
    }

    // MARK: - La photo

    private var photoFrame: some View {
        GeometryReader { proxy in
            let fitted = UpdatePhotoFormat.fitInside(
                width: state.photo.width,
                height: state.photo.height,
                frameWidth: Int(proxy.size.width),
                frameHeight: Int(proxy.size.height)
            )
            ZStack(alignment: .topLeading) {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .frame(width: CGFloat(fitted.width), height: CGFloat(fitted.height))
                        .accessibilityHidden(true)
                }
                // La croix fait ce que fait « Recommencer » (D22) : retour au viseur, photo oubliée, rien publié, rien demandé.
                RoundOverlayButton(systemName: "xmark", labelKey: "update_close_a11y", onTap: onRetake)
                    .padding(Spacing.xs)
                if let notice = state.notice {
                    VStack {
                        Spacer(minLength: 0)
                        UpdateNoticeCard(
                            notice: notice, canRetry: !state.publishing, onRetry: onPublish, actions: actions
                        )
                        .padding(Spacing.sm)
                    }
                }
                if state.publishing {
                    PublishingVeil()
                }
            }
            .frame(width: CGFloat(fitted.width), height: CGFloat(fitted.height))
            .clipShape(RoundedRectangle(cornerRadius: UpdateLayout.previewCorner, style: .continuous))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    // MARK: - Lignes sous la photo

    private var visibilityLines: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(verbatim: visibilityText)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorUpdateOnPhoto.color)
                .frame(maxWidth: .infinity, alignment: .leading)
            // La photo part TOUJOURS avec la position (décision du PM du 2026-11-06) : dit simplement et sobrement, aucune option « sans lieu ».
            HStack(alignment: .top, spacing: Spacing.xs) {
                Image(systemName: "mappin.and.ellipse")
                    .font(.system(size: 14, weight: .regular))
                    .foregroundColor(colorUpdateOnPhoto.color)
                    .accessibilityHidden(true)
                Text("update_preview_place")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorUpdateOnPhoto.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var visibilityText: String {
        miffCount >= 2
            ? L10n.format("update_preview_visibility_many", miffCount)
            : L10n.string("update_preview_visibility")
    }

    private var buttons: some View {
        HStack(spacing: Spacing.sm) {
            Spacer(minLength: 0)
            RetakeButton(enabled: !state.publishing, onTap: onRetake)
            PublishButton(enabled: state.canPublish, onTap: onPublish)
        }
    }
}

/// Le voile posé sur la photo pendant l'envoi : rien dessous ne répond (ni second toucher, ni annulation une fois partie).
private struct PublishingVeil: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            colorUpdateVeil.color
            HStack(spacing: Spacing.sm) {
                // « Réduire les animations » : l'indicateur est remplacé par un point fixe, le texte dit déjà ce qui se passe.
                if reduceMotion {
                    Image(systemName: "ellipsis")
                        .foregroundColor(colorActionPrimary.color)
                        .accessibilityHidden(true)
                } else {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(colorActionPrimary.color)
                        .accessibilityHidden(true)
                }
                Text("update_publishing")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm + Spacing.xs)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(colorBgSurface.color))
            .accessibilityElement(children: .combine)
        }
        .contentShape(Rectangle())
        .onTapGesture {}
        .onAppear {
            UpdateAnnouncer.post(L10n.string("update_publishing"))
        }
    }
}

/// Une carte calme sur la photo : ce qui ne s'est pas passé, en mots simples (jamais « erreur »), la photo est toujours là, et une suite
/// quand il y en a une.
private struct UpdateNoticeCard: View {
    let notice: PreviewNotice
    let canRetry: Bool
    let onRetry: () -> Void
    let actions: UpdateActions

    private var text: String {
        if case .partial(let shared, let total) = notice {
            return L10n.format(notice.textKey, shared, total)
        }
        return L10n.string(notice.textKey)
    }

    private var iconName: String {
        switch notice {
        case .offline:
            return "icloud.slash"
        case .positionNotFound, .locationOff:
            return "mappin.and.ellipse"
        default:
            return "clock"
        }
    }

    /// La suite proposée : une action quand il y en a une (clé du catalogue et geste), sinon rien.
    private var action: UpdatePopIn.Action? {
        switch notice {
        case .needsAgreement:
            return UpdatePopIn.Action(titleKey: "update_action_review_agreement", run: actions.onReviewAgreement)
        case .sharingStopped:
            return UpdatePopIn.Action(titleKey: "update_action_resume_sharing", run: actions.onResumeSharing)
        case .locationOff:
            return UpdatePopIn.Action(titleKey: "update_action_open_settings", run: actions.onOpenSettings)
        case .unconfirmed:
            return UpdatePopIn.Action(titleKey: "update_action_open_feed", run: actions.onClose)
        case .stale, .tooMany:
            return nil
        default:
            return canRetry ? UpdatePopIn.Action(titleKey: "update_action_retry", run: onRetry) : nil
        }
    }

    var body: some View {
        VStack(alignment: .trailing, spacing: Spacing.xs) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: iconName)
                    .font(.system(size: 18, weight: .regular))
                    .foregroundColor(colorTextPrimary.color)
                    .accessibilityHidden(true)
                Text(verbatim: text)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if let action {
                Button {
                    action.run()
                } label: {
                    Text(verbatim: L10n.string(action.titleKey))
                        .miffTextStyle(textBodyBold14)
                        .foregroundColor(colorActionPrimary.color)
                        .frame(minHeight: Spacing.minTouchTarget)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(colorBgSurface.color))
        .accessibilityElement(children: .contain)
        .onAppear {
            UpdateAnnouncer.post(text)
        }
    }
}
