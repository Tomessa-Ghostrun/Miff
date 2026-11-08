import SwiftUI

// L'en-tête du Feed (maquette 01) : MON avatar avec l'anneau de MON statut (il ouvre l'onglet Profil), le logo (il ramène le fil en
// haut) et, dessous, la pastille de la Miff. La cloche du centre de notifications est en fin de rangée (NOTIF-6I) : un simple point
// quand il y a du non lu, jamais un chiffre ; sans Miff (rien à notifier) les 44 pt restent vides. Il se masque quand on descend dans
// la liste et revient dès qu'on remonte (D41) ; l'écran lui donne son déplacement.

struct FeedHeaderContent: View {
    let me: FeedMe
    let myLevel: StatusLevel
    let myStatusDescription: String?
    let pill: FeedPillState?
    /// La cloche : montrée seulement avec au moins une Miff ; `unreadNotifications` > 0 allume le point.
    let showsBell: Bool
    let unreadNotifications: Int
    let onOpenNotifications: () -> Void
    let onOpenProfile: () -> Void
    let onLogo: () -> Void
    let onSelect: (String) -> Void
    let onCreate: () -> Void
    let onJoin: () -> Void
    @Binding var selectorOpen: Bool

    private var profileLabel: Text {
        if let description = myStatusDescription {
            return Text(verbatim: L10n.format("feed_header_avatar_a11y", description))
        }
        return Text("feed_header_avatar_plain_a11y")
    }

    var body: some View {
        VStack(spacing: Spacing.sm) {
            ZStack {
                HStack {
                    Button(action: onOpenProfile) {
                        FeedAvatar(jpeg: me.avatarJpeg, initials: me.initials, level: myLevel)
                            .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                            .contentShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(profileLabel)
                    Spacer(minLength: 0)
                    if showsBell {
                        NotificationBell(unreadCount: unreadNotifications, action: onOpenNotifications)
                    } else {
                        Color.clear.frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                            .accessibilityHidden(true)
                    }
                }
                Button(action: onLogo) {
                    MiffWordmark()
                        .frame(width: Spacing.logoWidth / 2)
                        .frame(minHeight: Spacing.minTouchTarget)
                        .contentShape(Rectangle())
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text("home_logo_a11y"))
                        .accessibilityHint(Text("feed_header_logo_a11y"))
                }
                .buttonStyle(.plain)
            }
            if let pill {
                FeedPill(
                    state: pill,
                    selectorOpen: $selectorOpen,
                    onSelect: onSelect,
                    onCreate: onCreate,
                    onJoin: onJoin
                )
            }
        }
        .padding(.horizontal, Spacing.md)
        .padding(.bottom, Spacing.sm)
    }
}

/// Ce que dit la pastille de la Miff.
enum FeedPillState: Equatable {
    /// Aucune Miff : « Pas encore de Miff », ne fait rien.
    case noMiff
    /// Une Miff (compte gratuit) : le nom, à plat, non touchable.
    case plain(name: String)
    /// Plusieurs Miffs possibles (Premium, ou formule inconnue) : le nom, une flèche, une liste.
    case selectable(name: String, miffs: [MiffSummary], activeId: String?)
}

/// La pastille de la Miff. Avec UNE Miff elle est plate, sans ombre ni flèche, annoncée comme du texte (pas un bouton) : elle ne fait
/// rien et ne doit pas faire croire qu'elle le fait. Avec plusieurs (Premium) elle a l'ombre et la flèche bleue de la maquette et
/// ouvre la liste (une feuille d'actions du système : « Rejoindre » et « Créer » y sont réservés au Premium).
struct FeedPill: View {
    let state: FeedPillState
    @Binding var selectorOpen: Bool
    let onSelect: (String) -> Void
    let onCreate: () -> Void
    let onJoin: () -> Void

    var body: some View {
        switch state {
        case .noMiff:
            flat(text: L10n.string("feed_selector_none"))
        case .plain(let name):
            flat(text: name)
        case .selectable(let name, let miffs, _):
            Button {
                selectorOpen = true
            } label: {
                HStack(spacing: Spacing.sm) {
                    Text(verbatim: name)
                        .miffTextStyle(textBodyItalic)
                        .foregroundColor(colorTextForm.color)
                    Image(systemName: "arrowtriangle.down.fill")
                        .font(.system(size: 10))
                        .foregroundColor(colorActionPrimary.color)
                        .accessibilityHidden(true)
                }
                .padding(.horizontal, Spacing.md)
                .frame(minHeight: Spacing.minTouchTarget)
                .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
                .miffShadow(shadowDefault)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(verbatim: L10n.format("feed_selector_a11y", name)))
            .confirmationDialog(Text(verbatim: name), isPresented: $selectorOpen, titleVisibility: .visible) {
                ForEach(miffs, id: \.id) { miff in
                    Button {
                        onSelect(miff.id)
                    } label: {
                        Text(verbatim: miff.name)
                    }
                }
                Button("feed_selector_join", action: onJoin)
                Button("feed_selector_create", action: onCreate)
            }
        }
    }

    private func flat(text: String) -> some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyItalic)
            .foregroundColor(colorTextForm.color)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: 40)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(Color.black.opacity(0.05)))
    }
}
