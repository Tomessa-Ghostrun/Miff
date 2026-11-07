import SwiftUI

/// Le champ qui a le focus dans les écrans du flux Miff.
enum MiffField: Hashable {
    case name
    case code
}

/// Le bouton principal dessiné « à la main » pour les endroits où ce n'est pas un `Button` (le lien de partage du système) :
/// même fond, même ombre, même texte que `MiffPrimaryButtonStyle`.
struct MiffPrimaryLabel: View {
    let key: LocalizedStringKey

    var body: some View {
        Text(key)
            .miffTextStyle(textBodyLarge)
            .foregroundColor(colorTextOnAction.color)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .fill(colorActionPrimary.color)
                    .miffShadow(shadowDefault)
            )
            .padding(.vertical, Spacing.xs)
    }
}

/// « Choisis comment tu veux commencer » (maquette originale « Welcoming screen -3 ») : deux grandes cartes, « Créer une nouvelle
/// Miff » et « Rejoindre une Miff existante ». Le champ du code de la carte d'origine est passé sur sa propre page (un seul
/// champ de code, ticket §4 n° 9). Pas de flèche de retour : le compte vient d'être créé (décision Q11 du ticket
/// Authentification) ; un lien discret « Se déconnecter » est la sortie.
struct MiffChoiceView: View {
    let onCreate: () -> Void
    let onJoin: () -> Void
    let onSignOut: () -> Void

    var body: some View {
        MiffFrame(title: nil, onBack: nil) {
            VStack(spacing: Spacing.md) {
                MiffWordmark()
                    .frame(width: Spacing.logoWidth / 2)
                    .padding(.top, Spacing.sm)
                Text("miff_choice_title")
                    .miffTextStyle(textDisplayHeadline)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.center)
                    .padding(.vertical, Spacing.md)
                    .accessibilityAddTraits(.isHeader)
                MiffChoiceCard(
                    systemImage: "plus",
                    title: L10n.string("miff_choice_create_title"),
                    detail: L10n.string("miff_choice_create_body"),
                    action: onCreate
                )
                MiffChoiceCard(
                    systemImage: "person.2",
                    title: L10n.string("miff_choice_join_title"),
                    detail: L10n.string("miff_choice_join_body"),
                    action: onJoin
                )
                Button(action: onSignOut) {
                    Text("auth_signout_action")
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorActionPrimary.color)
                        .frame(minHeight: Spacing.minTouchTarget)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }
}

private struct MiffChoiceCard: View {
    let systemImage: String
    let title: String
    let detail: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                Image(systemName: systemImage)
                    .font(.system(size: 24, weight: .regular))
                    .foregroundColor(colorActionPrimary.color)
                    .frame(width: Spacing.minTouchTarget + Spacing.sm, height: Spacing.minTouchTarget + Spacing.sm)
                    .background(Circle().fill(colorBgSurface.color))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(verbatim: title)
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorActionPrimary.color)
                    Text(verbatim: detail)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextForm.color)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            }
            .padding(Spacing.md)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint20.color)
            )
            .miffShadow(shadowDefault)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

/// Le message unique quand une Miff n'est plus accessible (décision QM8) : le même pour un retrait et pour une suppression,
/// calme, sans cause, sans reproche. Montré une fois, puis l'app continue avec une autre Miff ou l'accueil sans Miff. Il couvre
/// tout l'écran : rien de cette Miff ne reste visible derrière.
struct AccessLostView: View {
    let miffName: String?
    let onAcknowledge: () -> Void

    private var title: String {
        if let miffName {
            return L10n.format("miff_lost_title", miffName)
        }
        return L10n.string("miff_lost_title_unknown")
    }

    var body: some View {
        CenteredFrame {
            MiffWordmark()
                .frame(width: Spacing.logoWidth / 2)
            Text(verbatim: title)
                .miffTextStyle(textDisplayHeadline)
                .foregroundColor(colorTextForm.color)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text("miff_lost_body")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
            Button("miff_lost_ok_action", action: onAcknowledge)
                .buttonStyle(.miffPrimaryFullWidth)
        }
        .accessibilityAddTraits(.isModal)
        .onAppear {
            Announcer.announce(title + " " + L10n.string("miff_lost_body"))
        }
    }
}
