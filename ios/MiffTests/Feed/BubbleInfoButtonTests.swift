// Les assertions citent des lignes de code : elles dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// COULEURS-2 : le petit bouton « i » de la bulle du Feed (décision du PM du 2026-11-13, elle remplace « toucher la bulle ») : EN DEHORS de la bulle,
/// son bas aligné sur celui de la bulle et sa droite sur celle des cartes, de la couleur de la bulle, immobile ; zone de toucher d'au moins 44 pt, aucun recouvrement du contenu (donc ni « Contacter », ni « Reprendre le partage », même à la plus grande taille
/// de texte), contraste sur chaque teinte, libellé parlé, présence dans chaque variante, aucune réaction au toucher de la bulle, navigation.
/// Miroir des tests Android du même changement.
final class BubbleInfoButtonTests: XCTestCase {
    private struct GlyphOnTint {
        let name: String
        let glyph: ColorToken
        let tint: ColorToken
    }

    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func source(_ relative: String) throws -> String {
        try String(contentsOf: iosRoot.appendingPathComponent("Miff").appendingPathComponent(relative), encoding: .utf8)
    }

    private func catalogValue(_ key: String, _ language: String) throws -> String? {
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        let localizations = strings[key]?["localizations"] as? [String: Any]
        let unit = (localizations?[language] as? [String: Any])?["stringUnit"] as? [String: Any]
        return unit?["value"] as? String
    }

    func testTheTouchAreaOfTheInfoButtonIsAtLeast44PointsEvenThoughTheDiscIsSmaller() {
        XCTAssertGreaterThanOrEqual(BubbleLayout.infoTouchSize, Spacing.minTouchTarget)
        XCTAssertGreaterThanOrEqual(BubbleLayout.infoTouchSize, 44)
        XCTAssertLessThan(BubbleLayout.infoDiscSize, BubbleLayout.infoTouchSize, "un disque discret")
        XCTAssertLessThan(BubbleLayout.infoGlyphSize, BubbleLayout.infoDiscSize)
    }

    /// Décision du PM : le bas du bouton est aligné sur le bas de la bulle, sa droite sur la droite des cartes (la boîte de la bulle a la largeur des
    /// cartes : même marge de la liste).
    func testTheButtonBottomIsTheBottomOfTheBubbleAndItsRightIsTheRightOfTheCards() throws {
        for box in [CGSize(width: 288, height: 264), CGSize(width: 361, height: 264), CGSize(width: 361, height: 650)] {
            let frames = BubbleLayout.infoButtonFrames(box: box)

            XCTAssertEqual(frames.disc.maxY, box.height, accuracy: 0.001, "bas du disque = bas de la bulle")
            XCTAssertEqual(frames.disc.maxX, box.width, accuracy: 0.001, "droite du disque = droite de la boîte (celle des cartes)")
            XCTAssertEqual(frames.touch.maxY, box.height, accuracy: 0.001)
            XCTAssertEqual(frames.touch.maxX, box.width, accuracy: 0.001)
        }
        let list = UpdatePrivacyTests.codeLines(try source("Views/Feed/FeedListView.swift")).joined(separator: "\n")
        let blob = UpdatePrivacyTests.codeLines(try source("Views/Feed/FeedBubbleView.swift")).joined(separator: "\n")

        XCTAssertTrue(list.contains("BubbleView(") && list.contains("MemberCardView("), "la bulle et les cartes sont dans la même colonne")
        XCTAssertTrue(blob.contains(".frame(maxWidth: .infinity, minHeight: BubbleLayout.minContentHeight"), "la boîte de la bulle prend toute la largeur de la colonne")
        XCTAssertTrue(list.contains(".padding(.horizontal, Spacing.md)"), "une seule marge de liste pour la bulle et les cartes")
    }

