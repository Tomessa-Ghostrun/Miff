import SwiftUI

/// Slide 3 : les trois cartes de notification (« Welcoming screen -17 »), en vectoriel et en vrais textes. Les messages
/// sont des exemples (clés `onboarding_sample_msg_*` et `onboarding_sample_when_*`).
struct MessagesIllustration: View {
    let unit: CGFloat

    var body: some View {
        MockCanvas(unit: unit, width: WelcomeBlobs.unitsWidth, height: WelcomeBlobs.unitsHeight) {
            NotificationCard(
                unit: unit,
                spec: NotificationCardSpec(
                    left: 31,
                    top: 169,
                    photo: "OnboardingPhotoThomas",
                    messageKey: "onboarding_sample_msg_1",
                    whenKey: "onboarding_sample_when_1",
                    messageLines: 1
                )
            )
            NotificationCard(
                unit: unit,
                spec: NotificationCardSpec(
                    left: 50,
                    top: 246,
                    photo: "OnboardingPhotoAmandine",
                    messageKey: "onboarding_sample_msg_2",
                    whenKey: "onboarding_sample_when_2",
                    messageLines: 2
                )
            )
            NotificationCard(
                unit: unit,
                spec: NotificationCardSpec(
                    left: 30,
                    top: 323,
                    photo: "OnboardingPhotoMaman",
                    messageKey: "onboarding_sample_msg_3",
                    whenKey: "onboarding_sample_when_3",
                    messageLines: 2
                )
            )
            // Le petit point jaune-vert au coin de la première carte.
            Circle()
                .fill(colorBrandPrimary.color)
                .placed(unit, x: 264.5, y: 161.5, w: 14, h: 14)
        }
    }
}

/// Ce qui change d'une carte de notification à l'autre.
struct NotificationCardSpec {
    let left: Double
    let top: Double
    let photo: String
    let messageKey: String
    let whenKey: String
    let messageLines: Int
}

/// Une carte de notification : photo à anneau bleu, message en gras sur une ou deux lignes, et l'heure dessous.
struct NotificationCard: View {
    let unit: CGFloat
    let spec: NotificationCardSpec

    var body: some View {
        let textLeft = spec.left + 47
        let oneLine = spec.messageLines == 1
        Group {
            card
            ring
            MockText(
                text: L10n.string(spec.messageKey),
                unit: unit,
                x: textLeft,
                y: spec.top + 22,
                style: MockTextStyle(
                    size: 12.2,
                    color: colorTextPrimary,
                    width: 205,
                    lines: spec.messageLines,
                    display: true
                )
            )
            MockText(
                text: L10n.string(spec.whenKey),
                unit: unit,
                x: textLeft + 4,
                y: spec.top + (oneLine ? 44 : 51),
                style: MockTextStyle(size: 9.6, color: colorTextSecondary, width: 120)
            )
        }
    }

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: unit * 4, style: .continuous)
        return shape
            .fill(colorBgSurface.color)
            .overlay(shape.stroke(colorActionPrimaryTint20.color, lineWidth: max(unit, 1) * 0.8))
            .shadow(color: colorTextPrimary.withAlpha(0.2).color, radius: unit * 0.8, x: 0, y: unit * 0.4)
            .placed(unit, x: spec.left, y: spec.top, w: 259, h: 62)
    }

    private var ring: some View {
        ZStack {
            Circle().fill(colorActionPrimary.color)
            MockPhoto(name: spec.photo)
                .frame(width: unit * 34.4, height: unit * 34.4)
                .clipShape(Circle())
        }
        .placed(unit, x: spec.left + 5, y: spec.top + 12.5, w: 38, h: 38)
    }
}
