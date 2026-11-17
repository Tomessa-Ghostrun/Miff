import SwiftUI

// L'encart du compte à rebours (ticket §2.8) : un sablier à gauche, deux lignes (« Tu es en dehors de tes lieux de confiance » puis « Tu
// passeras orange dans … »), un chevron discret, fond blanc, coins `radius/md`, ombre `shadow/default` comme les cartes du Feed, et la bordure
// dynamique (piste grise + arc qui se vide). Un seul élément pour le lecteur d'écran, de rôle bouton. AUCUN rouge, AUCUN point d'exclamation.
// Le texte passe à la ligne (jamais coupé) avec les plus grandes tailles de texte : l'encart grandit, la bordure suit son contour réel.

struct CountdownCard: View {
    let display: CountdownDisplay
    let texts: CountdownTexts
    let onOpen: () -> Void
    /// Le sablier suit la taille du texte, sans dépasser 1,5 fois sa taille de base.
    @ScaledMetric(relativeTo: .callout) private var hourglassSize: CGFloat = 24

    private static let maxHourglassScale: CGFloat = 1.5
    private static let baseHourglassSize: CGFloat = 24

    /// La taille du sablier : celle du texte, sans dépasser 1,5 fois la taille de base.
    static func hourglassSize(scaled: CGFloat) -> CGFloat {
        min(scaled, baseHourglassSize * maxHourglassScale)
    }

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: Spacing.sm + Spacing.xs) {
                // Un caractère de la chaîne d'affichage (U+23F3), pas du catalogue de traduction : décoratif, caché du lecteur d'écran.
                Text(verbatim: "\u{23F3}")
                    .font(.system(size: Self.hourglassSize(scaled: hourglassSize)))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: texts.title)
                        .miffTextStyle(textBodyBold14)
                    Text(verbatim: texts.body(display.duration))
                        .miffTextStyle(textBodyRegular14)
                }
                .foregroundColor(colorTextForm.color)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(colorTextMuted.color)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm + Spacing.xs)
            .frame(minHeight: Spacing.minTouchTarget)
            .background(
                RoundedRectangle(cornerRadius: radiusMd).fill(colorBgSurface.color).miffShadow(shadowDefault)
            )
            .overlay(CountdownBorderRing(fraction: display.fraction))
            .contentShape(RoundedRectangle(cornerRadius: radiusMd))
        }
        .buttonStyle(.plain)
        // Un seul bouton : le nom dit les deux phrases avec la durée PARLÉE ; « Donner de tes nouvelles » est ce que fait le toucher. Pas de région
        // annoncée : la valeur est lue à jour quand l'élément reçoit le focus, jamais chaque minute.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: texts.accessibilityLabel(display.duration)))
        .accessibilityHint(Text(verbatim: texts.accessibilityAction))
        .accessibilityAddTraits(.isButton)
    }
}

/// La piste (le contour complet en `border/default`) et l'arc qui se vide. La longueur de l'arc est animable : le glissement de 600 ms vient de
/// l'animation de la transaction (rien ne bouge entre deux minutes). La couleur suit la même fraction.
struct CountdownBorderRing: View {
    var fraction: Double

    var body: some View {
        ZStack {
            CountdownBorderShape()
                .stroke(colorBorderDefault.color, style: StrokeStyle(lineWidth: CountdownBorder.lineWidth))
            CountdownBorderShape()
                .trim(from: 0, to: CGFloat(fraction))
                .stroke(
                    CountdownBorder.color(fraction: fraction).color,
                    style: StrokeStyle(lineWidth: CountdownBorder.lineWidth, lineCap: .butt)
                )
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