    /// Le bouton est EN DEHORS de la bulle : le disque et son contour ne touchent pas la tache, quelle que soit la hauteur de la bulle, même
    /// avec l'inclinaison de la tache.
    func testTheButtonIsOutsideTheShapeOfTheBubble() {
        for box in [CGSize(width: 288, height: 264), CGSize(width: 361, height: 264), CGSize(width: 361, height: 650), CGSize(width: 390, height: 900)] {
            let disc = BubbleLayout.infoButtonFrames(box: box).disc
            let shape = FeedBubbleShape().path(in: CGRect(origin: .zero, size: box))
            let center = CGPoint(x: disc.midX, y: disc.midY)

            XCTAssertFalse(shape.contains(center), "le centre du disque est dans la tache : \(box)")
            for step in 0..<24 {
                let angle = Double(step) * .pi / 12
                let point = CGPoint(x: center.x + disc.width / 2 * CGFloat(cos(angle)), y: center.y + disc.height / 2 * CGFloat(sin(angle)))

                XCTAssertFalse(shape.contains(point), "le bord du disque entre dans la tache : \(box), \(step)")
            }
        }
    }

    func testTheButtonStaysOutOfTheMotionOfTheBubble() throws {
        let code = UpdatePrivacyTests.codeLines(try source("Views/Feed/FeedBubbleView.swift")).joined(separator: "\n")
        let drift = try XCTUnwrap(code.range(of: ".offset(y: motion ? BubbleMotion.drift(blob) : 0)"))
        let button = try XCTUnwrap(code.range(of: "BubbleInfoButton(level: level, action: onInfo)"))

        XCTAssertGreaterThan(button.lowerBound, drift.upperBound, "le bouton est posé après le mouvement et la dérive de la tache : immobile")
        XCTAssertTrue(code.contains(".overlay(alignment: .bottomTrailing) {"))
    }

    func testTheButtonIsTheColourOfTheLevelShownByTheBubble() throws {
        let code = UpdatePrivacyTests.codeLines(try source("Views/Feed/BubbleInfoButton.swift")).joined(separator: "\n")

        XCTAssertTrue(code.contains("Circle().fill(level.tint)"), "le disque a la teinte de la bulle")
        XCTAssertTrue(code.contains(".foregroundColor(level.onTint)"), "le « i » a la couleur du texte de la bulle")
        let feed = UpdatePrivacyTests.codeLines(try source("Views/Feed/FeedBubbleView.swift")).joined(separator: "\n")
        XCTAssertTrue(feed.contains("BubbleBlob(level: current.level, motion: motion, onInfo: onInfo)"), "il suit la page du carrousel")
    }

    func testTheTouchAreaNeverCoversTheContentEvenAtTheLargestTextSize() {
        XCTAssertTrue(BubbleLayout.infoButtonFitsBottomBand, "la zone de toucher recouvrirait le contenu")
        for height in [20.0, 100, 175, 400, 900] {
            let size = CGSize(width: 239, height: height)
            let outer = BubbleLayout.outerSize(content: size)
            let frame = BubbleLayout.contentFrame(content: size)
            let touch = BubbleLayout.infoButtonFrames(box: outer).touch

            XCTAssertLessThanOrEqual(frame.maxY + BubbleMotion.driftPoints, touch.minY + 0.001, "le bouton « i » chevauche un contenu de \(height) pt de haut")
        }
    }

    func testAShortBubbleKeepsItsHeight() {
        let outer = BubbleLayout.outerSize(content: CGSize(width: 100, height: 20))

        XCTAssertEqual(outer.height, 264, accuracy: 0.001, "la hauteur d'une bulle courte n'a pas changé")
    }

    private func blend(_ top: Int, over below: Int, alpha: Double) -> Int {
        Int((Double(top) * alpha + Double(below) * (1 - alpha)).rounded())
    }

