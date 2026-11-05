import SwiftUI

/// Un proche : photo ronde (ou initiales), prénom et nom, puis les étiquettes « Créateur » et « Toi ». Pour le créateur, sur la
/// ligne des AUTRES proches seulement, un bouton « ⋯ » ouvre un petit menu avec le seul choix de cette version, « Retirer de la
/// Miff » (jamais une corbeille rouge : une confirmation nominative suit, ticket §3.3). Aucun anneau de couleur : cet écran ne
/// montre aucun statut (critère G7).
struct MiffMemberRow: View {
    let member: MiffMember
    let isMe: Bool
    /// « Retirer de la Miff » : donné au créateur, sur la ligne des autres proches seulement (jamais sur sa propre ligne).
    var onRemove: (() -> Void)?
    var menuEnabled = true

    var body: some View {
        HStack(spacing: Spacing.sm) {
            person
            if let onRemove {
                MiffMemberMenu(callName: member.callName, isEnabled: menuEnabled, onRemove: onRemove)
            }
        }
        .padding(Spacing.sm + Spacing.xs)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
    }

    /// La personne (photo, nom, étiquettes) est UNE chose pour le lecteur d'écran ; le menu à côté reste un bouton à part.
    /// L'action « Retirer de la Miff » est aussi proposée comme action personnalisée de VoiceOver.
    @ViewBuilder
    private var person: some View {
        if let onRemove, menuEnabled {
            personContent
                .accessibilityAction(named: Text("miff_member_remove_action")) {
                    onRemove()
                }
        } else {
            personContent
        }
    }

    private var personContent: some View {
        HStack(spacing: Spacing.md) {
            MiffAvatar(jpeg: member.avatarJpeg, initials: Initials.of(first: member.firstName, last: member.lastName))
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(verbatim: member.displayName)
                    .miffTextStyle(textDisplayLabel)
                    .foregroundColor(colorTextPrimary.color)
                if member.role == .owner || isMe {
                    HStack(spacing: Spacing.xs) {
                        if member.role == .owner {
                            MiffRolePill(text: L10n.string("miff_role_owner"), isOwner: true)
                        }
                        if isMe {
                            MiffRolePill(text: L10n.string("miff_role_you"), isOwner: false)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}

/// Le « ⋯ » d'une ligne : un bouton de 44 pt qui ouvre un petit menu avec un seul choix, « Retirer de la Miff ». Annoncé par
/// VoiceOver « Actions pour {prénom} ».
private struct MiffMemberMenu: View {
    let callName: String
    let isEnabled: Bool
    let onRemove: () -> Void

    var body: some View {
        Menu {
            Button {
                onRemove()
            } label: {
                Text("miff_member_remove_action")
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor((isEnabled ? colorTextPrimary : colorTextDescription).color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .contentShape(Rectangle())
        }
        .disabled(!isEnabled)
        .accessibilityLabel(Text(verbatim: L10n.format("miff_member_menu_a11y", callName)))
    }
}
