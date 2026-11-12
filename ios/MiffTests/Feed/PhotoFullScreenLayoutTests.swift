// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La mise en page du plein écran de la photo d'un proche (retour du PM sur le build 25 : « un VRAI plein écran ») : la photo occupe TOUT l'écran, zones sûres
// comprises ; la légende, « Position mesurée… » et « Voir où c'était » sont POSÉES PAR-DESSUS, en bas, sur un voile sombre (texte blanc, contraste ≥ 4,5:1),
// dans une zone BORNÉE (45 % de la hauteur : elle défile en grand texte ou en paysage) qui ne masque jamais toute la photo ; la croix et l'icône restent en haut.
final class PhotoFullScreenLayoutTests: XCTestCase {
    /// La hauteur sous la zone sûre de quelques iPhone (points), approximative : portrait puis paysage.
    private let screens: [(String, CGFloat)] = [
        ("SE 1", 548), ("SE", 647), ("11 Pro", 734), ("16", 760), ("SE paysage", 375), ("11 Pro paysage", 375 - 21), ("16 paysage", 393 - 21)
    ]

    // MARK: - La légende est bornée : la photo n'est jamais masquée

    func testTheCaptionZoneIsBoundedAtFortyFivePercentOfTheHeightSoThePhotoIsNeverHiddenInFull() {
        for (name, height) in screens {
            let maximum = PhotoFullScreenLayout.footerMaxHeight(screenHeight: height)

            XCTAssertEqual(maximum, height * 0.45, accuracy: 0.001, name)
            XCTAssertLessThan(maximum, height / 2, "au moins la moitié de la photo reste visible : \(name)")
            XCTAssertGreaterThan(maximum, 150, "assez de place pour la légende et le bouton, qui défilent au besoin : \(name)")
        }
    }

    func testTheCaptionZoneNeverGoesBelowZeroOnAbsurdHeights() {
        XCTAssertEqual(PhotoFullScreenLayout.footerMaxHeight(screenHeight: 0), 0)
        XCTAssertEqual(PhotoFullScreenLayout.footerMaxHeight(screenHeight: -50), 0)
    }

    // MARK: - Lisibilité : texte blanc sur le voile sombre

    func testWhiteTextOnTheScrimHasAtLeast45To1ContrastEvenOverAWhitePhoto() {
        let contrast = PhotoFullScreenLayout.whiteTextContrast(overScrimAlpha: PhotoFullScreenLayout.scrimAlpha)

        XCTAssertGreaterThanOrEqual(contrast, PhotoFullScreenLayout.minimumContrast, "contraste \(contrast)")
        XCTAssertEqual(colorPhotoCaptionScrim.alpha, PhotoFullScreenLayout.scrimAlpha, accuracy: 0.0001, "le jeton de couleur est celui du calcul")
        XCTAssertEqual(colorPhotoCaptionScrim.hexString, "#000000")
    }

    func testTheContrastRulesAreSane() {
        XCTAssertEqual(PhotoFullScreenLayout.whiteTextContrast(overScrimAlpha: 1), 21, accuracy: 0.01, "blanc sur noir")
        XCTAssertEqual(PhotoFullScreenLayout.whiteTextContrast(overScrimAlpha: 0), 1, accuracy: 0.01, "blanc sur blanc")
        XCTAssertLessThan(PhotoFullScreenLayout.whiteTextContrast(overScrimAlpha: 0.3), PhotoFullScreenLayout.minimumContrast, "un voile trop léger ne suffirait pas")
    }

    // MARK: - La photo remplit tout l'écran

    func testThePhotoOfTheScreenShapeFillsTheWholeScreen() {
        // Le JPEG publié est au format du viseur (9:21 à 9:16) : sur un 11 Pro (375 x 812 points, zones sûres comprises) il occupe tout l'écran.
        let screen = CGSize(width: 375, height: 812)
        let photo = CGSize(width: 375, height: 812)

        let shown = PhotoFullScreenLayout.fittedSize(imageSize: photo, in: screen)

        XCTAssertEqual(shown.width, screen.width, accuracy: 0.001)
        XCTAssertEqual(shown.height, screen.height, accuracy: 0.001)
    }

    func testASmallImageIsEnlargedToFillTheScreenItIsNeverLimitedByItsOwnSize() {
        // ~461 x 1 024 px : bien plus petit que l'écran en points.
        let image = CGSize(width: 200, height: 440)
        let screen = CGSize(width: 375, height: 812)

        let shown = PhotoFullScreenLayout.fittedSize(imageSize: image, in: screen)

        XCTAssertGreaterThan(shown.width, image.width, "agrandie au-delà de sa taille propre")
        XCTAssertLessThanOrEqual(shown.width, screen.width + 0.001)
        XCTAssertLessThanOrEqual(shown.height, screen.height + 0.001)
        XCTAssertEqual(max(shown.width / screen.width, shown.height / screen.height), 1, accuracy: 0.0001, "elle touche l'écran sur un côté au moins")
        XCTAssertEqual(shown.width / shown.height, image.width / image.height, accuracy: 0.0001, "jamais déformée")
    }

