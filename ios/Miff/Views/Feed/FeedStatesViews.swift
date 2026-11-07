import SwiftUI

// Les états du Feed qui ne sont pas la liste : le squelette, « Pas encore de Miff », les mentions au-dessus de la bulle (hors connexion,
// publication en cours), « Cette Miff est bien remplie ! ». Tous calmes : jamais une phrase de sécurité sans savoir, jamais une
// alarme.

/// Une mention au-dessus de la bulle : grise pour « pas de connexion » (aucune teinte d'erreur), bleu pâle pour « Partage de ton statut
/// en cours… ». Annoncée au lecteur d'écran à son apparition.
struct FeedBannerView: View {
    let banner: FeedBanner
    let texts: FeedTexts

    var body: some View {
        switch banner {
        case .offline(let latest):
            HStack(spacing: Spacing.sm) {
                Image(systemName: "wifi.slash")
                    .font(.system(size: 18))
                    .foregroundColor(colorTextPrimary.color)
                    .accessibilityHidden(true)
                Text(verbatim: texts.offlineBanner(latest))
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(Spacing.md)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderStrong.color, lineWidth: strokeHairline)
            )
        case .publishing:
            HStack(spacing: Spacing.sm) {
                // Décoratif : le texte porte le sens, et ce n'est jamais une erreur.
                ProgressView()
                    .tint(colorActionPrimary.color)
                    .accessibilityHidden(true)
                Text("feed_banner_publishing")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(Spacing.md)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint10.color)
            )
        }
    }
}

/// « Cette Miff est bien remplie ! » : une note verte qui n'est pas un bouton et ne ressemble pas à un bouton grisé.
struct FeedFullNote: View {
    var body: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "person.2")
                .font(.system(size: 18))
                .accessibilityHidden(true)
            Text("feed_miff_full")
                .miffTextStyle(textBodyBold14)
        }
        .foregroundColor(colorStatusSafeText.color)
        .frame(maxWidth: .infinity)
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorStatusSafeTint.color))
    }
}

/// Le squelette : des formes grises là où seront la bulle et deux cartes. Aucune couleur de statut, aucune phrase de sécurité, aucune
/// animation ; un mot calme dessous dit l'attente.
struct FeedSkeleton: View {
    var body: some View {
        VStack(spacing: Spacing.sm + Spacing.xs + 2) {
            Text("feed_loading_news")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            Capsule()
                .fill(colorBgPlaceholder.color.opacity(0.45))
                .frame(height: 176)
            ForEach(0..<2, id: \.self) { _ in
                SkeletonCard()
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("feed_loading_a11y"))
    }
}

private struct SkeletonCard: View {
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Circle()
                .fill(colorBgPlaceholder.color.opacity(0.65))
                .frame(width: Spacing.memberAvatar, height: Spacing.memberAvatar)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .fill(colorBgPlaceholder.color.opacity(0.65))
                    .frame(width: 120, height: 16)
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .fill(colorBgPlaceholder.color.opacity(0.65))
                    .frame(width: 180, height: 24)
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBgPlaceholder.color, lineWidth: strokeHairline)
        )
    }
}

/// « Pas encore de Miff » : une phrase calme et les deux façons de commencer (le filet de sécurité : normalement la personne n'est
/// jamais ici). Aucune phrase de sécurité.
struct FeedNoMiff: View {
    let onCreate: () -> Void
    let onJoin: () -> Void
    let onSignOut: () -> Void

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Text("feed_nomiff_body")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .padding(.vertical, Spacing.md)
            Button(action: onCreate) {
                Text("feed_create_action")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            Button(action: onJoin) {
                Text("feed_join_action")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            // Sans barre ni Profil (donc sans Réglages) la déconnexion reste ici : la seule sortie de cet écran.
            Button(action: onSignOut) {
                Text("auth_signout_action")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorActionPrimary.color)
                    .frame(minHeight: Spacing.minTouchTarget)
            }
        }
        .padding(.horizontal, Spacing.md)
    }
}

/// Un message calme de l'action qui n'a pas abouti (« Cette action n'est pas disponible sur cet appareil. ») : un petit encadré qui
/// disparaît seul, jamais une alerte.
struct FeedActionNotice: View {
    var body: some View {
        Text("feed_action_unavailable")
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
                MiffNoticeAnnouncements.announceOnce(L10n.string("feed_action_unavailable"))
            }
    }
}
