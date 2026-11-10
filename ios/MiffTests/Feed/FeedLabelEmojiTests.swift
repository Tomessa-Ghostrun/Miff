// Les assertions citent les phrases réelles du catalogue : leurs lignes sont plus longues que la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// L'emoji de l'étiquette « dans un lieu de confiance » du Feed (décision du PM, 2026-11-10 ; miroir d'Android) : avant les mots, seulement
/// quand le lieu est connu avec une catégorie ; jamais pour « A quitté… », une zone de danger, « Pas encore de nouvelles ».
final class FeedLabelEmojiTests: XCTestCase {
    func testTheEmojiOfTheCategoryComesBeforeTheWordsOfAnAtLabelOnly() {
        let places = [
            feedPlace("p1", category: .home),
            feedPlace("p2", name: "Chez Mamie", category: .other),
            feedPlace("p3", name: "Le club", category: .leisure),
            feedPlace("p4", name: "Vieux lieu", category: nil)
        ]
        let members = [feedMember("a"), feedMember("b"), feedMember("c"), feedMember("d"), feedMember("e"), feedMember("f"), feedMember("me")]
        let statuses = [
            "a": feedStatus(.inTrusted, placeId: "p1"),
            "b": feedStatus(.inTrusted, placeId: "p2"),
            "c": feedStatus(.inTrusted, placeId: "p3"),
            "d": feedStatus(.inTrusted, placeId: "p4"),
            "e": feedStatus(.inTrusted, placeId: "gone"),
            "f": feedStatus(.out, placeId: "p1", departedAgo: feedMinutes(10))
        ]

        let content = buildFeed(feedInput(members: members, statuses: statuses, places: places))

        XCTAssertEqual(content.card("a")?.labelCategory, .home)
        XCTAssertEqual(content.card("b")?.labelCategory, .other)
        XCTAssertEqual(content.card("c")?.labelCategory, .leisure)
        XCTAssertNil(content.card("d")?.labelCategory, "lieu sans catégorie : le texte seul, jamais « Autre »")
        XCTAssertNil(content.card("e")?.labelCategory, "lieu supprimé : le texte seul")
        XCTAssertNil(content.card("f")?.labelCategory, "« A quitté… » n'a pas d'emoji")
        XCTAssertNil(content.card("me")?.labelCategory, "pas de statut : pas d'emoji")
    }

    func testNoEmojiInADangerZoneOrWithoutNews() {
        let places = [feedPlace("p1", category: .home)]
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let statuses = ["a": feedStatus(.inRisk, placeId: "p1")]

        let content = buildFeed(feedInput(members: members, statuses: statuses, places: places))

        XCTAssertNil(content.card("a")?.labelCategory)
        XCTAssertNil(content.card("b")?.labelCategory)
    }
}
