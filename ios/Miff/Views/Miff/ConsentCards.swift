import SwiftUI

// Le texte du consentement, version « 2026-11-v03 » (`MiffSharingConsent.version` ; maquettes 21 et 24 de
// design/exports/lieux/claude, docs/legal/consentement-localisation-v3.md). UN seul texte pour l'entrée (créer, rejoindre), la
// reprise du partage et l'écran de renouvellement : une ligne changée ici l'est pour tous (et dans le catalogue d'Android, clés
// `miff_consent3_*`). Rien dans ce fichier ne lit ni n'écrit quoi que ce soit.

/// Une ligne du consentement : un pictogramme bleu, un intitulé en gras, la phrase du texte. Lue d'un bloc par le lecteur
/// d'écran : l'intitulé d'abord, puis la phrase.
struct ConsentLine: View {
    let systemImage: String
    let titleKey: String
    let bodyKey: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.lg)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: L10n.string(titleKey))
                    .miffTextStyle(textBodyBold14)
                    .foregroundColor(colorTextPrimary.color)
                Text(verbatim: L10n.string(bodyKey))
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }
}

private struct ConsentDivider: View {
    var body: some View {
        Rectangle()
            .fill(colorBorderDefault.color)
            .frame(height: strokeHairline)
            .accessibilityHidden(true)
    }
}

/// La carte à contour bleu qui tient les lignes (le contour `border/action` des cartes de consentement du flux).
private struct ConsentFrame<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
    }
}

/// Les lignes que la carte d'entrée et l'écran de renouvellement ont en commun.
private struct ConsentWhoSeesLines: View {
    var body: some View {
        ConsentLine(systemImage: "eye", titleKey: "miff_consent3_who_title", bodyKey: "miff_consent3_who_body")
        ConsentDivider()
        ConsentLine(systemImage: "iphone", titleKey: "miff_consent3_closed_title", bodyKey: "miff_consent3_closed_body")
        ConsentDivider()
        ConsentLine(systemImage: "trash", titleKey: "miff_consent3_erase_title", bodyKey: "miff_consent3_erase_body")
        ConsentDivider()
        ConsentLine(
            systemImage: "person.2",
            titleKey: "miff_consent3_members_title",
            bodyKey: "miff_consent3_members_body"
        )
        ConsentDivider()
        ConsentLine(
            systemImage: "smallcircle.filled.circle",
            titleKey: "miff_consent3_places_title",
            bodyKey: "miff_consent3_places_body"
        )
    }
}

/// Le consentement montré avant « Créer et partager », « Rejoindre et partager » et dans la feuille « Reprendre le partage »
/// (maquette 24) : le titre, la phrase qui vaut accord (`sentence`), les lignes du texte, et la note sur la permission du
/// téléphone (option A du PM : plus de « Tu peux le refuser », Miff a besoin de « Toujours »). Visible SANS action, avant le bouton.
struct SharingConsentCard: View {
    let sentence: String

    var body: some View {
        ConsentFrame {
            Text("miff_consent3_title")
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextPrimary.color)
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: sentence)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
                .fixedSize(horizontal: false, vertical: true)
            ConsentLine(
                systemImage: "mappin.and.ellipse",
                titleKey: "miff_consent3_kept_title",
                bodyKey: "miff_consent3_kept_body"
            )
            ConsentDivider()
            ConsentWhoSeesLines()
            Text("miff_consent3_system_note")
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// La carte de l'écran de renouvellement (maquette 21) : les mêmes lignes, sans « Ta dernière position connue » (le chapeau de
/// l'écran la dit).
struct RenewalDetailsCard: View {
    var body: some View {
        ConsentFrame {
            ConsentWhoSeesLines()
        }
    }
}
