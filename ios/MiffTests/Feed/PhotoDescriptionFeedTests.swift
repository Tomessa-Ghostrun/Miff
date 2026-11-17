// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import FirebaseFirestore
import XCTest
@testable import Miff

/// La description d'une photo, côté LECTURE et plein écran du Feed (ticket description-update, critères F1 à F6, F8, C2, C3, C5, A3, A4) : ce qui vient
/// du réseau est nettoyé avant d'être montré (quelqu'un d'autre l'a écrit), une ancienne photo (sans clé) reste telle qu'avant, la description est
/// AU-DESSUS de « Photo de … », le pied reste borné à 45 % de la hauteur et défile avec la plus grande police. Miroir de `PhotoDescriptionTest.kt` et
/// de `PhotoFullScreenDescriptionTest.kt` d'Android.
final class PhotoDescriptionFeedTests: XCTestCase {
    private func raw(description: Any?) -> RawPhoto {
        RawPhoto(
            jpeg: Data(repeating: 7, count: 1_000),
            createdAtMillis: feedNow - feedMinutes(20),
            expiresAtMillis: feedNow + feedHours(22),
            lat: 48.85661,
            lng: 2.35222,
            measuredAtMillis: feedNow - feedMinutes(22),
            writtenDescription: description
        )
    }

    private func read(_ description: Any?) throws -> SharedPhoto {
        try XCTUnwrap(SharedPhoto.fromServer(raw(description: description), nowMillis: feedNow))
    }

    // MARK: - Lecture

    func testAPhotoFromTheServerCarriesTheCleanedDescriptionOrNone() throws {
        XCTAssertEqual(try read("Bien arrivé").descriptionText, "Bien arrivé")
        XCTAssertEqual(try read("a\n\u{202E}b").descriptionText, "a b")
        XCTAssertNil(try read(nil).descriptionText)
        XCTAssertNil(try read(42).descriptionText)
        XCTAssertNil(try read("").descriptionText)
        XCTAssertNil(try read(NSNull()).descriptionText)
    }

    func testAnInvalidDescriptionNeverHidesThePhotoItself() throws {
        let photo = try read(String(repeating: "x", count: 500))

        XCTAssertNil(photo.descriptionText, "un texte que les règles n'auraient jamais laissé passer est ignoré")
        XCTAssertEqual(photo.jpeg.count, 1_000)
        XCTAssertNotNil(photo.place)
    }

    func testAPhotoOfTheOldKindHasNoDescriptionAndShowsNothingOfItself() {
        let old = SharedPhoto(jpeg: Data([1, 2, 3]), createdAtMillis: 1, expiresAtMillis: 2, place: nil)
        let withText = SharedPhoto(jpeg: Data([1, 2, 3]), createdAtMillis: 1, expiresAtMillis: 2, place: nil, descriptionText: "secret words")

        XCTAssertNil(old.descriptionText)
        XCTAssertEqual("\(withText)", "SharedPhoto(redacted)")
        XCTAssertFalse("\(withText)".contains("secret"))
        XCTAssertNotEqual(old, withText, "deux photos qui ne diffèrent que par la description ne sont pas égales")
    }

    func testTheFirestoreDocumentIsReadFieldByFieldAndADescriptionOfAnotherTypeIsIgnored() throws {
        let now = Date(timeIntervalSince1970: Double(feedNow) / 1000)
        func fields(_ description: Any?) -> [String: Any] {
            var document: [String: Any] = [
                "jpeg": Data(repeating: 7, count: 1_000),
                "createdAt": Timestamp(date: now.addingTimeInterval(-1_200)),
                "expiresAt": Timestamp(date: now.addingTimeInterval(79_200)),
                "lat": 48.85661, "lng": 2.35222,
                "measuredAt": Timestamp(date: now.addingTimeInterval(-1_320))
            ]
            if let description {
                document["description"] = description
            }
            return document
        }

        for (value, expected) in [("Je suis là", "Je suis là"), ("  Coucou \n ", "Coucou")] {
            guard case .photo(let photo) = FirestorePhotoSource.photo(fields: fields(value), nowMillis: feedNow) else {
                return XCTFail("une photo était attendue")
            }
            XCTAssertEqual(photo.descriptionText, expected)
        }
        for value in [nil, 12, true, ["a"]] as [Any?] {
            guard case .photo(let photo) = FirestorePhotoSource.photo(fields: fields(value), nowMillis: feedNow) else {
                return XCTFail("la photo reste montrée")
            }
            XCTAssertNil(photo.descriptionText)
        }
    }

    // MARK: - Le pied du plein écran

    func testTheDescriptionIsAboveThePhotoTitleThenThePlace() {
        XCTAssertEqual(PhotoFullScreenLayout.footerRows(hasDescription: true, hasPlace: true), [.description, .title, .place])
        XCTAssertEqual(PhotoFullScreenLayout.footerRows(hasDescription: true, hasPlace: false), [.description, .title])
    }

