// Les assertions citent des lignes de code : elles dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// COULEURS-2 : le petit bouton « i » en bas à droite de la bulle du Feed (décision du PM du 2026-11-13, elle remplace « toucher la bulle ») :
/// zone de toucher d'au moins 44 pt, aucun recouvrement du contenu (donc ni « Contacter », ni « Reprendre le partage », même à la plus grande taille
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

    func testTheTouchAreaOfTheInfoButtonIsAtLeast44PointsEvenThoughTheGlyphIsSmall() {
        XCTAssertGreaterThanOrEqual(BubbleLayout.infoTouchSize, Spacing.minTouchTarget)
        XCTAssertGreaterThanOrEqual(BubbleLayout.infoTouchSize, 44)
        XCTAssertLessThan(BubbleLayout.infoGlyphSize, BubbleLayout.infoTouchSize, "un glyphe discret")
    }

    func testTheInfoButtonLivesInTheBottomBandWhereTheContentNeverGoes() {
        XCTAssertTrue(BubbleLayout.infoButtonFitsBottomBand, "la zone de toucher recouvrirait le contenu")
        for height in [20.0, 100, 175, 400, 900] {
            let size = CGSize(width: 239, height: height)
            let outer = BubbleLayout.outerSize(content: size)
            let frame = BubbleLayout.contentFrame(content: size)
            let touchTop = outer.height - BubbleLayout.infoBottomInset - BubbleLayout.infoTouchSize

            XCTAssertLessThanOrEqual(frame.maxY, touchTop + 0.001, "le bouton « i » chevauche un contenu de \(height) pt de haut")
        }
    }

    func testTheInfoButtonStaysInsideTheBubbleOnTheNarrowestPhone() {
        // 320 pt moins les marges de la liste (16 de chaque côté) : la zone de toucher reste à l'intérieur de la boîte de la bulle.
        let width: CGFloat = 320 - 2 * Spacing.md

        XCTAssertGreaterThan(width - BubbleLayout.infoTrailingInset - BubbleLayout.infoTouchSize, 0)
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
