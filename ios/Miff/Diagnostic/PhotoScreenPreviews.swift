#if DEBUG
import SwiftUI
import UIKit

/// La photo d'un proche en plein écran, montrée SEULE pour les captures d'écran de la CI (`xcrun simctl launch ... -MiffPreview photo-3-4`),
/// présentée dans un VRAI `fullScreenCover` comme dans le Feed. Aucune session, aucun réseau, aucune écriture : une image de démonstration
/// dessinée ici (un cadre blanc, une grille, « HAUT » et « BAS ») pour voir à l'œil si la photo est centrée, entière et si la légende est collée en
/// bas. Compilé en configuration Debug SEULEMENT, comme `ScreenPreview`.
struct PhotoScreenPreview: View {
    let name: String
    @State private var opened: OpenedPhoto?

    var body: some View {
        Color.gray
            .ignoresSafeArea()
            .fullScreenCover(item: $opened) { photo in
                PhotoFullScreen(
                    opened: photo,
                    texts: FeedTexts(),
                    nowMillis: Self.now,
                    zone: .current,
                    onOpenMap: { _, done in done(true) },
                    onDismiss: {}
                )
            }
            .task {
                try? await Task.sleep(nanoseconds: 400_000_000)
                opened = sample
            }
    }

    private static var now: Int {
        Int(Date().timeIntervalSince1970 * 1000)
    }

    /// « photo-tall » : le format du viseur de l'Update (9:19,5) ; sinon un 3:4, comme la photo de la capture du PM.
    private var sample: OpenedPhoto {
        let size = name == "photo-tall" ? CGSize(width: 576, height: 1_248) : CGSize(width: 900, height: 1_200)
        let image = Self.image(size: size)
        let now = Self.now
        let photo = SharedPhoto(
            jpeg: image.jpegData(compressionQuality: 0.8) ?? Data(),
            createdAtMillis: now - 60_000,
            expiresAtMillis: now + 22 * 3_600_000,
            place: PhotoPlace(lat: 48.85661, lng: 2.35222, measuredAtMillis: now - 180_000)
        )
        return OpenedPhoto(firstName: "Tomessa", photo: photo, image: image)
    }

    private static func image(size: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cg = context.cgContext
            let colors = [UIColor.systemBlue.cgColor, UIColor.systemOrange.cgColor] as CFArray
            let space = CGColorSpaceCreateDeviceRGB()
            if let gradient = CGGradient(colorsSpace: space, colors: colors, locations: [0, 1]) {
                cg.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
            }
            cg.setStrokeColor(UIColor.white.withAlphaComponent(0.5).cgColor)
            cg.setLineWidth(2)
            for x in stride(from: CGFloat(0), through: size.width, by: 100) {
                cg.move(to: CGPoint(x: x, y: 0))
                cg.addLine(to: CGPoint(x: x, y: size.height))
            }
            for y in stride(from: CGFloat(0), through: size.height, by: 100) {
                cg.move(to: CGPoint(x: 0, y: y))
                cg.addLine(to: CGPoint(x: size.width, y: y))
            }
            cg.strokePath()
            cg.setStrokeColor(UIColor.white.cgColor)
            cg.setLineWidth(16)
            cg.stroke(CGRect(origin: .zero, size: size).insetBy(dx: 8, dy: 8))
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: 64), .foregroundColor: UIColor.white
            ]
            ("HAUT" as NSString).draw(at: CGPoint(x: 40, y: 40), withAttributes: attributes)
            ("BAS" as NSString).draw(at: CGPoint(x: 40, y: size.height - 120), withAttributes: attributes)
        }
    }
}
#endif
