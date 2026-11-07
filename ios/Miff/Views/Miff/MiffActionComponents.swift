import SwiftUI

// Les briques de MIFF-4 sur l'écran d'une Miff : le lien qui ressemble à un lien, l'invitation discrète à renouveler le code
// après un départ, le pied d'écran (« Quitter la Miff » pour un membre, « Supprimer la Miff » pour le créateur).

/// Une ligne de texte qui agit comme un lien, de 44 pt de haut au moins : « Créer un nouveau code », « Quitter la Miff »,
/// « Supprimer la Miff », « Plus tard ». Grisée à 50 % quand elle est désactivée.
struct MiffLinkButton: View {
    let title: String
    let action: () -> Void
    var isEnabled = true
    var color = colorActionPrimary

    var body: some View {
        Button(action: action) {
            Text(verbatim: title)
                .miffTextStyle(textBodyBold16)
                .foregroundColor(color.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.5)
    }
}

/// « Un proche a quitté la Miff. Tu veux créer un nouveau code ? C'est facultatif. » avec « Plus tard » (montrée une seule fois
/// par départ, sans insister).
struct MiffRenewInvitation: View {
    let isEnabled: Bool
    let onRenew: () -> Void
    let onLater: () -> Void

    var body: some View {
        VStack(spacing: Spacing.xs) {
            MiffNotice(text: L10n.string("miff_renew_invite"))
            Button(action: onRenew) {
                Text("miff_renew_action")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!isEnabled)
            MiffLinkButton(title: L10n.string("miff_renew_invite_later"), action: onLater)
        }
    }
}

/// Le bas de l'écran. Le créateur n'a pas de « Quitter » (décision QM1) : une phrase dit ce qu'il peut faire à la place, et
/// « Supprimer la Miff » est un lien discret de la couleur des erreurs (pas de fond rouge, pas d'alarme). Un membre lit qu'il
/// peut partir à tout moment.
struct MiffFootSection: View {
    let state: MiffScreenState
    let actions: MiffScreenActions

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Text(verbatim: L10n.string(state.isOwner ? "miff_owner_foot" : "miff_leave_hint"))
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if state.isOwner {
                MiffLinkButton(
                    title: L10n.string("miff_delete_link"),
                    action: actions.onDeleteRequested,
                    isEnabled: state.canWrite,
                    color: colorTextError
                )
            } else {
                MiffLinkButton(
                    title: L10n.string("miff_leave_link"),
                    action: actions.onLeaveRequested,
                    isEnabled: state.canWrite,
                    color: colorTextError
                )
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// Ce que dit la banderole de la suppression. Sur iPhone il n'y a pas de message système court comme sur Android : une
/// banderole calme en haut de l'écran, qui s'efface seule au bout de quelques secondes.
enum DeletionBanner: Equatable {
    /// « On reprend la suppression de ta Miff… »
    case resumed
    /// « La Miff a été supprimée. »
    case done

    var text: String {
        L10n.string(self == .resumed ? "miff_delete_resume" : "miff_delete_done")
    }
}

struct DeletionBannerView: View {
    let banner: DeletionBanner

    var body: some View {
        VStack {
            MiffNotice(text: banner.text)
                .padding(.horizontal, Spacing.lg)
                .padding(.top, Spacing.sm)
            Spacer()
        }
        .transition(.opacity)
        .allowsHitTesting(false)
    }
}
