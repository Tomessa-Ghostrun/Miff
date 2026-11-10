import XCTest
@testable import Miff

/// Les textes des lieux (ticket LOC-3 ; critère TRA1) : « zone de danger » partout, jamais « zone à risque » (décision du PM), le
/// tutoiement, les mêmes valeurs insérées en français et en anglais, et chaque clé que le code des lieux utilise existe.
final class PlaceTextsTests: XCTestCase {
    private typealias Catalog = [String: [String: Any]]

    private func loadCatalog() throws -> Catalog {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Place
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Catalogue source introuvable (\(url.path)).")
        }
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? Catalog)
    }

    /// Tous les textes d'une langue pour une clé (un seul texte, ou une variante par forme de pluriel).
    private func values(_ entry: [String: Any], language: String) -> [String] {
        let localization = (entry["localizations"] as? [String: Any])?[language] as? [String: Any]
        if let unit = localization?["stringUnit"] as? [String: Any], let value = unit["value"] as? String {
            return [value]
        }
        let plural = (localization?["variations"] as? [String: Any])?["plural"] as? [String: Any] ?? [:]
        return plural.values.compactMap { variant in
            ((variant as? [String: Any])?["stringUnit"] as? [String: Any])?["value"] as? String
        }
    }

    private func placeTexts(_ language: String) throws -> [String: [String]] {
        let catalog = try loadCatalog()
        var texts: [String: [String]] = [:]
        for (key, entry) in catalog where key.hasPrefix("place_") {
            texts[key] = values(entry, language: language)
        }
        return texts
    }

    func testEveryTextOfTheCategoriesAndTheTypesExistsInBothLanguages() throws {
        let french = try placeTexts("fr")
        let english = try placeTexts("en")
        let keys = PlaceCategory.allCases.map { $0.labelKey } + [
            "place_type_trusted", "place_type_danger", "place_type_trusted_desc", "place_type_danger_desc"
        ]

        for key in keys {
            XCTAssertFalse((french[key] ?? []).filter { !$0.isEmpty }.isEmpty, "\(key) : français manquant")
            XCTAssertFalse((english[key] ?? []).filter { !$0.isEmpty }.isEmpty, "\(key) : anglais manquant")
        }
        XCTAssertGreaterThan(french.count, 100, "les clés place_* du catalogue (viennent de LOC-1)")
    }

    func testTheDangerZoneIsNeverCalledARiskZone() throws {
        for (key, texts) in try placeTexts("fr") {
            for text in texts {
                XCTAssertFalse(text.lowercased().contains("zone à risque"), "\(key) : « zone de danger » seulement")
                XCTAssertFalse(text.lowercased().contains("zones à risque"), "\(key)")
            }
        }
        for (key, texts) in try placeTexts("en") {
            for text in texts {
                XCTAssertFalse(text.lowercased().contains("risk zone"), "\(key) : « danger zone » only")
            }
        }
        XCTAssertEqual(PlaceType.risk.label.lowercased().contains("danger"), true)
    }

    func testTheFrenchTextsAreInTheInformalYouNeverTheFormalOne() throws {
        let formal = ["vous ", "votre ", "vos ", "choisissez", "entrez", "sélectionnez", "veuillez"]
        for (key, texts) in try placeTexts("fr") {
            for text in texts {
                let lowered = text.lowercased()
                for word in formal {
                    XCTAssertFalse(lowered.contains(word), "\(key) : vouvoiement (« \(word) »)")
                }
            }
        }
    }

    func testFrenchAndEnglishInsertTheSameValuesInTheSamePlaces() throws {
        let pattern = try NSRegularExpression(pattern: "%(?:\\d+\\$)?(?:lld|@|d)")
        func placeholders(_ text: String) -> Set<String> {
            let range = NSRange(text.startIndex..., in: text)
            let found = pattern.matches(in: text, range: range).compactMap { Range($0.range, in: text) }
            return Set(found.map { String(text[$0]) })
        }
        let french = try placeTexts("fr")
        let english = try placeTexts("en")

        for (key, texts) in french {
            let inFrench = texts.reduce(into: Set<String>()) { $0.formUnion(placeholders($1)) }
            let inEnglish = (english[key] ?? []).reduce(into: Set<String>()) { $0.formUnion(placeholders($1)) }
            XCTAssertEqual(inFrench, inEnglish, "\(key) : valeurs insérées différentes entre fr et en")
        }
    }

    func testTheKeysTheCodeOfThePlacesUsesAllExist() throws {
        let catalog = try loadCatalog()
        let sources = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Place
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
        guard let files = FileManager.default.enumerator(at: sources, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables (\(sources.path)).")
        }
        let regex = try NSRegularExpression(pattern: "\"(place_[a-z0-9_]+)\"")
        var used = Set<String>()
        for case let file as URL in files where file.pathExtension == "swift" {
            let text = try String(contentsOf: file, encoding: .utf8)
            for match in regex.matches(in: text, range: NSRange(text.startIndex..., in: text)) {
                if let range = Range(match.range(at: 1), in: text) {
                    used.insert(String(text[range]))
                }
            }
        }

        XCTAssertGreaterThan(used.count, 60)
        for key in used.sorted() {
            XCTAssertNotNil(catalog[key], "Clé « \(key) » utilisée par les lieux mais absente du catalogue")
        }
    }

    // MARK: - Les emoji des catégories (décision du PM, 2026-11-10)

    /// La table COMMUNE avec Android : la changer ici sans la changer là-bas ferait voir deux catégories pour un même lieu.
    func testEveryCategoryHasItsEmojiTheSameAsOnAndroid() {
        let expected: [PlaceCategory: String] = [
            .home: "🏠", .work: "💼", .school: "🏫", .sport: "⚽",
            .relative: "👨‍👩‍👧", .leisure: "🎉", .other: "📍"
        ]

        XCTAssertEqual(Set(PlaceCategory.allCases), Set(expected.keys))
        for category in PlaceCategory.allCases {
            XCTAssertEqual(category.emoji, expected[category], "\(category)")
        }
    }

    func testEachEmojiIsOneDistinctCharacterOnScreen() {
        let emojis = PlaceCategory.allCases.map { $0.emoji }

        XCTAssertEqual(Set(emojis).count, PlaceCategory.allCases.count, "deux catégories ne partagent jamais un emoji")
        for emoji in emojis {
            XCTAssertEqual(emoji.count, 1, "un seul caractère affiché (la famille est une séquence collée)")
        }
    }
}
