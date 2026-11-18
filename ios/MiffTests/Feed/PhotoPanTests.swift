// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// Le déplacement d'une photo zoomée en plein écran (FEED-8) : borné, on ne peut jamais la faire glisser hors de l'écran, et, la photo REMPLISSANT
// l'écran (aucune marge, exigence du PM), jamais en faire apparaître un bord vide.
final class PhotoPanTests: XCTestCase {
    private let area = CGSize(width: 400, height: 800)

    func testAPhotoOfTheScreenShapeAtOneTimesThereIsNothingToMove() {
        let limit = PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: area, zoom: 1)

        XCTAssertEqual(limit, .zero)
    }

    func testZoomedTwiceThePhotoMayMoveByHalfWhatItOverflows() {
        // 100 x 200 remplit 400 x 800 (x4), puis zoomée x2 : 800 x 1 600, elle dépasse de 400 et 800.
        let limit = PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: area, zoom: 2)

        XCTAssertEqual(limit, CGSize(width: 200, height: 400))
    }

    func testAWidePhotoFillsTheHeightAndCanMoveSidewaysEvenAtOneTimes() {
        // 200 x 100 remplit 400 x 800 : x8 = 1 600 x 800, elle dépasse déjà de 1 200 en largeur : on peut la faire glisser de 600 de chaque côté.
        XCTAssertEqual(PhotoPan.limit(imageSize: CGSize(width: 200, height: 100), area: area, zoom: 1), CGSize(width: 600, height: 0))
        // Zoomée x2 : 3 200 x 1 600 : 1 400 de chaque côté en largeur, 400 en hauteur.
        XCTAssertEqual(PhotoPan.limit(imageSize: CGSize(width: 200, height: 100), area: area, zoom: 2), CGSize(width: 1_400, height: 400))
    }

    func testAnOffsetBeyondTheLimitIsBroughtBack() {
        let image = CGSize(width: 100, height: 200)

        XCTAssertEqual(
            PhotoPan.clamp(CGSize(width: 900, height: -900), imageSize: image, area: area, zoom: 2),
            CGSize(width: 200, height: -400)
        )
        XCTAssertEqual(
            PhotoPan.clamp(CGSize(width: 50, height: -60), imageSize: image, area: area, zoom: 2),
            CGSize(width: 50, height: -60),
            "dans les bornes : inchangé"
        )
    }

    func testWhenTheZoomGoesBackToOneTheOffsetIsBroughtToZeroForAPhotoOfTheScreenShape() {
        let image = CGSize(width: 100, height: 200)

        let offset = PhotoPan.clamp(CGSize(width: 150, height: 300), imageSize: image, area: area, zoom: 1)

        XCTAssertEqual(offset, .zero)
    }

    /// Aucune position permise ne fait apparaître un bord vide : à l'offset maximal, le bord de la photo coïncide avec celui de l'écran, jamais en deçà.
    func testNoAllowedOffsetEverShowsAnEmptyEdgeWhateverThePhotoTheScreenAndTheZoom() {
        let screens = [CGSize(width: 375, height: 667), CGSize(width: 375, height: 812), CGSize(width: 393, height: 852), CGSize(width: 852, height: 393)]
        let photos = [
            CGSize(width: 576, height: 1_248), CGSize(width: 900, height: 1_200), CGSize(width: 1_200, height: 900),
            CGSize(width: 4_000, height: 1_000), CGSize(width: 300, height: 3_000)
        ]
        for screen in screens {
            for photo in photos {
                for zoom in [CGFloat(1), 1.5, 2, 4] {
                    let filled = PhotoFullScreenLayout.filledSize(imageSize: photo, in: screen)
                    let extreme = PhotoPan.clamp(CGSize(width: 99_999, height: -99_999), imageSize: photo, area: screen, zoom: zoom)
                    let label = "\(screen) \(photo) x\(zoom)"

                    // Bord gauche de la photo (centrée, zoomée, décalée) : jamais à droite du bord gauche de l'écran ; bord haut : idem ; et symétriquement.
                    let left = (screen.width - filled.width * zoom) / 2 + extreme.width
                    let right = (screen.width + filled.width * zoom) / 2 + extreme.width
                    let top = (screen.height - filled.height * zoom) / 2 + extreme.height
                    let bottom = (screen.height + filled.height * zoom) / 2 + extreme.height
                    XCTAssertLessThanOrEqual(left, 0.001, label)
                    XCTAssertGreaterThanOrEqual(right, screen.width - 0.001, label)
                    XCTAssertLessThanOrEqual(top, 0.001, label)
                    XCTAssertGreaterThanOrEqual(bottom, screen.height - 0.001, label)
                }
            }
        }
    }

    func testUnusableSizesGiveNoMovement() {
        XCTAssertEqual(PhotoPan.limit(imageSize: .zero, area: area, zoom: 2), .zero)
        XCTAssertEqual(PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: .zero, zoom: 2), .zero)
        XCTAssertEqual(PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: area, zoom: .nan), .zero)
        XCTAssertEqual(PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: area, zoom: 0), .zero)
    }
}
// swiftlint:enable line_length
