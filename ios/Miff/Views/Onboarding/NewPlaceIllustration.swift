import SwiftUI

/// Slide 2 : l'écran « Nouveau lieu » (« Welcoming screen -16 »), en vectoriel et en vrais textes. Comme la capture de la
/// maquette, il est coupé en haut. Les textes sont des exemples (clés `onboarding_sample_place_*` et `ios_onboarding_*`).
struct NewPlaceIllustration: View {
    let unit: CGFloat

    var body: some View {
        MockCanvas(unit: unit, width: WelcomeBlobs.unitsWidth, height: WelcomeBlobs.unitsHeight) {
            MockScreen(unit: unit) {
                header
                nameSection
                addressSection
                radiusSection
                typeSection
            }
        }
    }

    private func text(_ key: String, x: Double, y: Double, style: MockTextStyle) -> MockText {
        MockText(text: L10n.string(key), unit: unit, x: x, y: y, style: style)
    }

    private var hintStyle: MockTextStyle {
        MockTextStyle(size: 5.2, color: colorTextSecondary, width: 120)
    }

    /// Flèche de retour, titre, bouton de validation et trait de séparation.
    private var header: some View {
        Group {
            MockIcon(unit: unit, x: 7, y: -1, size: 8, kind: .back, color: colorActionPrimary)
            text(
                "onboarding_sample_place_title",
                x: 77,
                y: 1.5,
                style: MockTextStyle(size: 9.5, color: colorTextPrimary, width: 80, display: true, centered: true)
            )
            Circle()
                .fill(colorActionPrimary.color)
                .placed(unit, x: 132, y: -9, w: 21, h: 21)
            MockIcon(unit: unit, x: 137, y: 0.5, size: 9, kind: .check, color: colorTextOnAction)
            Rectangle()
                .fill(colorBorderDefault.color)
                .placed(unit, x: 0, y: 18.5, w: 155, h: 1.2)
        }
    }

    private var nameSection: some View {
        Group {
            FormSection(unit: unit, top: 30, icon: .pencil, titleKey: "onboarding_sample_place_name")
            text(
                "ios_onboarding_name_hint",
                x: 29,
                y: 52.5,
                style: MockTextStyle(size: 5.2, color: colorTextSecondary, width: 120, lines: 2)
            )
            fieldBox(top: 64, valueKey: "onboarding_sample_place_name_value", counterKey: "ios_onboarding_name_counter")
        }
    }

    private var addressSection: some View {
        Group {
            FormSection(unit: unit, top: 88.5, icon: .personPin, titleKey: "onboarding_sample_place_address")
            text("ios_onboarding_address_hint", x: 29, y: 107.5, style: hintStyle)
            fieldBox(top: 115, valueKey: "onboarding_sample_place_address_value", counterKey: nil)
        }
    }

    /// Le périmètre : fond de carte, cercle, curseur et sa valeur.
    private var radiusSection: some View {
        Group {
            FormSection(unit: unit, top: 138, icon: .target, titleKey: "onboarding_sample_place_radius")
            text("ios_onboarding_radius_hint", x: 29, y: 157, style: hintStyle)
            map
            slider
        }
    }

    private var map: some View {
        let shape = RoundedRectangle(cornerRadius: unit * 3.5, style: .continuous)
        return ZStack {
            MockPhoto(name: "OnboardingPhotoMap")
                .frame(width: unit * 124, height: unit * 48)
            Canvas { context, size in
                let scale = size.width / 124
                let center = CGPoint(x: 62 * scale, y: 23.5 * scale)
                let ring = Path(
                    ellipseIn: CGRect(
                        x: center.x - 21.3 * scale,
                        y: center.y - 21.3 * scale,
                        width: 42.6 * scale,
                        height: 42.6 * scale
                    )
                )
                context.stroke(
                    ring,
                    with: .color(colorActionPrimary.withAlpha(0.55).color),
                    style: StrokeStyle(lineWidth: 0.6 * scale)
                )
                let dot = Path(
                    ellipseIn: CGRect(
                        x: center.x - 2.3 * scale, y: center.y - 2.3 * scale, width: 4.6 * scale, height: 4.6 * scale)
                )
                context.fill(dot, with: .color(colorActionPrimary.color))
            }
            .frame(width: unit * 124, height: unit * 48)
        }
        .frame(width: unit * 124, height: unit * 48)
        .clipShape(shape)
        .overlay(shape.stroke(colorBorderDefault.color, lineWidth: max(unit, 1) * 0.7))
        .placed(unit, x: 15, y: 164, w: 124, h: 48)
    }