    /// La couleur du glyphe est celle du texte posé sur la teinte du niveau : le contraste est celui du texte de la bulle, sur CHAQUE variante.
    func testTheInfoGlyphIsReadableOnEveryTintOfTheBubble() {
        let tints = [
            GlyphOnTint(name: "vert", glyph: colorStatusSafeText, tint: colorStatusSafeTint),
            GlyphOnTint(name: "orange", glyph: colorStatusWarningText, tint: colorStatusWarningTint),
            GlyphOnTint(name: "rouge", glyph: colorStatusDangerText, tint: colorStatusDangerTint),
            GlyphOnTint(name: "gris", glyph: colorTextForm, tint: colorStatusNeutralTint)
        ]
        for entry in tints {
            let tint = entry.tint
            // La teinte est semi-transparente : on la pose sur le fond de l'app.
            let red = blend(tint.red, over: colorBgCanvas.red, alpha: tint.alpha)
            let green = blend(tint.green, over: colorBgCanvas.green, alpha: tint.alpha)
            let blue = blend(tint.blue, over: colorBgCanvas.blue, alpha: tint.alpha)
            let flat = ColorToken(hex: UInt32(red << 16 | green << 8 | blue))

            XCTAssertGreaterThanOrEqual(entry.glyph.contrastRatio(with: flat), 4.5, "bulle \(entry.name)")
        }
    }

    func testEveryBubbleCarriesTheButtonAndTheBubbleItselfReactsToNoTouch() throws {
        let code = UpdatePrivacyTests.codeLines(try source("Views/Feed/FeedBubbleView.swift")).joined(separator: "\n")
        let blobLines = code.components(separatedBy: "\n").filter { $0.contains("BubbleBlob(") }

        // Les sept variantes calmes et chaque page d'un carrousel passent par `BubbleBlob` ; chaque usage donne le bouton « i ».
        XCTAssertEqual(blobLines.count, 2)
        XCTAssertTrue(blobLines.allSatisfy { $0.contains("onInfo:") }, "chaque BubbleBlob reçoit onInfo")
        XCTAssertTrue(code.contains("BubbleBlob(level: current.level, motion: motion, onInfo: onInfo)"), "le bouton suit la page du carrousel")
        XCTAssertTrue(code.contains("BubbleInfoButton(level: level, action: onInfo)"))
        XCTAssertFalse(code.contains("onTapGesture"), "toucher la bulle ne fait rien")
        XCTAssertFalse(code.contains("TapGesture"), "toucher la bulle ne fait rien")
        // Les deux boutons de la bulle gardent leur action propre.
        XCTAssertTrue(code.contains("onResumeSharing: actions.onResumeSharing"))
        XCTAssertTrue(code.contains("AlertLines(page: current, texts: texts, onMessage: onMessage)"))
    }

    func testTheInfoButtonIsNamedByTheSpokenLabelNeverBareI() throws {
        let code = UpdatePrivacyTests.codeLines(try source("Views/Feed/BubbleInfoButton.swift")).joined(separator: "\n")

        XCTAssertTrue(code.contains(".accessibilityLabel(Text(\"feed_colors_bubble_action_a11y\"))"))
        XCTAssertEqual(try catalogValue("feed_colors_bubble_action_a11y", "fr"), "Comprendre les couleurs")
        XCTAssertEqual(try catalogValue("feed_colors_bubble_action_a11y", "en"), "Understand the colors")
    }

    // MARK: - La navigation (E7)

    func testTheColoursScreenIsPushedOnTheHostStackAndBelongsToNoMiff() {
        XCTAssertFalse(MiffRoute.colorsInfo.isSettingsScreen)
        XCTAssertFalse(MiffRoute.colorsInfo.isNotificationScreen)
        XCTAssertFalse(MiffRoute.colorsInfo.isMyInfoScreen)
        XCTAssertFalse(MiffRoute.colorsInfo.belongs(to: "anyMiff"), "reste ouvert quand l'accès à une Miff est perdu")
    }

    func testTwoQuickTouchesOpenOneScreenAndOneBackReturnsToTheFeed() {
        var path: [MiffRoute] = []

        path.openOnce(.colorsInfo)
        path.openOnce(.colorsInfo)

        XCTAssertEqual(path, [.colorsInfo])
        path.removeLast()
        XCTAssertTrue(path.isEmpty)
    }

    func testOpeningAnotherScreenAfterTheColoursScreenStillStacks() {
        var path: [MiffRoute] = [.colorsInfo]

        path.openOnce(.settings(.settings))

        XCTAssertEqual(path, [.colorsInfo, .settings(.settings)])
    }
}
// swiftlint:enable line_length
