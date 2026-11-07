import XCTest
@testable import Miff

/// Tokens de couleur : valeurs du design system, contraste du texte d'erreur, et identité avec Android
/// (mêmes noms de code, mêmes valeurs : critère I8).
final class ColorTokensTests: XCTestCase {
    // MARK: - Valeurs de design/design-system.md

    func testBrandAndActionColors() {
        XCTAssertEqual(colorBrandPrimary.hexString, "#D1D828")
        XCTAssertEqual(colorActionPrimary.hexString, "#043CD1")
        XCTAssertEqual(colorActionPrimaryTint20, ColorToken(hex: 0x043CD1, alpha: 0.20))
        XCTAssertEqual(colorTextOnAction.hexString, "#FFFFFF")
    }

    func testStatusColors() {
        XCTAssertEqual(colorStatusSafe.hexString, "#D1D828")
        XCTAssertEqual(colorStatusWarning.hexString, "#FFB650")
        // Rouge de STATUT (anneaux, pastilles, fonds) : volontairement pas assombri. Le texte d'erreur a le sien.
        XCTAssertEqual(colorStatusDanger.hexString, "#FF5650")
        XCTAssertEqual(colorStatusDangerTint.alphaPercent, 30)
    }

    func testErrorTextColorIsTheValidatedOne() {
        XCTAssertEqual(colorTextError.hexString, "#D60700")
    }

    func testAlphaPercentIsRoundedLikeTheDesignSystem() {
        XCTAssertEqual(colorTextDescription.alphaPercent, 70)
        XCTAssertEqual(colorActionPrimaryTint5.alphaPercent, 5)
        XCTAssertEqual(colorBrandPrimary.alphaPercent, 100)
    }

    // MARK: - Contraste WCAG

    func testErrorTextHasAtLeast4Point5ContrastOnEveryBackground() {
        let backgrounds = [
            ("colorBgSurface", colorBgSurface),
            ("colorBgCanvas", colorBgCanvas),
            ("colorBgGradientEnd", colorBgGradientEnd)
        ]
        for (name, background) in backgrounds {
            let ratio = colorTextError.contrastRatio(with: background)
            XCTAssertGreaterThanOrEqual(ratio, 4.5, "colorTextError sur \(name) : contraste \(ratio), sous 4,5")
        }
    }

    func testContrastRatioMatchesTheWCAGReferenceValues() {
        XCTAssertEqual(colorTextPrimary.contrastRatio(with: colorBgSurface), 21, accuracy: 0.001)
        XCTAssertEqual(colorBgSurface.contrastRatio(with: colorBgSurface), 1, accuracy: 0.001)
    }

    // MARK: - Liste complète

    func testTheGalleryListsEveryColorOnceWithUniqueNames() {
        let names = ColorTokens.all.map(\.name)

        XCTAssertEqual(names.count, 29)
        XCTAssertEqual(Set(names).count, names.count)
    }

    // MARK: - Identité avec Android

    private func androidThemeFile(_ name: String) throws -> String {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .deletingLastPathComponent() // racine du dépôt
        let url = root.appendingPathComponent("android/app/src/main/java/com/tomessa/miff/ui/theme/\(name)")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Fichier Android introuvable (\(url.path)) : comparaison impossible sur ce poste.")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    private func matches(_ pattern: String, in text: String) throws -> [[String]] {
        let regex = try NSRegularExpression(pattern: pattern, options: [.anchorsMatchLines])
        let range = NSRange(text.startIndex..., in: text)
        return regex.matches(in: text, range: range).map { match in
            (1..<match.numberOfRanges).compactMap { index in
                Range(match.range(at: index), in: text).map { String(text[$0]) }
            }
        }
    }

    func testColorsAreIdenticalToAndroid() throws {
        let source = try androidThemeFile("Color.kt")
        var expected: [String: ColorToken] = [:]
        for groups in try matches("val (color\\w+) = Color\\(0xFF([0-9A-Fa-f]{6})\\)", in: source) {
            let hex = try XCTUnwrap(UInt32(groups[1], radix: 16))
            expected[groups[0]] = ColorToken(hex: hex)
        }
        for groups in try matches("val (color\\w+) = (color\\w+)\\.copy\\(alpha = ([0-9.]+)f\\)", in: source) {
            let base = try XCTUnwrap(expected[groups[1]], "base inconnue : \(groups[1])")
            expected[groups[0]] = base.withAlpha(try XCTUnwrap(Double(groups[2])))
        }
        for groups in try matches("val (color\\w+) = (color\\w+)[ \\t]*$", in: source) {
            expected[groups[0]] = try XCTUnwrap(expected[groups[1]])
        }

        let names = Set(ColorTokens.all.map(\.name))
        XCTAssertEqual(names, Set(expected.keys), "Les noms de tokens diffèrent d'Android")
        for entry in ColorTokens.all {
            let android = try XCTUnwrap(expected[entry.name], entry.name)
            XCTAssertEqual(entry.value.hexString, android.hexString, "\(entry.name) : couleur")
            XCTAssertEqual(entry.value.alpha, android.alpha, accuracy: 0.0001, "\(entry.name) : opacité")
        }
    }

    func testTextStylesAreIdenticalToAndroid() throws {
        let source = try androidThemeFile("Type.kt")
        var expected: [String: (size: Double, line: Double)] = [:]
        for groups in try matches("val (text\\w+) = style\\(\\w+, (\\d+), (\\d+)", in: source) {
            expected[groups[0]] = (try XCTUnwrap(Double(groups[1])), try XCTUnwrap(Double(groups[2])))
        }

        let names = Set(TextStyleTokens.all.map(\.name))
        XCTAssertEqual(names, Set(expected.keys), "Les noms de styles diffèrent d'Android")
        for token in TextStyleTokens.all {
            let android = try XCTUnwrap(expected[token.name], token.name)
            XCTAssertEqual(Double(token.size), android.size, "\(token.name) : taille")
            XCTAssertEqual(Double(token.lineHeight), android.line, "\(token.name) : interligne")
        }
    }

    func testRadiiAndShadowsAreIdenticalToAndroid() {
        XCTAssertEqual(radiusMd, 10)
        XCTAssertEqual(radiusSm, 5)
        XCTAssertEqual(shadowDefault.offsetY, 4)
        XCTAssertEqual(shadowDefault.blur, 4)
        XCTAssertEqual(shadowDefault.color, ColorToken(hex: 0x000000, alpha: 0.25))
        XCTAssertEqual(shadowSubtle.blur, 3)
        XCTAssertEqual(shadowSubtle.color, ColorToken(hex: 0x000000, alpha: 0.08))
    }
}