    func testWithoutADescriptionTheFooterIsExactlyWhatItAlwaysWasNoEmptyLineNoLabel() {
        XCTAssertEqual(PhotoFullScreenLayout.footerRows(hasDescription: false, hasPlace: true), [.title, .place])
        XCTAssertEqual(PhotoFullScreenLayout.footerRows(hasDescription: false, hasPlace: false), [.title])
    }

    func testNoDescriptionAddsNoHeightSoTheFooterIsTheOneOfBefore() {
        XCTAssertEqual(PhotoFullScreenLayout.descriptionHeight(unitCount: 0, fontSize: 16, lineHeight: 22, width: 343), 0)
        XCTAssertEqual(PhotoFullScreenLayout.descriptionHeight(unitCount: 50, fontSize: 16, lineHeight: 22, width: 0), 0)
    }

    func testTheDescriptionHeightGrowsWithTheLengthAndWithTheFontSize() {
        let short = PhotoFullScreenLayout.descriptionHeight(unitCount: 20, fontSize: 16, lineHeight: 22, width: 343)
        let full = PhotoFullScreenLayout.descriptionHeight(unitCount: 140, fontSize: 16, lineHeight: 22, width: 343)
        let large = PhotoFullScreenLayout.descriptionHeight(unitCount: 140, fontSize: 44, lineHeight: 60, width: 343)

        XCTAssertGreaterThan(full, short)
        XCTAssertGreaterThan(large, full)
        // 140 caractères tiennent sur environ 4 lignes en police normale (le ticket annonce « environ 4 lignes »).
        XCTAssertEqual(full, 4 * 22 + PhotoFullScreenLayout.captionSpacing, accuracy: 0.001)
    }

    func testALongDescriptionFitsAtNormalSizeOnTallPhonesAndScrollsOnSmallOnesAndWithTheLargestFont() {
        let fade = PhotoFullScreenLayout.fadeHeight
        let normal = PhotoFullScreenLayout.footerEstimate + 34
            + PhotoFullScreenLayout.descriptionHeight(unitCount: 140, fontSize: 16, lineHeight: 22, width: 343) + fade
        XCTAssertFalse(PhotoFullScreenLayout.footerScrolls(estimate: normal, screenHeight: 812), "11 Pro : tient dans 45 %")
        XCTAssertTrue(PhotoFullScreenLayout.footerScrolls(estimate: normal, screenHeight: 647), "iPhone SE : défile")

        // La plus grande taille de texte : le pied (x 2,7), le corps (44) et l'interligne (60) grandissent ; il défile même sur un grand écran.
        let largest = PhotoFullScreenLayout.footerEstimate * 2.7 + 34
            + PhotoFullScreenLayout.descriptionHeight(unitCount: 140, fontSize: 44, lineHeight: 60, width: 343) + fade
        XCTAssertTrue(PhotoFullScreenLayout.footerScrolls(estimate: largest, screenHeight: 812))
        // La zone reste bornée à 45 % : la photo n'est jamais masquée en entier.
        XCTAssertLessThan(PhotoFullScreenLayout.footerMaxHeight(screenHeight: 647), 647 / 2)
    }

    func testTheDescriptionIsWhiteOnTheSameDarkVeilSoItKeepsAtLeast45To1EvenOverAWhitePhoto() {
        let contrast = PhotoFullScreenLayout.whiteTextContrast(overScrimAlpha: colorPhotoCaptionScrim.alpha)

        XCTAssertGreaterThanOrEqual(contrast, PhotoFullScreenLayout.minimumContrast, "contraste \(contrast)")
    }

    func testTheDescriptionStringsOfTheCatalogAreTheOnesOfTheTicket() throws {
        let expected: [String: (fr: String, en: String)] = [
            "update_description_placeholder": ("Renseigne une description", "Add a description"),
            "update_description_a11y_label": ("Description de ta photo (facultatif)", "Photo description (optional)"),
            "update_description_a11y_hint": ("Tes proches la verront avec ta photo pendant 24 h.", "Your loved ones will see it with your photo for 24 hours."),
            "update_description_counter": ("%1$lld/%2$lld", "%1$lld/%2$lld"),
            "update_description_counter_a11y": ("%1$lld caractères sur %2$lld", "%1$lld of %2$lld characters"),
            "update_description_limit": ("Limite de %1$lld caractères atteinte.", "Limit of %1$lld characters reached."),
            "update_description_shown_a11y": ("Description : %1$@", "Description: %1$@")
        ]
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])

        for (key, texts) in expected {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
            for (language, text) in [("fr", texts.fr), ("en", texts.en)] {
                let unit = try XCTUnwrap(localizations[language]?["stringUnit"] as? [String: Any], "\(key) \(language)")
                XCTAssertEqual(unit["value"] as? String, text, "\(key) \(language)")
            }
        }
    }
}
// swiftlint:enable line_length