    func testAWideImageFillsTheWidthAndATallOneTheHeightNeverBeyondTheScreen() {
        let screen = CGSize(width: 375, height: 812)

        let wide = PhotoFullScreenLayout.fittedSize(imageSize: CGSize(width: 1_000, height: 500), in: screen)
        let tall = PhotoFullScreenLayout.fittedSize(imageSize: CGSize(width: 400, height: 1_000), in: screen)

        XCTAssertEqual(wide.width, screen.width, accuracy: 0.001)
        XCTAssertEqual(tall.height, screen.height, accuracy: 0.001)
        for shown in [wide, tall] {
            XCTAssertLessThanOrEqual(shown.width, screen.width + 0.001)
            XCTAssertLessThanOrEqual(shown.height, screen.height + 0.001)
        }
    }

    func testUnusableSizesGiveNoPhoto() {
        XCTAssertEqual(PhotoFullScreenLayout.fittedSize(imageSize: .zero, in: CGSize(width: 100, height: 100)), .zero)
        XCTAssertEqual(PhotoFullScreenLayout.fittedSize(imageSize: CGSize(width: 10, height: 10), in: .zero), .zero)
    }

    // MARK: - Par lecture de la vue

    func testTheViewPutsThePhotoOnTheWholeScreenAndTheCaptionOnTopOfItAtTheBottom() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        let code = UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8)).joined(separator: "\n")

        XCTAssertFalse(code.contains("PreferenceKey"), "aucune hauteur mesurée")
        XCTAssertFalse(code.contains(".layoutPriority("), "plus de partage de hauteur : la légende est une surimpression")
        XCTAssertTrue(code.contains(".scaledToFit()"), "la photo entière, jamais recadrée")
        XCTAssertFalse(code.contains(".scaledToFill()"))
        XCTAssertTrue(code.contains(".frame(width: area.size.width, height: area.size.height)"), "la photo prend toute la zone, pas sa taille propre")
        XCTAssertTrue(code.contains(".ignoresSafeArea()"), "la photo occupe l'écran entier, zones sûres comprises")
        XCTAssertTrue(code.contains("footerOverlay(screenHeight: screen.size.height)"), "la légende est posée par-dessus la photo")
        XCTAssertTrue(code.contains("LinearGradient("), "dégradé qui fond la photo dans le voile")
        XCTAssertTrue(code.contains("colorPhotoCaptionScrim"), "voile sombre du design system, jamais une couleur en dur")
        XCTAssertTrue(code.contains("accessibilityReduceTransparency"), "« Réduire la transparence » : voile opaque")
        XCTAssertTrue(code.contains("scrim.ignoresSafeArea(edges: .bottom)"), "le voile va jusqu'au bas de l'écran, le texte reste dans la zone sûre")
        XCTAssertTrue(code.contains("ViewThatFits(in: .vertical)"), "la légende entière si ça tient, sinon elle défile")
        XCTAssertTrue(code.contains("PhotoFullScreenLayout.footerMaxHeight(screenHeight:"), "zone bornée")
        XCTAssertTrue(code.contains(".zIndex(1)"), "la croix et l'icône restent au-dessus, en haut")
        XCTAssertTrue(code.contains(".hidesInAppSwitcher()"), "le voile des applications récentes est conservé")
        // Ordre de la pile : la photo d'abord (dessous), puis la légende, puis la croix et l'icône (dessus).
        let photoAt = try XCTUnwrap(code.range(of: "                photo\n")?.lowerBound)
        let captionAt = try XCTUnwrap(code.range(of: "footerOverlay(screenHeight: screen.size.height)")?.lowerBound)
        let closeAt = try XCTUnwrap(code.range(of: "labelKey: \"update_photo_close_a11y\"")?.lowerBound)
        XCTAssertLessThan(photoAt, captionAt)
        XCTAssertLessThan(captionAt, closeAt)
    }

    func testTheCaptionTextIsWhiteFromTheDesignSystem() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        let code = UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8)).joined(separator: "\n")

        XCTAssertFalse(code.contains("colorTextPrimary"), "pas de texte sombre sur le voile")
        XCTAssertGreaterThanOrEqual(code.components(separatedBy: "colorUpdateOnPhoto.color").count - 1, 4, "titre, position, indisponible, bouton : tous en blanc")
        XCTAssertEqual(colorUpdateOnPhoto.hexString, "#FFFFFF")
    }

    // MARK: - La légende collée en bas (retour du PM sur le build 27)

    func testTheCaptionIsTightAndSitsRightAboveTheBottomSafeAreaLikeOnAndroid() throws {
        // Avant : 8 pt de marge basse et 8 pt entre la légende et le bouton.
        XCTAssertLessThanOrEqual(PhotoFullScreenLayout.captionBottomPadding, 4, "juste au-dessus de la zone sûre du bas")
        XCTAssertLessThanOrEqual(PhotoFullScreenLayout.captionSpacing, 4, "légende et bouton serrés")
        XCTAssertLessThan(PhotoFullScreenLayout.captionBottomPadding, Spacing.sm)
        XCTAssertLessThan(PhotoFullScreenLayout.captionSpacing, Spacing.sm)
        XCTAssertGreaterThan(PhotoFullScreenLayout.fadeHeight, 0, "le dégradé reste")
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        let code = UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8)).joined(separator: "\n")

        XCTAssertTrue(code.contains("spacing: PhotoFullScreenLayout.captionSpacing"))
        XCTAssertTrue(code.contains(".padding(.bottom, PhotoFullScreenLayout.captionBottomPadding)"))
        XCTAssertFalse(code.contains("minHeight: Spacing.minTouchTarget + Spacing.xs"), "le bouton « Voir où c'était » ne gagne plus de hauteur : 44 pt")
        XCTAssertTrue(code.contains("minHeight: Spacing.minTouchTarget)"))
    }

    // MARK: - Les pictogrammes sont centrés dans leur rond (retour du PM sur le build 27)

    private func boundsCenter(_ glyph: PhotoOverlayGlyph) -> (CGPoint, CGRect) {
        let box = CGRect(x: 0, y: 0, width: PhotoOverlayIconButton.glyphBox, height: PhotoOverlayIconButton.glyphBox)
        let bounds = PhotoGlyphShape(glyph: glyph).path(in: box).boundingRect
        return (CGPoint(x: bounds.midX, y: bounds.midY), box)
    }

    func testTheDownloadAndCloseGlyphsAreCenteredInTheirSquareSoCenteredInTheCircle() {
        for glyph in [PhotoOverlayGlyph.download, .close] {
            let (center, box) = boundsCenter(glyph)

            XCTAssertEqual(center.x, box.midX, accuracy: 0.05, "\(glyph) : centré horizontalement")
            XCTAssertEqual(center.y, box.midY, accuracy: 0.05, "\(glyph) : centré verticalement")
        }
    }

    func testTheGlyphsStayInsideTheirSquareAndInsideTheDiscWithRoomForTheStroke() {
        let disc = UpdateLayout.roundButtonDisc
        for glyph in [PhotoOverlayGlyph.download, .close] {
            let (_, box) = boundsCenter(glyph)
            let bounds = PhotoGlyphShape(glyph: glyph).path(in: box).boundingRect

            XCTAssertTrue(box.insetBy(dx: -0.001, dy: -0.001).contains(bounds), "\(glyph) reste dans son carré")
            XCTAssertLessThan(PhotoOverlayIconButton.glyphBox + PhotoOverlayIconButton.lineWidth, disc, "le trait ne touche pas le bord du disque")
            XCTAssertGreaterThan(bounds.width, box.width * 0.5, "\(glyph) assez grand pour se lire")
        }
    }

    func testTheCenterIsTheSameWhateverTheSquareSize() {
        for size: CGFloat in [10, 20, 33.3] {
            let box = CGRect(x: 7, y: 11, width: size, height: size)
            let bounds = PhotoGlyphShape(glyph: .download).path(in: box).boundingRect

            XCTAssertEqual(bounds.midX, box.midX, accuracy: 0.001 * size)
            XCTAssertEqual(bounds.midY, box.midY, accuracy: 0.001 * size)
        }
    }

    func testTheFullScreenUsesTheDrawnGlyphsForTheCloseAndSaveIconsInsteadOfTypographicSymbols() throws {
        let miff = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Feed")
        let screen = UpdatePrivacyTests.codeLines(try String(contentsOf: miff.appendingPathComponent("FeedPhotoFullScreen.swift"), encoding: .utf8)).joined(separator: "\n")
        let save = UpdatePrivacyTests.codeLines(try String(contentsOf: miff.appendingPathComponent("PhotoSaveButton.swift"), encoding: .utf8)).joined(separator: "\n")
        let button = UpdatePrivacyTests.codeLines(try String(contentsOf: miff.appendingPathComponent("PhotoOverlayIconButton.swift"), encoding: .utf8)).joined(separator: "\n")

        XCTAssertTrue(screen.contains("PhotoOverlayIconButton(glyph: .close"))
        XCTAssertFalse(screen.contains("RoundOverlayButton"))
        XCTAssertFalse(save.contains("RoundOverlayButton"))
        XCTAssertFalse(button.contains("Image(systemName"), "aucun symbole typographique : le pictogramme est un tracé centré")
        XCTAssertTrue(button.contains("ZStack {"), "le rond et le tracé partagent le même centre")
        XCTAssertGreaterThanOrEqual(UpdateLayout.roundButtonSize, 44, "zone de toucher de 44 pt au moins")
    }
}
// swiftlint:enable line_length
