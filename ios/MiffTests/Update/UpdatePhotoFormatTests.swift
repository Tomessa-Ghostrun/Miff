import XCTest
@testable import Miff

/// Le FORMAT de la photo d'une update (design/screens/update.md §6.1, ticket Update D24, critères V3 et V7) : les cas sont ceux d'Android
/// (`UpdatePhotoFormatTest.kt`), le contrat commun. Rien n'est lu sur un téléphone : le recadrage, les tailles et l'échelle de compression
/// sont purs.
final class UpdatePhotoFormatTests: XCTestCase {
    private func output(_ width: Int, _ height: Int) -> PixelSize {
        UpdatePhotoFormat.outputSize(cropWidth: width, cropHeight: height, longSide: 1024)
    }

    private func aspect(_ rect: PixelRect) -> Double {
        Double(rect.width) / Double(rect.height)
    }

    // MARK: - Le ratio du viseur, borné entre 9:21 et 9:16

    func testTheRatioOfTheTwoPhonesOfThePmIsKeptAsItIs() {
        // iPhone 11 Pro : 1125 x 2436 (19,5:9). OPPO Find X5 Lite : 1080 x 2400 (20:9).
        let iphone = UpdatePhotoFormat.boundedAspect(widthPx: 1125, heightPx: 2436)
        XCTAssertEqual(iphone, 1125.0 / 2436.0, accuracy: 0.0001)
        let oppo = UpdatePhotoFormat.boundedAspect(widthPx: 1080, heightPx: 2400)
        XCTAssertEqual(oppo, 1080.0 / 2400.0, accuracy: 0.0001)
    }

    func testATallerScreenIsBoundedAt9To21AWiderOneAt9To16() {
        XCTAssertEqual(UpdatePhotoFormat.boundedAspect(widthPx: 900, heightPx: 2400), 9.0 / 21.0, accuracy: 0.0001)
        XCTAssertEqual(UpdatePhotoFormat.boundedAspect(widthPx: 1200, heightPx: 1600), 9.0 / 16.0, accuracy: 0.0001)
        // Une tablette en paysage, un pliant : toujours borné.
        XCTAssertEqual(UpdatePhotoFormat.boundedAspect(widthPx: 2560, heightPx: 1600), 9.0 / 16.0, accuracy: 0.0001)
    }

    func testAZoneWithNoSizeGivesTheWidestRatioNeverADivisionByZero() {
        XCTAssertEqual(UpdatePhotoFormat.boundedAspect(widthPx: 0, heightPx: 0), 9.0 / 16.0, accuracy: 0.0001)
        XCTAssertEqual(UpdatePhotoFormat.boundedAspect(widthPx: 100, heightPx: 0), 9.0 / 16.0, accuracy: 0.0001)
    }

    // MARK: - Le recadrage « cover »

    func testASensorImageOf3To4IsCutInWidthToTheRatioOfTheViewfinder() throws {
        // Un capteur 4:3 tenu droit : 3000 x 4000. Le viseur est du 20:9 en hauteur (0,45) : la largeur est coupée, au centre.
        let crop = try XCTUnwrap(UpdatePhotoFormat.coverCrop(sourceWidth: 3000, sourceHeight: 4000, aspect: 0.45))

        XCTAssertEqual(crop, PixelRect(left: 600, top: 0, width: 1800, height: 4000))
        XCTAssertEqual(aspect(crop), 0.45, accuracy: 0.001)
    }

    func testASourceTallerThanTheRatioIsCutInHeight() throws {
        let crop = try XCTUnwrap(UpdatePhotoFormat.coverCrop(sourceWidth: 1000, sourceHeight: 3000, aspect: 9.0 / 16.0))

        XCTAssertEqual(crop, PixelRect(left: 0, top: 611, width: 1000, height: 1778))
    }

    func testTheCropIsAlwaysInsideTheSourceWhateverTheShape() throws {
        let sizes = [(1, 1), (1, 5000), (5000, 1), (640, 480), (480, 640), (4000, 3000), (3000, 4000), (7, 13)]
        let ratios = [UpdatePhotoFormat.minAspect, 0.45,
            UpdatePhotoFormat.maxAspect, Double.nan, 0, -1, Double.infinity]
        for (width, height) in sizes {
            for ratio in ratios {
                let found = UpdatePhotoFormat.coverCrop(sourceWidth: width, sourceHeight: height, aspect: ratio)
                let crop = try XCTUnwrap(found)
                let inside = (1...width).contains(crop.width) && (1...height).contains(crop.height)
                XCTAssertTrue(inside, "\(width) x \(height) à \(ratio) : \(crop)")
                XCTAssertTrue(
                    crop.left >= 0 && crop.top >= 0,
                    "\(width) x \(height) à \(ratio) : \(crop)"
                    )
                    XCTAssertTrue(
                    crop.left + crop.width <= width && crop.top + crop.height <= height,
                    "\(width) x \(height) à \(ratio) : \(crop)"
                )
            }
        }
    }

