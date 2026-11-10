import SwiftUI
import UIKit

// La photo de la dernière update sur la carte d'un membre, la mienne comprise (FEED-8 ; maquettes 06 et 09, décision D39 du PM, 2026-11-06 : un CARRÉ
// aussi large que la carte, CENTRÉ sur le milieu de la photo, recadré sans déformer ; la photo reste COMPLÈTE en plein écran au toucher).
// CONFIDENTIALITÉ, COÛT et SÉCURITÉ, pour ce fichier :
// - la photo n'est lue QUE quand la carte vient à l'écran (la liste est paresseuse : la tâche de `loadsPhoto` démarre à l'apparition de la carte et
//   s'arrête à sa disparition), seulement pendant que l'onglet Feed est en usage, et seulement quand le statut dit qu'il y a une photo
//   (`PhotoSlot.candidate`) ; une photo déjà lue n'est pas relue ;
// - rien n'est écrit ni supprimé d'ici, JAMAIS : un refus des règles se dit par une phrase calme (« Partage ta position pour voir cette photo. »),
//   jamais « erreur », jamais pris pour une photo expirée ;
// - une photo absente, expirée, illisible ou hors connexion ne dessine RIEN (ni trou ni message) ;
// - aucune coordonnée n'est dans un texte, une `description` ou un nom parlé : le lieu de la photo n'est donné qu'à Plans, au toucher de
//   « Voir où c'était » (plein écran), sans nom ni étiquette.

/// Où le Feed lit les photos, donné par l'onglet Feed. Absent (aperçus, tests) : aucune photo n'est dessinée.
private struct FeedPhotosKey: EnvironmentKey {
    static let defaultValue: FeedPhotos? = nil
}

extension EnvironmentValues {
    var feedPhotos: FeedPhotos? {
        get { self[FeedPhotosKey.self] }
        set { self[FeedPhotosKey.self] = newValue }
    }
}

/// La photo ouverte en plein écran. Ne garde que ce qui est à l'écran ; `description` ne montre rien.
struct OpenedPhoto: Identifiable, CustomStringConvertible {
    let id = UUID()
    let firstName: String
    let photo: SharedPhoto
    let image: UIImage

    var description: String {
        "OpenedPhoto(redacted)"
    }
}

/// Où et quand la photo d'une carte se lit.
struct PhotoSlotContext: Equatable {
    /// La Miff de la carte (la photo se lit dans cette Miff).
    var miffId = ""
    /// Le Feed dit « Dernières informations de… » : le passage à « en ligne » relance UNE lecture (jamais en boucle).
    var offline = false
    /// L'onglet Feed est en usage et l'app au premier plan : sinon rien n'est lu (détachement hors onglet).
    var active = false
}

// MARK: - La lecture, à l'apparition de la carte

private struct PhotoTaskKey: Equatable {
    let request: PhotoRequest?
    let context: PhotoSlotContext
}

private struct PhotoLoading: ViewModifier {
    let card: FeedMemberCard
    let context: PhotoSlotContext
    @Environment(\.feedPhotos) private var photos

    private var request: PhotoRequest? {
        guard case .candidate(let lastUpdate) = card.photo else {
            return nil
        }
        return PhotoRequest(
            miffId: context.miffId, uid: card.member.uid, lastUpdateAtMillis: lastUpdate, isMe: card.isMe
        )
    }

    func body(content: Content) -> some View {
        content.task(id: PhotoTaskKey(request: request, context: context)) {
            guard let reader = photos else {
                return
            }
            guard let wanted = request else {
                // Plus de photo candidate (statut changé, partage arrêté, plus de photo) : plus rien d'ancien ne peut être redessiné.
                reader.forget(miffId: context.miffId, uid: card.member.uid)
                return
            }
            // Rien n'est lu hors du Feed en usage, ni pendant « Dernières informations de… » (la relance se fait au retour du réseau).
            guard context.active, !context.offline else {
                return
            }
            await reader.model(for: wanted).load(wanted)
        }
    }
}

extension View {
    /// Lit la photo de cette carte quand elle vient à l'écran (et seulement alors) : UNE lecture, jamais en boucle.
    func loadsPhoto(of card: FeedMemberCard, in context: PhotoSlotContext) -> some View {
        modifier(PhotoLoading(card: card, context: context))
    }
}

