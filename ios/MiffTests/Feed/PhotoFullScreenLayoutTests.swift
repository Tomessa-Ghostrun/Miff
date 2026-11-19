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

    // MARK: - La photo remplit tout l'écran, sans aucune marge (exigence ferme du PM)

    /// Des écrans (points, zones sûres comprises) : iPhone SE, 11 Pro, 16, 16 Pro Max, et un format carré / paysage absurde.
    private let fullScreens: [(String, CGSize)] = [
        ("SE", CGSize(width: 375, height: 667)), ("11 Pro", CGSize(width: 375, height: 812)), ("16", CGSize(width: 393, height: 852)),
        ("16 Pro Max", CGSize(width: 440, height: 956)), ("paysage", CGSize(width: 852, height: 393)), ("carré", CGSize(width: 500, height: 500))
    ]

    /// Des photos (pixels) : le viseur (9:19,5), un 3:4, un 9:16, un carré, un paysage 4:3, un panorama, une très haute et une toute petite.
    private let photos: [CGSize] = [
        CGSize(width: 576, height: 1_248), CGSize(width: 900, height: 1_200), CGSize(width: 1_080, height: 1_920), CGSize(width: 800, height: 800),
        CGSize(width: 1_200, height: 900), CGSize(width: 4_000, height: 1_000), CGSize(width: 300, height: 3_000), CGSize(width: 20, height: 30)
    ]

    func testThePhotoOfTheScreenShapeFillsTheWholeScreenWithNothingCut() {
        // Le JPEG publié est au format du viseur : sur un écran du même rapport il occupe tout l'écran, rien n'est coupé.
        let screen = CGSize(width: 375, height: 812)
        let shown = PhotoFullScreenLayout.filledSize(imageSize: CGSize(width: 375, height: 812), in: screen)

        XCTAssertEqual(shown.width, screen.width, accuracy: 0.001)
        XCTAssertEqual(shown.height, screen.height, accuracy: 0.001)
        XCTAssertEqual(PhotoFullScreenLayout.croppedShare(imageSize: CGSize(width: 375, height: 812), in: screen), 0, accuracy: 0.0001)
    }

    func testThePhotoNeverLeavesAFreeSpaceWhateverThePhotoAndTheScreen() {
        for (name, screen) in fullScreens {
            for photo in photos {
                let shown = PhotoFullScreenLayout.filledSize(imageSize: photo, in: screen)
                let label = "\(name) \(Int(photo.width))x\(Int(photo.height))"

                XCTAssertGreaterThanOrEqual(shown.width, screen.width - 0.001, "aucune bande à gauche ou à droite : \(label)")
                XCTAssertGreaterThanOrEqual(shown.height, screen.height - 0.001, "aucune bande en haut ou en bas : \(label)")
                XCTAssertEqual(shown.width / shown.height, photo.width / photo.height, accuracy: 0.0001, "jamais déformée : \(label)")
                // Elle touche l'écran SUR UN CÔTÉ EXACTEMENT (le plus petit des dépassements est nul) : pas plus grande que nécessaire.
                XCTAssertEqual(min(shown.width / screen.width, shown.height / screen.height), 1, accuracy: 0.0001, label)
            }
        }
    }

    func testASmallImageIsEnlargedToFillTheScreenItIsNeverLimitedByItsOwnSize() {
        let shown = PhotoFullScreenLayout.filledSize(imageSize: CGSize(width: 200, height: 440), in: CGSize(width: 375, height: 812))

        XCTAssertGreaterThan(shown.width, 200, "agrandie au-delà de sa taille propre")
        XCTAssertGreaterThanOrEqual(shown.width, 375 - 0.001)
        XCTAssertGreaterThanOrEqual(shown.height, 812 - 0.001)
    }

    func testAWideImageIsCutOnTheSidesAndATallOneAtTheTopAndBottom() {
        let screen = CGSize(width: 375, height: 812)

        let wide = PhotoFullScreenLayout.filledSize(imageSize: CGSize(width: 1_000, height: 500), in: screen)
        let tall = PhotoFullScreenLayout.filledSize(imageSize: CGSize(width: 400, height: 1_000), in: screen)

        XCTAssertEqual(wide.height, screen.height, accuracy: 0.001, "le paysage remplit la hauteur : il dépasse en largeur")
        XCTAssertGreaterThan(wide.width, screen.width)
        XCTAssertEqual(tall.width, screen.width, accuracy: 0.001, "la haute remplit la largeur : elle dépasse en hauteur")
        XCTAssertGreaterThan(tall.height, screen.height)
    }

    /// Les effets de bord de la recette : la part de la photo coupée sur un écran plus allongé que la photo (recadrage centré).
    func testTheCroppedShareOfTheUsualPhotosOnTheUsualScreens() {
        // Un 3:4 (900 x 1 200) sur un 11 Pro (375 x 812, rapport 0,462) : il remplit la hauteur, 0,462 / 0,75 de la largeur reste, 38 % est coupé.
        XCTAssertEqual(PhotoFullScreenLayout.croppedShare(imageSize: CGSize(width: 900, height: 1_200), in: CGSize(width: 375, height: 812)), 0.384, accuracy: 0.001)
        // Le viseur (9:19,5, rapport 0,4615) sur l'iPhone 16 (393 x 852, rapport 0,4613) : presque rien n'est coupé.
        XCTAssertLessThan(PhotoFullScreenLayout.croppedShare(imageSize: CGSize(width: 576, height: 1_248), in: CGSize(width: 393, height: 852)), 0.001)
        // Le même sur un iPhone SE (375 x 667, rapport 0,562) : environ 18 % coupé en haut et en bas.
        XCTAssertEqual(PhotoFullScreenLayout.croppedShare(imageSize: CGSize(width: 576, height: 1_248), in: CGSize(width: 375, height: 667)), 0.179, accuracy: 0.002)
        for (name, screen) in fullScreens {
            for photo in photos {
                let share = PhotoFullScreenLayout.croppedShare(imageSize: photo, in: screen)
                XCTAssertGreaterThanOrEqual(share, 0, name)
                XCTAssertLessThan(share, 1, name)
            }
        }
    }

    func testUnusableSizesGiveNoPhoto() {
        XCTAssertEqual(PhotoFullScreenLayout.filledSize(imageSize: .zero, in: CGSize(width: 100, height: 100)), .zero)
        XCTAssertEqual(PhotoFullScreenLayout.filledSize(imageSize: CGSize(width: 10, height: 10), in: .zero), .zero)
        XCTAssertEqual(PhotoFullScreenLayout.croppedShare(imageSize: .zero, in: CGSize(width: 100, height: 100)), 0)
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
        XCTAssertFalse(code.contains("ViewThatFits"), "plus de choix implicite : la légende défile selon un calcul testé (`footerScrolls`)")
        XCTAssertEqual(code.components(separatedBy: "GeometryReader").count - 1, 1, "UN seul GeometryReader, à la racine : aucun imbriqué")
        XCTAssertTrue(code.contains("proxy.safeAreaInsets"), "les vraies zones sûres, lues une fois")
        XCTAssertTrue(code.contains(".scaledToFill()"), "la photo REMPLIT l'écran : aucune marge visible (exigence du PM)")
        XCTAssertFalse(code.contains(".scaledToFit()"), "jamais « entière dans les marges » : cela laissait des bandes noires")
        XCTAssertTrue(code.contains(".clipped()"), "le dépassement du recadrage est coupé aux bords de l'écran")
        XCTAssertTrue(code.contains("topScrim(height: insets.top)") && code.contains(".allowsHitTesting(false)"), "un voile léger sous la barre d'état, sans toucher")
        XCTAssertTrue(code.contains(".preferredColorScheme(.dark)"), "barre d'état en clair sur ce plein écran")
        XCTAssertTrue(code.contains(".frame(width: size.width, height: size.height)"), "la photo (et son conteneur) prennent l'écran ENTIER : la photo est centrée")
        XCTAssertTrue(code.contains("            .ignoresSafeArea()\n"), "le CONTENU occupe l'écran entier, zones sûres comprises")
        XCTAssertFalse(code.contains("        }\n        .ignoresSafeArea()\n"), "jamais sur le GeometryReader lui-même : il rendrait des zones sûres à zéro")
        XCTAssertTrue(code.contains("PhotoFullScreenLayout.screenSize("), "l'écran entier = zone visible + zones sûres")
        XCTAssertTrue(code.contains(".overlay(alignment: .top)"), "la croix et l'icône sont une surimpression collée en haut")
        XCTAssertTrue(code.contains(".overlay(alignment: .bottom)"), "la légende est une surimpression collée en bas")
        XCTAssertTrue(code.contains("footerOverlay(size: size, insets: insets)"))
        XCTAssertTrue(code.contains("LinearGradient("), "dégradé qui fond la photo dans le voile")
        XCTAssertTrue(code.contains("colorPhotoCaptionScrim"), "voile sombre du design system, jamais une couleur en dur")
        XCTAssertTrue(code.contains("accessibilityReduceTransparency"), "« Réduire la transparence » : voile opaque")
        XCTAssertTrue(code.contains(".padding(.bottom, insets.bottom)"), "marge basse = zone sûre lue (au moins le plancher) + 16 pt, calculée par PhotoFullScreenLayout")
        XCTAssertTrue(code.contains("PhotoSafeArea.read(proxy: proxyInsets)"), "les zones sûres de la fenêtre sont lues aussi (le cover peut annoncer 0)")
        XCTAssertTrue(code.contains("PhotoFullScreenLayout.topMargin(safeInset: read.top, isPortrait: portrait)"))
        XCTAssertTrue(code.contains("PhotoFullScreenLayout.bottomMargin(safeInset: read.bottom, isPortrait: portrait)"))
        XCTAssertTrue(code.contains("PhotoFullScreenLayout.footerScrolls("), "défilement décidé par un calcul pur")
        XCTAssertTrue(code.contains("frame(minHeight: cap - PhotoFullScreenLayout.fadeHeight, alignment: .bottom)"), "défilante, la légende reste ANCRÉE EN BAS : jamais de vide noir sous le bouton")
        XCTAssertTrue(code.contains(".accessibilitySortPriority(1)"), "la croix est lue en premier")
        XCTAssertTrue(code.contains(".hidesInAppSwitcher()"), "le voile des applications récentes est conservé")
        // Ordre : la photo (dessous), puis la légende et la barre du haut en surimpression.
        let photoAt = try XCTUnwrap(code.range(of: "photo(size: size)")?.lowerBound)
        let topAt = try XCTUnwrap(code.range(of: ".overlay(alignment: .top)")?.lowerBound)
        let bottomAt = try XCTUnwrap(code.range(of: ".overlay(alignment: .bottom)")?.lowerBound)
        XCTAssertLessThan(photoAt, topAt)
        XCTAssertLessThan(topAt, bottomAt)
    }

    func testTheScreenSizeIsTheVisibleAreaPlusTheSafeAreas() {
        let size = PhotoFullScreenLayout.screenSize(visible: CGSize(width: 390, height: 751), top: 59, bottom: 34, leading: 0, trailing: 0)
        XCTAssertEqual(size.width, 390)
        XCTAssertEqual(size.height, 844)
        XCTAssertEqual(PhotoFullScreenLayout.screenSize(visible: CGSize(width: 678, height: 354), top: 0, bottom: 21, leading: 59, trailing: 59), CGSize(width: 796, height: 375))
    }

    // MARK: - Quand la légende défile (calcul pur)

    func testTheCaptionDoesNotScrollOnATallScreenWithNormalTextAndScrollsInLandscapeOrWithHugeText() {
        let estimate = PhotoFullScreenLayout.footerEstimate
        let bottom: CGFloat = 34
        let fade = PhotoFullScreenLayout.fadeHeight

        XCTAssertFalse(PhotoFullScreenLayout.footerScrolls(estimate: estimate + bottom + fade, screenHeight: 812), "11 Pro, texte normal : entière")
        XCTAssertFalse(PhotoFullScreenLayout.footerScrolls(estimate: estimate + bottom + fade, screenHeight: 667), "SE, texte normal : entière")
        XCTAssertTrue(PhotoFullScreenLayout.footerScrolls(estimate: estimate + 0 + fade, screenHeight: 375), "paysage : elle défile")
        XCTAssertTrue(PhotoFullScreenLayout.footerScrolls(estimate: estimate * 3 + bottom + fade, screenHeight: 812), "texte maximal : elle défile")
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
        // La légende et le bouton restent serrés (4 pt) ; la marge basse vient de `bottomMargin` (planchers).
        XCTAssertLessThanOrEqual(PhotoFullScreenLayout.captionSpacing, 4, "légende et bouton serrés")
        XCTAssertLessThan(PhotoFullScreenLayout.captionSpacing, Spacing.sm)
        XCTAssertGreaterThan(PhotoFullScreenLayout.fadeHeight, 0, "le dégradé reste")
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        let code = UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8)).joined(separator: "\n")

        XCTAssertTrue(code.contains("spacing: PhotoFullScreenLayout.captionSpacing"))
        XCTAssertFalse(code.contains("captionBottomPadding"))
        XCTAssertFalse(code.contains("minHeight: Spacing.minTouchTarget + Spacing.xs"), "le bouton « Voir où c'était » ne gagne plus de hauteur : 44 pt")
        XCTAssertTrue(code.contains("minHeight: Spacing.minTouchTarget)"))
    }

    // MARK: - Planchers des zones sûres (retour du PM, TestFlight 32 : croix écrasée contre l'heure, bouton collé au bord bas)

    func testSafeAreaFloorsHoldWhenTheCoverReportsZeroInsets() {
        // Cas du fullScreenCover sur un vrai appareil : zones sûres nulles. La croix reste sous l'encoche, le bouton au-dessus de la barre d'accueil.
        XCTAssertEqual(PhotoFullScreenLayout.topMargin(safeInset: 0, isPortrait: true), 47 + 12)
        XCTAssertEqual(PhotoFullScreenLayout.bottomMargin(safeInset: 0, isPortrait: true), 34 + 16)
    }

    func testSafeAreaFloorsWithANotchedPhone() {
        // iPhone 11 Pro : 47 pt en haut, 34 pt en bas.
        XCTAssertEqual(PhotoFullScreenLayout.topMargin(safeInset: 47, isPortrait: true), 59)
        XCTAssertEqual(PhotoFullScreenLayout.bottomMargin(safeInset: 34, isPortrait: true), 50)
    }

    func testSafeAreaFloorsNeverShrinkALargerRealInset() {
        // Île dynamique : 59 pt en haut ; la vraie valeur gagne sur le plancher.
        XCTAssertEqual(PhotoFullScreenLayout.topMargin(safeInset: 59, isPortrait: true), 71)
        XCTAssertEqual(PhotoFullScreenLayout.bottomMargin(safeInset: 40, isPortrait: true), 56)
    }

    func testSafeAreaFloorsDoNotApplyInLandscapeAndPortraitDetection() {
        XCTAssertEqual(PhotoFullScreenLayout.topMargin(safeInset: 0, isPortrait: false), 12)
        XCTAssertEqual(PhotoFullScreenLayout.bottomMargin(safeInset: 21, isPortrait: false), 21 + 16)
        XCTAssertTrue(PhotoFullScreenLayout.isPortrait(CGSize(width: 390, height: 844)))
        XCTAssertFalse(PhotoFullScreenLayout.isPortrait(CGSize(width: 844, height: 390)))
    }

    func testTheWindowSafeAreaIsReadOnlyAndTheDebugOverrideIsDebugOnly() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Feed/PhotoSafeArea.swift")
        let raw = try String(contentsOf: url, encoding: .utf8)
        XCTAssertTrue(raw.contains("safeAreaInsets"))
        XCTAssertTrue(raw.contains("max(proxy.top, window.top)"), "le plus grand des deux, côté par côté")
        let debugAt = try XCTUnwrap(raw.range(of: "#if DEBUG")?.lowerBound)
        let overrideAt = try XCTUnwrap(raw.range(of: "forceZeroSafeArea")?.lowerBound)
        let endAt = try XCTUnwrap(raw.range(of: "#endif")?.lowerBound)
        XCTAssertLessThan(debugAt, overrideAt)
        XCTAssertLessThan(overrideAt, endAt, "la simulation de marges nulles n'existe qu'en Debug")
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