    func testAnImageWithNoSizeHasNoCrop() {
        XCTAssertNil(UpdatePhotoFormat.coverCrop(sourceWidth: 0, sourceHeight: 100, aspect: 0.5))
        XCTAssertNil(UpdatePhotoFormat.coverCrop(sourceWidth: 100, sourceHeight: 0, aspect: 0.5))
    }

    // MARK: - La taille de l'image enregistrée

    func testTheLongSideIsAtMost1024TheRatioIsKeptASmallPhotoIsNeverEnlarged() {
        XCTAssertEqual(output(1800, 4000), PixelSize(width: 461, height: 1024))
        XCTAssertEqual(output(1000, 1778), PixelSize(width: 576, height: 1024))
        XCTAssertEqual(output(900, 2100), PixelSize(width: 439, height: 1024))
        XCTAssertEqual(output(300, 600), PixelSize(width: 300, height: 600))
        // Une source plus large que haute (une tablette) est aussi bornée sur son côté long.
        XCTAssertEqual(output(2000, 1125), PixelSize(width: 1024, height: 576))
    }

    // MARK: - L'échelle de compression

    func testTheLadderStopsAtTheFirstResultThatFitsBestQualityAndBiggestSideFirst() {
        var tried: [[Int]] = []
        let bytes = UpdatePhotoFormat.fit { side, quality in
            tried.append([side, quality])
            return Data(count: quality > 60 ? 160_000 : 120_000)
        }

        XCTAssertEqual(bytes?.count, 120_000)
        XCTAssertEqual(tried, [[1024, 80], [1024, 70], [1024, 60]])
    }

    func testWhenTheBiggestSideNeverFitsTheNextSideIsTriedDownTo768() {
        var tried: [[Int]] = []
        let bytes = UpdatePhotoFormat.fit { side, quality in
            tried.append([side, quality])
            return Data(count: side == 768 && quality == 55 ? 149_999 : 200_000)
        }

        XCTAssertEqual(bytes?.count, 149_999)
        XCTAssertEqual(tried.first, [1024, 80])
        XCTAssertEqual(tried.last, [768, 55])
        XCTAssertEqual(tried.count, 12)
    }

    func testTheLimitIs150000BytesIncludedAndNotOneMore() {
        XCTAssertEqual(UpdatePhotoFormat.fit { _, _ in Data(count: 150_000) }?.count, 150_000)
        XCTAssertNil(UpdatePhotoFormat.fit { _, _ in Data(count: 150_001) })
        XCTAssertEqual(UpdatePhotoFormat.maxBytes, 150_000)
    }

    func testWhenNothingFitsOrTheEncoderFailsThereIsNoPhoto() {
        XCTAssertNil(UpdatePhotoFormat.fit { _, _ in Data(count: 500_000) })
        XCTAssertNil(UpdatePhotoFormat.fit { _, _ in nil })
        // Un résultat vide n'est pas une photo non plus.
        XCTAssertNil(UpdatePhotoFormat.fit { _, _ in Data() })
    }

    func testTheFrameOfThePreviewKeepsThePhotoWhole() {
        // Une photo de 461 x 1024 dans un cadre de 360 x 600 : la hauteur décide.
        let fitted = UpdatePhotoFormat.fitInside(width: 461, height: 1024, frameWidth: 360, frameHeight: 600)
        XCTAssertEqual(fitted, PixelSize(width: 270, height: 600))
        let empty = UpdatePhotoFormat.fitInside(width: 0, height: 10, frameWidth: 360, frameHeight: 600)
        XCTAssertEqual(empty, PixelSize(width: 0, height: 0))
    }

    // MARK: - La photo en mémoire

    func testAPhotoWaitingMoreThan5MinutesIsOldAtExactly5MinutesItIsNot() {
        let photo = UpdatePhoto(jpeg: Data(count: 10), width: 461, height: 1024, takenAtMillis: 1_000_000)

        XCTAssertFalse(photo.isStale(nowMillis: 1_000_000 + UpdatePhoto.staleMillis))
        XCTAssertTrue(photo.isStale(nowMillis: 1_000_000 + UpdatePhoto.staleMillis + 1))
    }

    func testAPhotoShowsNothingOfItself() {
        let photo = UpdatePhoto(jpeg: Data([1, 2, 3]), width: 461, height: 1024, takenAtMillis: 5)

        XCTAssertEqual("\(photo)", "UpdatePhoto(redacted)")
        XCTAssertEqual(String(describing: PreviewState(photo: photo)), "PreviewState(redacted)")
    }

    // MARK: - Le reflet (D37)

    func testOnlyTheFrontCameraIsMirroredTheBackOneNever() {
        XCTAssertTrue(PhotoMirror.shouldMirror(frontCamera: true))
        XCTAssertFalse(PhotoMirror.shouldMirror(frontCamera: false))
    }
}
