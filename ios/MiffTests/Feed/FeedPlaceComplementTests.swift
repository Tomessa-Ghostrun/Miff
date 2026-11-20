import XCTest
@testable import Miff

/// Le complément de lieu des phrases (« A quitté la maison », « Thomas a rejoint l'école à 16 h 02 ») est une clé COMPLÈTE, préposition et
/// article compris, par catégorie ET par sens (`feed_place_leave_*` après « a quitté », `feed_place_arrive_*` après « a rejoint »). Le code ne
/// colle jamais un article ni une préposition à un nom de lieu : en espagnol, italien, portugais et allemand, ils se contractent et changent de
/// forme (« del », « al », « do », « na », accusatif ; docs/i18n/glossaire.md §5). Les phrases françaises et anglaises restent exactement celles
/// d'avant.
final class FeedPlaceComplementTests: XCTestCase {
    private func lookup(_ language: String) throws -> (String) -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        return { key in
            let localization = (strings[key]?["localizations"] as? [String: Any])?[language] as? [String: Any]
            return (localization?["stringUnit"] as? [String: Any])?["value"] as? String ?? "MISSING:\(key)"
        }
    }

    private func texts(_ language: String) throws -> FeedTexts {
        FeedTexts(
            lookup: try lookup(language),
            locale: Locale(identifier: language == "fr" ? "fr_FR" : "en_US"),
            zone: TimeZone(identifier: "Europe/Paris") ?? .current,
            is24Hour: true
        )
    }

    private struct Category {
        let kind: PlaceKind
        let french: String
        let english: String
        let englishArrive: String
    }

    private let categories = [
        Category(kind: .home, french: "la maison", english: "home", englishArrive: "at home"),
        Category(kind: .work, french: "le travail", english: "work", englishArrive: "at work"),
        Category(kind: .school, french: "l'école", english: "school", englishArrive: "at school"),
        Category(kind: .sport, french: "le sport", english: "the gym", englishArrive: "at the gym"),
        Category(
            kind: .relative,
            french: "le domicile d'un proche",
            english: "a loved one's place",
            englishArrive: "at a loved one's place"
        )
    ]

    func testFrenchComplementsAreTheSameAfterLeavingAndArrivingAsBefore() throws {
        let fr = try texts("fr")

        for item in categories {
            XCTAssertEqual(fr.placeComplement(.category(item.kind), movement: .leave), item.french)
            XCTAssertEqual(fr.placeComplement(.category(item.kind), movement: .arrive), item.french)
        }
        XCTAssertEqual(fr.placeComplement(.unknown, movement: .leave), "un lieu de confiance")
        XCTAssertEqual(fr.placeComplement(.unknown, movement: .arrive), "un lieu de confiance")
    }

    /// En anglais la préposition d'arrivée (« at ») est DANS le complément, comme « de » ou « a » le seront dans les autres langues.
    func testEnglishComplementsCarryTheirOwnPrepositionAfterArriving() throws {
        let en = try texts("en")

        for item in categories {
            XCTAssertEqual(en.placeComplement(.category(item.kind), movement: .leave), item.english)
            XCTAssertEqual(en.placeComplement(.category(item.kind), movement: .arrive), item.englishArrive)
        }
        XCTAssertEqual(en.placeComplement(.unknown, movement: .leave), "a trusted place")
        XCTAssertEqual(en.placeComplement(.unknown, movement: .arrive), "at a trusted place")
    }

    /// Le nom saisi par la personne passe tel quel (jamais traduit, jamais décliné) ; même un « % » dans le nom reste un simple caractère.
    func testANamedPlaceIsTheNameAsTypedInTheComplementOfItsLanguage() throws {
        let fr = try texts("fr")
        let en = try texts("en")

        XCTAssertEqual(fr.placeComplement(.named("Chez Mamie"), movement: .leave), "Chez Mamie")
        XCTAssertEqual(fr.placeComplement(.named("Chez Mamie"), movement: .arrive), "Chez Mamie")
        XCTAssertEqual(en.placeComplement(.named("Grandma's"), movement: .leave), "Grandma's")
        XCTAssertEqual(en.placeComplement(.named("Grandma's"), movement: .arrive), "at Grandma's")
        XCTAssertEqual(fr.placeComplement(.named("100 %@ salle"), movement: .leave), "100 %@ salle")
    }

    func testTheSentencesAreTheSameAsBefore() throws {
        let fr = try texts("fr")
        let en = try texts("en")

        XCTAssertEqual(fr.label(.left(.category(.school))), "A quitté l'école")
        XCTAssertEqual(en.label(.left(.category(.school))), "Left school")
        XCTAssertEqual(fr.label(.left(.named("Chez Mamie"))), "A quitté Chez Mamie")
        XCTAssertEqual(en.label(.left(.named("Chez Mamie"))), "Left Chez Mamie")
        XCTAssertEqual(fr.label(.left(.unknown)), "A quitté un lieu de confiance")
    }

    /// Tout complément de lieu des deux sens existe dans les deux langues du catalogue, avec le même nombre de valeurs que le français.
    func testEveryComplementKeyExistsInEveryLanguageOfTheApp() throws {
        let names = ["home", "work", "school", "sport", "relative", "generic", "named"]
        let keys = names.flatMap { ["feed_place_leave_\($0)", "feed_place_arrive_\($0)"] }
        XCTAssertEqual(keys.count, 14)
        for language in try I18nRegistry.shipped() {
            let found = try lookup(language)
            for key in keys {
                let value = found(key)
                XCTAssertFalse(value.hasPrefix("MISSING"), "\(key) \(language)")
                XCTAssertFalse(value.isEmpty, "\(key) \(language)")
                let hasValue = value.contains("%1$@")
                XCTAssertEqual(hasValue, key.hasSuffix("_named"), "\(key) \(language) : un argument ?")
            }
        }
    }

    /// Le code ne bâtit plus de nom de lieu avec un article : plus de `feed_place_noun_*`, plus de `placeNoun`.
    func testNoCodeStillBuildsAPlaceNounAndTheOldKeysAreGone() throws {
        let sources = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
        let enumerator = try XCTUnwrap(FileManager.default.enumerator(at: sources, includingPropertiesForKeys: nil))
        for case let file as URL in enumerator where ["swift", "xcstrings"].contains(file.pathExtension) {
            let text = try String(contentsOf: file, encoding: .utf8)
            XCTAssertFalse(text.contains("feed_place_noun"), "\(file.lastPathComponent) : ancienne clé de nom de lieu")
            XCTAssertFalse(text.contains("placeNoun"), "\(file.lastPathComponent) : ancienne fonction de nom de lieu")
        }
    }
}
