import SwiftUI

// La carte d'un membre (maquette 06 et le « Welcoming screen » du PM) : un liseré de la couleur du niveau à gauche, l'avatar et son
// anneau, le prénom, la batterie, l'étiquette de l'état, la ligne de temps, et les trois gestes. MA carte est la DERNIÈRE de la
// liste et n'a aucun geste. La couleur est le seul signal du niveau (liseré, anneau, étiquette : pas de pictogramme, décision du PM,
// 2026-11-06) ; les MOTS de l'état restent et sont lus. Pour le lecteur d'écran, l'information est UN bloc (« Thomas, à la maison,
// depuis 12 min, batterie 85 % ») ; les gestes sont des boutons à part, après lui.

/// Ce que font les boutons d'une carte (le Feed n'écrit rien : il confie aux applications du système).
struct MemberCardActions {
    let onLocate: (String) -> Void
    let onMessage: (String) -> Void
    let onCall: (String) -> Void
    /// FEED-8 : toucher l'aperçu de la photo l'ouvre en plein écran (la photo COMPLÈTE).
    var onOpenPhoto: (OpenedPhoto) -> Void = { _ in }
}

struct MemberCardView: View {
    let card: FeedMemberCard
    let texts: FeedTexts
    let locating: Bool
    let actions: MemberCardActions
    /// FEED-8 : où et quand la photo de la carte se lit.
    var photoContext = PhotoSlotContext()

    private static let stripWidth: CGFloat = 5

    var body: some View {
        HStack(spacing: 0) {
            Rectangle()
                .fill(card.level.ring)
                .frame(width: Self.stripWidth)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                information
                // FEED-8 : la photo de la dernière update (un carré centré ; la photo entière en plein écran). Rien quand il n'y en a pas.
                MemberPhotoSlot(card: card, context: photoContext, texts: texts, onOpen: actions.onOpenPhoto)
                CardActionRow(card: card, locating: locating, actions: actions)
            }
            .padding(.leading, Spacing.md - Spacing.xs)
            .padding(.trailing, Spacing.sm + Spacing.xs)
            .padding(.vertical, Spacing.sm + Spacing.xs)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(
            RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                .fill(colorBgSurface.color)
                .miffShadow(shadowSubtle)
        )
        .clipShape(RoundedRectangle(cornerRadius: radiusSm, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
        // FEED-8 : la photo se lit quand la carte vient à l'écran, jamais avant, et s'arrête quand elle disparaît.
        .loadsPhoto(of: card, in: photoContext)
    }

    /// UN bloc pour le lecteur d'écran, lu dans l'ordre du ticket (T2) : la personne, l'état, le temps, la batterie. Décision D42 :
    /// aucune mention « Toi » à côté du prénom ; ma carte se reconnaît à sa ligne « Ce que tes proches voient de toi », lue dans le
    /// même bloc.
    private var information: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.sm + Spacing.xs) {
                FeedAvatar(
                    jpeg: card.member.avatarJpeg,
                    initials: Initials.of(first: card.member.firstName, last: card.member.lastName),
                    level: card.level
                )
                Text(verbatim: card.member.callName)
                    .miffTextStyle(textDisplayLabel)
                    .foregroundColor(colorTextPrimary.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let battery = card.battery {
                    BatteryChip(text: texts.battery(battery))
                }
            }
            StateChip(text: texts.label(card.label), emoji: card.labelCategory?.emoji, level: card.level)
            if card.limitedLocation {
                LimitedLocationBadge()
            }
            if let line = texts.timeLine(card.time) {
                Text(verbatim: line)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
            }
            if card.isMe {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "eye")
                        .font(.system(size: 14))
                        .foregroundColor(colorTextDescription.color)
                    Text("feed_card_you_sees")
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextDescription.color)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: texts.cardSpoken(card)))
    }
}

/// La batterie : « 85 % » seul, jamais l'heure de la mesure (D44).
private struct BatteryChip: View {
    let text: String

    var body: some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: "battery.75")
                .font(.system(size: 13))
            Text(verbatim: text)
                .miffTextStyle(textCaptionBold12)
        }
        .foregroundColor(colorTextDescription.color)
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs / 2)
        .background(RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(colorBorderDefault.color))
    }
}

/// L'étiquette de l'état : ses mots sur la teinte du niveau. La couleur est le seul signal, aucun pictogramme.
private struct StateChip: View {
    let text: String
    /// L'emoji de la catégorie du lieu, avant les mots (étiquette « dans un lieu de confiance » seulement) ; décoratif.
    var emoji: String?
    let level: StatusLevel

    var body: some View {
        // Un nom de lieu (Loisir, Autre) peut être long (jusqu'à 40 signes) : deux lignes au plus, la fin coupée ensuite ; le lecteur
        // d'écran lit toujours la carte en entier (`cardSpoken`, sans l'emoji).
        HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
            if let emoji {
                // Même style que les mots : l'emoji suit la taille du texte de l'étiquette (pas de pastille de taille fixe ici).
                Text(verbatim: emoji)
                    .miffTextStyle(textBodyRegular14)
                    .accessibilityHidden(true)
            }
            Text(verbatim: text)
                .miffTextStyle(textBodyRegular14)
                .lineLimit(2)
        }
        .foregroundColor(level.onTint)
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .background(RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(level.tint))
    }
}

/// « Localisation limitée » : un badge doux, sans effet sur la bulle (il garde son pictogramme, ce n'est pas une étiquette d'état).
private struct LimitedLocationBadge: View {
    var body: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "mappin.slash")
                .font(.system(size: 14))
            Text("feed_label_limited_location")
                .miffTextStyle(textBodyRegular14)
        }
        .foregroundColor(colorActionPrimary.color)
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .background(RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(colorActionPrimaryTint10.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeHairline)
        )
    }
}

/// Localiser, Message, Appeler : trois boutons de 44 pt en pictogrammes (comme la maquette du PM), chacun nommé pour le lecteur
/// d'écran. Aucun sur MA carte. « Écrire à » et « Appeler » n'existent qu'avec un numéro ; « Localiser » que si la carte dit qu'elle
/// peut être localisée.
private struct CardActionRow: View {
    let card: FeedMemberCard
    let locating: Bool
    let actions: MemberCardActions

    var body: some View {
        if !card.isMe && (card.canLocate || card.phone != nil) {
            HStack(spacing: 0) {
                Spacer(minLength: 0)
                if card.canLocate {
                    locateButton
                }
                if let phone = card.phone {
                    iconButton(symbol: "message", key: "feed_card_message_a11y") { actions.onMessage(phone) }
                    iconButton(symbol: "phone", key: "feed_card_call_a11y") { actions.onCall(phone) }
                }
            }
        }
    }

    @ViewBuilder
    private var locateButton: some View {
        if locating {
            ProgressView()
                .tint(colorActionPrimary.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: L10n.format("feed_card_locating_a11y", card.member.callName)))
        } else {
            iconButton(symbol: "mappin.and.ellipse", key: "feed_card_locate_a11y") { actions.onLocate(card.member.uid) }
        }
    }

    private func iconButton(symbol: String, key: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 22))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: L10n.format(key, card.member.callName)))
    }
}
