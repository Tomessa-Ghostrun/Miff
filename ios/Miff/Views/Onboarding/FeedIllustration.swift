import SwiftUI

/// Slide 1 : l'écran du Fil (« Welcoming screen »), en vectoriel et en vrais textes. Les noms de personnes, de lieux et de
/// groupe sont des exemples (clés `onboarding_sample_*` et `ios_onboarding_*`).
struct FeedIllustration: View {
    let unit: CGFloat

    var body: some View {
        MockCanvas(unit: unit, width: WelcomeBlobs.unitsWidth, height: WelcomeBlobs.unitsHeight) {
            MockScreen(unit: unit) {
                selector
                bubble
                MemberCard(unit: unit, spec: .thomas)
                MemberCard(unit: unit, spec: .amandine)
            }
        }
    }

    /// Le sélecteur de groupe (« Famille Alberta ») avec son petit triangle.
    private var selector: some View {
        Group {
            RoundedRectangle(cornerRadius: unit * 4, style: .continuous)
                .fill(colorBgSurface.color)
                .shadow(color: colorTextPrimary.withAlpha(0.2).color, radius: unit * 0.75, x: 0, y: unit * 0.4)
                .placed(unit, x: 34, y: 4, w: 88, h: 15)
            MockText(
                text: L10n.string("ios_onboarding_family_name"),
                unit: unit,
                x: 72,
                y: 11.5,
                style: MockTextStyle(size: 7.4, color: colorTextForm, width: 70, italic: true, centered: true)
            )
            MockIcon(unit: unit, x: 111, y: 8, size: 6, kind: .triangleDown, color: colorActionPrimary)
        }
    }

    /// La bulle verte « Tout le monde est en sécurité », son étoile et ses deux textes.
    private var bubble: some View {
        Group {
            BubbleShape()
                .fill(colorStatusSafeTint.color)
                .placed(unit, x: 8, y: 33, w: 144, h: 95)
            MockText(
                text: L10n.string("onboarding_sample_all_safe"),
                unit: unit,
                x: 24,
                y: 71,
                style: MockTextStyle(size: 10.2, color: colorTextPrimary, width: 100, lines: 2, display: true)
            )
            MockText(
                text: L10n.string("onboarding_sample_updated"),
                unit: unit,
                x: 24,
                y: 97,
                style: MockTextStyle(size: 6.2, color: colorTextForm, width: 110)
            )
            MockIcon(unit: unit, x: 105, y: 75, size: 16, kind: .sparkle, color: colorStatusSafe)
        }
    }
}

/// La bulle verte organique (courbes de Bézier dans un cadre de 144 x 95 unités).
private struct BubbleShape: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = rect.width / 144
        func point(_ valueX: CGFloat, _ valueY: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + valueX * scale, y: rect.minY + valueY * scale)
        }
        var path = Path()
        path.move(to: point(8, 50))
        path.addCurve(to: point(90, 6), control1: point(8, 18), control2: point(52, 2))
        path.addCurve(to: point(143, 58), control1: point(125, 9), control2: point(144, 32))
        path.addCurve(to: point(66, 91), control1: point(142, 82), control2: point(105, 94))
        path.addCurve(to: point(8, 50), control1: point(28, 88), control2: point(8, 76))
        path.closeSubpath()
        return path
    }
}

/// Ce qui change d'une carte de membre à l'autre.
struct MemberCardSpec {
    let top: Double
    let height: Double
    let photo: String
    let nameKey: String
    let battery: String
    let placeKey: String
    let placeIcon: MockIconKind
    /// Le texte « Arrivé il y a 12 min » et le détail (photo du salon, trois actions) : seulement sur la première carte.
    let showsDetails: Bool

    static let thomas = MemberCardSpec(
        top: 136,
        height: 118,
        photo: "OnboardingPhotoThomas",
        nameKey: "onboarding_sample_name_1",
        battery: "ios_onboarding_battery_1",
        placeKey: "onboarding_sample_status_home",
        placeIcon: .home,
        showsDetails: true
    )

