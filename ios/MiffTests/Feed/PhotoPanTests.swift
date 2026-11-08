import XCTest
@testable import Miff

// Le déplacement d'une photo zoomée en plein écran (FEED-8) : borné, on ne peut jamais la faire glisser hors de l'écran.
final class PhotoPanTests: XCTestCase {
    private let area = CGSize(width: 400, height: 800)

    func testEntireAtOneTimesThereIsNothingToMove() {
        let limit = PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: area, zoom: 1)

        XCTAssertEqual(limit, .zero)
    }

    func testZoomedTwiceThePhotoMayMoveByHalfWhatItOverflows() {
        // 100 x 200 montrée entière dans 400 x 800 (x4), puis zoomée x2 : 800 x 1 600, elle dépasse de 400 et 800.
        let limit = PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: area, zoom: 2)

        XCTAssertEqual(limit, CGSize(width: 200, height: 400))
    }

    func testAWidePhotoBarsAboveAndBelowCanOnlyMoveSideways() {
        // 200 x 100 entière dans 400 x 800 : 400 x 200 ; zoomée x2 : 800 x 400 : dépasse en largeur seulement.
        let limit = PhotoPan.limit(imageSize: CGSize(width: 200, height: 100), area: area, zoom: 2)

        XCTAssertEqual(limit, CGSize(width: 200, height: 0))
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

    func testWhenTheZoomGoesBackToOneTheOffsetIsBroughtToZero() {
        let image = CGSize(width: 100, height: 200)

        let offset = PhotoPan.clamp(CGSize(width: 150, height: 300), imageSize: image, area: area, zoom: 1)

        XCTAssertEqual(offset, .zero)
    }

    func testUnusableSizesGiveNoMovement() {
        XCTAssertEqual(PhotoPan.limit(imageSize: .zero, area: area, zoom: 2), .zero)
        XCTAssertEqual(PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: .zero, zoom: 2), .zero)
        XCTAssertEqual(PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: area, zoom: .nan), .zero)
        XCTAssertEqual(PhotoPan.limit(imageSize: CGSize(width: 100, height: 200), area: area, zoom: 0), .zero)
    }
}