// MARK: - L'emplacement de la photo

/// L'emplacement de la photo d'une carte : le cadre, la photo, la phrase de réciprocité, ou rien.
struct MemberPhotoSlot: View {
    let card: FeedMemberCard
    let context: PhotoSlotContext
    let texts: FeedTexts
    let onOpen: (OpenedPhoto) -> Void
    @Environment(\.feedPhotos) private var photos

    var body: some View {
        if case .candidate(let lastUpdate) = card.photo, let photos {
            let request = PhotoRequest(
                miffId: context.miffId, uid: card.member.uid, lastUpdateAtMillis: lastUpdate, isMe: card.isMe
            )
            PhotoSlotContent(
                photos: photos,
                model: photos.model(for: request),
                texts: texts,
                firstName: card.member.callName,
                onOpen: onOpen
            )
        }
    }
}

private struct PhotoSlotContent: View {
    let photos: FeedPhotos
    @ObservedObject var model: PhotoSlotViewModel
    let texts: FeedTexts
    let firstName: String
    let onOpen: (OpenedPhoto) -> Void

    @ViewBuilder
    var body: some View {
        switch model.state {
        case .loading, .nothing:
            // Pendant la lecture comme sans photo : RIEN (aucune réserve d'espace, aucune apparition qui décale la liste avant l'arrivée de la photo).
            EmptyView()
        case .needsOwnPosition:
            NeedsPositionTile()
        case .shown(let photo):
            let now = photos.nowMillis()
            // Une photo expirée n'est jamais montrée, même depuis la mémoire.
            if !photo.isExpired(nowMillis: now), let image = model.image {
                PhotoWindow(
                    image: image,
                    caption: caption(photo, now: now),
                    opening: L10n.format("update_card_photo_open_a11y", firstName),
                    onOpen: { open(photo) }
                )
            }
        }
    }

    /// Le plein écran reçoit la photo ENTIÈRE (décodée à l'ouverture), jamais l'aperçu recadré.
    private func open(_ photo: SharedPhoto) {
        guard let full = model.fullImage() else {
            return
        }
        onOpen(OpenedPhoto(firstName: firstName, photo: photo, image: full))
    }

    /// « Photo de 15 h 40 » : l'heure de publication de la photo.
    private func caption(_ photo: SharedPhoto, now: Int) -> String {
        let moment = Moment.of(atMillis: photo.createdAtMillis, nowMillis: now, zone: photos.zone())
        return L10n.format("update_card_photo_label", texts.from(moment))
    }
}

/// L'aperçu : un CARRÉ aussi large que la carte, centré sur le milieu de la photo (recadrage sans déformation), la légende en bas à gauche.
private struct PhotoWindow: View {
    let image: UIImage
    let caption: String
    let opening: String
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            Color.clear
                .aspectRatio(1, contentMode: .fit)
                .overlay {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                }
                .overlay(alignment: .bottomLeading) {
                    Text(verbatim: caption)
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextPrimary.color)
                        .padding(.horizontal, Spacing.sm)
                        .padding(.vertical, Spacing.xs)
                        .background(
                            RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(colorBgSurface.color)
                        )
                        .padding(Spacing.sm)
                }
                .clipShape(RoundedRectangle(cornerRadius: radiusSm, style: .continuous))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(opening). \(caption)"))
        .accessibilityAddTraits(.isButton)
    }
}

/// « Partage ta position pour voir cette photo. » : une tuile bleu pâle à bord pointillé et un cadenas (maquette 09, C), jamais « erreur ».
private struct NeedsPositionTile: View {
    private static let minimumHeight: CGFloat = 108

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "lock")
                .font(.system(size: 20))
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            Text("update_card_photo_needs_position")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, minHeight: Self.minimumHeight)
        .background(RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(colorActionPrimaryTint5.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                .strokeBorder(
                    colorBorderAction.withAlpha(0.4).color,
                    style: StrokeStyle(lineWidth: strokeHairline, dash: [4, 3])
                )
        )
        .accessibilityElement(children: .combine)
    }
}