    static let amandine = MemberCardSpec(
        top: 261,
        height: 60,
        photo: "OnboardingPhotoAmandine",
        nameKey: "onboarding_sample_name_2",
        battery: "ios_onboarding_battery_2",
        placeKey: "onboarding_sample_status_school",
        placeIcon: .mortarboard,
        showsDetails: false
    )
}

/// Une carte de membre du Fil : barre verte à gauche, photo à anneau vert, nom, pastille de batterie, pastille de lieu.
struct MemberCard: View {
    let unit: CGFloat
    let spec: MemberCardSpec

    var body: some View {
        Group {
            card
            photoRing
            MockText(
                text: L10n.string(spec.nameKey),
                unit: unit,
                x: 43,
                y: spec.top + 10,
                style: MockTextStyle(size: 8.6, color: colorTextPrimary, width: 70, display: true)
            )
            batteryChip
            placePill
            details
        }
    }

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: unit * 2.5, style: .continuous)
        return shape
            .fill(colorBgSurface.color)
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(colorBrandPrimary.color)
                    .frame(width: unit * 1.8)
            }
            .clipShape(shape)
            .shadow(color: colorTextPrimary.withAlpha(0.2).color, radius: unit * 0.4, x: 0, y: unit * 0.3)
            .placed(unit, x: 5, y: spec.top, w: 144, h: spec.height)
    }

    /// La photo dans son anneau vert de la marque.
    private var photoRing: some View {
        ZStack {
            Circle().fill(colorBrandPrimary.color)
            MockPhoto(name: spec.photo)
                .frame(width: unit * 18.8, height: unit * 18.8)
                .clipShape(Circle())
        }
        .placed(unit, x: 15, y: spec.top + 5, w: 22, h: 22)
    }

    private var batteryChip: some View {
        Group {
            RoundedRectangle(cornerRadius: unit * 1.5, style: .continuous)
                .fill(colorBorderDefault.withAlpha(0.8).color)
                .placed(unit, x: 121, y: spec.top + 4.5, w: 23, h: 8)
            MockIcon(unit: unit, x: 123, y: spec.top + 5.8, size: 5.2, kind: .battery, color: colorTextSecondary)
            MockText(
                text: L10n.string(spec.battery),
                unit: unit,
                x: 130,
                y: spec.top + 8.5,
                style: MockTextStyle(size: 5.8, color: colorTextSecondary, width: 14)
            )
        }
    }

    private var placePill: some View {
        Group {
            RoundedRectangle(cornerRadius: unit * 1.8, style: .continuous)
                .fill(colorStatusSafeTint.color)
                .placed(unit, x: 43, y: spec.top + 18, w: 45, h: 8.6)
            MockIcon(unit: unit, x: 46, y: spec.top + 19.6, size: 5.4, kind: spec.placeIcon, color: colorStatusSafe)
            MockText(
                text: L10n.string(spec.placeKey),
                unit: unit,
                x: 54,
                y: spec.top + 22.3,
                style: MockTextStyle(size: 6, color: colorStatusSafe, width: 34)
            )
        }
    }

    @ViewBuilder
    private var details: some View {
        if spec.showsDetails {
            MockText(
                text: L10n.string("ios_onboarding_arrived_ago"),
                unit: unit,
                x: 43,
                y: spec.top + 35,
                style: MockTextStyle(size: 5.8, color: colorTextSecondary, width: 80)
            )
            MockPhoto(name: "OnboardingPhotoSalon")
                .frame(width: unit * 130, height: unit * 50)
                .clipShape(RoundedRectangle(cornerRadius: unit * 2, style: .continuous))
                .placed(unit, x: 15, y: spec.top + 45, w: 130, h: 50)
            MockIcon(unit: unit, x: 87.5, y: spec.top + 100, size: 9, kind: .pin, color: colorActionPrimary)
            MockIcon(unit: unit, x: 107.5, y: spec.top + 101, size: 9, kind: .message, color: colorActionPrimary)
            MockIcon(unit: unit, x: 131.5, y: spec.top + 100, size: 9, kind: .phone, color: colorActionPrimary)
        }
    }
}