    private var slider: some View {
        Group {
            Capsule()
                .fill(colorBorderDefault.color)
                .placed(unit, x: 28, y: 227.6, w: 94, h: 2.8)
            Capsule()
                .fill(colorActionPrimary.color)
                .placed(unit, x: 28, y: 227.6, w: 21, h: 2.8)
            Circle()
                .fill(colorBgSurface.color)
                .overlay(Circle().stroke(colorActionPrimary.color, lineWidth: unit * 1.2))
                .placed(unit, x: 45, y: 224.8, w: 8.4, h: 8.4)
            RoundedRectangle(cornerRadius: unit * 2.5, style: .continuous)
                .fill(colorBorderDefault.color)
                .placed(unit, x: 128, y: 222, w: 24, h: 14)
            text(
                "ios_onboarding_radius_value",
                x: 140,
                y: 229,
                style: MockTextStyle(size: 5.8, color: colorActionPrimary, width: 22, bold: true, centered: true)
            )
        }
    }

    /// Le type de zone : deux cartes, zone de confiance et zone à risque.
    private var typeSection: some View {
        Group {
            FormSection(unit: unit, top: 245, icon: .eye, titleKey: "ios_onboarding_type_title")
            text("ios_onboarding_type_hint", x: 29, y: 264.2, style: hintStyle)
            ZoneCard(
                unit: unit,
                spec: ZoneCardSpec(
                    left: 28,
                    width: 72,
                    titleKey: "ios_onboarding_zone_trust",
                    hintKey: "ios_onboarding_zone_trust_hint",
                    isTrust: true
                )
            )
            ZoneCard(
                unit: unit,
                spec: ZoneCardSpec(
                    left: 103,
                    width: 60,
                    titleKey: "ios_onboarding_zone_risk",
                    hintKey: "ios_onboarding_zone_risk_hint",
                    isTrust: false
                )
            )
        }
    }

    /// Un champ de saisie dessiné : cadre blanc, valeur d'exemple et, parfois, le compteur « 13/50 ».
    private func fieldBox(top: Double, valueKey: String, counterKey: String?) -> some View {
        Group {
            RoundedRectangle(cornerRadius: unit * 2.5, style: .continuous)
                .fill(colorBgSurface.color)
                .overlay(
                    RoundedRectangle(cornerRadius: unit * 2.5, style: .continuous)
                        .stroke(colorBorderDefault.color, lineWidth: max(unit, 1) * 0.6)
                )
                .placed(unit, x: 27, y: top, w: 123, h: 12.5)
            text(valueKey, x: 33, y: top + 6.3, style: MockTextStyle(size: 5.6, color: colorTextForm, width: 100))
            if let counterKey {
                text(
                    counterKey,
                    x: 133,
                    y: top + 6.3,
                    style: MockTextStyle(size: 4.6, color: colorTextSecondary.withAlpha(0.6), width: 17)
                )
            }
        }
    }
}

/// Le titre d'une section du formulaire : pastille bleu pâle avec son icône, et le titre en gras.
private struct FormSection: View {
    let unit: CGFloat
    let top: Double
    let icon: MockIconKind
    let titleKey: String

    var body: some View {
        Group {
            RoundedRectangle(cornerRadius: unit * 4, style: .continuous)
                .fill(colorActionPrimaryTint20.color)
                .placed(unit, x: 6, y: top, w: 16, h: 16)
            MockIcon(unit: unit, x: 9, y: top + 3, size: 10, kind: icon, color: colorActionPrimary)
            MockText(
                text: L10n.string(titleKey),
                unit: unit,
                x: 29,
                y: top + 7,
                style: MockTextStyle(size: 9.2, color: colorTextPrimary, width: 100, display: true)
            )
        }
    }
}

struct ZoneCardSpec {
    let left: Double
    let width: Double
    let titleKey: String
    let hintKey: String
    let isTrust: Bool
}

/// Une carte « Zone de confiance » (fond vert pâle) ou « Zone à risque » (fond blanc, trait rouge).
private struct ZoneCard: View {
    let unit: CGFloat
    let spec: ZoneCardSpec

    var body: some View {
        let tint = spec.isTrust ? colorStatusSafeTint : colorBgSurface
        let line = spec.isTrust ? colorStatusSafe.withAlpha(0.6) : colorStatusDanger.withAlpha(0.6)
        Group {
            RoundedRectangle(cornerRadius: unit * 2.5, style: .continuous)
                .fill(tint.color)
                .overlay(
                    RoundedRectangle(cornerRadius: unit * 2.5, style: .continuous)
                        .stroke(line.color, lineWidth: max(unit, 1) * 0.7)
                )
                .placed(unit, x: spec.left, y: 271, w: spec.width, h: 40)
            MockIcon(
                unit: unit,
                x: spec.left + 3,
                y: 277,
                size: 9,
                kind: spec.isTrust ? .checkCircle : .warning,
                color: spec.isTrust ? colorStatusSafe : colorStatusDanger
            )
            MockText(
                text: L10n.string(spec.titleKey),
                unit: unit,
                x: spec.left + 14,
                y: 279,
                style: MockTextStyle(size: 5.4, color: colorTextPrimary, width: spec.width - 15, display: true)
            )
            MockText(
                text: L10n.string(spec.hintKey),
                unit: unit,
                x: spec.left + 14,
                y: 288.5,
                style: MockTextStyle(size: 4.4, color: colorTextSecondary, width: spec.width - 15)
            )
        }
    }
}
